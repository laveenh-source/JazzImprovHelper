package dev.laveenh.jazzanalyzer.config;

import dev.laveenh.jazzanalyzer.analysis.ChartAnalyzer;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the Spring-free analysis engine into the application. The engine classes are stateless and
 * thread-safe (the scale rules are loaded once and never change), so one shared instance is enough.
 */
@Configuration
@EnableConfigurationProperties(JazzProperties.class)
public class AppConfig {

    /** One clock for the whole app so tests can pin the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MusicXmlParser musicXmlParser() {
        return new MusicXmlParser();
    }

    @Bean
    ChartAnalyzer chartAnalyzer() {
        return new ChartAnalyzer();
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Jazz Chart Analyzer API")
                .version("v1")
                .description("Upload a MusicXML jazz lead sheet and get back its key regions, "
                        + "the Roman-numeral function of every chord and ranked improvisation scales."));
    }
}
