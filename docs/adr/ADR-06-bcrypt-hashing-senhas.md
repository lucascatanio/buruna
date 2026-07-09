# ADR-06 — BCrypt para hashing de senhas

**Contexto:** Senhas de usuários precisam ser armazenadas de forma segura.

**Decisão:** BCrypt via `PasswordEncoder` do Spring Security.

**Por quê:** BCrypt é um algoritmo de hashing adaptativo feito especificamente para senhas. Inclui salt automático e work factor configurável que torna brute force impraticável. SHA-256, por outro lado, é rápido por design, que é o oposto do que você quer para senhas (facilita brute force). O Spring Security já vem com BCrypt como implementação padrão, sem config extra.

**Tradeoff:** BCrypt é mais lento por request de login comparado a SHA-256 (~100ms vs ~1ms). Esse custo é intencional e imperceptível pro usuário.
