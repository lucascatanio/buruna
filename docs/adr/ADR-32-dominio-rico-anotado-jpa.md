# ADR-32 — Domínio rico anotado com JPA (não separar entidade de domínio da entidade JPA)

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** Clean Arch canônica pede domínio livre de framework, o que implicaria, por agregado, uma entidade de domínio pura + uma entidade JPA + um mapper entre as duas. As entidades atuais são anêmicas (`@Getter/@Setter` JPA) com a regra nos services. Precisamos de domínio testável **sem** explodir o número de classes — legibilidade e baixa barreira de entrada são objetivo de primeira classe.

**Decisão:** Usar **uma única classe de domínio rica por agregado, ainda anotada com JPA** (`@Entity`). A riqueza vem de: construtor/factory que valida invariantes, métodos de negócio (`promoteToPublic`, `addVolume`, `approve`…), e **encapsulamento** (setters de mutação privados/`protected`; nada de `@Setter` público de Lombok em campos com invariante). As anotações JPA são tratadas como **metadado passivo**.

**Por quê:** O que impede testar regra hoje não são as anotações JPA — é `HttpStatus`/lógica nos services e a anemia das entidades. `new Manga(...).promoteToPublic(...)` não toca banco, logo é testável em JUnit puro mesmo com `@Entity`. A opção pura dobraria classes e adicionaria um mapper por agregado — a ceremônia que mais confunde um contribuidor novo ("por que dois `Manga`?") sem ganho proporcional num projeto deste tamanho.

**Tradeoff:** O domínio não é 100% framework-free; um caminho legado poderia persistir estado que burlou uma invariante. Mitigação: encapsular mutação atrás de métodos de negócio e construtores que validam. Se um agregado específico um dia exigir pureza total (cenário improvável aqui), separa-se **apenas** aquele, sem impor o custo aos demais.
