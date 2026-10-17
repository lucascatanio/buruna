package com.buruna.work.application;

import com.buruna.work.domain.WorkFormat;
import com.buruna.work.domain.WorkStatusOrigin;
import com.buruna.work.domain.WorkStatusSite;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cria a obra pública de uma importação (contexto sourcing), com as tags que faltarem. Entra
 * como incompleta no site: os capítulos chegam depois, pelo executor. Mesmo título de outra
 * obra pública dá conflito, e o ADM pode vincular a importação à obra que já existe.
 */
@Service
public class CreateImportedWorkUseCase {

    private static final int MAX_TITLE_LENGTH = 255;

    private final CreatePublicWorkUseCase createPublicWork;
    private final EnsureTagsUseCase ensureTags;

    public CreateImportedWorkUseCase(CreatePublicWorkUseCase createPublicWork, EnsureTagsUseCase ensureTags) {
        this.createPublicWork = createPublicWork;
        this.ensureTags = ensureTags;
    }

    @Transactional
    public WorkInfo handle(ImportedWorkCommand command, UUID actorId) {
        String title = command.title().length() > MAX_TITLE_LENGTH
                ? command.title().substring(0, MAX_TITLE_LENGTH)
                : command.title();
        WorkRequest request = new WorkRequest(
                title,
                command.alternativeTitles(),
                command.synopsis(),
                command.coverDataUri(),
                WorkFormat.valueOf(command.format()),
                command.originCountry(),
                WorkStatusOrigin.valueOf(command.statusOrigin()),
                WorkStatusSite.INCOMPLETE,
                command.year(),
                command.contentWarnings(),
                ensureTags.handle(command.tags()));
        WorkResponse created = createPublicWork.handle(request, actorId);
        return new WorkInfo(created.id(), created.slug(), created.title(), created.coverUrl());
    }
}
