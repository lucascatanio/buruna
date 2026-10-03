package com.buruna.manga.application;

import java.util.Map;

public record VolumeUploadUrlResponse(
        String uploadUrl,
        String objectName,
        Map<String, String> requiredHeaders
) {}
