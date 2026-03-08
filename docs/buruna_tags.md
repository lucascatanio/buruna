# Burūna — Lista Inicial de Tags

---

## Categorias e Tags

### Formato

| Slug     | Nome exibido |
| -------- | ------------ |
| manga    | Mangá        |
| manhwa   | Manhwa       |
| manhua   | Manhua       |
| webtoon  | Webtoon      |
| one-shot | One-shot     |

---

### Demografia

| Slug    | Nome exibido | Público-alvo              |
| ------- | ------------ | ------------------------- |
| shounen | Shounen      | Jovens do sexo masculino  |
| shoujo  | Shoujo       | Jovens do sexo feminino   |
| seinen  | Seinen       | Adultos do sexo masculino |
| josei   | Josei        | Adultas do sexo feminino  |
| kodomo  | Kodomo       | Crianças                  |

---

### Gênero

| Slug              | Nome exibido      |
| ----------------- | ----------------- |
| acao              | Ação              |
| aventura          | Aventura          |
| comedia           | Comédia           |
| drama             | Drama             |
| fantasia          | Fantasia          |
| ficcao-cientifica | Ficção Científica |
| horror            | Horror            |
| misterio          | Mistério          |
| romance           | Romance           |
| slice-of-life     | Slice of Life     |
| sobrenatural      | Sobrenatural      |
| suspense          | Suspense          |
| esportes          | Esportes          |
| psicologico       | Psicológico       |
| historico         | Histórico         |
| militar           | Militar           |
| musica            | Música            |
| culinaria         | Culinária         |
| artes-marciais    | Artes Marciais    |

---

### Tema / Tropo

| Slug            | Nome exibido                    |
| --------------- | ------------------------------- |
| escola          | Escola                          |
| isekai          | Isekai                          |
| reencarnacao    | Reencarnação                    |
| viagem-no-tempo | Viagem no tempo                 |
| poderes         | Poderes / Habilidades especiais |
| apocalipse      | Apocalipse                      |
| magia           | Magia                           |
| robos-mechas    | Robôs / Mechas                  |
| vampiros        | Vampiros                        |
| zumbis          | Zumbis                          |
| samurai         | Samurai / Feudal                |
| cyberpunk       | Cyberpunk                       |
| harem           | Harém                           |
| competicao      | Competição / Torneio            |
| vinganca        | Vingança                        |
| amizade         | Amizade                         |
| sobrevivencia   | Sobrevivência                   |
| vida-cotidiana  | Vida cotidiana                  |

---

### Avisos de Conteúdo

| Slug             | Nome exibido             |
| ---------------- | ------------------------ |
| nsfw             | NSFW (conteúdo adulto)   |
| gore             | Gore (violência extrema) |
| gatilho-suicidio | Gatilho: Suicídio        |
| gatilho-abuso    | Gatilho: Abuso           |
| gatilho-trauma   | Gatilho: Trauma          |

---

## Notas de Implementação

- Autor (story) e Artista (art) são tratados como **tags de categoria própria**, pois seus valores são únicos por obra e não reutilizáveis como gêneros.
- O campo `slug` deve ser único por categoria para evitar conflitos.
- Soft delete via campo `deleted_at` — tags deletadas não aparecem na UI mas mantêm integridade referencial no banco.
