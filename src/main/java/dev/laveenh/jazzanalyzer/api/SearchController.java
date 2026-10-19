package dev.laveenh.jazzanalyzer.api;

import dev.laveenh.jazzanalyzer.api.dto.PageResponse;
import dev.laveenh.jazzanalyzer.api.dto.ProgressionMatchDto;
import dev.laveenh.jazzanalyzer.api.dto.StatsResponse;
import dev.laveenh.jazzanalyzer.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Search", description = "Find progressions across the library and see library statistics")
public class SearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @Operation(summary = "Find songs containing a chord progression",
            description = "pattern is one of ii-V-I, ii-V-i, ii-V, V-I, I-vi-ii-V, iii-vi-ii-V, ii-bII7-I "
                    + "(case matters). key is optional: a note such as Bb or 'G minor'; leave it out to search "
                    + "every key. Each match says which key it was found in and in which measures.")
    @GetMapping("/search/progressions")
    public PageResponse<ProgressionMatchDto> progressions(
            @RequestParam String pattern,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkPaging(page, size);
        try {
            var result = search.findProgression(pattern, key, size, (long) page * size);
            return PageResponse.of(result.matches(), result.total(), page, size, ProgressionMatchDto::from);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    @Operation(summary = "Library statistics",
            description = "The most common chord qualities (with their share) and the most common chord-to-chord "
                    + "moves by Roman numeral across all analysed songs.")
    @GetMapping("/stats")
    public StatsResponse stats(@RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return new StatsResponse(search.chordQualities(limit), search.transitions(limit));
    }

    private static void checkPaging(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }
}
