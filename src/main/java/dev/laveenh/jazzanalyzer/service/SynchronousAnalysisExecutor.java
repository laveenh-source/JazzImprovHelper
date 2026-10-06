package dev.laveenh.jazzanalyzer.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
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
