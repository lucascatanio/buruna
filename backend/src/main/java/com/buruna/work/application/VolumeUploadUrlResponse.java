package com.buruna.work.application;

import java.util.Map;

public record VolumeUploadUrlResponse(
        String uploadUrl,
        String objectName,
        Map<String, String> requiredHeaders
) {}
