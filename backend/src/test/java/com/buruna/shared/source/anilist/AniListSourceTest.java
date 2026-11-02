package com.buruna.shared.source.anilist;

import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.ExternalWorkNotFoundException;
import com.buruna.shared.source.SourceContentRating;
import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.shared.source.SourceWorkStatus;
import com.buruna.shared.source.SourceWorkSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AniListSourceTest {

    private static final String API = "https://graphql.test";
    private static final String USER_AGENT = "Buruna-Test/1.0";

    private static final String MEDIA = """
            {"data": {"media": {
              "id": 118586,
              "title": {"romaji": "Sousou no Frieren", "english": "Frieren: Beyond Journey's End", "nativeTitle": "葬送のフリーレン"},
              "synonyms": ["Frieren", "Sousou no Frieren"],
              "description": "An elf mage.<br><br>~!She outlives them all.!~ The <i>journey</i> &quot;goes on&quot;.<br>\\n(Source: Viz)",
              "startDate": {"year": 2020}, "status": "RELEASING", "countryOfOrigin": "JP", "format": "MANGA",
              "isAdult": false, "genres": ["Adventure", "Drama", "Fantasy", "Ecchi"],
              "tags": [
                {"name": "Magic", "rank": 95, "isMediaSpoiler": false, "isGeneralSpoiler": false},
                {"name": "Shounen", "rank": 90, "isMediaSpoiler": false, "isGeneralSpoiler": false},
                {"name": "Tragedy", "rank": 85, "isMediaSpoiler": true, "isGeneralSpoiler": false},
                {"name": "Time Skip", "rank": 92, "isMediaSpoiler": false, "isGeneralSpoiler": false},
                {"name": "Vampire", "rank": 79, "isMediaSpoiler": false, "isGeneralSpoiler": false},
                {"name": "Mahjong", "rank": 81, "isMediaSpoiler": false, "isGeneralSpoiler": false},
                {"name": "Board Game", "rank": 80, "isMediaSpoiler": false, "isGeneralSpoiler": false}
              ],
              "coverImage": {"extraLarge": "https://s4.anilist.co/file/anilistcdn/media/manga/cover/large/x.jpg"},
              "siteUrl": "https://anilist.co/manga/118586"
            }}}""";

    private MockRestServiceServer server;
    private List<Duration> sleeps;
    private AniListSource source;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        sleeps = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC);
        source = new AniListSource(restTemplate, USER_AGENT, clock, sleeps::add, API);
    }

    @Test
    void shouldMapWorkToCatalogVocabulary_whenMediaIsFound() {
        server.expect(requestTo(API)).andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", USER_AGENT))
                .andExpect(jsonPath("$.variables.id").value(118586))
                .andRespond(withSuccess(MEDIA, MediaType.APPLICATION_JSON));

        SourceWorkDetails details = source.fetchWork("118586");

        assertThat(details.title()).isEqualTo("Sousou no Frieren");
        assertThat(details.alternativeTitles())
                .containsExactly("Frieren: Beyond Journey's End", "葬送のフリーレン", "Frieren");
        assertThat(details.originalLanguage()).isEqualTo("ja");
        assertThat(details.status()).isEqualTo(SourceWorkStatus.ONGOING);
        assertThat(details.year()).isEqualTo(2020);
        assertThat(details.contentRating()).isEqualTo(SourceContentRating.SUGGESTIVE);
        assertThat(details.demographic()).isEqualTo("shounen");
        assertThat(details.longStrip()).isFalse();
        assertThat(details.coverUrl()).startsWith(AniListSource.COVER_PREFIX);
        assertThat(details.descriptions())
                .containsExactly(Map.entry("en", "An elf mage.\n\nThe journey \"goes on\".\n\n(Source: Viz)"));
    }

    @Test
    void shouldKeepOnlyMappedNonSpoilerTagsFromRank80_whenMediaHasTags() {
        server.expect(requestTo(API)).andRespond(withSuccess(MEDIA, MediaType.APPLICATION_JSON));

        SourceWorkDetails details = source.fetchWork("118586");

        // Ecchi não tem par; Tragedy é spoiler; Time Skip não tem par; Vampire ficou abaixo do rank;
        // Mahjong e Board Game caem na mesma tag; Shounen vai para a demografia
        assertThat(details.tags()).containsExactly(
                new SourceTag("Adventure", SourceTagGroup.GENRE),
                new SourceTag("Drama", SourceTagGroup.GENRE),
                new SourceTag("Fantasy", SourceTagGroup.GENRE),
                new SourceTag("Magic", SourceTagGroup.THEME),
                new SourceTag("Traditional Games", SourceTagGroup.THEME));
    }

    @Test
    void shouldMarkAdultWorkAsPornographic_whenAniListFlagsIt() {
        server.expect(requestTo(API)).andRespond(withSuccess(MEDIA.replace("\"isAdult\": false", "\"isAdult\": true"),
                MediaType.APPLICATION_JSON));

        assertThat(source.fetchWork("118586").contentRating()).isEqualTo(SourceContentRating.PORNOGRAPHIC);
    }

    @Test
    void shouldAddOneshotAndLongStrip_whenFormatAndTagSaySo() {
        String oneshot = MEDIA.replace("\"format\": \"MANGA\"", "\"format\": \"ONE_SHOT\"")
                .replace("\"name\": \"Time Skip\", \"rank\": 92", "\"name\": \"Long Strip\", \"rank\": 40");
        server.expect(requestTo(API)).andRespond(withSuccess(oneshot, MediaType.APPLICATION_JSON));

        SourceWorkDetails details = source.fetchWork("118586");

        assertThat(details.longStrip()).isTrue();
        assertThat(details.tags()).contains(new SourceTag("Oneshot", SourceTagGroup.FORMAT))
                .doesNotContain(new SourceTag("Long Strip", SourceTagGroup.FORMAT));
    }

    @Test
    void shouldLeaveDescriptionsEmpty_whenDescriptionIsOnlySpoiler() {
        String spoilerOnly = MEDIA.replaceFirst("\"description\": \"[^\"]*\"", "\"description\": \"~!All of it.!~\"");
        server.expect(requestTo(API)).andRespond(withSuccess(spoilerOnly, MediaType.APPLICATION_JSON));

        assertThat(source.fetchWork("118586").descriptions()).isEmpty();
    }

    @Test
    void shouldThrowWorkNotFound_whenAniListAnswers404() {
        server.expect(requestTo(API)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errors\":[{\"message\":\"Not Found.\",\"status\":404}],\"data\":{\"media\":null}}"));

        assertThatThrownBy(() -> source.fetchWork("999")).isInstanceOf(ExternalWorkNotFoundException.class);
    }

    @Test
    void shouldThrowWorkNotFound_whenIdIsNotANumber() {
        assertThatThrownBy(() -> source.fetchWork("md-uuid")).isInstanceOf(ExternalWorkNotFoundException.class);
        server.verify();
    }

    @Test
    void shouldThrowWorkNotFound_whenIdDoesNotFitGraphQLInt() {
        assertThatThrownBy(() -> source.fetchWork("99999999999")).isInstanceOf(ExternalWorkNotFoundException.class);
        server.verify();
    }

    @Test
    void shouldFailRightAway_whenRateLimited() {
        server.expect(requestTo(API)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> source.search("frieren", 20))
                .isInstanceOf(ExternalSourceException.class)
                .isNotInstanceOf(ExternalWorkNotFoundException.class);
    }

    @Test
    void shouldMapSearchResultsAndSpaceCalls_whenSearchingTwice() {
        String page = """
                {"data": {"page": {"media": [{
                  "id": 118586, "title": {"romaji": null, "english": "Frieren", "nativeTitle": "葬送のフリーレン"},
                  "startDate": {"year": 2020}, "status": "FINISHED", "countryOfOrigin": "KR", "isAdult": false,
                  "genres": ["Hentai"], "coverImage": {"medium": "https://s4.anilist.co/file/anilistcdn/m.jpg"},
                  "siteUrl": "https://anilist.co/manga/118586"}]}}}""";
        server.expect(requestTo(API)).andExpect(jsonPath("$.variables.search").value("frieren"))
                .andRespond(withSuccess(page, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API)).andRespond(withSuccess(page, MediaType.APPLICATION_JSON));

        List<SourceWorkSummary> results = source.search("frieren", 20);
        source.search("frieren", 20);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.externalId()).isEqualTo("118586");
            assertThat(result.title()).isEqualTo("Frieren");
            assertThat(result.originalLanguage()).isEqualTo("ko");
            assertThat(result.status()).isEqualTo(SourceWorkStatus.COMPLETED);
            assertThat(result.contentRating()).isEqualTo(SourceContentRating.PORNOGRAPHIC);
        });
        // o relógio parado faz a segunda chamada esperar o espaçamento inteiro
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void shouldRefuseCover_whenUrlIsNotFromAniList() {
        assertThatThrownBy(() -> source.download("https://evil.test/x.jpg", 1024))
                .isInstanceOf(ExternalSourceException.class);
        assertThatThrownBy(() -> source.download(AniListSource.COVER_PREFIX + "../x.jpg", 1024))
                .isInstanceOf(ExternalSourceException.class);
        server.verify();
    }

    @Test
    void shouldFail_whenCoverIsLargerThanTheLimit() {
        String url = AniListSource.COVER_PREFIX + "media/manga/cover/large/x.jpg";
        server.expect(requestTo(url)).andRespond(withSuccess(new byte[2048], MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> source.download(url, 1024)).isInstanceOf(ExternalSourceException.class);
    }
}
