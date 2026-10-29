package com.buruna.shared.source.mangadex;

import com.buruna.shared.time.Sleeper;
import com.buruna.shared.source.ExternalSource;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.ExternalWorkNotFoundException;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceContentRating;
import com.buruna.shared.source.SourceGroup;
import com.buruna.shared.source.SourcePage;
import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.shared.source.SourceWorkStatus;
import com.buruna.shared.source.SourceWorkSummary;
import com.buruna.shared.source.mangadex.MangaDexDtos.AtHomeResponse;
import com.buruna.shared.source.mangadex.MangaDexDtos.Chapter;
import com.buruna.shared.source.mangadex.MangaDexDtos.ChapterFeed;
import com.buruna.shared.source.mangadex.MangaDexDtos.Manga;
import com.buruna.shared.source.mangadex.MangaDexDtos.MangaAttributes;
import com.buruna.shared.source.mangadex.MangaDexDtos.MangaList;
import com.buruna.shared.source.mangadex.MangaDexDtos.MangaResponse;
import com.buruna.shared.source.mangadex.MangaDexDtos.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adaptador do MangaDex para a porta {@link ExternalSource}. Segue as regras da API:
 * User-Agent próprio em toda chamada, nunca Authorization nem Via, espaçamento entre
 * chamadas (limite global de ~5 req/s por IP e 40/min no at-home), espera no 429 e report
 * ao MangaDex@Home para imagens de nós de terceiros.
 *
 * <p>Os métodos de consulta são {@code synchronized}: o espaçamento depende de estado
 * compartilhado e o uso é serial (um executor por vez).
 */
public class MangaDexSource implements ExternalSource {

    private static final Logger log = LoggerFactory.getLogger(MangaDexSource.class);

    static final String DEFAULT_API_BASE_URL = "https://api.mangadex.org";
    static final String DEFAULT_UPLOADS_BASE_URL = "https://uploads.mangadex.org";
    static final String DEFAULT_REPORT_URL = "https://api.mangadex.network/report";
    // o 522 do endpoint de report pede pelo menos 120 s de espera
    private static final Duration REPORT_PAUSE_AFTER_FAILURE = Duration.ofMinutes(2);

    private static final String TITLE_URL = "https://mangadex.org/title/";
    private static final String LONG_STRIP_TAG = "Long Strip";
    private static final String OFFICIAL_HOST = "mangadex.org";

    private static final Duration API_SPACING = Duration.ofMillis(250);
    private static final Duration AT_HOME_SPACING = Duration.ofMillis(1500);
    private static final Duration DEFAULT_RATE_LIMIT_WAIT = Duration.ofSeconds(60);
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofMinutes(5);
    private static final String RETRY_AFTER_HEADER = "X-RateLimit-Retry-After";
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final int MAX_API_ATTEMPTS = 3;
    private static final Duration API_BACKOFF_STEP = Duration.ofSeconds(2);

    private static final int DOWNLOAD_EXTRA_ATTEMPTS = 2;
    private static final Duration DOWNLOAD_RETRY_WAIT = Duration.ofSeconds(2);

    private static final int FEED_PAGE_SIZE = 500;
    // A API recusa offset + limit > 10000.
    private static final int FEED_MAX_WINDOW = 10_000;

    private static final String[] CONTENT_RATINGS = {"safe", "suggestive", "erotica", "pornographic"};

    private final RestTemplate restTemplate;
    private final String userAgent;
    private final Clock clock;
    private final Sleeper sleeper;
    private final String apiBaseUrl;
    private final String uploadsBaseUrl;
    private final String reportUrl;
    private final RestTemplate reportRestTemplate;

    private Instant lastApiCall;
    // depois de um report que falhou, os próximos esperam: o endpoint fora do ar não pode
    // segurar cada página pelo tempo do timeout
    private volatile Instant reportSuspendedUntil = Instant.MIN;
    private Instant lastAtHomeCall;

