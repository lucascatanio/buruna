# ADR-14 — client_max_body_size 600M no nginx + proxy_request_buffering off

**Contexto:** Volumes de mangá podem ter centenas de MB. O nginx precisa aceitar requests grandes para o proxy pass ao backend.

**Decisão:** `client_max_body_size 600M` e `proxy_request_buffering off` na config do nginx.

**Por quê:** O limite padrão do nginx é 1 MB, insuficiente pra uploads de PDF. 600 MB cobre até os maiores tankōbon digitais. O `proxy_request_buffering off` evita que o nginx armazene o corpo inteiro em disco/memória antes de encaminhar, fazendo streaming direto pro backend. Sem isso, o container do frontend estoura memória.

**Nota:** Depois da ADR-24 (upload direto ao GCS), uploads grandes não passam mais pelo nginx. O `client_max_body_size` alto ficou como safety net mas raramente é exercitado. Os maiores payloads via nginx agora são JSONs de metadados.
