package com.buruna.sourcing.application;

/** Crédito exigido pelo MangaDex (ADR-46): nome da fonte e link da obra lá. */
public record WorkSourceResponse(String source, String url) {
}
