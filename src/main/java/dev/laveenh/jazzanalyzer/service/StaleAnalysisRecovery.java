package dev.laveenh.jazzanalyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Background analyses live in memory, so a crash or restart strands any that were queued or running as
 * PENDING/RUNNING forever. At startup, analyses stuck like that for longer than any real analysis could
 * take are marked FAILED with a clear message, and uploading the same file again retries them.
 */
@Component
public class StaleAnalysisRecovery {

    private static final Logger log = LoggerFactory.getLogger(StaleAnalysisRecovery.class);
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final JdbcClient jdbc;
    private final Clock clock;

    public StaleAnalysisRecovery(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public int recover() {
        int fixed = jdbc.sql("""
                        UPDATE analyses
                        SET status = 'FAILED',
                            error_message = 'The analysis was interrupted by a server restart. Upload the file again to retry.',
                            completed_at = :now
                        WHERE status IN ('PENDING', 'RUNNING')
                          AND created_at < :cutoff
                        """)
                .param("now", java.sql.Timestamp.from(clock.instant()))
                .param("cutoff", java.sql.Timestamp.from(clock.instant().minus(STALE_AFTER)))
                .update();
        if (fixed > 0) {
            log.warn("Marked {} stale analyses as FAILED after a restart", fixed);
        }
        return fixed;
    }
}
