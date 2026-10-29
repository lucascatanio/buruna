package com.buruna.shared.source.mangadex;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MangaDexSourceTest {

    private static final String API = "https://api.test";
    private static final String UPLOADS = "https://uploads.test";
    private static final String REPORT = "https://report.test/report";
    private static final String USER_AGENT = "Buruna-Test/1.0";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private MockRestServiceServer server;
    private TestClock clock;
    private List<Duration> sleeps;
    private MangaDexSource source;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        clock = new TestClock(NOW);
        sleeps = new ArrayList<>();
        // O sleeper de teste só registra e adianta o relógio, sem dormir.
        source = new MangaDexSource(restTemplate, USER_AGENT, clock, duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        }, API, UPLOADS, REPORT);
    }

    // ---------- search ----------

    @Test
    void shouldMapSummary_whenSearchingByTitle() {
        // Arrange
        server.expect(queryContains("/manga", "title=One Piece", "limit=5", "includes[]=cover_art",
                        "order[relevance]=desc", "contentRating[]=safe", "contentRating[]=pornographic"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(headerDoesNotExist("Via"))
                .andRespond(withSuccess("""
                        {"result":"ok","data":[{
                          "id":"m-1",
                          "attributes":{
                            "title":{"ja-ro":"Wan Pisu","en":"One Piece"},
                            "originalLanguage":"ja","status":"ongoing","year":1997,
                            "contentRating":"suggestive",
                            "availableTranslatedLanguages":["en","pt-br","es-la",null]
                          },
                          "relationships":[
                            {"id":"c-1","type":"cover_art","attributes":{"fileName":"capa.jpg"}}
                          ]
                        }]}
                        """, MediaType.APPLICATION_JSON));

        // Act
        List<SourceWorkSummary> result = source.search("One Piece", 5);

        // Assert
        assertThat(result).hasSize(1);
        SourceWorkSummary summary = result.get(0);
        assertThat(summary.externalId()).isEqualTo("m-1");
        assertThat(summary.title()).isEqualTo("One Piece");
        assertThat(summary.year()).isEqualTo(1997);
        assertThat(summary.contentRating()).isEqualTo(SourceContentRating.SUGGESTIVE);
        assertThat(summary.status()).isEqualTo(SourceWorkStatus.ONGOING);
        assertThat(summary.originalLanguage()).isEqualTo("ja");
        assertThat(summary.availableLanguages()).containsExactly("en", "pt-BR", "es-419");
        assertThat(summary.coverThumbnailUrl()).isEqualTo(UPLOADS + "/covers/m-1/capa.jpg.256.jpg");
        assertThat(summary.sourceUrl()).isEqualTo("https://mangadex.org/title/m-1");
        server.verify();
    }

    @Test
    void shouldUseFirstTitleAndNullCoverAndYear_whenWorkHasNoEnglishTitleOrCover() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga?")))
                .andRespond(withSuccess("""
                        {"data":[{
                          "id":"m-2",
                          "attributes":{
                            "title":{"ja-ro":"Nihon no Hon"},
                            "originalLanguage":"ja-ro","status":"hiatus","year":null,
                            "contentRating":"safe","availableTranslatedLanguages":[]
                          },
                          "relationships":[{"id":"a-1","type":"author"}]
                        }]}
                        """, MediaType.APPLICATION_JSON));

        // Act
        SourceWorkSummary summary = source.search("nihon", 10).get(0);

        // Assert
        assertThat(summary.title()).isEqualTo("Nihon no Hon");
        assertThat(summary.year()).isNull();
        assertThat(summary.coverThumbnailUrl()).isNull();
        assertThat(summary.originalLanguage()).isEqualTo("ja-Latn");
        assertThat(summary.status()).isEqualTo(SourceWorkStatus.HIATUS);
    }

    // ---------- fetchWork ----------

    @Test
    void shouldMapDetails_whenFetchingWork() {
        // Arrange
        server.expect(queryContains("/manga/m-1", "includes[]=cover_art"))
                .andRespond(withSuccess("""
                        {"result":"ok","data":{
                          "id":"m-1",
                          "attributes":{
                            "title":{"en":"One Piece"},
                            "altTitles":[
                              {"en":"One Piece"},
                              {"ja":"ワンピース"},
                              {"pt-br":"One Piece: O Pirata"},
                              {"ja-ro":"ワンピース"}
                            ],
                            "description":{"en":"Piratas.","pt-br":"Piratas em PT.","es-la":"","ja-ro":"Wan Pisu"},
                            "originalLanguage":"ja","status":"completed","year":1997,
                            "contentRating":"safe","publicationDemographic":"shounen",
                            "availableTranslatedLanguages":["pt-br","en"],
                            "tags":[
                              {"attributes":{"name":{"en":"Action"},"group":"genre"}},
                              {"attributes":{"name":{"en":"Long Strip"},"group":"format"}},
                              {"attributes":{"name":{"en":"Gore"},"group":"content"}},
                              {"attributes":{"name":{"en":"Pirates"},"group":"theme"}}
                            ]
                          },
                          "relationships":[
                            {"id":"c-1","type":"cover_art","attributes":{"fileName":"capa.jpg"}}
                          ]
                        }}
                        """, MediaType.APPLICATION_JSON));

        // Act
        SourceWorkDetails details = source.fetchWork("m-1");

        // Assert
        assertThat(details.title()).isEqualTo("One Piece");
        assertThat(details.availableLanguages()).containsExactly("pt-BR", "en");
        assertThat(details.alternativeTitles())
                .containsExactly("ワンピース", "One Piece: O Pirata");
        assertThat(details.descriptions())
                .containsOnlyKeys("en", "pt-BR", "ja-Latn")
                .containsEntry("pt-BR", "Piratas em PT.");
        assertThat(details.tags()).containsExactly(
                new SourceTag("Action", SourceTagGroup.GENRE),
                new SourceTag("Long Strip", SourceTagGroup.FORMAT),
                new SourceTag("Gore", SourceTagGroup.CONTENT),
                new SourceTag("Pirates", SourceTagGroup.THEME));
        assertThat(details.longStrip()).isTrue();
        assertThat(details.demographic()).isEqualTo("shounen");
        assertThat(details.status()).isEqualTo(SourceWorkStatus.COMPLETED);
        assertThat(details.contentRating()).isEqualTo(SourceContentRating.SAFE);
        assertThat(details.coverUrl()).isEqualTo(UPLOADS + "/covers/m-1/capa.jpg");
        assertThat(details.sourceUrl()).isEqualTo("https://mangadex.org/title/m-1");
    }

    @Test
    void shouldReturnNullDemographicAndNoLongStrip_whenWorkHasNeither() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-3")))
                .andRespond(withSuccess("""
                        {"data":{"id":"m-3","attributes":{
                          "title":{"en":"Sem Extras"},"altTitles":[],"description":{},
                          "originalLanguage":"ko","status":"ongoing","year":2020,
                          "contentRating":"erotica","publicationDemographic":null,"tags":[]
                        },"relationships":[]}}
                        """, MediaType.APPLICATION_JSON));

        // Act
        SourceWorkDetails details = source.fetchWork("m-3");

        // Assert
        assertThat(details.demographic()).isNull();
        assertThat(details.longStrip()).isFalse();
        assertThat(details.coverUrl()).isNull();
        assertThat(details.alternativeTitles()).isEmpty();
        assertThat(details.descriptions()).isEmpty();
    }

    @Test
    void shouldThrowNotFound_whenWorkDoesNotExist() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/ausente")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Act + Assert
        assertThatThrownBy(() -> source.fetchWork("ausente"))
                .isInstanceOf(ExternalWorkNotFoundException.class);
    }

    // ---------- listChapters ----------

    @Test
    void shouldPaginateFeed_whenTotalExceedsPageSize() {
        // Arrange
        server.expect(queryContains("/manga/m-1/feed", "translatedLanguage[]=pt-br", "limit=500", "offset=0",
                        "order[chapter]=asc", "order[volume]=asc", "includes[]=scanlation_group",
                        "includeExternalUrl=0", "includeEmptyPages=0", "includeFuturePublishAt=0",
                        "contentRating[]=erotica"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess(feed(501, chapterJson("ch-1", "1", 20, "null", "")), MediaType.APPLICATION_JSON));
        server.expect(queryContains("/manga/m-1/feed", "offset=500"))
                .andRespond(withSuccess(feed(501, chapterJson("ch-2", "2", 18, "null", "")), MediaType.APPLICATION_JSON));

        // Act
        List<SourceChapterInfo> chapters = source.listChapters("m-1", Set.of("pt-BR"));

        // Assert
        assertThat(chapters).extracting(SourceChapterInfo::externalId).containsExactly("ch-1", "ch-2");
        server.verify();
    }

    @Test
    void shouldSkipExternalEmptyAndUnavailableChapters_whenListingChapters() {
        // Arrange
        String ok = chapterJson("ok", "3", 12, "null", """
                {"id":"g-1","type":"scanlation_group","attributes":{"name":"Grupo Um"}},
                {"id":"g-2","type":"scanlation_group"},
                {"id":"u-1","type":"user"}
                """);
        String external = chapterJson("ext", "4", 0, "\"https://outro.site/cap\"", "");
        String empty = chapterJson("vazio", "5", 0, "null", "");
        String unavailable = chapterJson("retirado", "6", 20, "null", "").replace("\"pages\":20", "\"pages\":20,\"isUnavailable\":true");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1/feed")))
                .andRespond(withSuccess(feed(4, ok + "," + external + "," + empty + "," + unavailable), MediaType.APPLICATION_JSON));

        // Act
        List<SourceChapterInfo> chapters = source.listChapters("m-1", Set.of("pt-BR"));

        // Assert
        assertThat(chapters).hasSize(1);
        SourceChapterInfo chapter = chapters.get(0);
        assertThat(chapter.externalId()).isEqualTo("ok");
        assertThat(chapter.language()).isEqualTo("pt-BR");
        assertThat(chapter.number()).isEqualTo("3");
        assertThat(chapter.volume()).isEqualTo("1");
        assertThat(chapter.title()).isNull();
        assertThat(chapter.pages()).isEqualTo(12);
        assertThat(chapter.groups()).containsExactly(
                new SourceGroup("g-1", "Grupo Um"),
                new SourceGroup("g-2", "g-2"));
        assertThat(chapter.publishedAt()).isEqualTo(Instant.parse("2025-06-01T10:00:00Z").atOffset(ZoneOffset.UTC));
        assertThat(chapter.updatedAt()).isEqualTo(Instant.parse("2025-06-02T10:00:00Z").atOffset(ZoneOffset.UTC));
    }

    // ---------- fetchPages ----------

    @Test
    void shouldBuildDataAndDataSaverUrls_whenFetchingPages() {
        // Arrange
        server.expect(requestTo(API + "/at-home/server/ch-1"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess("""
                        {"result":"ok","baseUrl":"https://no.exemplo.network:443/token123",
                         "chapter":{"hash":"abc","data":["1-a.png","2-b.png","3-c.png"],
                                    "dataSaver":["1-a.jpg","2-b.jpg"]}}
                        """, MediaType.APPLICATION_JSON));

        // Act
        List<SourcePage> pages = source.fetchPages("ch-1");

        // Assert
        String base = "https://no.exemplo.network:443/token123";
        assertThat(pages).containsExactly(
                new SourcePage(base + "/data/abc/1-a.png", base + "/data-saver/abc/1-a.jpg"),
                new SourcePage(base + "/data/abc/2-b.png", base + "/data-saver/abc/2-b.jpg"),
                new SourcePage(base + "/data/abc/3-c.png", null));
    }

    // ---------- download ----------

    @Test
    void shouldReportSuccess_whenDownloadingFromThirdPartyNode() {
        // Arrange
        String url = "https://no.exemplo.network/data/abc/1.png";
        server.expect(requestTo(url))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(new byte[]{1, 2, 3, 4}, MediaType.IMAGE_PNG).header("X-Cache", "HIT"));
        server.expect(requestTo(REPORT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$.url").value(url))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.bytes").value(4))
                .andExpect(jsonPath("$.duration").isNumber())
                .andRespond(withSuccess());

        // Act
        byte[] bytes = source.download(url, 100);

        // Assert
        assertThat(bytes).containsExactly(1, 2, 3, 4);
        server.verify();
    }

    @Test
    void shouldNotReport_whenDownloadingFromMangaDexHost() {
        // Arrange
        String url = "https://uploads.mangadex.org/covers/m-1/capa.jpg";
        server.expect(requestTo(url)).andRespond(withSuccess(new byte[]{9}, MediaType.IMAGE_JPEG));

        // Act
        byte[] bytes = source.download(url, 100);

        // Assert
        assertThat(bytes).containsExactly(9);
        server.verify(); // qualquer POST de report sem expectativa já teria falhado
    }

    @Test
    void shouldThrow_whenDownloadExceedsMaxBytes() {
        // Arrange
        String url = "https://uploads.mangadex.org/covers/m-1/grande.jpg";
        server.expect(requestTo(url)).andRespond(withSuccess(new byte[10], MediaType.IMAGE_JPEG));

        // Act + Assert
        assertThatThrownBy(() -> source.download(url, 5))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessageContaining("passa de 5 bytes");
        assertThat(sleeps).isEmpty(); // estourar o limite não tenta de novo
    }

    @Test
    void shouldReportFailureAndRetry_whenNodeKeepsFailing() {
        // Arrange
        String url = "https://no.exemplo.network/data/abc/1.png";
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.FORBIDDEN));
            server.expect(requestTo(REPORT))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.cached").value(false))
                    .andExpect(jsonPath("$.bytes").value(0))
                    .andRespond(withSuccess());
        }

        // Act + Assert
        assertThatThrownBy(() -> source.download(url, 100)).isInstanceOf(ExternalSourceException.class);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void shouldReturnImageAndPauseReports_whenReportFails() {
        // Arrange
        String url = "https://no.exemplo.network/data/abc/1.png";
        server.expect(requestTo(url)).andRespond(withServerError());
        server.expect(requestTo(REPORT)).andRespond(withServerError()); // falha do report só vira log
        // a segunda tentativa não reporta: depois de uma falha, os reports ficam pausados
        server.expect(requestTo(url)).andRespond(withSuccess(new byte[]{7}, MediaType.IMAGE_PNG));

        // Act
        byte[] bytes = source.download(url, 100);

        // Assert
        assertThat(bytes).containsExactly(7);
        server.verify();
    }

    @Test
    void shouldRefuseImageUrl_whenItCouldReachTheInternalNetwork() {
        // Act + Assert
        for (String url : List.of("http://no.exemplo.network/1.png", "https://169.254.169.254/x",
                "https://metadata.google.internal/x", "https://localhost/x", "https://[::1]/x",
                "https://user@no.exemplo.network/1.png")) {
            assertThatThrownBy(() -> source.download(url, 100))
                    .as(url).isInstanceOf(ExternalSourceException.class).hasMessageContaining("recusada");
        }
        server.verify();
    }

    // ---------- limites de taxa e falhas ----------

    @Test
    void shouldWaitUntilRetryAfterAndRetry_when429() {
        // Arrange
        long retryAt = NOW.plusSeconds(30).getEpochSecond();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("X-RateLimit-Retry-After", String.valueOf(retryAt)));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withSuccess(minimalWork("m-1"), MediaType.APPLICATION_JSON));

        // Act
        SourceWorkDetails details = source.fetchWork("m-1");

        // Assert
        assertThat(details.externalId()).isEqualTo("m-1");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(30));
        server.verify();
    }

    @Test
    void shouldWaitSixtySeconds_when429HasNoRetryAfterHeader() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withSuccess(minimalWork("m-1"), MediaType.APPLICATION_JSON));

        // Act
        source.fetchWork("m-1");

        // Assert
        assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void shouldThrow_whenRateLimitedMoreThanThreeTimes() {
        // Arrange
        for (int i = 0; i < 4; i++) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        // Act + Assert
        assertThatThrownBy(() -> source.fetchWork("m-1")).isInstanceOf(ExternalSourceException.class);
        assertThat(sleeps).hasSize(3);
        server.verify();
    }

    @Test
    void shouldBackOffAndRetry_whenServerErrorsThenRecovers() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1"))).andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1"))).andRespond(withServerError());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withSuccess(minimalWork("m-1"), MediaType.APPLICATION_JSON));

        // Act
        source.fetchWork("m-1");

        // Assert
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
        server.verify();
    }

    @Test
    void shouldThrow_whenServerErrorsOnAllThreeAttempts() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1"))).andRespond(withServerError());
        }

        // Act + Assert
        assertThatThrownBy(() -> source.fetchWork("m-1")).isInstanceOf(ExternalSourceException.class);
        server.verify();
    }

    @Test
    void shouldSleepQuarterSecond_whenCallingApiTwiceInARow() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withSuccess(minimalWork("m-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-2")))
                .andRespond(withSuccess(minimalWork("m-2"), MediaType.APPLICATION_JSON));

        // Act
        source.fetchWork("m-1");
        assertThat(sleeps).isEmpty();
        source.fetchWork("m-2");

        // Assert
        assertThat(sleeps).containsExactly(Duration.ofMillis(250));
    }

    @Test
    void shouldNotSleep_whenEnoughTimePassedBetweenCalls() {
        // Arrange
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-1")))
                .andRespond(withSuccess(minimalWork("m-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(API + "/manga/m-2")))
                .andRespond(withSuccess(minimalWork("m-2"), MediaType.APPLICATION_JSON));

        // Act
        source.fetchWork("m-1");
        clock.advance(Duration.ofSeconds(1));
        source.fetchWork("m-2");

        // Assert
        assertThat(sleeps).isEmpty();
    }

    @Test
    void shouldSleepOneAndHalfSeconds_whenCallingAtHomeTwiceInARow() {
        // Arrange
        String body = """
                {"baseUrl":"https://no.exemplo.network","chapter":{"hash":"h","data":["1.png"],"dataSaver":[]}}
                """;
        server.expect(requestTo(API + "/at-home/server/ch-1")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/at-home/server/ch-2")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // Act
        source.fetchPages("ch-1");
        source.fetchPages("ch-2");

        // Assert
        assertThat(sleeps).containsExactly(Duration.ofMillis(1500));
    }

    // ---------- apoio ----------

    /** Confere caminho e trechos da query já decodificados (ex.: {@code includes[]=cover_art}). */
    private static RequestMatcher queryContains(String path, String... fragments) {
        return request -> {
            assertThat(request.getURI().getHost()).isEqualTo("api.test");
            assertThat(request.getURI().getPath()).isEqualTo(path);
            String query = request.getURI().getQuery();
            for (String fragment : fragments) {
                assertThat(query).contains(fragment);
            }
        };
    }

    private static String minimalWork(String id) {
        return """
                {"data":{"id":"%s","attributes":{"title":{"en":"Obra"},"originalLanguage":"ja",
                "status":"ongoing","contentRating":"safe"},"relationships":[]}}
                """.formatted(id);
    }

    @Test
    void shouldCountRemovedAndExternalChapters_whenCountingPublished() {
        // Arrange
        server.expect(queryContains("/manga/m-1/feed", "includeUnavailable=1"))
                .andExpect(queryContains("/manga/m-1/feed", "includeExternalUrl=1"))
                .andExpect(queryContains("/manga/m-1/feed", "translatedLanguage[]=pt-br"))
                .andRespond(withSuccess(feed(119, ""), MediaType.APPLICATION_JSON));

        // Act
        int published = source.countPublishedChapters("m-1", "pt-BR");

        // Assert
        assertThat(published).isEqualTo(119);
        server.verify();
    }

    private static String feed(int total, String chaptersJson) {
        return """
                {"result":"ok","total":%d,"data":[%s]}
                """.formatted(total, chaptersJson);
    }

    private static String chapterJson(String id, String number, int pages, String externalUrl, String groups) {
        return """
                {"id":"%s","type":"chapter","attributes":{
                  "volume":"1","chapter":"%s","title":"","translatedLanguage":"pt-br",
                  "externalUrl":%s,"pages":%d,
                  "publishAt":"2025-06-01T10:00:00+00:00","updatedAt":"2025-06-02T10:00:00+00:00"},
                 "relationships":[%s]}
                """.formatted(id, number, externalUrl, pages, groups);
    }

    /** Relógio que só anda quando o teste manda. */
    private static final class TestClock extends Clock {
        private Instant now;

        TestClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
