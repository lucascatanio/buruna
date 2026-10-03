package com.buruna.shared.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final String RESEND_BATCH_URL = "https://api.resend.com/emails/batch";
    // os e-mails saem dentro da requisição (Cloud Run corta a CPU fora dela), então o
    // timeout de resposta limita quanto o usuário espera se o Resend travar
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String from;

    public ResendEmailSender(
            @Value("${resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String from) {
        this.restTemplate = new RestTemplateBuilder()
                .requestFactory(() -> {
                    HttpComponentsClientHttpRequestFactory factory =
                            new HttpComponentsClientHttpRequestFactory();
                    factory.setConnectTimeout(TIMEOUT);
                    factory.setConnectionRequestTimeout(TIMEOUT);
                    factory.setReadTimeout(TIMEOUT);
                    return factory;
                })
                .build();
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void sendOrFail(String to, String subject, String body) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[EMAIL SKIP] RESEND_API_KEY not configured. Would send to: " + to + " | Subject: " + subject);
            return;
        }
        try {
            restTemplate.exchange(RESEND_API_URL, HttpMethod.POST,
                    new HttpEntity<>(payload(to, subject, body), headers()), Void.class);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            throw new EmailDeliveryException("Falha ao enviar e-mail para " + to, e);
        }
    }

    @Override
    public void sendToEach(List<String> recipients, String subject, String body) {
        if (recipients.isEmpty()) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[EMAIL SKIP] RESEND_API_KEY not configured. Would send to: " + recipients + " | Subject: " + subject);
            return;
        }
        try {
            List<Map<String, Object>> batch = recipients.stream()
                    .map(to -> payload(to, subject, body))
                    .toList();
            restTemplate.exchange(RESEND_BATCH_URL, HttpMethod.POST,
                    new HttpEntity<>(batch, headers()), Void.class);
            log.info("Email sent successfully to {}", recipients);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", recipients, e.getMessage());
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private Map<String, Object> payload(String to, String subject, String body) {
        return Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "text", body
        );
    }
}
