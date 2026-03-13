package com.buruna.manga.repository;

import java.util.UUID;

public interface VolumeStorageProjection {
    UUID getOwnerId();
    Long getTotalBytes();
}