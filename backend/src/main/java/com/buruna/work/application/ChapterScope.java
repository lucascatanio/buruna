package com.buruna.work.application;

/**
 * De onde vem o pedido sobre capítulos: da coleção privada do dono ({@code /my/works}) ou do
 * catálogo público ({@code /works}, colaborador dono ou ADMIN). Define a checagem de acesso e
 * se a quota vale.
 */
public enum ChapterScope {
    PRIVATE,
    PUBLIC
}
