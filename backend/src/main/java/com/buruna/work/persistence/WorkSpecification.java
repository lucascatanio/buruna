package com.buruna.work.persistence;

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