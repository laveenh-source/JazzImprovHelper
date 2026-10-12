package dev.laveenh.jazzanalyzer.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
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
        executor.setTaskDecorator(copyLoggingContext());
        // let running analyses finish when the app is asked to stop
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** Carries the request id from the uploading thread into the worker thread so log lines stay linked. */
    private static TaskDecorator copyLoggingContext() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
