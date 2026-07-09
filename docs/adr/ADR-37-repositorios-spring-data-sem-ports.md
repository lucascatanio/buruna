# ADR-37 — Repositórios permanecem Spring Data (não criar ports custom)

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** Clean Arch canônica costuma definir uma interface de repositório na camada de domínio/aplicação e uma implementação na infraestrutura, para "inverter" a dependência do banco. Surge a tentação de criar, por agregado, uma porta `MangaRepositoryPort` + um adapter sobre Spring Data.

**Decisão:** **Não** criar portas de repositório custom. Os repositórios continuam como interfaces **Spring Data JPA** (`MangaRepository extends JpaRepository<...>`), vivendo em `persistence/` e consumidos diretamente pelos casos de uso. Esta é uma decisão de **não-abstração deliberada**.

**Por quê:** A interface Spring Data **já é** a abstração — é uma interface declarativa sem implementação concreta acoplada no código de aplicação. Envolvê-la em outra interface own + adapter duplicaria a superfície (dois lugares para cada método de query) sem ganho real: não há um segundo provedor de persistência no horizonte, e a portabilidade teórica não se paga num projeto solo. Além disso, a camada extra elevaria a barreira para contribuidores ("por que dois níveis de repositório?"). Mantém-se a regra de dependência (ADR-31): `persistence` depende de `domain`, e a aplicação depende da **interface** do repositório, não de uma classe concreta.

**Tradeoff:** Os casos de uso dependem de um tipo do Spring Data (`JpaRepository`), então não são 100% framework-free — mas eles já vivem na camada `application`, onde Spring é permitido (`@Service`/`@Transactional`). O **domínio** continua puro. Testes de aplicação usam mocks da interface do repositório; testes de query usam `@DataJpaTest`. Se algum dia surgir um motivo concreto para trocar a persistência, introduz-se a porta **naquele** ponto — não preventivamente.
