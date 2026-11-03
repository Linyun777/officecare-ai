package com.ruoyi.officecare.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.ruoyi.officecare.domain.OcDocumentVersion;

/** Content-addressed parsed results keep earlier results immutable. */
public final class DocumentArtifactKeys
{
    private DocumentArtifactKeys()
    {
    }

    public static String parsedResult(OcDocumentVersion version, byte[] content)
    {
        String objectKey = version.getObjectKey();
        int original = objectKey.lastIndexOf("/original/");
        String base = original > 0 ? objectKey.substring(0, original)
            : "documents/" + version.getDocumentId() + "/versions/" + version.getVersionNo();
        return base + "/parsed/parse-" + digest(content) + ".json";
    }

    public static String digest(byte[] content)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
