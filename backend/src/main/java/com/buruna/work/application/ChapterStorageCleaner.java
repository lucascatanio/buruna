package com.buruna.work.application;

import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.persistence.ChapterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Objetos de storage dos capítulos (páginas, variante de economia, arquivo de livro e arquivo enviado), para
 * quem apaga capítulo ou obra. As linhas saem do banco por cascata; os objetos, não.
 */
@Component
public class ChapterStorageCleaner {

    private static final Logger log = LoggerFactory.getLogger(ChapterStorageCleaner.class);

    private final ChapterRepository chapterRepository;
    private final StorageClient storageClient;

    public ChapterStorageCleaner(ChapterRepository chapterRepository, StorageClient storageClient) {
        this.chapterRepository = chapterRepository;
        this.storageClient = storageClient;
    }

    public List<String> objectNamesOfWorks(Collection<UUID> workIds) {
        if (workIds.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        chapterRepository.findWithPagesByWorkIdIn(workIds).forEach(chapter -> names.addAll(objectNamesOf(chapter)));
        return names;
    }

    public static List<String> objectNamesOf(Chapter chapter) {
        List<String> names = new ArrayList<>();
        for (ChapterPage page : chapter.getPages()) {
            names.add(page.getObjectName());
            page.getDataSaverObjectName().ifPresent(names::add);
        }
        chapter.getSourceObjectName().ifPresent(names::add);
        chapter.getFileObjectName().ifPresent(names::add);
        return names;
    }

    // best-effort, como no delete de volume (ADR-24): o objeto que falhar fica órfão no bucket
    public void deleteQuietly(Collection<String> objectNames) {
        for (String name : objectNames) {
            try {
                storageClient.delete(name);
            } catch (RuntimeException e) {
                log.warn("Não foi possível apagar {} do storage", name, e);
            }
        }
    }
}
