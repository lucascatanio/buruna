package com.buruna.identity.application.authentication;

import com.buruna.identity.domain.User;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.OptionalLong;

/**
 * {@link #verify(User, String)} é o ponto único de verificação (FIND-004):
 * recusa se o usuário estiver bloqueado por força bruta, casa o código contra
 * os passos de tempo -1/0/+1 e então aceita (rejeitando replay do mesmo passo)
 * ou registra a falha no agregado. Usado por autenticação de 2FA, verify/disable
 * de 2FA e reset de senha com 2FA — o único ponto de entrada para não duplicar a
 * lógica de bloqueio/replay em cada caller.
 */
@Service
public class TotpService {

    private static final String ISSUER = "Burūna";
    private static final int TIME_PERIOD_SECONDS = 30;
    private static final int ALLOWED_TIME_STEP_DISCREPANCY = 1;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final Clock clock;

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String generateQrUri(String secret, String email) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(TIME_PERIOD_SECONDS)
                .build();
        return data.getUri();
    }

    /**
     * Testa o código contra os passos de tempo -1/0/+1 (tolerância a diferença de
     * relógio) e devolve o passo que casou, para o agregado poder rejeitar reuso
     * do mesmo passo (replay).
     */
    public OptionalLong matchingTimeStep(String secret, String code) {
        long currentStep = Math.floorDiv(timeProvider.getTime(), TIME_PERIOD_SECONDS);
        for (int offset = -ALLOWED_TIME_STEP_DISCREPANCY; offset <= ALLOWED_TIME_STEP_DISCREPANCY; offset++) {
            long step = currentStep + offset;
            try {
                if (codeGenerator.generate(secret, step).equals(code)) {
                    return OptionalLong.of(step);
                }
            } catch (CodeGenerationException e) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Ponto único de verificação de TOTP (FIND-004). Quem chama precisa rodar
     * dentro de uma transação com {@code noRollbackFor = BadCredentialsException.class}:
     * a falha lança essa exceção, e sem a anotação o rollback padrão desfaria o
     * incremento do contador de tentativas no agregado.
     */
    public void verify(User user, String code) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        user.assertTotpNotLocked(now);

        OptionalLong step = matchingTimeStep(user.getTotpSecret(), code);
        if (step.isEmpty()) {
            user.registerTotpFailure(now);
            throw new BadCredentialsException("Invalid TOTP code");
        }
        user.acceptTotpStep(step.getAsLong());
    }
}
