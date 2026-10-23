# ADR-49 — Sync das obras acompanhadas

**Status:** Aceita

**Contexto:** Depois do import ([ADR-47](ADR-47-contexto-sourcing.md)), a obra continua saindo no
MangaDex: capítulos novos, traduções de outros grupos, capítulos retirados, páginas corrigidas.
O MangaDex não avisa de nada disso (não há webhook), e a lista de capítulos de uma obra não
diz o que mudou desde a última leitura.

**Decisão:**

- **Polling a cada 3 h.** O Cloud Scheduler (3º job da conta, [ADR-03](ADR-03-async-e-scheduled-internos.md))
  dispara o Job de ingest com `--sourcing.sync=true` ([ADR-48](ADR-48-ingest-em-cloud-run-job.md)).
  A rodada pede a verificação de todas as obras ativas e lista cada uma inteira. O ADM também
  pode pedir "verificar agora" numa obra.
- **Comparação com o que já se conhece**, a cada listagem:
  - versão nova entra pela escolha de versão de sempre (grupo preferido, senão a mais recente);
  - versão que sumiu da fonte ganha o aviso "removida na fonte". O capítulo baixado **continua
    no ar**; versão ainda não baixada deixa de entrar;
  - versão baixada cujas páginas a fonte trocou (`updatedAt` mais novo que o do download) ganha o
    aviso "páginas trocadas", com o botão **baixar de novo**;
  - versão do grupo preferido que chega depois de o número já ter entrado por outro grupo fica
    de fora, com o aviso e o botão **usar esta versão**;
  - listagem vazia de uma obra que tinha capítulos conta como falha da fonte, não como remoção
    de tudo.
- **Trocar de versão e baixar de novo mantêm o capítulo:** as páginas novas vão para um
  subprefixo da revisão (`chapters/{id}/r{n}/`) e substituem as antigas no mesmo capítulo
  (`ReplaceImportedChapterPagesUseCase`). O id não muda, então o progresso e o histórico de
  quem lê continuam valendo. As antigas são apagadas depois.
- **Falha de download** volta para a fila sozinha nos syncs seguintes, até 3 tentativas. Depois
  disso, só pelo botão "tentar de novo" (a contagem recomeça).
- **Pausa automática:** 8 falhas seguidas ao listar uma obra (~1 dia) a pausam, com o motivo. O
  ADM retoma pelo botão, o que zera o contador e pede uma verificação. Pausada pelo ADM, a obra
  não é verificada nem baixa nada.
- **Ignorar um grupo** (pedido de remoção do grupo, exigência da AUP do MangaDex,
  [ADR-46](ADR-46-mangadex-como-fonte-externa.md)): apaga os capítulos que vieram dele e deixa a
  escolha trazer a versão de outro grupo. As versões futuras do grupo não entram.
- **E-mail diário** para todos os ADMs ativos, só quando há novidade desde o último: versão que
  falhou de vez, removida na fonte, páginas trocadas, versão do grupo preferido disponível, obra
  pausada sozinha ou com falha ao verificar. Sai no fim do primeiro sync depois das 8 h de
  Brasília, sob lock no lease, para não sair duas vezes. Sem novidade, só registra o dia.

**Alternativas descartadas:** `updatedAtSince` no feed (não mostra remoções, que exigiriam a
lista inteira de qualquer forma; com ~1 requisição por 500 versões, listar tudo a cada 3 h cabe
no limite de taxa); trocar de versão apagando o capítulo e criando outro (perderia o progresso
de quem lê); um job do Scheduler só para o e-mail (seria o 4º job, fora da cota gratuita).

**Tradeoff:** uma obra muito longa (milhares de versões) faz mais requisições a cada sync, e
mais de 10.000 versões não cabem na janela que a API permite listar. Nesse caso a listagem é
parcial, e o aviso de "removida na fonte" pode aparecer para versões que só ficaram fora da
janela. Não há obra assim hoje.
