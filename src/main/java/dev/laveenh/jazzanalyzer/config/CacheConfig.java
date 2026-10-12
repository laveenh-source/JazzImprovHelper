package dev.laveenh.jazzanalyzer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory cache for finished analyses. A COMPLETE analysis never changes, so repeated polls and views of
 * the same result can skip the database; the entry is dropped when its song is deleted.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COMPLETED_ANALYSES = "completedAnalyses";

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(COMPLETED_ANALYSES);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        return manager;
    }
}
