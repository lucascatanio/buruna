package com.buruna.shared.source.anilist;

import com.buruna.shared.source.SourceTag;
import com.buruna.shared.source.SourceTagGroup;

import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * Gêneros e tags do AniList que têm par no vocabulário que o catálogo já usa (o do MangaDex,
 * traduzido em {@code SourceTagTranslations}), com o grupo que a tag tem lá (ADR-54). O que não
 * está aqui é ignorado: o catálogo não ganha tag nova por causa do AniList. Só pares exatos;
 * aproximações (Time Loop, Mixed Gender Harem, Work, Monster Boy) ficaram de fora de propósito.
 */
final class AniListTags {

    private static final Map<String, SourceTag> GENRES = Map.ofEntries(
            genre("Action"), genre("Adventure"), genre("Comedy"), genre("Drama"), genre("Fantasy"),
            genre("Horror"), entry("Mahou Shoujo", new SourceTag("Magical Girls", SourceTagGroup.GENRE)),
            genre("Mecha"), genre("Mystery"), genre("Psychological"), genre("Romance"), genre("Sci-Fi"),
            genre("Slice of Life"), genre("Sports"), genre("Thriller"),
            theme("Music"), theme("Supernatural"));

    private static final Map<String, SourceTag> TAGS = Map.ofEntries(
            // gêneros no catálogo
            genre("Crime"), genre("Historical"), genre("Isekai"), genre("Superhero"), genre("Tragedy"),
            entry("Medicine", new SourceTag("Medical", SourceTagGroup.GENRE)),
            entry("Philosophy", new SourceTag("Philosophical", SourceTagGroup.GENRE)),
            // temas
            theme("Aliens"), theme("Animals"), theme("Delinquents"), theme("Demons"), theme("Incest"),
            theme("Mafia"), theme("Magic"), theme("Martial Arts"), theme("Military"), theme("Police"),
            theme("Post-Apocalyptic"), theme("Reincarnation"), theme("Samurai"), theme("Survival"),
            theme("Video Games"), theme("Villainess"),
            entry("Ghost", new SourceTag("Ghosts", SourceTagGroup.THEME)),
            entry("Monster Girl", new SourceTag("Monster Girls", SourceTagGroup.THEME)),
            entry("Vampire", new SourceTag("Vampires", SourceTagGroup.THEME)),
            entry("Zombie", new SourceTag("Zombies", SourceTagGroup.THEME)),
            entry("Food", new SourceTag("Cooking", SourceTagGroup.THEME)),
            entry("Gender Bending", new SourceTag("Genderswap", SourceTagGroup.THEME)),
            entry("Female Harem", new SourceTag("Harem", SourceTagGroup.THEME)),
            entry("Male Harem", new SourceTag("Reverse Harem", SourceTagGroup.THEME)),
            entry("School", new SourceTag("School Life", SourceTagGroup.THEME)),
            entry("Virtual World", new SourceTag("Virtual Reality", SourceTagGroup.THEME)),
            entry("Board Game", new SourceTag("Traditional Games", SourceTagGroup.THEME)),
            entry("Mahjong", new SourceTag("Traditional Games", SourceTagGroup.THEME)),
            entry("Shogi", new SourceTag("Traditional Games", SourceTagGroup.THEME)),
            // formato
            format("Anthology"), format("Full Color"), format("Long Strip"),
            // aviso de conteúdo
            entry("Gore", new SourceTag("Gore", SourceTagGroup.CONTENT)),
            entry("Rape", new SourceTag("Sexual Violence", SourceTagGroup.CONTENT)));

    private AniListTags() {
    }

    static Optional<SourceTag> fromGenre(String aniListGenre) {
        return Optional.ofNullable(GENRES.get(aniListGenre));
    }

    static Optional<SourceTag> fromTag(String aniListTag) {
        return Optional.ofNullable(TAGS.get(aniListTag));
    }

    private static Map.Entry<String, SourceTag> genre(String name) {
        return entry(name, new SourceTag(name, SourceTagGroup.GENRE));
    }

    private static Map.Entry<String, SourceTag> theme(String name) {
        return entry(name, new SourceTag(name, SourceTagGroup.THEME));
    }

    private static Map.Entry<String, SourceTag> format(String name) {
        return entry(name, new SourceTag(name, SourceTagGroup.FORMAT));
    }
}
