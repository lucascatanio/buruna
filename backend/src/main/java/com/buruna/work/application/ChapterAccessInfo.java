package com.buruna.work.application;

import java.util.UUID;

public record ChapterAccessInfo(UUID chapterId, UUID workId, int pageCount) {
}
