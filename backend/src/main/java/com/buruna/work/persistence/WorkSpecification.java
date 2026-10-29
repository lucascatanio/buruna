package com.buruna.work.persistence;

import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.domain.Volume;
import com.buruna.work.domain.Work;
import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

public class WorkSpecification {

    /** Idioma atribuído aos volumes legados, que não guardam idioma. */
    public static final String LEGACY_VOLUME_LANGUAGE = "pt-BR";

    private WorkSpecification() {}

    public static Specification<Work> isPublic() {
        return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
    }

    public static Specification<Work> titleContains(String title) {
        return (root, query, cb) -> {
            if (title == null || title.isBlank()) return null;
            String pattern = "%" + title.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("alternativeTitles")), pattern)
            );
        };
    }

    public static Specification<Work> hasFormat(WorkFormat format) {
        return (root, query, cb) ->
                format == null ? null : cb.equal(root.get("format"), format);
    }

    public static Specification<Work> hasStatusOrigin(WorkStatusOrigin statusOrigin) {
        return (root, query, cb) ->
                statusOrigin == null ? null : cb.equal(root.get("statusOrigin"), statusOrigin);
    }

    /**
     * Obra com capítulo publicado no idioma. Volume legado não tem idioma e conta como pt-BR,
     * o idioma de quase todo upload antigo, até a migração do legado (E8) virar capítulo.
     */
    public static Specification<Work> hasLanguage(String language) {
        return (root, query, cb) -> {
            if (language == null) return null;
            var chapters = query.subquery(Long.class);
            var chapter = chapters.from(Chapter.class);
            chapters.select(cb.literal(1L)).where(
                    cb.equal(chapter.get("workId"), root.get("id")),
                    cb.equal(chapter.get("language"), language),
                    cb.equal(chapter.get("status"), ChapterStatus.PUBLISHED));
            if (!LEGACY_VOLUME_LANGUAGE.equals(language)) {
                return cb.exists(chapters);
            }
            var volumes = query.subquery(Long.class);
            var volume = volumes.from(Volume.class);
            volumes.select(cb.literal(1L)).where(cb.equal(volume.get("work").get("id"), root.get("id")));
            return cb.or(cb.exists(chapters), cb.exists(volumes));
        };
    }

    public static Specification<Work> hasTagIds(Set<UUID> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) return null;

            // AND: mangá precisa ter TODAS as tags selecionadas
            var predicates = tagIds.stream()
                    .map(tagId -> {
                        var subquery = query.subquery(Long.class);
                        var workTag = subquery.from(Work.class);
                        Join<Work, Tag> tagJoin = workTag.join("tags", JoinType.INNER);
                        subquery.select(cb.literal(1L))
                                .where(
                                        cb.equal(workTag.get("id"), root.get("id")),
                                        cb.equal(tagJoin.get("id"), tagId)
                                );
                        return cb.exists(subquery);
                    })
                    .toArray(jakarta.persistence.criteria.Predicate[]::new);

            return cb.and(predicates);
        };
    }
}