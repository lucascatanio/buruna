export interface TagCategory {
    id: string;
    name: string;
}

export interface Tag {
    id: string;
    name: string;
    slug: string;
    category: TagCategory;
}

export interface Volume {
    id: string;
    volumeNumber: number;
    fileSizeBytes: number;
    createdAt: string;
}

export interface MangaCard {
    id: string;
    slug: string;
    title: string;
    coverUrl: string | null;
    format: string;
    statusOrigin: string;
    year: number | null;
    avgRating: number;
    ratingCount: number;
}

export interface MangaDetail {
    id: string;
    slug: string;
    title: string;
    alternativeTitles: string[];
    synopsis: string | null;
    coverUrl: string | null;
    format: string;
    originCountry: string | null;
    statusOrigin: string;
    statusSite: string;
    year: number | null;
    contentWarnings: string[];
    avgRating: number;
    ratingCount: number;
    viewCount: number;
    isPublic: boolean;
    ownerId: string;
    tags: Tag[];
    volumes: Volume[];
    createdAt: string;
}

export interface MangaRequest {
    title: string;
    alternativeTitles: string[];
    synopsis?: string | null;
    coverBase64?: string;
    format: string;
    originCountry?: string | null;
    statusOrigin: string;
    statusSite: string;
    year?: number | null;
    contentWarnings: string[];
    tagIds: string[];
}

export interface MangaListFilters {
    title?: string;
    format?: string;
    statusOrigin?: string;
    tagIds?: string[];
    page?: number;
    size?: number;
}

export interface VolumeUploadUrlResponse {
    uploadUrl: string;
    objectName: string;
}

export interface CreatedManga {
    id: string;
    slug: string;
    title: string;
}

export interface TagRequest {
    name: string;
    slug: string;
    categoryId: string;
}

export interface PrivateManga {
    id: string;
    title: string;
    synopsis: string | null;
    coverUrl: string | null;
    volumes: Volume[];
    createdAt: string;
    updatedAt: string;
    submissionStatus: string | null;
    rejectionReason: string | null;
}

export interface PrivateMangaRequest {
    title: string;
    synopsis?: string | null;
    coverBase64?: string | null;
}

export interface QuotaInfo {
    usedBytes: number;
    quotaBytes: number;
}

export interface PendingSubmission {
    id: string;
    title: string;
    coverUrl: string | null;
    submitterUsername: string;
    submitterEmail: string;
    submittedAt: string;
}
