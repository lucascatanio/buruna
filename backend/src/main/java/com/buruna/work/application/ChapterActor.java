package com.buruna.work.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Quem age sobre um capítulo; {@code quotaGb} só conta no escopo privado. */
public record ChapterActor(UUID actorId, boolean isAdmin, BigDecimal quotaGb) {
}
