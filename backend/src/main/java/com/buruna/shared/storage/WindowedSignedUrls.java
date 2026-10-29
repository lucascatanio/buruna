package com.buruna.shared.storage;

import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * URLs de leitura assinadas que se repetem dentro de uma janela de tempo (ADR-07, atualização
 * de 2026-10-10). A assinatura V4 embute o instante em que foi feita, então assinar de novo
 * sempre muda a URL e o navegador baixa a mesma página outra vez. Guardando a URL de cada
 * objeto até o fim da janela, quem reabre um capítulo recebe a mesma URL e o cache do
 * navegador funciona.
 *
 * <p>Toda URL emitida numa janela vale até o fim da janela seguinte, ou seja, pelo menos uma
 * janela inteira depois de entregue. O cache é por instância do Cloud Run e é esvaziado na
 * virada da janela.
 */
@Component
public class WindowedSignedUrls {

    static final Duration WINDOW = Duration.ofHours(1);

    private record Signed(long window, URL url) {
    }

    private final StorageClient storageClient;
    private final Clock clock;
    private final Map<String, Signed> cache = new ConcurrentHashMap<>();
    private volatile long latestWindow = -1;

    public WindowedSignedUrls(StorageClient storageClient, Clock clock) {
        this.storageClient = storageClient;
        this.clock = clock;
    }

    public URL urlFor(String objectName) {
        Instant now = clock.instant();
        long window = now.toEpochMilli() / WINDOW.toMillis();
        evictOlderThan(window);
        // a janela vai junto com a URL: uma requisição que leu o relógio antes da virada não
        // pode deixar no cache, para a janela nova, uma URL com o prazo da anterior
        return cache.compute(objectName, (name, cached) -> cached != null && cached.window() == window
                ? cached
                : new Signed(window, storageClient.generateSignedUrl(name, Duration.between(now, expiresAt(window)))))
                .url();
    }

    private void evictOlderThan(long window) {
        if (window > latestWindow) {
            latestWindow = window;
            cache.values().removeIf(signed -> signed.window() < window);
        }
    }

    /** Até quando as URLs emitidas agora valem. */
    public Instant currentExpiry() {
        return expiresAt(clock.instant().toEpochMilli() / WINDOW.toMillis());
    }

    private static Instant expiresAt(long window) {
        return Instant.ofEpochMilli((window + 2) * WINDOW.toMillis());
    }
}
