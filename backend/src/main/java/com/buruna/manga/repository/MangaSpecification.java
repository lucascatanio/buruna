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
            return cb.like(cb.lower(root.get("title")), pattern);
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
            query.distinct(true);
            Join<Manga, Tag> tags = root.join("tags", JoinType.LEFT);
            return tags.get("id").in(tagIds);
        };
    }
}