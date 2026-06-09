package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.search.ImageSearchService;
import com.aleksamitic.videosearch.search.ImageSearchResult;
import com.aleksamitic.videosearch.search.LuceneTextSearchService;
import com.aleksamitic.videosearch.search.SearchResult;
import com.aleksamitic.videosearch.search.VisualSemanticSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {
    private final LuceneTextSearchService luceneTextSearchService;
    private final ImageSearchService imageSearchService;
    private final VisualSemanticSearchService visualSemanticSearchService;

    public SearchController(
            LuceneTextSearchService luceneTextSearchService,
            ImageSearchService imageSearchService,
            VisualSemanticSearchService visualSemanticSearchService
    ) {
        this.luceneTextSearchService = luceneTextSearchService;
        this.imageSearchService = imageSearchService;
        this.visualSemanticSearchService = visualSemanticSearchService;
    }

    @PostMapping("/api/search/text")
    public Map<String, Object> textSearch(@RequestBody TextSearchRequest request) {
        List<SearchResult> results = luceneTextSearchService.search(
                request.query(),
                request.fields(),
                request.limit() == null ? 10 : request.limit()
        );
        return Map.of("results", results);
    }

    @PostMapping("/api/search/image")
    public Map<String, Object> imageSearch(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) throws Exception {
        return Map.of("results", imageSearchService.search(image.getBytes(), image.getOriginalFilename(), limit));
    }

    @PostMapping("/api/search/visual-semantic")
    public Map<String, Object> visualSemanticSearch(@RequestBody VisualSemanticSearchRequest request) {
        List<ImageSearchResult> results = visualSemanticSearchService.search(
                request.query(),
                request.limit() == null ? 10 : request.limit()
        );
        return Map.of("results", results);
    }

    public record TextSearchRequest(
            String query,
            List<String> fields,
            Integer limit
    ) {
    }

    public record VisualSemanticSearchRequest(
            String query,
            Integer limit
    ) {
    }
}
