package com.buruna.manga.repository;

import com.buruna.manga.domain.Manga;
import com.buruna.manga.domain.MangaFormat;
import com.buruna.manga.domain.MangaStatusOrigin;
import com.buruna.manga.domain.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

public class MangaSpecification {

    private MangaSpecification() {}

    public static Specification<Manga> isPublic() {
        return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
    }

    public static Specification<Manga> titleContains(String title) {
        return (root, query, cb) -> {
            if (title == null || title.isBlank()) return null;
            String pattern = "%" + title.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("alternativeTitles")), pattern)
            );
        };
    }

    public static Specification<Manga> hasFormat(MangaFormat format) {
        return (root, query, cb) ->
                format == null ? null : cb.equal(root.get("format"), format);
    }

    public static Specification<Manga> hasStatusOrigin(MangaStatusOrigin statusOrigin) {
        return (root, query, cb) ->
                statusOrigin == null ? null : cb.equal(root.get("statusOrigin"), statusOrigin);
    }

    public static Specification<Manga> hasTagIds(Set<UUID> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) return null;

            // AND: mangá precisa ter TODAS as tags selecionadas
            var predicates = tagIds.stream()
                    .map(tagId -> {
                        var subquery = query.subquery(Long.class);
                        var mangaTag = subquery.from(Manga.class);
                        Join<Manga, Tag> tagJoin = mangaTag.join("tags", JoinType.INNER);
                        subquery.select(cb.literal(1L))
                                .where(
                                        cb.equal(mangaTag.get("id"), root.get("id")),
                                        cb.equal(tagJoin.get("id"), tagId)
                                );
                        return cb.exists(subquery);
                    })
                    .toArray(jakarta.persistence.criteria.Predicate[]::new);

            return cb.and(predicates);
        };
    }
}