    public MangaDexSource(RestTemplate restTemplate, String userAgent, Clock clock, Sleeper sleeper) {
        this(restTemplate, userAgent, clock, sleeper,
                DEFAULT_API_BASE_URL, DEFAULT_UPLOADS_BASE_URL, DEFAULT_REPORT_URL, restTemplate);
    }

    /** {@code reportRestTemplate}: com timeout curto, porque o report não pode atrasar o download. */
    public MangaDexSource(RestTemplate restTemplate, String userAgent, Clock clock, Sleeper sleeper,
                          RestTemplate reportRestTemplate) {
        this(restTemplate, userAgent, clock, sleeper,
                DEFAULT_API_BASE_URL, DEFAULT_UPLOADS_BASE_URL, DEFAULT_REPORT_URL, reportRestTemplate);
    }

    /** As URLs existem para os testes apontarem para um servidor simulado. */
    public MangaDexSource(RestTemplate restTemplate, String userAgent, Clock clock, Sleeper sleeper,
                          String apiBaseUrl, String uploadsBaseUrl, String reportUrl) {
        this(restTemplate, userAgent, clock, sleeper, apiBaseUrl, uploadsBaseUrl, reportUrl, restTemplate);
    }

    private MangaDexSource(RestTemplate restTemplate, String userAgent, Clock clock, Sleeper sleeper,
                           String apiBaseUrl, String uploadsBaseUrl, String reportUrl,
                           RestTemplate reportRestTemplate) {
        this.restTemplate = restTemplate;
        this.reportRestTemplate = reportRestTemplate;
        this.userAgent = userAgent;
        this.clock = clock;
        this.sleeper = sleeper;
        this.apiBaseUrl = withoutTrailingSlash(apiBaseUrl);
        this.uploadsBaseUrl = withoutTrailingSlash(uploadsBaseUrl);
        this.reportUrl = reportUrl;
    }

    @Override
    public synchronized List<SourceWorkSummary> search(String title, int limit) {
        List<String> params = new ArrayList<>(List.of(
                "title", title,
                "limit", String.valueOf(limit),
                "includes[]", "cover_art",
                "order[relevance]", "desc"));
        addContentRatings(params);
        MangaList response = apiGet(uri(apiBaseUrl + "/manga", params), MangaList.class, false);
        if (response.data() == null) {
            return List.of();
        }
        return response.data().stream().map(this::toSummary).toList();
    }

    @Override
    public synchronized SourceWorkDetails fetchWork(String externalId) {
        URI uri = uri(apiBaseUrl + "/manga/" + pathSegment(externalId), List.of("includes[]", "cover_art"));
        MangaResponse response;
        try {
            response = apiGet(uri, MangaResponse.class, false);
        } catch (NotFoundResponse e) {
            throw new ExternalWorkNotFoundException(externalId);
        }
        if (response.data() == null || response.data().attributes() == null) {
            throw new ExternalSourceException("Resposta sem dados para a obra " + externalId);
        }
        return toDetails(response.data());
    }

    @Override
    public synchronized List<SourceChapterInfo> listChapters(String externalId, Set<String> languages) {
        List<String> base = new ArrayList<>();
        // Ordenado para a URL ser estável.
        for (String language : new TreeSet<>(languages.stream().map(MangaDexLanguages::toMangaDex).toList())) {
            base.add("translatedLanguage[]");
            base.add(language);
        }
        List<SourceChapterInfo> chapters = new ArrayList<>();
        int offset = 0;
        int total = 0;
        do {
            if (offset + FEED_PAGE_SIZE > FEED_MAX_WINDOW) {
                log.warn("Feed da obra {} tem {} capítulos; só os primeiros {} cabem na janela da API",
                        externalId, total, offset);
                break;
            }
            List<String> params = new ArrayList<>(base);
            params.addAll(List.of(
                    "limit", String.valueOf(FEED_PAGE_SIZE),
                    "offset", String.valueOf(offset),
                    "order[chapter]", "asc",
                    "order[volume]", "asc",
                    "includes[]", "scanlation_group",
                    "includeExternalUrl", "0",
                    "includeEmptyPages", "0",
                    "includeFuturePublishAt", "0"));
            addContentRatings(params);
            ChapterFeed feed = apiGet(
                    uri(apiBaseUrl + "/manga/" + pathSegment(externalId) + "/feed", params),
                    ChapterFeed.class, false);
            List<Chapter> page = feed.data() == null ? List.of() : feed.data();
            total = feed.total() == null ? 0 : feed.total();
            for (Chapter chapter : page) {
                toChapterInfo(chapter).ifPresent(chapters::add);
            }
            if (page.isEmpty()) {
                break;
            }
            offset += FEED_PAGE_SIZE;
        } while (offset < total);
        return chapters;
    }

