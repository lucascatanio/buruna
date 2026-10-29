package com.buruna.work.application;

import java.util.UUID;

/**
 * Pede a extração das páginas de um capítulo em processamento. As implementações só agem
 * depois do commit da transação de quem chama: antes disso o capítulo ainda não existe para
 * quem vai processá-lo.
 */
public interface ChapterIngestTrigger {

    void requestIngest(UUID chapterId);
}
