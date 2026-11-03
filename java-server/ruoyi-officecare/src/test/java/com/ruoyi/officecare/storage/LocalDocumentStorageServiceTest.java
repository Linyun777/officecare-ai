package com.ruoyi.officecare.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.officecare.config.OfficeCareStorageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 解析结果本地存储测试。
 *
 * @author officecare
 */
class LocalDocumentStorageServiceTest
{
    Path storageRoot;

    @BeforeEach
    void setUp() throws IOException
    {
        storageRoot = Path.of("target", "test-storage",
            UUID.randomUUID().toString()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    @AfterEach
    void tearDown() throws IOException
    {
        if (!Files.exists(storageRoot))
        {
            return;
        }
        try (var paths = Files.walk(storageRoot))
        {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
            {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void shouldAtomicallyReplaceGeneratedResult() throws IOException
    {
        LocalDocumentStorageService service = createService();
        String objectKey = "documents/1/versions/1/parsed/parse-result.json";

        service.storeGenerated("first".getBytes(StandardCharsets.UTF_8), objectKey);
        service.storeGenerated("second".getBytes(StandardCharsets.UTF_8), objectKey);

        try (var input = service.open(objectKey))
        {
            assertEquals("second",
                new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var files = Files.list(storageRoot.resolve(
            "documents/1/versions/1/parsed")))
        {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                .contains(".tmp-")));
        }
    }

    @Test
    void shouldDeleteAllDerivedResultsWithoutTouchingOriginalOrOtherVersion() throws IOException
    {
        LocalDocumentStorageService service = createService();
        String base = "documents/1/versions/1/";
        service.storeGenerated(new byte[] {1}, base + "parsed/parse-result.json");
        service.storeGenerated(new byte[] {2}, base + "parsed/parse-new.json");
        service.storeGenerated(new byte[] {3}, base + "original/file.pdf");
        service.storeGenerated(new byte[] {4}, "documents/1/versions/2/parsed/parse.json");

        service.deleteParsedArtifacts(base + "parsed/parse-new.json");

        assertFalse(Files.exists(storageRoot.resolve(base + "parsed")));
        assertEquals(1, Files.size(storageRoot.resolve(base + "original/file.pdf")));
        assertEquals(1, Files.size(storageRoot.resolve("documents/1/versions/2/parsed/parse.json")));
        assertThrows(IOException.class, () -> service.deleteParsedArtifacts("../outside/parsed/file.json"));
        assertThrows(IOException.class, () -> service.deleteParsedArtifacts(base + "original/file.pdf"));
    }

    @Test
    void shouldRejectObjectKeyOutsideStorageRoot()
    {
        LocalDocumentStorageService service = createService();

        assertThrows(IOException.class, () -> service.storeGenerated(
            "content".getBytes(StandardCharsets.UTF_8), "../outside.json"));
    }

    private LocalDocumentStorageService createService()
    {
        OfficeCareStorageProperties properties = new OfficeCareStorageProperties();
        properties.setRoot(storageRoot.toString());
        return new LocalDocumentStorageService(properties);
    }
}
