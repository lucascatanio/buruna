package com.buruna.manga.persistence;

import java.util.UUID;

public interface VolumeStorageProjection {
    UUID getOwnerId();
    Long getTotalBytes();
}