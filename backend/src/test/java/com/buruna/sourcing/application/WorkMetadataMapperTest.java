package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceContentRating;
import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;
import com.buruna.shared.source.SourceWorkDetails;
import com.buruna.shared.source.SourceWorkStatus;
import com.buruna.work.application.ImportedWorkCommand;
import com.buruna.work.application.TagSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkMetadataMapperTest {

    static SourceWorkDetails details(String originalLanguage, boolean longStrip, SourceContentRating rating,
                                     List<SourceTag> tags, Map<String, String> descriptions) {
        return new SourceWorkDetails("md-1", "Título", List.of("Alt"), descriptions, originalLanguage,
                SourceWorkStatus.COMPLETED, 2020, rating, tags, longStrip, null,
                "https://mangadex.org/title/md-1", "seinen");
    }

    @Test
    void shouldMapKoreanToManhwa_whenOriginalLanguageIsKorean() {
        assertThat(WorkMetadataMapper.formatOf(details("ko", true, SourceContentRating.SAFE, List.of(), Map.of())))
                .isEqualTo("MANHWA");
    }

    @Test
    void shouldMapLongStripToWebtoon_whenNotKoreanNorChinese() {
        assertThat(WorkMetadataMapper.formatOf(details("en", true, SourceContentRating.SAFE, List.of(), Map.of())))
                .isEqualTo("WEBTOON");
    }

    @Test
    void shouldPreferPortugueseSynopsis_whenAvailable() {
        ImportedWorkCommand command = WorkMetadataMapper.toCommand(details("ja", false, SourceContentRating.SAFE,
                List.of(), Map.of("en", "English", "pt-BR", "Português")), null);

        assertThat(command.synopsis()).isEqualTo("Português");
        assertThat(command.originCountry()).isEqualTo("Japan");
        assertThat(command.statusOrigin()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldTranslateTagsAndAddWarnings_whenEroticaWithGore() {
        ImportedWorkCommand command = WorkMetadataMapper.toCommand(details("ja", false, SourceContentRating.EROTICA,
                List.of(new SourceTag("Action", SourceTagGroup.GENRE), new SourceTag("Gore", SourceTagGroup.CONTENT),
                        new SourceTag("Unknown Tag", SourceTagGroup.THEME)), Map.of()), null);

        assertThat(command.contentWarnings()).containsExactly("NSFW", "GORE");
        assertThat(command.tags()).contains(
                new TagSpec("Ação", "Gênero"),
                new TagSpec("Gore", "Aviso de Conteúdo"),
                new TagSpec("Unknown Tag", "Tema"),
                new TagSpec("Seinen", "Demografia"),
                new TagSpec("Mangá", "Formato"));
    }
}
