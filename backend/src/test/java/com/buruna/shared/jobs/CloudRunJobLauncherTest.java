package com.buruna.shared.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudRunJobLauncherTest {

    private static final String JOB = "projects/p/locations/us-east1/jobs/buruna-ingest";
    private static final String RUN_URL = "https://run.googleapis.com/v2/" + JOB + ":run";

    private MockRestServiceServer server;
    private CloudRunJobLauncher launcher;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        launcher = new CloudRunJobLauncher(restTemplate, () -> "um-token", JOB);
    }

    @Test
    void shouldOverrideContainerArgsWithBearerToken_whenRunningTheJob() {
        // Arrange
        server.expect(requestTo(RUN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer um-token"))
                .andExpect(jsonPath("$.overrides.containerOverrides[0].args[0]").value("--ingest.chapter-id=abc"))
                .andRespond(withSuccess());

        // Act
        launcher.run(List.of("--ingest.chapter-id=abc"));

        // Assert
        server.verify();
    }

    @Test
    void shouldThrowJobLaunchException_whenCloudRunRejectsTheCall() {
        // Arrange
        server.expect(requestTo(RUN_URL)).andRespond(withServerError());

        // Act / Assert
        assertThatThrownBy(() -> launcher.run(List.of())).isInstanceOf(JobLaunchException.class);
        server.verify();
    }
}
