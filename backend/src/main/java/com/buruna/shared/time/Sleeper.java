package com.buruna.shared.time;

import java.time.Duration;

/** Espera um tempo. Existe para os testes não dormirem de verdade. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration);
}
