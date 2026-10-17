package com.ruoyi.officecare.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.officecare.config.OfficeCareStorageProperties;

/**
 * 本地文件系统文档存储。
 *
 * @author officecare
 */
@Service
@ConditionalOnProperty(prefix = "officecare.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService
{
    private final Path storageRoot;

    public LocalDocumentStorageService(OfficeCareStorageProperties properties)
    {
        this.storageRoot = Path.of(properties.getRoot()).toAbsolutePath().normalize();
    }

    @Override
    public StoredDocumentFile store(MultipartFile file, String objectKey) throws IOException
    {
        return writeAtomically(file.getInputStream(), objectKey, false);
    }

    @Override
    public StoredDocumentFile storeGenerated(byte[] content, String objectKey) throws IOException
    {
        return writeAtomically(new ByteArrayInputStream(content), objectKey, true);
    }

    private StoredDocumentFile writeAtomically(InputStream source, String objectKey,
                                               boolean replaceExisting) throws IOException
    {
        Path target = resolveObjectKey(objectKey);
        Files.createDirectories(target.getParent());
        Path staged = target.resolveSibling(target.getFileName()
            + ".tmp-" + UUID.randomUUID().toString().replace("-", ""));
        MessageDigest digest = sha256Digest();
        try
        {
            try (InputStream input = new BufferedInputStream(
                     new DigestInputStream(source, digest));
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(
                     staged, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))
            {
                input.transferTo(output);
            }
            moveStagedFile(staged, target, replaceExisting);
            return new StoredDocumentFile("local", objectKey, Files.size(target),
                HexFormat.of().formatHex(digest.digest()));
        }
        catch (IOException | RuntimeException e)
        {
            Files.deleteIfExists(staged);
            throw e;
        }
    }

    private void moveStagedFile(Path staged, Path target, boolean replaceExisting)
        throws IOException
    {
        StandardCopyOption[] options = replaceExisting
            ? new StandardCopyOption[] {
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            }
            : new StandardCopyOption[] { StandardCopyOption.ATOMIC_MOVE };
        try
        {
            Files.move(staged, target, options);
        }
        catch (AtomicMoveNotSupportedException ignored)
        {
            if (replaceExisting)
            {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
            else
            {
                Files.move(staged, target);
            }
        }
    }

    @Override
    public InputStream open(String objectKey) throws IOException
    {
        Path target = resolveObjectKey(objectKey);
        if (!Files.isRegularFile(target))
        {
            throw new IOException("文档文件不存在");
        }
        return new BufferedInputStream(Files.newInputStream(target));
    }

    @Override
    public void delete(String objectKey) throws IOException
    {
        Files.deleteIfExists(resolveObjectKey(objectKey));
    }

    private Path resolveObjectKey(String objectKey) throws IOException
    {
        Path target = storageRoot.resolve(objectKey).normalize();
        if (!target.startsWith(storageRoot))
        {
            throw new IOException("非法的文档存储路径");
        }
        return target;
    }

    private MessageDigest sha256Digest()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("当前Java运行环境不支持SHA-256", e);
        }
    }
}
