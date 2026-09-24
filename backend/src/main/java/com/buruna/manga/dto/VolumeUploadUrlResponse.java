package com.buruna.manga.dto;

import java.util.Map;

public record VolumeUploadUrlResponse(
        String uploadUrl,
        String objectName,
        Map<String, String> requiredHeaders
) {}
