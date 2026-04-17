package com.buruna.infra.notification;

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
                    factory.setConnectTimeout(Duration.ofSeconds(10));
                    factory.setConnectionRequestTimeout(Duration.ofSeconds(10));
                    return factory;
                })
                .build();
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[EMAIL SKIP] RESEND_API_KEY not configured. Would send to: " + to + " | Subject: " + subject);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> payload = Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", subject,
                    "text", body
            );

            restTemplate.exchange(RESEND_API_URL, HttpMethod.POST,
                    new HttpEntity<>(payload, headers), Void.class);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}