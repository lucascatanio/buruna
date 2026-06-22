package com.buruna.identity.application.authentication;

import com.buruna.identity.domain.InvalidCaptchaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class CaptchaService {

    private static final String HCAPTCHA_VERIFY_URL = "https://api.hcaptcha.com/siteverify";

    private final String secret;
    private final RestTemplate restTemplate = new RestTemplate();

    public CaptchaService(@Value("${app.hcaptcha.secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(String token, String clientIp) {
        if (secret == null || secret.isBlank()) {
            // dev: skippa captcha quando não configurado
            return;
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secret);
        params.add("response", token);
        if (clientIp != null && !clientIp.isBlank()) {
            params.add("remoteip", clientIp);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    HCAPTCHA_VERIFY_URL,
                    new HttpEntity<>(params, headers),
                    Map.class
            );
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new InvalidCaptchaException();
            }
        } catch (InvalidCaptchaException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCaptchaException();
        }
    }
}
