package com.buruna.feedback;

import com.buruna.shared.notification.EmailService;
import com.buruna.user.domain.Role;
import com.buruna.user.domain.User;
import com.buruna.user.domain.UserStatus;
import com.buruna.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private final EmailService emailService;
    private final UserRepository userRepository;

    public FeedbackController(EmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<Void> submit(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal User sender) {

        List<User> admins = userRepository.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        for (User admin : admins) {
            emailService.sendFeedbackNotification(
                    admin.getEmail(),
                    sender.getUsername(),
                    sender.getEmail(),
                    request.message(),
                    timestamp
            );
        }

        return ResponseEntity.ok().build();
    }
}
