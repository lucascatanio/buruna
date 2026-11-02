package com.buruna.sourcing.application;

/** Miniatura de capa vinda de uma fonte, servida pelo backend: as fontes não liberam hotlink no site. */
public record CoverThumbnail(byte[] content, String contentType) {
}
