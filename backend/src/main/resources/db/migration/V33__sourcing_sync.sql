-- Sync das obras acompanhadas (ADR-49): verificação pedida pelo ADM ou pelo Scheduler, pausa
-- automática, grupos ignorados, novas tentativas, avisos (removido na fonte, páginas trocadas)
-- e troca de versão mantendo o capítulo (e o progresso de quem lê).

ALTER TABLE tracked_works
    ADD COLUMN listing_requested BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN paused_at         TIMESTAMP WITH TIME ZONE,
    -- preenchido só na pausa automática; nulo na pausa do ADM
    ADD COLUMN paused_reason     TEXT;

-- grupos cujas versões nunca entram nesta obra (pedido de remoção do grupo)
CREATE TABLE tracked_work_ignored_groups
(
    tracked_work_id UUID         NOT NULL REFERENCES tracked_works (id) ON DELETE CASCADE,
    group_id        VARCHAR(100) NOT NULL,
    group_name      VARCHAR(255),
    PRIMARY KEY (tracked_work_id, group_id)
);

ALTER TABLE source_chapters
    ADD COLUMN attempts                 INT NOT NULL DEFAULT 0,
    ADD COLUMN failed_at                TIMESTAMP WITH TIME ZONE,
    ADD COLUMN removed_at_source        TIMESTAMP WITH TIME ZONE,
    -- quando o sync viu que a fonte trocou as páginas de uma versão já baixada
    ADD COLUMN source_update_detected_at TIMESTAMP WITH TIME ZONE,
    -- capítulo cujas páginas esta versão vai substituir (trocar de versão, baixar de novo)
    ADD COLUMN replaces_chapter_id      UUID REFERENCES chapters (id) ON DELETE SET NULL;

-- último e-mail diário do sync, por fonte
ALTER TABLE source_leases
    ADD COLUMN last_digest_at TIMESTAMP WITH TIME ZONE;
