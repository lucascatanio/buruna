package com.buruna.sourcing.application;

import com.buruna.shared.media.ArchiveFormat;
import com.buruna.shared.media.ArchiveLimits;
import com.buruna.shared.media.ImageInspector.InspectedImage;
import com.buruna.shared.media.PageExtractor;
import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.SourceChapterInfo;
import com.buruna.shared.source.SourceGroup;
import com.buruna.shared.source.mangadownloader.MangaDownloaderClient;
import com.buruna.shared.source.mangadownloader.SiteListing;
import com.buruna.sourcing.domain.SourceChapter;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Qualquer site do manga-downloader (ADR-54): lista pela CLI e baixa cada capítulo em CBZ, que o
 * {@link PageExtractor} abre com os mesmos limites do upload. As páginas vêm numa qualidade só.
 *
 * <p>A CLI baixa por número, com filtro de idioma e grupo: capítulo sem número não tem como ser
 * baixado, e duas versões do mesmo número, idioma e grupo ficam com a que a CLI escolher.
 */
@Component
class MangaDownloaderChapterSource implements ChapterSource {

    private static final Logger log = LoggerFactory.getLogger(MangaDownloaderChapterSource.class);
    private static final long MB = 1024L * 1024;
    // site sem id de capítulo: a chave é número (ou título), idioma e grupos
    static final String NO_ID_PREFIX = "sem-id:";
    private static final int MAX_EXTERNAL_ID_LENGTH = 500;
    // "Chapter 12", "Capítulo 12 - Título": o rótulo que a CLI monta quando o site não dá título
    private static final Pattern NUMBER_LABEL = Pattern.compile(
            "(?i)^\\s*(?:chapter|chapitre|cap[ií]tulo|capitolo|cap|ch|episode|ep)\\.?\\s*0*(\\d+(?:\\.\\d+)?)\\s*(?:[:.\\-–—]+\\s*)?");

    private static final Pattern ESCAPE = Pattern.compile("%25|%2C");

    private final MangaDownloaderClient client;
    private final PageExtractor pageExtractor;
    private final ArchiveLimits limits;

    MangaDownloaderChapterSource(MangaDownloaderClient client,
                                 PageExtractor pageExtractor,
                                 @Value("${app.ingest.max-pages}") int maxPages,
                                 @Value("${app.ingest.max-total-mb}") long maxTotalMb,
                                 @Value("${app.ingest.max-page-mb}") long maxPageMb) {
        this.client = client;
        this.pageExtractor = pageExtractor;
        this.limits = new ArchiveLimits(maxPages, maxTotalMb * MB, maxPageMb * MB);
    }

    @Override
    public SourceName source() {
        return SourceName.MANGA_DOWNLOADER;
    }

    @Override
    public List<SourceChapterInfo> listChapters(TrackedWork tracked) {
        Set<String> codes = tracked.sourceLanguageCodes().stream()
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return client.list(tracked.getExternalId()).chapters().stream()
                .filter(chapter -> !chapter.locked())
                .filter(chapter -> codes.contains(languageOf(chapter).toLowerCase(Locale.ROOT)))
                .map(MangaDownloaderChapterSource::toInfo)
                .toList();
    }

    @Override
    public Set<String> availableLanguages(TrackedWork tracked) {
        return client.list(tracked.getExternalId()).chapters().stream()
                .map(MangaDownloaderChapterSource::languageOf)
                .collect(Collectors.toSet());
    }

