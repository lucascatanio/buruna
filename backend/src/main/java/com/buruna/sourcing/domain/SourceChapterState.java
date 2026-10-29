package com.buruna.sourcing.domain;

/**
 * PENDING: escolhida para entrar e ainda não baixada. READY: virou capítulo no Burūna.
 * FAILED: o download ou a publicação falhou. IGNORED: outra versão do mesmo número foi
 * escolhida (grupo preferido ou a mais recente).
 */
public enum SourceChapterState {
    PENDING,
    READY,
    FAILED,
    IGNORED
}
