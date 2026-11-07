package com.buruna.sourcing.application;

import com.buruna.shared.media.ImageInspector.InspectedImage;

/** Página baixada de uma fonte: a original e, quando a fonte oferece, a versão leve (ou nula). */
record FetchedPage(InspectedImage original, InspectedImage dataSaver) {
}
