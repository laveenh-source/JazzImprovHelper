package dev.laveenh.jazzanalyzer.service;

import java.util.UUID;

/**
 * Decides <em>how</em> a pending analysis gets run. Today it runs inline during the request; Phase 4
 * swaps in a version that hands the work to a background thread pool. Nothing else changes.
 */
public interface AnalysisExecutor {

    void submit(UUID analysisId);
}
