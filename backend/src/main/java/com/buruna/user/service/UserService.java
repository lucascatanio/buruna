package com.buruna.user.service;

import com.buruna.shared.notification.EmailService;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import com.buruna.user.domain.UserStatus;
import com.buruna.user.dto.*;
import com.buruna.user.exception.UserNotFoundException;
import com.buruna.user.exception.UserNotPendingException;
import com.buruna.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public Page<UserResponse> listAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<UserResponse> listPending(Pageable pageable) {
        return userRepository.findByStatus(UserStatus.PENDING, pageable).map(this::toResponse);
    }

    public UserResponse findById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public void approve(UUID id) {
        User user = findOrThrow(id);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserNotPendingException();
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        emailService.sendApprovalNotification(user.getEmail(), user.getUsername());
    }

    @Transactional
    public void reject(UUID id, String reason) {
        User user = findOrThrow(id);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new UserNotPendingException();
        }

        userRepository.delete(user);
        emailService.sendRejectionNotification(user.getEmail(), user.getUsername(), reason);
    }

    @Transactional
    public UserResponse updateRole(UUID id, Role role) {
        User user = findOrThrow(id);
        user.setRole(role);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status) {
        User user = findOrThrow(id);
        user.setStatus(status);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateQuota(UUID id, BigDecimal quotaGb) {
        User user = findOrThrow(id);
        user.setQuotaGb(quotaGb);
        return toResponse(userRepository.save(user));
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getPresentationMessage(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getQuotaGb(),
                user.getCreatedAt()
        );
    }
}
