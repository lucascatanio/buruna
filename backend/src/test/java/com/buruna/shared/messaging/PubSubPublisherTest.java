package com.buruna.shared.messaging;

import com.buruna.shared.exception.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PubSubPublisherTest {

    private static final String TOPIC = "projects/p/topics/t";
    private static final String PUBLISH_URL = "https://pubsub.googleapis.com/v1/projects/p/topics/t:publish";

    private MockRestServiceServer server;
    private PubSubPublisher publisher;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        publisher = new PubSubPublisher(restTemplate, () -> "um-token");
    }

    @Test
    void shouldPostBase64EncodedDataWithBearerToken_whenPublishing() {
        String json = "{\"email\":\"a@b.com\"}";
        String expectedData = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(PUBLISH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer um-token"))
                .andExpect(jsonPath("$.messages[0].data").value(expectedData))
                .andRespond(withSuccess());

        publisher.publish(TOPIC, json);

        server.verify();
    }

    @Test
    void shouldThrowMessagingException_whenHttpCallFails() {
        server.expect(requestTo(PUBLISH_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> publisher.publish(TOPIC, "{}"))
                .isInstanceOf(MessagingException.class);

        server.verify();
    }
}
