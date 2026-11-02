package com.buruna.shared.source.anilist;

import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.ExternalWorkNotFoundException;
import com.buruna.shared.source.SourceContentRating;
import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.shared.source.SourceWorkStatus;
import com.buruna.shared.source.SourceWorkSummary;
import com.buruna.shared.source.WorkMetadataSource;
import com.buruna.shared.source.anilist.AniListDtos.Media;
import com.buruna.shared.source.anilist.AniListDtos.MediaResponse;
import com.buruna.shared.source.anilist.AniListDtos.SearchResponse;
import com.buruna.shared.source.anilist.AniListDtos.Tag;
import com.buruna.shared.source.anilist.AniListDtos.Title;
import com.buruna.shared.time.Sleeper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adaptador do AniList para a porta {@link WorkMetadataSource} (ADR-54). Só mangá, manhwa e manhua:
 * light novel ({@code NOVEL}) fica fora da busca e, pedida por id, conta como inexistente. A API é GraphQL, sem
 * chave, e limita por IP (30 req/min em 2026-11). As chamadas são espaçadas para caber nesse
 * limite, e um 429 vira falha na hora: quem pede é o ADM numa tela, que tenta de novo.
 *
 * <p>Só tag com rank a partir de {@value #MIN_TAG_RANK} e que não seja spoiler entra, e só as que
 * têm par no vocabulário do catálogo ({@link AniListTags}).
 */
public class AniListSource implements WorkMetadataSource {

    static final String DEFAULT_API_URL = "https://graphql.anilist.co";
    static final String COVER_PREFIX = "https://s4.anilist.co/file/anilistcdn/";
    static final int MIN_TAG_RANK = 80;

    private static final Duration SPACING = Duration.ofSeconds(2);
    private static final String DESCRIPTION_LANGUAGE = "en";
    private static final Set<String> DEMOGRAPHICS = Set.of("Shounen", "Shoujo", "Seinen", "Josei");
    private static final Map<String, String> LANGUAGES = Map.of("JP", "ja", "KR", "ko", "CN", "zh", "TW", "zh");

    private static final String SEARCH_QUERY = """
            query ($search: String, $perPage: Int) {
              page: Page(perPage: $perPage) {
                media(search: $search, type: MANGA, format_not: NOVEL, sort: SEARCH_MATCH) {
                  id title { romaji english nativeTitle: native } startDate { year } status
                  countryOfOrigin isAdult genres coverImage { medium } siteUrl
                }
              }
            }""";

    private static final String MEDIA_QUERY = """
            query ($id: Int) {
              media: Media(id: $id, type: MANGA, format_not: NOVEL) {
                id title { romaji english nativeTitle: native } synonyms description(asHtml: false)
                startDate { year } status countryOfOrigin format isAdult genres
                tags { name rank isMediaSpoiler isGeneralSpoiler } coverImage { extraLarge } siteUrl
              }
            }""";

    // ~!texto!~ é spoiler no AniList; some inteiro da sinopse
    private static final Pattern SPOILER = Pattern.compile("~!.*?!~", Pattern.DOTALL);
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final RestTemplate restTemplate;
    private final String userAgent;
    private final Clock clock;
    private final Sleeper sleeper;
    private final String apiUrl;

    private Instant lastCall;

    public AniListSource(RestTemplate restTemplate, String userAgent, Clock clock, Sleeper sleeper) {
        this(restTemplate, userAgent, clock, sleeper, DEFAULT_API_URL);
    }

    AniListSource(RestTemplate restTemplate, String userAgent, Clock clock, Sleeper sleeper, String apiUrl) {
        this.restTemplate = restTemplate;
        this.userAgent = userAgent;
        this.clock = clock;
        this.sleeper = sleeper;
        this.apiUrl = apiUrl;
    }

    @Override
    public List<SourceWorkSummary> search(String title, int limit) {
        SearchResponse response = query(SEARCH_QUERY, Map.of("search", title, "perPage", limit), SearchResponse.class, null);
        if (response == null || response.data() == null || response.data().page() == null
                || response.data().page().media() == null) {
            throw new ExternalSourceException("Resposta da busca no AniList sem resultados");
        }
        return response.data().page().media().stream().map(AniListSource::toSummary).toList();
    }

    @Override
    public SourceWorkDetails fetchWork(String externalId) {
        // o id do AniList é um Int do GraphQL (32 bits): fora disso a API responderia 400
        int id;
        try {
            id = Integer.parseInt(externalId);
        } catch (NumberFormatException e) {
            throw new ExternalWorkNotFoundException(externalId);
        }
        MediaResponse response = query(MEDIA_QUERY, Map.of("id", id), MediaResponse.class, externalId);
        if (response == null || response.data() == null || response.data().media() == null) {
            throw new ExternalWorkNotFoundException(externalId);
        }
        return toDetails(response.data().media());
    }

    /**
     * Só aceita endereço de capa do próprio AniList: a URL chega do navegador do ADM (miniatura da
     * busca) e o endpoint não pode virar proxy aberto.
     */
    @Override
    public byte[] download(String url, long maxBytes) {
        if (url == null || !url.startsWith(COVER_PREFIX) || url.contains("..") || url.contains("?")) {
            throw new ExternalSourceException("URL de capa recusada: " + url);
        }
        try {
            return restTemplate.execute(URI.create(url), HttpMethod.GET,
                    request -> request.getHeaders().set(HttpHeaders.USER_AGENT, userAgent),
                    response -> readLimited(response.getBody(), maxBytes, url));
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ExternalSourceException("Falha ao baixar " + url, e);
        }
    }

    // ---------- HTTP ----------

    /** {@code externalId}: a obra pedida, para um 404 virar obra inexistente; nulo na busca. */
    private synchronized <T> T query(String query, Map<String, Object> variables, Class<T> type, String externalId) {
        waitForTurn();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            return restTemplate.exchange(URI.create(apiUrl), HttpMethod.POST,
                    new HttpEntity<>(Map.of("query", query, "variables", variables), headers), type).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            if (externalId != null) {
                throw new ExternalWorkNotFoundException(externalId);
            }
            throw new ExternalSourceException("AniList respondeu 404", e);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ExternalSourceException("Limite de taxa do AniList estourado", e);
        } catch (RestClientException e) {
            throw new ExternalSourceException("Falha ao consultar o AniList", e);
        }
    }

    private void waitForTurn() {
        if (lastCall != null) {
            Duration elapsed = Duration.between(lastCall, clock.instant());
            if (elapsed.compareTo(SPACING) < 0) {
                sleeper.sleep(SPACING.minus(elapsed));
            }
        }
        lastCall = clock.instant();
    }

    // para de ler ao passar do limite, sem puxar a imagem inteira para a memória
    private static byte[] readLimited(InputStream body, long maxBytes, String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = body.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ExternalSourceException("Capa passa de " + maxBytes + " bytes: " + url);
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    // ---------- conversão ----------

    private static SourceWorkSummary toSummary(Media media) {
        return new SourceWorkSummary(
                String.valueOf(media.id()),
                mainTitle(media.title()),
                media.startDate() == null ? null : media.startDate().year(),
                contentRating(media),
                LANGUAGES.get(media.countryOfOrigin()),
                status(media.status()),
                List.of(),
                media.coverImage() == null ? null : media.coverImage().medium(),
                media.siteUrl());
    }

    private static SourceWorkDetails toDetails(Media media) {
        String title = mainTitle(media.title());
        List<Tag> tags = media.tags() == null ? List.of() : media.tags();
        String description = media.description() == null ? "" : plainText(media.description());
        return new SourceWorkDetails(
                String.valueOf(media.id()),
                title,
                alternativeTitles(media, title),
                description.isEmpty() ? Map.of() : Map.of(DESCRIPTION_LANGUAGE, description),
                LANGUAGES.get(media.countryOfOrigin()),
                status(media.status()),
                media.startDate() == null ? null : media.startDate().year(),
                contentRating(media),
                catalogTags(media, tags),
                // decide o formato (webtoon), então vale com qualquer rank: é fato técnico, não opinião votada
                tags.stream().anyMatch(tag -> "Long Strip".equals(tag.name())),
                media.coverImage() == null ? null : media.coverImage().extraLarge(),
                media.siteUrl(),
                demographic(tags),
                List.of());
    }

    // romaji primeiro, como o título principal das obras que vieram do MangaDex
    private static String mainTitle(Title title) {
        if (title == null) {
            throw new ExternalSourceException("Obra do AniList sem título");
        }
        return firstNonBlank(title.romaji(), title.english(), title.nativeTitle())
                .orElseThrow(() -> new ExternalSourceException("Obra do AniList sem título"));
    }

    private static List<String> alternativeTitles(Media media, String mainTitle) {
        Set<String> result = new LinkedHashSet<>();
        if (media.title() != null) {
            result.add(media.title().english());
            result.add(media.title().nativeTitle());
        }
        if (media.synonyms() != null) {
            result.addAll(media.synonyms());
        }
        return result.stream().filter(t -> t != null && !t.isBlank() && !t.equals(mainTitle)).toList();
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * A sinopse vem com {@code <br>}, ênfase em HTML e spoilers {@code ~!…!~}: vira texto puro,
     * sem o spoiler.
     */
    static String plainText(String description) {
        String text = SPOILER.matcher(description).replaceAll("");
        text = LINE_BREAK.matcher(text).replaceAll("\n");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = text.replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&")
                .replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    private static SourceWorkStatus status(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "RELEASING" -> SourceWorkStatus.ONGOING;
            case "FINISHED" -> SourceWorkStatus.COMPLETED;
            case "HIATUS" -> SourceWorkStatus.HIATUS;
            case "CANCELLED" -> SourceWorkStatus.CANCELLED;
            default -> null;
        };
    }

    private static SourceContentRating contentRating(Media media) {
        List<String> genres = media.genres() == null ? List.of() : media.genres();
        if (Boolean.TRUE.equals(media.isAdult()) || genres.contains("Hentai")) {
            return SourceContentRating.PORNOGRAPHIC;
        }
        return genres.contains("Ecchi") ? SourceContentRating.SUGGESTIVE : SourceContentRating.SAFE;
    }

    private static List<SourceTag> catalogTags(Media media, List<Tag> tags) {
        Set<SourceTag> result = new LinkedHashSet<>();
        if (media.genres() != null) {
            media.genres().forEach(genre -> AniListTags.fromGenre(genre).ifPresent(result::add));
        }
        tags.stream().filter(AniListSource::counts)
                .forEach(tag -> AniListTags.fromTag(tag.name()).ifPresent(result::add));
        // o mapeamento da obra lê o one-shot pela tag, como no MangaDex
        if ("ONE_SHOT".equals(media.format())) {
            result.add(new SourceTag("Oneshot", SourceTagGroup.FORMAT));
        }
        return new ArrayList<>(result);
    }

    private static String demographic(List<Tag> tags) {
        return tags.stream()
                .filter(tag -> DEMOGRAPHICS.contains(tag.name()) && counts(tag))
                .max(Comparator.comparingInt(Tag::rank))
                .map(tag -> tag.name().toLowerCase(Locale.ROOT))
                .orElse(null);
    }

    private static boolean counts(Tag tag) {
        return tag.rank() != null && tag.rank() >= MIN_TAG_RANK
                && !Boolean.TRUE.equals(tag.isMediaSpoiler()) && !Boolean.TRUE.equals(tag.isGeneralSpoiler());
    }
}
