package com.buruna.identity.application;

import com.buruna.identity.domain.User;
import com.buruna.identity.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura cross-contexto de dados de usuário (ADR-39): outros contextos pedem a
 * {@link UserSummary} por id em vez de tocar {@code UserRepository}/{@code User} de identity.
 */
@Service
public class GetUserSummaryUseCase {

    private final UserRepository userRepository;

    public GetUserSummaryUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findById(UUID userId) {
        return userRepository.findById(userId).map(GetUserSummaryUseCase::toSummary);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAllById(Collection<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(GetUserSummaryUseCase::toSummary)
                .toList();
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getEmail());
    }
}
