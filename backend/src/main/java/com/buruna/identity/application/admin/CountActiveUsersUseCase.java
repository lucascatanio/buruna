package com.buruna.identity.application.admin;

import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expõe a contagem de usuários ativos para o dashboard de admin (ADR-39), sem que
 * outros contextos toquem {@code UserRepository} diretamente.
 */
@Service
public class CountActiveUsersUseCase {

    private final UserRepository userRepository;

    public CountActiveUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public long handle() {
        return userRepository.countByStatus(UserStatus.ACTIVE);
    }
}
