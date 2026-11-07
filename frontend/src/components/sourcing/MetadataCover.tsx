import {useEffect, useState} from "react";
import {getMetadataCover} from "@/api/sourcingApi";

interface MetadataCoverProps {
    url: string | null;
    title: string;
    className?: string;
}

/** Capa do AniList, baixada pelo backend: o CSP do site não libera a imagem direto. */
export function MetadataCover({url, title, className = "w-14"}: MetadataCoverProps) {
    const [src, setSrc] = useState<string | null>(null);

    useEffect(() => {
        if (!url) return;
        let created: string | null = null;
        let cancelled = false;
        getMetadataCover(url)
            .then((objectUrl) => {
                created = objectUrl;
                if (!cancelled) setSrc(objectUrl);
            })
            .catch(() => undefined);
        return () => {
            cancelled = true;
            if (created) URL.revokeObjectURL(created);
        };
    }, [url]);

    return src
        ? <img src={src} alt={`Capa de ${title}`} className={`aspect-[2/3] shrink-0 rounded border object-cover ${className}`}/>
        : <div aria-hidden="true" className={`aspect-[2/3] shrink-0 rounded border bg-muted ${className}`}/>;
}
