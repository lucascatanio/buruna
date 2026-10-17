package com.buruna.sourcing.application;

import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;
import com.buruna.work.application.TagSpec;

import java.util.Map;
import static java.util.Map.entry;

/**
 * Traduz as tags do MangaDex (em inglês) para o vocabulário do Burūna e diz em que categoria
 * cada uma entra. Onde já existe tag equivalente semeada (V13), o nome é o dela, para cair na
 * mesma tag. Tag sem tradução entra com o nome original.
 */
final class SourceTagTranslations {

    private static final Map<String, String> NAMES = Map.ofEntries(
            // gêneros
            entry("Action", "Ação"), entry("Adventure", "Aventura"), entry("Comedy", "Comédia"),
            entry("Crime", "Crime"), entry("Drama", "Drama"), entry("Fantasy", "Fantasia"),
            entry("Historical", "Histórico"), entry("Horror", "Horror"), entry("Isekai", "Isekai"),
            entry("Magical Girls", "Garotas Mágicas"), entry("Mecha", "Robôs / Mechas"),
            entry("Medical", "Medicina"), entry("Mystery", "Mistério"), entry("Philosophical", "Filosófico"),
            entry("Psychological", "Psicológico"), entry("Romance", "Romance"),
            entry("Sci-Fi", "Ficção Científica"), entry("Slice of Life", "Slice of Life"),
            entry("Sports", "Esportes"), entry("Superhero", "Super-heróis"), entry("Thriller", "Suspense"),
            entry("Tragedy", "Tragédia"),
            // temas
            entry("Aliens", "Alienígenas"), entry("Animals", "Animais"), entry("Cooking", "Culinária"),
            entry("Delinquents", "Delinquentes"), entry("Demons", "Demônios"),
            entry("Genderswap", "Troca de Gênero"), entry("Ghosts", "Fantasmas"), entry("Harem", "Harém"),
            entry("Incest", "Incesto"), entry("Mafia", "Máfia"), entry("Magic", "Magia"),
            entry("Martial Arts", "Artes Marciais"), entry("Military", "Militar"),
            entry("Monster Girls", "Garotas Monstro"), entry("Monsters", "Monstros"), entry("Music", "Música"),
            entry("Office Workers", "Escritório"), entry("Police", "Polícia"),
            entry("Post-Apocalyptic", "Apocalipse"), entry("Reincarnation", "Reencarnação"),
            entry("Reverse Harem", "Harém Reverso"), entry("Samurai", "Samurai / Feudal"),
            entry("School Life", "Escola"), entry("Supernatural", "Sobrenatural"),
            entry("Survival", "Sobrevivência"), entry("Time Travel", "Viagem no Tempo"),
            entry("Traditional Games", "Jogos Tradicionais"), entry("Vampires", "Vampiros"),
            entry("Video Games", "Videogames"), entry("Villainess", "Vilã"),
            entry("Virtual Reality", "Realidade Virtual"), entry("Zombies", "Zumbis"),
            // formato
            entry("Adaptation", "Adaptação"), entry("Anthology", "Antologia"), entry("Award Winning", "Premiado"),
            entry("Fan Colored", "Colorido por Fãs"), entry("Full Color", "Colorido"), entry("Long Strip", "Webtoon"),
            entry("Official Colored", "Colorido Oficial"), entry("Oneshot", "One-shot"),
            entry("Self-Published", "Independente"), entry("User Created", "Criado por Usuário"),
            // conteúdo
            entry("Gore", "Gore"), entry("Sexual Violence", "Violência Sexual"));

    private static final Map<SourceTagGroup, String> CATEGORIES = Map.of(
            SourceTagGroup.GENRE, "Gênero",
            SourceTagGroup.THEME, "Tema",
            SourceTagGroup.FORMAT, "Formato",
            SourceTagGroup.CONTENT, "Aviso de Conteúdo");

    static final String DEMOGRAPHY_CATEGORY = "Demografia";
    static final String FORMAT_CATEGORY = "Formato";

    private SourceTagTranslations() {
    }

    static TagSpec translate(SourceTag tag) {
        return new TagSpec(NAMES.getOrDefault(tag.name(), tag.name()), CATEGORIES.get(tag.group()));
    }

    /** {@code shounen} → "Shounen", na categoria Demografia. */
    static TagSpec demographic(String value) {
        return new TagSpec(Character.toUpperCase(value.charAt(0)) + value.substring(1), DEMOGRAPHY_CATEGORY);
    }
}
