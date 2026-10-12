package dev.laveenh.jazzanalyzer.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Runs the analysis inside the request thread. Selected with jazz.analysis.mode=sync (used by most tests). */
@Component
@ConditionalOnProperty(name = "jazz.analysis.mode", havingValue = "sync")
public class SynchronousAnalysisExecutor implements AnalysisExecutor {

    private final AnalysisProcessor processor;

    public SynchronousAnalysisExecutor(AnalysisProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void submit(UUID analysisId) {
        processor.process(analysisId);
    }
}
