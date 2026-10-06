package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.AnalysisRepository;
import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import dev.laveenh.jazzanalyzer.persistence.SongEntity;
import dev.laveenh.jazzanalyzer.persistence.SongRepository;
import dev.laveenh.jazzanalyzer.storage.FileStorage;
import dev.laveenh.jazzanalyzer.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Accepting uploads (with duplicate detection) and deleting songs. */
@Service
public class SongService {

    private static final Logger log = LoggerFactory.getLogger(SongService.class);

    private final SongRepository songs;
    private final AnalysisRepository analyses;
    private final FileStorage storage;
    private final AnalysisExecutor executor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SongService(SongRepository songs, AnalysisRepository analyses, FileStorage storage,
                       AnalysisExecutor executor, TransactionTemplate tx, Clock clock) {
        this.songs = songs;
        this.analyses = analyses;
        this.storage = storage;
        this.executor = executor;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Stores a validated upload and starts its analysis.
     *
     * <p>The file's SHA-256 checksum is the identity of a song: uploading the same bytes again returns
     * the existing analysis instead of doing the work twice. The one exception is an earlier analysis
     * that FAILED, which is retried (the failure may have been fixed since).
     */
    public SubmitResult submit(String displayName, byte[] content) {
        String checksum = sha256(content);

        Optional<SongEntity> existing = songs.findByChecksumSha256(checksum);
        if (existing.isPresent()) {
            return reuse(existing.get());
        }

        String storageKey = storage.store(content);
        try {
            SubmitResult created = tx.execute(status -> {
                SongEntity song = songs.save(new SongEntity(titleFrom(displayName), displayName, storageKey,
                        checksum, clock.instant()));
                AnalysisEntity analysis = analyses.save(AnalysisEntity.pending(song.getId(), clock.instant()));
                return new SubmitResult(song.getId(), analysis.getId(), AnalysisStatus.PENDING, false);
            });
            return run(created);
        } catch (DataIntegrityViolationException e) {
            // Two identical uploads raced and the other one won the UNIQUE checksum. Use its song.
            discard(storageKey);
            return reuse(songs.findByChecksumSha256(checksum).orElseThrow(() -> e));
        } catch (RuntimeException e) {
            discard(storageKey);
            throw e;
        }
    }

    /** Deletes the song, its analyses (the database cascades) and the stored file. */
    public void delete(UUID songId) {
        SongEntity song = songs.findById(songId).orElseThrow(() -> new NotFoundException("Song", songId));
        songs.delete(song);
        discard(song.getStorageKey());
    }

    private SubmitResult reuse(SongEntity song) {
        AnalysisEntity latest = analyses.findFirstBySongIdOrderByCreatedAtDesc(song.getId()).orElse(null);
        if (latest != null && latest.getStatus() != AnalysisStatus.FAILED) {
            return new SubmitResult(song.getId(), latest.getId(), latest.getStatus(), true);
        }
        AnalysisEntity retry = analyses.save(AnalysisEntity.pending(song.getId(), clock.instant()));
        return run(new SubmitResult(song.getId(), retry.getId(), AnalysisStatus.PENDING, latest != null));
    }

    /** Starts the analysis and reports the status it has reached by the time this returns. */
    private SubmitResult run(SubmitResult submitted) {
        executor.submit(submitted.analysisId());
        AnalysisStatus status = analyses.findById(submitted.analysisId()).map(AnalysisEntity::getStatus)
                .orElse(submitted.status());
        return new SubmitResult(submitted.songId(), submitted.analysisId(), status, submitted.duplicate());
    }

    /** Best-effort cleanup of a stored file: a leftover file must never turn a success into an error. */
    private void discard(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (StorageException e) {
            log.warn("Could not delete stored file {}", storageKey, e);
        }
    }

    private static String titleFrom(String displayName) {
        int dot = displayName.lastIndexOf('.');
        return dot > 0 ? displayName.substring(0, dot) : displayName;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
