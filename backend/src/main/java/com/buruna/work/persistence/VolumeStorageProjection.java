package com.buruna.work.persistence;

import java.util.UUID;

public interface VolumeStorageProjection {
    UUID getOwnerId();
    Long getTotalBytes();
}