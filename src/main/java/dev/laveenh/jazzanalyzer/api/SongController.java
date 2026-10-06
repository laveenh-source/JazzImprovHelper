package dev.laveenh.jazzanalyzer.api;

import dev.laveenh.jazzanalyzer.api.dto.AnalysisResponse;
import dev.laveenh.jazzanalyzer.api.dto.PageResponse;
import dev.laveenh.jazzanalyzer.api.dto.SongChordsResponse;
import dev.laveenh.jazzanalyzer.api.dto.SongDetailDto;
import dev.laveenh.jazzanalyzer.api.dto.SongSummaryDto;
import dev.laveenh.jazzanalyzer.api.dto.UploadResponse;
import dev.laveenh.jazzanalyzer.persistence.SongQueryRepository;
import dev.laveenh.jazzanalyzer.service.AnalysisQueryService;
import dev.laveenh.jazzanalyzer.service.SongService;
import dev.laveenh.jazzanalyzer.service.SubmitResult;
import dev.laveenh.jazzanalyzer.service.UploadValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/songs")
@Tag(name = "Songs", description = "Upload lead sheets and read their analyses")
public class SongController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UploadValidator validator;
    private final SongService songService;
    private final AnalysisQueryService queries;

    public SongController(UploadValidator validator, SongService songService, AnalysisQueryService queries) {
        this.validator = validator;
        this.songService = songService;
        this.queries = queries;
    }

    @Operation(summary = "Upload a MusicXML lead sheet",
            description = "Accepts a .xml or .musicxml file of up to 2 MB. Returns 202 Accepted with the analysis id "
                    + "to poll at GET /api/v1/analyses/{id}. Uploading the same file again returns 200 OK with the "
                    + "existing analysis instead of repeating the work.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestPart("file") MultipartFile file) {
        byte[] content = validator.validate(file);
        SubmitResult result = songService.submit(validator.displayName(file.getOriginalFilename()), content);

        String analysisUrl = "/api/v1/analyses/" + result.analysisId();
        UploadResponse body = new UploadResponse(result.songId(), result.analysisId(), result.status(),
                result.duplicate(), analysisUrl);
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .location(URI.create(analysisUrl))
                .body(body);
    }

    @Operation(summary = "List songs",
            description = "Paginated. sort is 'field' or 'field,asc|desc' with field one of title, uploadedAt, "
                    + "detectedKey. key filters on the detected main key: 'Bb major' matches exactly, 'Bb' matches "
                    + "either mode.")
    @GetMapping
    public PageResponse<SongSummaryDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "uploadedAt,desc") String sort,
            @RequestParam(required = false) String key) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        String[] sortParts = sort.split(",", 2);
        String field = sortParts[0].trim();
        if (!SongQueryRepository.isSortable(field)) {
            throw new InvalidRequestException("Cannot sort by '" + field + "'. Use one of "
                    + SongQueryRepository.sortableFields().stream().sorted().toList() + ".");
        }
        boolean ascending = sortParts.length > 1 && sortParts[1].trim().equalsIgnoreCase("asc");
        if (sortParts.length > 1 && !ascending && !sortParts[1].trim().equalsIgnoreCase("desc")) {
            throw new InvalidRequestException("The sort direction must be 'asc' or 'desc'.");
        }
        var result = queries.listSongs(key, field, ascending, page, size);
        return PageResponse.of(result.rows(), result.total(), page, size, SongSummaryDto::from);
    }

    @Operation(summary = "Get a song with a summary of its latest analysis")
    @GetMapping("/{id}")
    public SongDetailDto get(@PathVariable UUID id) {
        return SongDetailDto.from(queries.getLatestForSong(id));
    }

    @Operation(summary = "Per-chord detail of the latest completed analysis",
            description = "Measure, symbol, quality, Roman numeral, function, local key and scale suggestions. "
                    + "Returns 409 if the latest analysis is not complete.")
    @GetMapping("/{id}/chords")
    public SongChordsResponse chords(@PathVariable UUID id) {
        return SongChordsResponse.from(queries.getCompleteForSong(id));
    }

    @Operation(summary = "Delete a song, its analyses and the stored file")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        songService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
