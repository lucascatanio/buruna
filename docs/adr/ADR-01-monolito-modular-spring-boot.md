# ADR-01 — Monolito modular (Spring Boot)

**Contexto:** Projeto solo, sem deadline, sem equipe distribuída. Todos os módulos (auth, manga, reader, admin) compartilham o mesmo banco e o mesmo ciclo de deploy.

**Decisão:** Monolito modular com separação por pacotes (`auth/`, `manga/`, `reader/`, etc.) em vez de microsserviços.

**Por quê:** Microsserviços adicionam uma pilha de complexidade operacional que não faz sentido para um dev sozinho: service discovery, comunicação inter-serviço, deploys independentes, tracing distribuído. O monolito com pacotes bem separados mantém boundaries claros entre domínios e dá liberdade para extrair serviços no futuro, se a necessidade aparecer, sem pagar esse custo agora.

**Tradeoff:** Se o projeto escalar para vários devs trabalhando em paralelo, o monolito pode gerar conflitos de merge e deploys acoplados. Aceitável no momento.
