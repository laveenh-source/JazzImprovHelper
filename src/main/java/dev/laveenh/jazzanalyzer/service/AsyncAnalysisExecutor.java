package dev.laveenh.jazzanalyzer.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hands the analysis to a bounded background pool, so an upload returns immediately with 202 and the
 * client polls for the result. The pool and its waiting queue are both bounded: when they are full we
 * refuse new work (503) instead of letting memory grow without limit.
 */
@Component
@ConditionalOnProperty(name = "jazz.analysis.mode", havingValue = "async", matchIfMissing = true)
public class AsyncAnalysisExecutor implements AnalysisExecutor {

    private final ThreadPoolTaskExecutor pool;
    private final AnalysisProcessor processor;

    public AsyncAnalysisExecutor(ThreadPoolTaskExecutor analysisTaskExecutor, AnalysisProcessor processor) {
        this.pool = analysisTaskExecutor;
        this.processor = processor;
    }

    @Override
    public void submit(UUID analysisId) {
        try {
            pool.execute(() -> processor.process(analysisId));
        } catch (TaskRejectedException e) {
            throw new AnalysisQueueFullException();
        }
    }
}