    @Override
    public synchronized int countPublishedChapters(String externalId, String language) {
        List<String> params = new ArrayList<>(List.of(
                "translatedLanguage[]", MangaDexLanguages.toMangaDex(language),
                "limit", "1",
                "includeUnavailable", "1",
                "includeExternalUrl", "1",
                "includeEmptyPages", "1"));
        addContentRatings(params);
        ChapterFeed feed = apiGet(uri(apiBaseUrl + "/manga/" + pathSegment(externalId) + "/feed", params),
                ChapterFeed.class, false);
        return feed.total() == null ? 0 : feed.total();
    }

    @Override
    public synchronized List<SourcePage> fetchPages(String externalChapterId) {
        URI uri = uri(apiBaseUrl + "/at-home/server/" + pathSegment(externalChapterId), List.of());
        AtHomeResponse response = apiGet(uri, AtHomeResponse.class, true);
        if (response.baseUrl() == null || response.chapter() == null || response.chapter().data() == null) {
            throw new ExternalSourceException("Resposta do at-home incompleta para o capítulo " + externalChapterId);
        }
        // O baseUrl vale como veio (muda por nó); só tiramos uma barra final para não duplicar.
        String baseUrl = withoutTrailingSlash(response.baseUrl());
        String hash = response.chapter().hash();
        List<String> data = response.chapter().data();
        List<String> dataSaver = response.chapter().dataSaver() == null ? List.of() : response.chapter().dataSaver();
        List<SourcePage> pages = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            String saver = i < dataSaver.size() ? baseUrl + "/data-saver/" + hash + "/" + dataSaver.get(i) : null;
            pages.add(new SourcePage(baseUrl + "/data/" + hash + "/" + data.get(i), saver));
        }
        return pages;
    }

    @Override
    public byte[] download(String url, long maxBytes) {
        URI uri = requireSafeImageUrl(url);
        boolean mustReport = shouldReport(uri);
        int attempt = 0;
        while (true) {
            attempt++;
            Instant start = clock.instant();
            try {
                Download download = restTemplate.execute(uri, HttpMethod.GET,
                        request -> request.getHeaders().set(HttpHeaders.USER_AGENT, userAgent),
                        response -> readLimited(response.getBody(), maxBytes,
                                response.getHeaders().getFirst("X-Cache")));
                if (download == null) {
                    throw new ExternalSourceException("Resposta vazia ao baixar " + url);
                }
                if (mustReport) {
                    // Passar do limite é problema nosso, não do nó: o report é de sucesso.
                    report(url, true, download.cached(), download.size(), start);
                }
                if (download.tooLarge()) {
                    throw new ExternalSourceException("Imagem passa de " + maxBytes + " bytes: " + url);
                }
                return download.bytes();
            } catch (RestClientException e) {
                if (mustReport) {
                    report(url, false, false, 0, start);
                }
                if (attempt > DOWNLOAD_EXTRA_ATTEMPTS) {
                    throw new ExternalSourceException("Falha ao baixar " + url, e);
                }
                log.warn("Falha ao baixar imagem (tentativa {}): {}", attempt, e.getMessage());
                sleeper.sleep(DOWNLOAD_RETRY_WAIT);
            }
        }
    }

    // ---------- HTTP da API ----------

    private <T> T apiGet(URI uri, Class<T> type, boolean atHome) {
        int rateLimitRetries = 0;
        int attempts = 0;
        while (true) {
            waitForTurn(atHome);
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.USER_AGENT, userAgent);
                T body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
                if (body == null) {
                    throw new ExternalSourceException("Resposta vazia da API: " + uri.getPath());
                }
                return body;
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (++rateLimitRetries > MAX_RATE_LIMIT_RETRIES) {
                    throw new ExternalSourceException("Limite de taxa do MangaDex estourado: " + uri.getPath(), e);
                }
                Duration wait = rateLimitWait(e.getResponseHeaders());
                log.warn("MangaDex respondeu 429; aguardando {} s", wait.toSeconds());
                sleeper.sleep(wait);
            } catch (HttpClientErrorException.NotFound e) {
                throw new NotFoundResponse(uri.getPath(), e);
            } catch (HttpClientErrorException e) {
                throw new ExternalSourceException(
                        "MangaDex recusou a chamada (" + e.getStatusCode().value() + "): " + uri.getPath(), e);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (++attempts >= MAX_API_ATTEMPTS) {
                    throw new ExternalSourceException("MangaDex indisponível: " + uri.getPath(), e);
                }
                Duration wait = API_BACKOFF_STEP.multipliedBy(attempts); // 2 s, 4 s
                log.warn("Falha ao chamar o MangaDex (tentativa {}); nova tentativa em {} s", attempts, wait.toSeconds());
                sleeper.sleep(wait);
            } catch (RestClientException e) {
                throw new ExternalSourceException("Resposta inesperada do MangaDex: " + uri.getPath(), e);
            }
        }
    }

    private void waitForTurn(boolean atHome) {
        if (atHome) {
            sleepUntilSpaced(lastAtHomeCall, AT_HOME_SPACING);
        }
        sleepUntilSpaced(lastApiCall, API_SPACING);
        Instant now = clock.instant();
        lastApiCall = now;
        if (atHome) {
            lastAtHomeCall = now;
        }
    }

    private void sleepUntilSpaced(Instant last, Duration spacing) {
        if (last == null) {
            return;
        }
        Duration remaining = spacing.minus(Duration.between(last, clock.instant()));
        if (!remaining.isNegative() && !remaining.isZero()) {
            sleeper.sleep(remaining);
        }
    }

    private Duration rateLimitWait(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(RETRY_AFTER_HEADER);
        if (value == null) {
            return DEFAULT_RATE_LIMIT_WAIT;
        }
        try {
            Duration wait = Duration.between(clock.instant(), Instant.ofEpochSecond(Long.parseLong(value.trim())));
            if (wait.isNegative()) {
                return Duration.ZERO;
            }
            return wait.compareTo(MAX_RATE_LIMIT_WAIT) > 0 ? MAX_RATE_LIMIT_WAIT : wait;
        } catch (NumberFormatException e) {
            return DEFAULT_RATE_LIMIT_WAIT;
        }
    }

    // ---------- download e report ----------

    private record Download(byte[] bytes, long size, boolean cached, boolean tooLarge) {
    }

    // Lê no máximo maxBytes; ao passar, para de ler (não puxa uma imagem enorme para a memória);
    // size é o que foi lido até ali.
    private static Download readLimited(InputStream body, long maxBytes, String xCache) throws IOException {
        boolean cached = xCache != null && xCache.toUpperCase(Locale.ROOT).startsWith("HIT");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = body.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return new Download(new byte[0], total, cached, true);
            }
            out.write(buffer, 0, read);
        }
        return new Download(out.toByteArray(), total, cached, false);
    }

    /**
     * A URL da imagem vem da resposta da fonte (o {@code baseUrl} do at-home). Só HTTPS com nome
     * de host público: o Job roda na VPC, e um endereço adulterado não pode alcançar a rede
     * interna nem o servidor de metadados do GCP. Redirects ficam desligados no cliente.
     */
    static URI requireSafeImageUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ExternalSourceException("URL de imagem inválida: " + url, e);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean ipLiteral = host.matches("[0-9.]+") || host.startsWith("[") || host.contains(":");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank() || ipLiteral || !host.contains(".")
                || host.equals("localhost") || host.endsWith(".internal") || host.endsWith(".local")
                || uri.getUserInfo() != null) {
            throw new ExternalSourceException("URL de imagem recusada: " + url);
        }
        return uri;
    }

    private static boolean shouldReport(URI uri) {
        String host = uri.getHost();
        return host == null || !host.contains(OFFICIAL_HOST);
    }

    // Falha do report nunca atrapalha o download.
    private void report(String url, boolean success, boolean cached, long bytes, Instant start) {
        if (clock.instant().isBefore(reportSuspendedUntil)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("success", success);
        body.put("cached", cached);
        body.put("bytes", bytes);
        body.put("duration", Duration.between(start, clock.instant()).toMillis());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        try {
            reportRestTemplate.exchange(URI.create(reportUrl), HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
        } catch (RestClientException e) {
            reportSuspendedUntil = clock.instant().plus(REPORT_PAUSE_AFTER_FAILURE);
            // só o tipo e o status: o corpo do erro (página do Cloudflare) não ajuda no log
            String status = e instanceof RestClientResponseException response
                    ? String.valueOf(response.getStatusCode().value()) : e.getClass().getSimpleName();
            log.warn("Falha ao reportar ao MangaDex@Home ({}); reports pausados por {}", status, REPORT_PAUSE_AFTER_FAILURE);
        }
    }

    // ---------- mapeamento ----------

    private SourceWorkSummary toSummary(Manga manga) {
        MangaAttributes attributes = manga.attributes();
        String coverFile = coverFileName(manga);
        return new SourceWorkSummary(
                manga.id(),
                mainTitle(attributes),
                attributes.year(),
                contentRating(attributes.contentRating()),
                MangaDexLanguages.toBcp47(attributes.originalLanguage()),
                status(attributes.status()),
                languages(attributes.availableTranslatedLanguages()),
                coverFile == null ? null : uploadsBaseUrl + "/covers/" + manga.id() + "/" + coverFile + ".256.jpg",
                TITLE_URL + manga.id());
    }

    private SourceWorkDetails toDetails(Manga manga) {
        MangaAttributes attributes = manga.attributes();
        String title = mainTitle(attributes);
        List<SourceTag> tags = tags(attributes);
        String coverFile = coverFileName(manga);
        return new SourceWorkDetails(
                manga.id(),
                title,
                alternativeTitles(attributes, title),
                descriptions(attributes),
                MangaDexLanguages.toBcp47(attributes.originalLanguage()),
                status(attributes.status()),
                attributes.year(),
                contentRating(attributes.contentRating()),
                tags,
                tags.stream().anyMatch(tag -> LONG_STRIP_TAG.equals(tag.name())),
                coverFile == null ? null : uploadsBaseUrl + "/covers/" + manga.id() + "/" + coverFile,
                TITLE_URL + manga.id(),
                attributes.publicationDemographic(),
                languages(attributes.availableTranslatedLanguages()));
    }

    private Optional<SourceChapterInfo> toChapterInfo(Chapter chapter) {
        var attributes = chapter.attributes();
        if (attributes == null || attributes.externalUrl() != null || Boolean.TRUE.equals(attributes.isUnavailable())
                || attributes.pages() == null || attributes.pages() == 0) {
            return Optional.empty();
        }
        return Optional.of(new SourceChapterInfo(
                chapter.id(),
                MangaDexLanguages.toBcp47(attributes.translatedLanguage()),
                attributes.chapter(),
                attributes.volume(),
                attributes.title() == null || attributes.title().isBlank() ? null : attributes.title(),
                groups(chapter.relationships()),
                attributes.pages(),
                parseDate(attributes.publishAt()),
                parseDate(attributes.updatedAt())));
    }

    // Prefere en; senão o primeiro idioma que vier.
    private static String mainTitle(MangaAttributes attributes) {
        Map<String, String> titles = attributes.title();
        if (titles == null || titles.isEmpty()) {
            throw new ExternalSourceException("Obra do MangaDex sem título");
        }
        String english = titles.get("en");
        return english != null ? english : titles.values().iterator().next();
    }

    private static List<String> alternativeTitles(MangaAttributes attributes, String mainTitle) {
        Set<String> result = new LinkedHashSet<>();
        if (attributes.altTitles() != null) {
            for (Map<String, String> byLanguage : attributes.altTitles()) {
                for (String alt : byLanguage.values()) {
                    if (alt != null && !alt.isBlank() && !alt.equals(mainTitle)) {
                        result.add(alt);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, String> descriptions(MangaAttributes attributes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (attributes.description() != null) {
            attributes.description().forEach((language, text) -> {
                if (text != null && !text.isBlank()) {
                    result.putIfAbsent(MangaDexLanguages.toBcp47(language), text);
                }
            });
        }
        return result;
    }

    private static List<SourceTag> tags(MangaAttributes attributes) {
        List<SourceTag> result = new ArrayList<>();
        if (attributes.tags() == null) {
            return result;
        }
        for (var tag : attributes.tags()) {
            if (tag.attributes() == null || tag.attributes().name() == null || tag.attributes().name().isEmpty()) {
                continue;
            }
            String name = tag.attributes().name().getOrDefault("en", tag.attributes().name().values().iterator().next());
            SourceTagGroup group = tagGroup(tag.attributes().group());
            if (group == null) {
                log.warn("Tag '{}' com grupo desconhecido '{}'; ignorada", name, tag.attributes().group());
                continue;
            }
            result.add(new SourceTag(name, group));
        }
        return result;
    }

    private static SourceTagGroup tagGroup(String group) {
        if (group == null) {
            return null;
        }
        try {
            return SourceTagGroup.valueOf(group.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> languages(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().filter(code -> code != null).map(MangaDexLanguages::toBcp47).toList();
    }

    private static SourceContentRating contentRating(String value) {
        try {
            return SourceContentRating.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ExternalSourceException("Classificação de conteúdo desconhecida no MangaDex: " + value, e);
        }
    }

    private static SourceWorkStatus status(String value) {
        try {
            return SourceWorkStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ExternalSourceException("Status desconhecido no MangaDex: " + value, e);
        }
    }

    private static String coverFileName(Manga manga) {
        if (manga.relationships() == null) {
            return null;
        }
        return manga.relationships().stream()
                .filter(rel -> "cover_art".equals(rel.type()) && rel.attributes() != null)
                .map(rel -> rel.attributes().fileName())
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static List<SourceGroup> groups(List<Relationship> relationships) {
        if (relationships == null) {
            return List.of();
        }
        return relationships.stream()
                .filter(rel -> "scanlation_group".equals(rel.type()))
                .map(rel -> new SourceGroup(rel.id(), rel.attributes() != null && rel.attributes().name() != null
                        ? rel.attributes().name() : rel.id()))
                .toList();
    }

    private static OffsetDateTime parseDate(String value) {
        return value == null ? null : OffsetDateTime.parse(value);
    }

    // ---------- URLs ----------

    private static void addContentRatings(List<String> params) {
        for (String rating : CONTENT_RATINGS) {
            params.add("contentRating[]");
            params.add(rating);
        }
    }

    // Nome e valor codificados por inteiro (inclui [] e +), que a API lê como deve.
    private static URI uri(String base, List<String> nameValuePairs) {
        StringBuilder url = new StringBuilder(base);
        for (int i = 0; i < nameValuePairs.size(); i += 2) {
            url.append(i == 0 ? '?' : '&')
                    .append(UriUtils.encode(nameValuePairs.get(i), StandardCharsets.UTF_8))
                    .append('=')
                    .append(UriUtils.encode(nameValuePairs.get(i + 1), StandardCharsets.UTF_8));
        }
        return URI.create(url.toString());
    }

    private static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 404 da API; só o fetchWork o traduz para "obra não encontrada". */
    private static class NotFoundResponse extends ExternalSourceException {
        NotFoundResponse(String path, Throwable cause) {
            super("Recurso não encontrado no MangaDex: " + path, cause);
        }
    }
}
