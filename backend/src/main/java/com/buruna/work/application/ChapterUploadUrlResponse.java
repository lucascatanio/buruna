package com.buruna.work.application;

import java.util.Map;

public record ChapterUploadUrlResponse(
        String uploadUrl,
        String objectName,
        Map<String, String> requiredHeaders
) {}
