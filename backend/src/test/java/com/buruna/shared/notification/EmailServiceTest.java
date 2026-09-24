package com.buruna.shared.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final List<String> ADMINS = List.of("a@buruna.test", "b@buruna.test", "c@buruna.test");

    @Mock EmailSender emailSender;
    @InjectMocks EmailService emailService;

    @Test
    void shouldSendSingleBatchToAllAdmins_whenNewRegistration() {
        emailService.sendNewRegistrationNotification(ADMINS, "novo", "novo@buruna.test");

        verify(emailSender).sendToEach(eq(ADMINS), anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldSendSingleBatchToAllAdmins_whenFeedback() {
        emailService.sendFeedbackNotification(ADMINS, "leitor", "leitor@buruna.test", "msg", "2026-09-24T00:00Z");

        verify(emailSender).sendToEach(eq(ADMINS), anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldSendSingleBatchToAllAdmins_whenMangaSubmitted() {
        emailService.sendMangaSubmissionNotification(ADMINS, "colab", "Titulo");

        verify(emailSender).sendToEach(eq(ADMINS), anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}
