// Cache de signed URLs em memória: volumeId → { url, expiresAt }
// Expira 25 min após geração (5 min antes da expiração real de 30 min no backend).

const TTL_MS = 25 * 60 * 1000;
const MARGIN_MS = 5 * 60 * 1000;

interface CacheEntry {
    url: string;
    fileSize: number;
    expiresAt: number;
}

const cache = new Map<string, CacheEntry>();

export function getSignedUrl(volumeId: string): { url: string; fileSize: number } | null {
    const entry = cache.get(volumeId);
    if (!entry) return null;
    if (Date.now() + MARGIN_MS >= entry.expiresAt) {
        cache.delete(volumeId);
        return null;
    }
    return { url: entry.url, fileSize: entry.fileSize };
}

export function setSignedUrl(volumeId: string, url: string, fileSize: number): void {
    cache.set(volumeId, { url, fileSize, expiresAt: Date.now() + TTL_MS });
}

export function clearSignedUrlCache(): void {
    cache.clear();
}
