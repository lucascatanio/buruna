package com.buruna.shared.jobs;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dispara o Cloud Run Job pela API REST (jobs.run), dentro da requisição: no mesmo espírito
 * do {@code PubSubPublisher}, nada de thread de fundo, que o cpu-throttling congela (ADR-42).
 * Os argumentos substituem os do container só nesta execução.
 */
public class CloudRunJobLauncher implements JobLauncher {

    private static final String RUN_URL = "https://run.googleapis.com/v2/%s:run";

    private final RestTemplate restTemplate;
    private final Supplier<String> accessToken;
    private final String jobName;

    /** {@code jobName} no formato {@code projects/<projeto>/locations/<região>/jobs/<job>}. */
    public CloudRunJobLauncher(RestTemplate restTemplate, Supplier<String> accessToken, String jobName) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken;
        this.jobName = jobName;
    }

    @Override
    public void run(List<String> args) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken.get());
        Map<String, Object> body = Map.of("overrides",
                Map.of("containerOverrides", List.of(Map.of("args", args))));
        try {
            restTemplate.exchange(RUN_URL.formatted(jobName), HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);
        } catch (RestClientException e) {
            throw new JobLaunchException("Falha ao disparar o job " + jobName, e);
        }
    }
}
