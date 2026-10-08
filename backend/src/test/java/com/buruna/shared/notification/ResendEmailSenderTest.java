package com.buruna.shared.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.IntStream;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailSenderTest {

    private static final String BATCH_URL = "https://api.resend.com/emails/batch";

    private MockRestServiceServer server;
    private ResendEmailSender sender;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        sender = new ResendEmailSender(restTemplate, "test-key", "noreply@buruna.test");
    }

    @Test
    void shouldSplitIntoCallsOfAtMost100_whenBatchIsLarger() {
        server.expect(once(), requestTo(BATCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.length()").value(ResendEmailSender.MAX_BATCH_SIZE))
                .andExpect(jsonPath("$[0].to[0]").value("user0@buruna.test"))
                .andRespond(withSuccess());
        server.expect(once(), requestTo(BATCH_URL))
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].to[0]").value("user100@buruna.test"))
                .andRespond(withSuccess());

        sender.sendBatch(emails(150));

        server.verify();
    }

    @Test
    void shouldStillSendNextChunk_whenOneChunkFails() {
        server.expect(once(), requestTo(BATCH_URL)).andRespond(withServerError());
        server.expect(once(), requestTo(BATCH_URL))
                .andExpect(jsonPath("$[0].to[0]").value("user100@buruna.test"))
                .andRespond(withSuccess());

        sender.sendBatch(emails(101));

        server.verify();
    }

    @Test
    void shouldSendPersonalizedContent_whenEachEmailDiffers() {
        server.expect(once(), requestTo(BATCH_URL))
                .andExpect(jsonPath("$[0].text").value("Hello ana"))
                .andExpect(jsonPath("$[1].text").value("Hello bia"))
                .andRespond(withSuccess());

        sender.sendBatch(List.of(
                new OutgoingEmail("ana@buruna.test", "Aviso", "Hello ana"),
                new OutgoingEmail("bia@buruna.test", "Aviso", "Hello bia")));

        server.verify();
    }

    private static List<OutgoingEmail> emails(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new OutgoingEmail("user" + i + "@buruna.test", "Assunto", "Corpo " + i))
                .toList();
    }
}
