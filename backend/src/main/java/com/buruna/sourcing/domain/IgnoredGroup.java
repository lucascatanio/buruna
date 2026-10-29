package com.buruna.sourcing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** Grupo de tradução cujas versões não entram numa obra (pedido de remoção do grupo). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IgnoredGroup {

    @Column(name = "group_id", nullable = false, length = 100)
    private String groupId;

    @Column(name = "group_name")
    private String groupName;

    public IgnoredGroup(String groupId, String groupName) {
        if (groupId == null || groupId.isBlank()) {
            throw new InvalidTrackedWorkException("Grupo obrigatório");
        }
        this.groupId = groupId;
        this.groupName = groupName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IgnoredGroup that)) return false;
        return groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId);
    }
}
