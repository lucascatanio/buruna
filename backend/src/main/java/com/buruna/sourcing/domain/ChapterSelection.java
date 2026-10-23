package com.buruna.sourcing.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Escolhe, entre as versões de um mesmo número num idioma, a que vira capítulo: a do grupo
 * preferido, senão a publicada mais recentemente. Sem número (oneshot, extra), concorrem as
 * versões do mesmo volume e título. Número que já tem versão baixada não troca de versão
 * aqui: reimportar é decisão do ADM.
 */
public final class ChapterSelection {

    private static final Comparator<SourceChapter> MOST_RECENT = Comparator.comparing(
            SourceChapter::getPublishedAt, Comparator.nullsFirst(Comparator.<OffsetDateTime>naturalOrder()))
            // empate na data: o id externo decide, para a escolha não variar entre execuções
            .thenComparing(SourceChapter::getExternalId);

    private ChapterSelection() {
    }

    /**
     * Marca cada versão como escolhida ou ignorada, idioma por idioma. Versão removida na fonte
     * ou de grupo ignorado nunca é escolhida.
     */
    public static void apply(Collection<SourceChapter> versions, Function<String, Optional<String>> preferredGroup,
                             Set<String> ignoredGroups) {
        Map<String, List<SourceChapter>> slots = new LinkedHashMap<>();
        for (SourceChapter version : versions) {
            slots.computeIfAbsent(slotOf(version), key -> new ArrayList<>()).add(version);
        }
        for (List<SourceChapter> slot : slots.values()) {
            if (slot.stream().anyMatch(SourceChapter::isTaken)) {
                continue;
            }
            List<SourceChapter> eligible = slot.stream()
                    .filter(version -> !version.isRemovedAtSource() && !version.hasAnyGroup(ignoredGroups))
                    .toList();
            SourceChapter chosen = eligible.isEmpty()
                    ? null
                    : choose(eligible, preferredGroup.apply(slot.get(0).getLanguage()));
            slot.forEach(candidate -> {
                if (candidate == chosen) {
                    candidate.select();
                } else {
                    candidate.ignore();
                }
            });
        }
    }

    /** Onde a versão concorre: idioma e número, ou, sem número, volume e título. */
    public static String slotOf(SourceChapter version) {
        return version.getLanguage() + "|" + version.getNumber().orElseGet(() -> unnumberedSlot(version));
    }

    // sem número, o que identifica o capítulo é o volume e o título: o mesmo oneshot traduzido
    // por dois grupos cai no mesmo lugar, e extras com títulos diferentes ficam separados
    private static String unnumberedSlot(SourceChapter version) {
        String title = version.getTitle() == null ? "" : version.getTitle().trim().toLowerCase(Locale.ROOT);
        String volume = version.getVolume() == null ? "" : version.getVolume();
        return "#" + volume + "|" + title;
    }

    private static SourceChapter choose(List<SourceChapter> candidates, Optional<String> preferredGroup) {
        Set<SourceChapter> pool = new HashSet<>(candidates);
        if (preferredGroup.isPresent()) {
            Set<SourceChapter> preferred = new HashSet<>();
            candidates.stream().filter(c -> c.hasGroup(preferredGroup.get())).forEach(preferred::add);
            if (!preferred.isEmpty()) {
                pool = preferred;
            }
        }
        return pool.stream().max(MOST_RECENT).orElseThrow();
    }
}
