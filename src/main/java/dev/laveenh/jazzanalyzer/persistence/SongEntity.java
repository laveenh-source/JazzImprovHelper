package dev.laveenh.jazzanalyzer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "songs")
public class SongEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "checksum_sha256", nullable = false, unique = true, length = 64)
    private String checksumSha256;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected SongEntity() {
        // required by JPA
    }

    public SongEntity(String title, String originalFilename, String storageKey, String checksumSha256, Instant uploadedAt) {
        this.title = title;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.checksumSha256 = checksumSha256;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
