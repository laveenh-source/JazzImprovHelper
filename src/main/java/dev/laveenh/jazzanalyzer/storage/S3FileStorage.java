package dev.laveenh.jazzanalyzer.storage;

import dev.laveenh.jazzanalyzer.config.JazzProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.util.UUID;

/**
 * Production storage: one S3 object per upload. Objects are encrypted at rest by S3 (SSE-S3, AES-256) and the
 * bucket itself is private; the application is the only reader. Active with the {@code s3} profile.
 */
@Component
@Profile("s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, JazzProperties properties) {
        this.s3 = s3;
        this.bucket = properties.storage().s3().bucket();
    }

    @Override
    public String store(byte[] content) {
        String key = UUID.randomUUID() + ".xml";
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/xml")
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageException("Cannot store file in S3", e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new StorageException("Stored file not found: " + key, e);
        } catch (SdkException e) {
            throw new StorageException("Cannot read stored file " + key + " from S3", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            // S3 deletes are idempotent: a missing key is not an error
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new StorageException("Cannot delete stored file " + key + " from S3", e);
        }
    }
}
