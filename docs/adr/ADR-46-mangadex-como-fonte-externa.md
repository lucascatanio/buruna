# ADR-46 — MangaDex como fonte externa, atrás da porta ExternalSource

**Status:** Aceita

**Contexto:** O ADM quer trazer obras e capítulos sem subir arquivo por arquivo, e acompanhar
os capítulos novos. O MangaDex tem API pública, sem login para leitura, com capítulos por
idioma e por grupo de tradução. Livros continuam só por upload.

**Decisão:**

- A integração fica atrás da porta `shared.source.ExternalSource` (buscar, metadados, listar
  versões de capítulo, endereços das páginas, baixar imagem). A única implementação é
  `MangaDexSource`. Outra fonte, se vier, é outra implementação, sem mexer no contexto
  `sourcing` ([ADR-47](ADR-47-contexto-sourcing.md)).
- Regras técnicas da API, seguidas pelo adaptador:
  - `User-Agent` próprio (`APP_SOURCING_USER_AGENT`), sem falsificar;
  - ~5 req/s na API e 40/min no `at-home`, com espera no 429 até `X-RateLimit-Retry-After`;
  - imagem de nó MangaDex@Home (host sem `mangadex.org`) é reportada em
    `api.mangadex.network/report`, com sucesso ou falha. O report tem timeout de 3 s e, depois
    de uma falha, fica pausado por 2 min: com o endpoint fora do ar, cada página esperaria o
    timeout;
  - a imagem não pode ser hotlinkada; a capa da busca passa pelo backend (`/admin/sourcing/cover`,
    só para `uploads.mangadex.org/covers/`).
- Capítulo que só aponta para fora (`externalUrl`, ex.: MANGA Plus), sem páginas ou marcado
  `isUnavailable` não entra.
- **Política de uso (AUP) do MangaDex:** crédito ao MangaDex e ao grupo de tradução, sem
  anúncio nem serviço pago, e atender aos pedidos de remoção dos grupos. O Burūna não tem
  monetização (nem doação). O crédito ao grupo vai no capítulo (`scanlation_group`); o da fonte,
  na página da obra (`GET /works/{id}/source`). Pedido de remoção: hoje o ADM apaga os
  capítulos do grupo; pausar o acompanhamento e ignorar um grupo entram com o sync (E7).
- Todas as classificações de conteúdo podem ser importadas. Obra erotica ou pornographic ganha
  o aviso NSFW; a tag Gore, o aviso GORE.

**Risco registrado:** os capítulos do MangaDex são, em geral, scanlations sem licença. Guardar
uma cópia no bucket (em vez de só apontar para a fonte) aumenta a exposição a pedidos de
remoção de detentores de direitos. Foi aceito com o compromisso de atender a remoção rápido; a
posição jurídica não foi avaliada por um advogado.

**Alternativas descartadas:** ler as imagens direto do MangaDex no navegador (a fonte recusa
hotlink e CORS, e o leitor dependeria da disponibilidade dela); outras fontes agora (sem API
estável ou com termos que proíbem o uso).
