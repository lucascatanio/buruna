package com.buruna.work.application;

/**
 * Página já gravada no storage por quem importa de uma fonte externa. {@code dataSaverObjectName}
 * é nulo quando a fonte não tem a versão de economia de dados.
 */
public record ImportedPage(int position, String objectName, String dataSaverObjectName,
                           int width, int height, long sizeBytes) {
}