    @Override
    public void fetchPages(TrackedWork tracked, SourceChapter version, Consumer<FetchedPage> sink) {
        String number = version.getNumber().orElseThrow(() ->
                new ExternalSourceException("Capítulo sem número: o manga-downloader só baixa pelo número."));
        String language = tracked.sourceCodeFor(version.getLanguage()).orElse("");
        Path directory = null;
        try {
            directory = Files.createTempDirectory("manga-downloader-");
            Path archive = client.download(tracked.getExternalId(), number, language, firstGroup(version), directory);
            int[] count = {0};
            pageExtractor.extract(archive, ArchiveFormat.CBZ, limits, page -> {
                count[0]++;
                sink.accept(new FetchedPage(new InspectedImage(page.content(), page.contentType(), page.extension(),
                        page.width(), page.height()), null));
            });
            if (count[0] == 0) {
                throw new ExternalSourceException("O capítulo " + number + " veio sem páginas.");
            }
        } catch (IOException e) {
            throw new ExternalSourceException("Não foi possível preparar o download", e);
        } finally {
            deleteQuietly(directory);
        }
    }

    static SourceChapterInfo toInfo(SiteListing.Chapter chapter) {
        String number = chapter.unnumbered() ? null : formatNumber(chapter.number());
        List<String> names = groupNames(chapter);
        String language = languageOf(chapter);
        String externalId = chapter.id() == null || chapter.id().isBlank()
                ? syntheticId(number, chapter.title(), language, names)
                : truncate(chapter.id());
        return new SourceChapterInfo(externalId, language, number,
                chapter.volume() > 0 ? String.valueOf(chapter.volume()) : null,
                titleOf(chapter.title(), number).orElse(null),
                names.stream().map(name -> new SourceGroup(groupId(name), name)).toList(),
                0, null, null);
    }

    /** {@code 12.0} → "12", {@code 34.5} → "34.5": o texto que a CLI aceita no intervalo. */
    static String formatNumber(double number) {
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    /**
     * O título sem o rótulo que só repete o número ("Capítulo 12" fica sem título; "Chapter 12 -
     * Fim" vira "Fim"). Sem número, o título fica como veio: ele é o que identifica o capítulo.
     */
    static Optional<String> titleOf(String title, String number) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER_LABEL.matcher(title);
        if (number != null && matcher.find() && new BigDecimal(matcher.group(1)).compareTo(new BigDecimal(number)) == 0) {
            String rest = title.substring(matcher.end()).strip();
            return rest.isEmpty() ? Optional.empty() : Optional.of(rest);
        }
        return Optional.of(title);
    }

    /**
     * O id de grupo é o nome com {@code %} e {@code ,} escapados: os ids são guardados juntos por
     * vírgula, e um nome pode ter vírgula. Só esses dois, para um nome em coreano ou japonês caber
     * nas colunas. O inverso, {@link #groupName}, é o que vai para o {@code --scanlator}.
     */
    static String groupId(String name) {
        return name.replace("%", "%25").replace(",", "%2C");
    }

    static String groupName(String groupId) {
        return ESCAPE.matcher(groupId).replaceAll(match -> match.group().equals("%2C") ? "," : "%");
    }

    // a CLI filtra por um grupo: o primeiro basta, porque a versão é do grupo todo
    private static String firstGroup(SourceChapter version) {
        String ids = version.getGroupIds();
        return ids == null || ids.isBlank() ? "" : groupName(ids.split(",")[0]);
    }

    private static List<String> groupNames(SiteListing.Chapter chapter) {
        if (chapter.groups() != null && !chapter.groups().isEmpty()) {
            return chapter.groups();
        }
        return chapter.group() == null || chapter.group().isBlank() ? List.of() : List.of(chapter.group());
    }

    private static String languageOf(SiteListing.Chapter chapter) {
        return chapter.language() == null ? "" : chapter.language();
    }

    private static String syntheticId(String number, String title, String language, List<String> groups) {
        return truncate(NO_ID_PREFIX + (number != null ? number : "#" + (title == null ? "" : title))
                + "|" + language + "|" + String.join(",", groups));
    }

    // cabe na coluna; dois ids que só diferem depois de 500 caracteres colidem, o que não se vê
    private static String truncate(String externalId) {
        return externalId.length() <= MAX_EXTERNAL_ID_LENGTH ? externalId : externalId.substring(0, MAX_EXTERNAL_ID_LENGTH);
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Não foi possível apagar {}", file, e);
                }
            });
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}", directory, e);
        }
    }
}
