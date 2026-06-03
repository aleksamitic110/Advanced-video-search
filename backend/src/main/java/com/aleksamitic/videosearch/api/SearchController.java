package com.aleksamitic.videosearch.api;

import com.aleksamitic.videosearch.search.LuceneTextSearchService;
import com.aleksamitic.videosearch.search.SearchResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {
    private final LuceneTextSearchService luceneTextSearchService;

    public SearchController(LuceneTextSearchService luceneTextSearchService) {
        this.luceneTextSearchService = luceneTextSearchService;
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

    public record TextSearchRequest(
            String query,
            List<String> fields,
            Integer limit
    ) {
    }
}
