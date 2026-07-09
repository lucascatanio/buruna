package com.buruna.identity.application;

import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Expõe os e-mails dos administradores ativos para outros contextos enviarem
 * notificações (ADR-39), sem que eles conheçam {@code Role}/{@code UserStatus} nem o
 * {@code UserRepository} de identity.
 */
@Service
public class ListActiveAdminEmailsUseCase {

    private final UserRepository userRepository;

    public ListActiveAdminEmailsUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<String> handle() {
        return userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE).stream()
                .map(User::getEmail)
                .toList();
    }
}
