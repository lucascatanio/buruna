package com.buruna.sourcing.application;

import com.buruna.shared.source.ExternalSourceException;
import com.buruna.shared.source.mangadownloader.MangaDownloaderClient;
import com.buruna.shared.source.mangadownloader.SiteListing;
import com.buruna.sourcing.domain.SiteListingFailedException;
import com.buruna.sourcing.domain.SourceName;
import com.buruna.sourcing.domain.TrackedWork;
import com.buruna.sourcing.persistence.TrackedWorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista uma série de site antes do import (ADR-54): idiomas, grupos e capítulos, inclusive os
 * bloqueados. Roda a CLI na própria requisição, no serviço web, que não tem navegador: site que
 * exige um falha aqui com o motivo, e o import segue com idioma e grupo informados à mão.
 */
@Service
public class ListSiteSeriesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListSiteSeriesUseCase.class);

    private final MangaDownloaderClient client;
    private final TrackedWorkRepository trackedWorkRepository;

    public ListSiteSeriesUseCase(MangaDownloaderClient client, TrackedWorkRepository trackedWorkRepository) {
        this.client = client;
        this.trackedWorkRepository = trackedWorkRepository;
    }

    public SiteSeriesListing list(String seriesUrl) {
        String url = seriesUrl.trim();
        TrackedWork.requireSeriesUrl(url);
        SiteListing listing;
        try {
            listing = client.list(url);
        } catch (ExternalSourceException e) {
            log.warn("Listagem de {} falhou", url, e);
            throw new SiteListingFailedException(e.getMessage());
        }
        Map<String, Integer> languages = new LinkedHashMap<>();
        Map<String, Integer> groups = new LinkedHashMap<>();
        List<SiteSeriesListing.Chapter> chapters = new ArrayList<>();
        for (SiteListing.Chapter chapter : listing.chapters()) {
            var info = MangaDownloaderChapterSource.toInfo(chapter);
            languages.merge(info.language(), 1, Integer::sum);
            List<String> names = info.groups().stream().map(group -> group.name()).toList();
            names.forEach(name -> groups.merge(name, 1, Integer::sum));
            chapters.add(new SiteSeriesListing.Chapter(info.number(), info.title(), info.language(), names,
                    info.volume() == null ? null : Integer.valueOf(info.volume()), chapter.locked()));
        }
        return new SiteSeriesListing(listing.title(), listing.grabber(),
                trackedWorkRepository.existsBySourceAndExternalId(SourceName.MANGA_DOWNLOADER, url),
                languages.entrySet().stream()
                        .map(entry -> new SiteSeriesListing.Language(entry.getKey(), entry.getValue()))
                        .sorted(Comparator.comparingInt(SiteSeriesListing.Language::chapterCount).reversed())
                        .toList(),
                groups.entrySet().stream()
                        .map(entry -> new SiteSeriesListing.Group(MangaDownloaderChapterSource.groupId(entry.getKey()),
                                entry.getKey(), entry.getValue()))
                        .sorted(Comparator.comparingInt(SiteSeriesListing.Group::chapterCount).reversed())
                        .toList(),
                chapters);
    }
}
