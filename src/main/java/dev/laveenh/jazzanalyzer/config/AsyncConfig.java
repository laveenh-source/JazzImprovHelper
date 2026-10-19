package dev.laveenh.jazzanalyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** The bounded worker pool that runs analyses in the background. */
@Configuration
public class AsyncConfig {

    @Bean
    ThreadPoolTaskExecutor analysisTaskExecutor(JazzProperties properties) {
        JazzProperties.Analysis settings = properties.analysis();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analysis-");
        executor.setCorePoolSize(settings.threads());
        executor.setMaxPoolSize(settings.threads());
        executor.setQueueCapacity(settings.queueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // let running analyses finish when the app is asked to stop
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
