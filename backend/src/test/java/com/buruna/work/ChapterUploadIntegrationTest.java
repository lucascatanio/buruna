package com.buruna.work;

import com.buruna.identity.domain.Email;
import com.buruna.identity.domain.Quota;
import com.buruna.identity.domain.Role;
import com.buruna.identity.domain.User;
import com.buruna.identity.domain.UserStatus;
import com.buruna.identity.domain.Username;
import com.buruna.identity.persistence.UserRepository;
import com.buruna.shared.exception.StorageException;
import com.buruna.shared.notification.EmailService;
import com.buruna.shared.storage.StorageClient;
import com.buruna.work.domain.Chapter;
import com.buruna.work.domain.ChapterPage;
import com.buruna.work.domain.ChapterStatus;
import com.buruna.work.persistence.ChapterRepository;
import com.buruna.work.persistence.WorkRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload de capítulo ponta a ponta (ADR-48). No profile local o ingest roda na própria
 * requisição, depois do commit, então o capítulo já está resolvido quando o finalize volta.
 * O storage é mockado: o CBZ "enviado" é o que {@code openRead} devolve.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers
class ChapterUploadIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @Autowired ChapterRepository chapterRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean StorageClient storageClient;
    @MockitoBean EmailService emailService;

    User owner;
    User other;
    User collab;
    User reader;

    @BeforeEach
    void setUp() throws Exception {
        when(storageClient.generateUploadSignedUrl(anyString(), any(Duration.class)))
                .thenReturn(new StorageClient.SignedUpload(new URL("https://storage.example.com/put"), Map.of()));
        when(storageClient.getFileMetadata(anyString())).thenReturn(new StorageClient.FileMetadata("md5", 4096L));

        chapterRepository.deleteAll();
        workRepository.deleteAll();
        userRepository.deleteAllInBatch();

        owner = userRepository.save(user("owner@chapters.test", "chapterOwner", Role.READER));
        other = userRepository.save(user("other@chapters.test", "chapterOther", Role.READER));
        collab = userRepository.save(user("collab@chapters.test", "chapterCollab", Role.COLLABORATOR));
        reader = userRepository.save(user("reader@chapters.test", "chapterReader", Role.READER));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    static User user(String email, String username, Role role) {
        User u = User.register(Email.of(email), Username.of(username),
                "$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC", "test",
                Quota.of(BigDecimal.ONE));
        u.changeRole(role);
        u.changeStatus(UserStatus.ACTIVE);
        return u;
    }

    RequestPostProcessor auth(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    String createPrivateWork(User u) throws Exception {
        String body = mockMvc.perform(post("/my/works").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Obra " + UUID.randomUUID() + "\"}").with(auth(u)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String createPublicWork(User u) throws Exception {
        String body = mockMvc.perform(post("/works").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Pública " + UUID.randomUUID() + "\",\"format\":\"MANGA\","
                                + "\"statusOrigin\":\"ONGOING\",\"statusSite\":\"INCOMPLETE\"}")
                        .with(auth(u)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String requestUploadUrl(String base, String workId, String number, User u) throws Exception {
        String body = mockMvc.perform(post(base + "/{id}/chapters/upload-url", workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"pt-BR\",\"number\":" + number + "}").with(auth(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.objectName");
    }

    ResultActions finalizeUpload(String base, String workId, String objectName, String number, User u) throws Exception {
        return mockMvc.perform(post(base + "/{id}/chapters/finalize", workId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectName\":\"" + objectName + "\",\"language\":\"pt-br\",\"number\":" + number
                        + ",\"scanlationGroup\":\"Grupo X\"}")
                .with(auth(u)));
    }

    static String sourceOf(String pendingObjectName) {
        return pendingObjectName.replaceFirst("^pending/chapters/", "chapter-sources/");
    }

    /** Sobe o capítulo: o CBZ que o storage devolve para o arquivo-fonte é {@code cbz}. */
    UUID upload(String base, String workId, String number, User u, byte[] cbz) throws Exception {
        String pending = requestUploadUrl(base, workId, number, u);
        when(storageClient.openRead(sourceOf(pending))).thenAnswer(inv -> new ByteArrayInputStream(cbz));
        String body = finalizeUpload(base, workId, pending, number, u)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** CBZ com as páginas fora de ordem no zip e lixo de sistema que precisa ser ignorado. */
    static byte[] cbz() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : List.of("page10.png", "page2.png", "page1.png")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(png(100 + name.length(), 150));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("ComicInfo.xml"));
            zip.write("<ComicInfo/>".getBytes());
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    Chapter reload(UUID chapterId) {
        return transactionTemplate.execute(tx -> {
            Chapter chapter = chapterRepository.findWithPagesById(chapterId).orElseThrow();
            chapter.getPages().size();
            return chapter;
        });
    }

    // ── Coleção privada ─────────────────────────────────────────────────────

    @Test
    void shouldPublishPagesInReadingOrder_whenPrivateCbzIsUploaded() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        String pending = requestUploadUrl("/my/works", workId, "1", owner);
        byte[] cbz = cbz();
        when(storageClient.openRead(sourceOf(pending))).thenAnswer(inv -> new ByteArrayInputStream(cbz));

        // Act
        ResultActions result = finalizeUpload("/my/works", workId, pending, "1", owner);

        // Assert
        String body = result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("pt-BR"))
                .andReturn().getResponse().getContentAsString();
        UUID chapterId = UUID.fromString(JsonPath.read(body, "$.id"));
        Chapter chapter = reload(chapterId);
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
        assertThat(chapter.getScanlationGroup()).contains("Grupo X");
        assertThat(chapter.getPages()).extracting(ChapterPage::getObjectName).containsExactly(
                "chapters/" + chapterId + "/1.png", "chapters/" + chapterId + "/2.png", "chapters/" + chapterId + "/3.png");
        // page1 (nome de 9 letras) vem primeiro, page10 por último
        assertThat(chapter.getPages()).extracting(ChapterPage::getWidth).containsExactly(109, 109, 110);
        assertThat(chapter.getSourceObjectName()).isEmpty();
        verify(storageClient).move(pending, sourceOf(pending));
        verify(storageClient).delete(sourceOf(pending));
    }

    @Test
    void shouldFailAndDropSource_whenCbzIsInvalid() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);

        // Act
        UUID chapterId = upload("/my/works", workId, "1", owner, "isto não é zip".getBytes());

        // Assert
        Chapter chapter = reload(chapterId);
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.FAILED);
        assertThat(chapter.getFailureReason()).contains("O arquivo não é um CBZ válido");
        assertThat(chapter.getSourceObjectName()).isEmpty();
        verify(storageClient, never()).upload(any(), startsWith("chapters/"), anyString(), anyLong());
    }

    @Test
    void shouldKeepSourceAndPublishOnRetry_whenFirstAttemptFailsTransiently() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        String pending = requestUploadUrl("/my/works", workId, "1", owner);
        byte[] cbz = cbz();
        when(storageClient.openRead(sourceOf(pending)))
                .thenThrow(new StorageException("GCS fora do ar", new IOException()))
                .thenAnswer(inv -> new ByteArrayInputStream(cbz));
        String body = finalizeUpload("/my/works", workId, pending, "1", owner)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID chapterId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(reload(chapterId).getStatus()).isEqualTo(ChapterStatus.FAILED);
        assertThat(reload(chapterId).getSourceObjectName()).contains(sourceOf(pending));

        // Act
        mockMvc.perform(post("/my/works/{w}/chapters/{c}/retry", workId, chapterId).with(auth(owner)))
                .andExpect(status().isOk());

        // Assert
        Chapter chapter = reload(chapterId);
        assertThat(chapter.getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
        assertThat(chapter.getPages()).hasSize(3);
    }

    @Test
    void shouldReturn400_whenRetryingAPublishedChapter() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        UUID chapterId = upload("/my/works", workId, "1", owner, cbz());

        // Act / Assert
        mockMvc.perform(post("/my/works/{w}/chapters/{c}/retry", workId, chapterId).with(auth(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409AndKeepFileInPending_whenNumberAlreadyExists() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        upload("/my/works", workId, "1", owner, cbz());
        String secondPending = transactionTemplate.execute(tx ->
                com.buruna.work.domain.ChapterObjectName.pendingFor(UUID.fromString(workId)));

        // Act
        ResultActions result = finalizeUpload("/my/works", workId, secondPending, "1.0", owner);

        // Assert
        result.andExpect(status().isConflict());
        verify(storageClient, never()).move(eq(secondPending), anyString());
        assertThat(chapterRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturn409BeforeUpload_whenUploadUrlIsRequestedForExistingNumber() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        upload("/my/works", workId, "1", owner, cbz());

        // Act / Assert
        mockMvc.perform(post("/my/works/{id}/chapters/upload-url", workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"pt-BR\",\"number\":1}").with(auth(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn422AndRegisterNothing_whenChapterExceedsQuota() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        String pending = requestUploadUrl("/my/works", workId, "1", owner);
        when(storageClient.getFileMetadata(pending))
                .thenReturn(new StorageClient.FileMetadata("md5", 2L * 1024 * 1024 * 1024));

        // Act
        ResultActions result = finalizeUpload("/my/works", workId, pending, "1", owner);

        // Assert
        result.andExpect(status().isUnprocessableEntity());
        assertThat(chapterRepository.count()).isZero();
    }

    @Test
    void shouldCountPublishedPagesInQuota_whenChapterIsPublished() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        UUID chapterId = upload("/my/works", workId, "1", owner, cbz());
        long pageBytes = reload(chapterId).getPages().stream().mapToLong(ChapterPage::getSizeBytes).sum();

        // Act
        ResultActions result = mockMvc.perform(get("/my/works/quota").with(auth(owner)));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.usedBytes").value(pageBytes));
    }

    @Test
    void shouldReturn400_whenPendingObjectBelongsToAnotherWork() throws Exception {
        // Arrange
        String workA = createPrivateWork(owner);
        String workB = createPrivateWork(owner);
        String pendingOfA = requestUploadUrl("/my/works", workA, "1", owner);

        // Act / Assert
        finalizeUpload("/my/works", workB, pendingOfA, "1", owner).andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn403_whenAnotherUserUploadsToPrivateWork() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);

        // Act / Assert
        mockMvc.perform(post("/my/works/{id}/chapters/upload-url", workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"pt-BR\",\"number\":1}").with(auth(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDeleteChapterAndItsPages_whenOwnerDeletesChapter() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        UUID chapterId = upload("/my/works", workId, "1", owner, cbz());

        // Act
        mockMvc.perform(delete("/my/works/{w}/chapters/{c}", workId, chapterId).with(auth(owner)))
                .andExpect(status().isNoContent());

        // Assert
        assertThat(chapterRepository.findById(chapterId)).isEmpty();
        verify(storageClient).delete("chapters/" + chapterId + "/1.png");
        verify(storageClient).delete("chapters/" + chapterId + "/3.png");
    }

    @Test
    void shouldReturn404_whenChapterBelongsToAnotherWork() throws Exception {
        // Arrange
        String workA = createPrivateWork(owner);
        String workB = createPrivateWork(owner);
        UUID chapterOfA = upload("/my/works", workA, "1", owner, cbz());

        // Act / Assert
        mockMvc.perform(delete("/my/works/{w}/chapters/{c}", workB, chapterOfA).with(auth(owner)))
                .andExpect(status().isNotFound());
        assertThat(chapterRepository.findById(chapterOfA)).isPresent();
    }

    @Test
    void shouldDeleteChapterPagesFromStorage_whenOwnerDeletesTheWork() throws Exception {
        // Arrange
        String workId = createPrivateWork(owner);
        UUID chapterId = upload("/my/works", workId, "1", owner, cbz());

        // Act
        mockMvc.perform(delete("/my/works/{id}", workId).with(auth(owner)));

        // Assert
        assertThat(chapterRepository.findById(chapterId)).isEmpty();
        verify(storageClient).delete("chapters/" + chapterId + "/2.png");
    }

    // ── Catálogo público ────────────────────────────────────────────────────

    @Test
    void shouldPublish_whenCollaboratorUploadsToOwnPublicWork() throws Exception {
        // Arrange
        String workId = createPublicWork(collab);

        // Act
        UUID chapterId = upload("/works", workId, "1", collab, cbz());

        // Assert
        assertThat(reload(chapterId).getStatus()).isEqualTo(ChapterStatus.PUBLISHED);
    }

    @Test
    void shouldReturn403_whenReaderUsesPublicChapterRoute() throws Exception {
        // Arrange
        String workId = createPublicWork(collab);

        // Act / Assert
        mockMvc.perform(post("/works/{id}/chapters/upload-url", workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"pt-BR\",\"number\":1}").with(auth(reader)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403_whenPublicRouteTargetsPrivateWork() throws Exception {
        // Arrange
        String workId = createPrivateWork(collab);

        // Act / Assert
        mockMvc.perform(post("/works/{id}/chapters/upload-url", workId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"pt-BR\",\"number\":1}").with(auth(collab)))
                .andExpect(status().isForbidden());
    }
}
