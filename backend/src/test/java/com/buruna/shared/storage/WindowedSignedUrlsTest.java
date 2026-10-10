package com.buruna.shared.storage;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WindowedSignedUrlsTest {

    static final Instant START = Instant.parse("2026-10-10T12:10:00Z");

    StorageClient storage = mock(StorageClient.class);
    AtomicReference<Instant> now = new AtomicReference<>(START);
    Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    WindowedSignedUrls urls = new WindowedSignedUrls(storage, clock);

    void signsWithAFreshUrlEachCall() throws Exception {
        int[] counter = {0};
        when(storage.generateSignedUrl(anyString(), any(Duration.class)))
                .thenAnswer(inv -> new URL("https://storage.example.com/" + inv.getArgument(0) + "?sig=" + counter[0]++));
    }

    @Test
    void shouldReturnTheSameUrl_whenTheSameObjectIsRequestedInTheSameWindow() throws Exception {
        // Arrange
        signsWithAFreshUrlEachCall();
        URL first = urls.urlFor("chapters/c/1.png");
        now.set(START.plus(Duration.ofMinutes(40)));

        // Act
        URL second = urls.urlFor("chapters/c/1.png");

        // Assert
        assertThat(second).isEqualTo(first);
        verify(storage, times(1)).generateSignedUrl(anyString(), any(Duration.class));
    }

    @Test
    void shouldSignAgain_whenTheWindowTurns() throws Exception {
        // Arrange
        signsWithAFreshUrlEachCall();
        URL first = urls.urlFor("chapters/c/1.png");
        now.set(Instant.parse("2026-10-10T13:00:01Z"));

        // Act
        URL second = urls.urlFor("chapters/c/1.png");

        // Assert
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void shouldStayValidUntilTheEndOfTheNextWindow_whenSigned() throws Exception {
        // Arrange
        signsWithAFreshUrlEachCall();

        // Act
        urls.urlFor("chapters/c/1.png");

        // Assert: janela 12:00–13:00, então vale até 14:00 (1h50 a partir de 12:10)
        verify(storage).generateSignedUrl("chapters/c/1.png", Duration.ofMinutes(110));
        assertThat(urls.currentExpiry()).isEqualTo(Instant.parse("2026-10-10T14:00:00Z"));
    }

    @Test
    void shouldSignAgainInTheCurrentWindow_whenTheCachedUrlIsFromAnOlderWindow() throws Exception {
        // Arrange: a janela nova já foi vista (a corrida real, entre threads, não é reproduzível aqui)
        signsWithAFreshUrlEachCall();
        now.set(Instant.parse("2026-10-10T13:00:01Z"));
        urls.urlFor("chapters/c/2.png");
        // uma requisição atrasada, com o relógio ainda na janela anterior, assina a página 1
        now.set(Instant.parse("2026-10-10T12:59:59Z"));
        URL late = urls.urlFor("chapters/c/1.png");
        now.set(Instant.parse("2026-10-10T13:05:00Z"));

        // Act
        URL current = urls.urlFor("chapters/c/1.png");

        // Assert: a página 1 é assinada de novo com o prazo da janela nova
        assertThat(current).isNotEqualTo(late);
        verify(storage).generateSignedUrl("chapters/c/1.png", Duration.ofMinutes(115));
    }
}
