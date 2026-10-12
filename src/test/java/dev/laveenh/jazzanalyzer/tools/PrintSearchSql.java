package dev.laveenh.jazzanalyzer.tools;

import dev.laveenh.jazzanalyzer.analysis.ProgressionPattern;
import dev.laveenh.jazzanalyzer.persistence.ProgressionSearchRepository;
import dev.laveenh.jazzanalyzer.persistence.SongQueryRepository;
import dev.laveenh.jazzanalyzer.persistence.StatsRepository;

/**
 * Developer tool for the benchmark script: prints the exact SQL the application runs, so it can be timed with
 * EXPLAIN ANALYZE in psql without copying queries around (and letting the copies drift).
 *
 * <pre>
 * java -cp target/classes:target/test-classes dev.laveenh.jazzanalyzer.tools.PrintSearchSql ii-V-I
 * java -cp ... dev.laveenh.jazzanalyzer.tools.PrintSearchSql stats-qualities | stats-transitions | song-list
 * </pre>
 */
public final class PrintSearchSql {

    private PrintSearchSql() {
    }

    public static void main(String[] args) {
        String what = args.length > 0 ? args[0] : "ii-V-I";
        switch (what) {
            case "stats-qualities" -> System.out.print(StatsRepository.QUALITIES_SQL);
            case "stats-transitions" -> System.out.print(StatsRepository.TRANSITIONS_SQL);
            case "song-list" -> System.out.print(SongQueryRepository.pageSql(SongQueryRepository.sortColumn("uploadedAt"), false));
            default -> {
                ProgressionPattern pattern = ProgressionPattern.parse(what).orElseThrow(() ->
                        new IllegalArgumentException("Unknown query. Patterns: " + ProgressionPattern.supportedNames()));
                System.out.print(ProgressionSearchRepository.buildSql(pattern));
            }
        }
    }
}
