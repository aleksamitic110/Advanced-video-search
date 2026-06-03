package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.search.ImageSearchService;
import com.aleksamitic.videosearch.search.LuceneTextSearchService;
import com.aleksamitic.videosearch.search.SearchResult;
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

    public SearchController(LuceneTextSearchService luceneTextSearchService, ImageSearchService imageSearchService) {
        this.luceneTextSearchService = luceneTextSearchService;
        this.imageSearchService = imageSearchService;
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

    public record TextSearchRequest(
            String query,
            List<String> fields,
            Integer limit
    ) {
    }
}
