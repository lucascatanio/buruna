package com.buruna.shared.storage;

import com.buruna.shared.exception.StorageException;
import com.buruna.shared.exception.StorageObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;

public class LocalStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageClient.class);

    private final Path storagePath;
    private final String baseUrl;

    public LocalStorageClient(Path storagePath, String baseUrl) {
        this.storagePath = storagePath;
        this.baseUrl = baseUrl;
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new StorageException("Não foi possível criar o diretório de storage: " + storagePath, e);
        }
    }

    private Path resolve(String fileName) {
        String safe = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        Path file = storagePath.resolve(safe).normalize();
        if (!file.startsWith(storagePath)) {
            throw new StorageException("Path traversal detectado: " + fileName, null);
        }
        return file;
    }

    @Override
    public String upload(InputStream content, String fileName, String contentType, long contentLength) {
        Path target = resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Arquivo salvo localmente: {} ({} bytes)", target, contentLength);
        } catch (IOException e) {
            throw new StorageException("Falha ao salvar arquivo local: " + fileName, e);
        }
        return fileName;
    }

    @Override
    public void delete(String fileName) {
        Path target = resolve(fileName);
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (!deleted) {
                log.warn("Arquivo local não encontrado para deleção: {}", target);
            }
        } catch (IOException e) {
            log.warn("Falha ao deletar arquivo local {}: {}", target, e.getMessage());
        }
    }

    @Override
    public URL generateSignedUrl(String fileName, Duration expiration) {
        log.debug("generateSignedUrl ignorou expiração (local mode). Expiration solicitado: {}", expiration);
        try {
            return new URL(baseUrl + "/api/local-storage/files/" + fileName);
        } catch (IOException e) {
            throw new StorageException("Falha ao gerar URL local para: " + fileName, e);
        }
    }

    @Override
    public SignedUpload generateUploadSignedUrl(String objectName, String contentType, Duration expiration) {
        log.debug("generateUploadSignedUrl ignorou expiração (local mode). Expiration solicitado: {}", expiration);
        try {
            // modo local não assina headers: não há GCS para impor x-goog-content-length-range.
            return new SignedUpload(
                    new URL(baseUrl + "/api/local-storage/upload/" + objectName), Map.of("Content-Type", contentType));
        } catch (IOException e) {
            throw new StorageException("Falha ao gerar upload URL local para: " + objectName, e);
        }
    }

    @Override
    public void move(String from, String to) {
        Path source = resolve(from);
        Path target = resolve(to);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException e) {
            throw new StorageObjectNotFoundException("Arquivo local não encontrado: " + from, e);
        } catch (IOException e) {
            throw new StorageException("Falha ao mover arquivo local de " + from + " para " + to, e);
        }
    }

    @Override
    public FileMetadata getFileMetadata(String objectName) {
        Path target = resolve(objectName);
        if (!Files.exists(target)) {
            throw new StorageObjectNotFoundException("Arquivo local não encontrado: " + objectName);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new DigestInputStream(Files.newInputStream(target), md)) {
                byte[] buf = new byte[8192];
                while (is.read(buf) != -1) {
                    // streaming — MD5 é atualizado pelo DigestInputStream
                }
            }
            return new FileMetadata(
                    HexFormat.of().formatHex(md.digest()),
                    Files.size(target)
            );
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Falha ao ler metadados do arquivo local: " + objectName, e);
        }
    }

    @Override
    public InputStream openRead(String objectName) {
        Path target = resolve(objectName);
        if (!Files.exists(target)) {
            throw new StorageObjectNotFoundException("Arquivo local não encontrado: " + objectName);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new StorageException("Falha ao abrir arquivo local: " + objectName, e);
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        try (Stream<Path> files = Files.walk(storagePath)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> storagePath.relativize(file).toString().replace('\\', '/'))
                    .filter(name -> name.startsWith(prefix))
                    .map(name -> new StoredObject(name, lastModified(name)))
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Falha ao listar arquivos locais com prefixo: " + prefix, e);
        }
    }

    private Instant lastModified(String name) {
        try {
            return Files.getLastModifiedTime(resolve(name)).toInstant();
        } catch (IOException e) {
            throw new StorageException("Falha ao ler data do arquivo local: " + name, e);
        }
    }
}
