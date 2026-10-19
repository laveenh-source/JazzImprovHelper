package dev.laveenh.jazzanalyzer.api;

import dev.laveenh.jazzanalyzer.api.dto.AnalysisResponse;
import dev.laveenh.jazzanalyzer.service.AnalysisQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analyses")
@Tag(name = "Analyses", description = "Poll the result of an upload")
public class AnalysisController {

    private final AnalysisQueryService queries;

    public AnalysisController(AnalysisQueryService queries) {
        this.queries = queries;
    }

    @Operation(summary = "Get an analysis",
            description = "Status is PENDING, RUNNING, COMPLETE or FAILED. When COMPLETE the response includes the "
                    + "key regions and every chord with its Roman numeral, function and scale suggestions; when "
                    + "FAILED it includes errorMessage.")
    @GetMapping("/{id}")
    public AnalysisResponse get(@PathVariable UUID id) {
        return AnalysisResponse.from(queries.getAnalysis(id));
    }
}
