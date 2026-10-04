-- Antes da V24, Manga.approve publicava e zerava submission_status. Só approve e reject
-- gravam reviewed_at, e reject deixa REJECTED: público + status nulo + reviewed_at
-- preenchido é exatamente o mangá aprovado. Promovidos direto (sem revisão) seguem nulos.
-- Separada da V24 porque o Postgres não deixa usar um valor de enum na mesma transação
-- em que ele foi criado.
UPDATE mangas
SET submission_status = 'APPROVED'
WHERE is_public = TRUE
  AND submission_status IS NULL
  AND reviewed_at IS NOT NULL;

-- Promovido direto com submissão aberta (antes de promoteToPublic encerrá-la) ficou público
-- com PENDING ou REJECTED: aparecia na fila do admin. Mesmo efeito do promoteToPublic atual.
UPDATE mangas
SET submission_status = NULL,
    rejection_reason = NULL
WHERE is_public = TRUE
  AND submission_status IN ('PENDING', 'REJECTED');
