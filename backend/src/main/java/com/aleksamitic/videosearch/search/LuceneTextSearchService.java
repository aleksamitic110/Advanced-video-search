package com.aleksamitic.videosearch.search;

import com.aleksamitic.videosearch.config.RuntimeConfigService;
import com.aleksamitic.videosearch.util.YouTubeUrls;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LuceneTextSearchService {
    private final RuntimeConfigService runtimeConfigService;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    public LuceneTextSearchService(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    public void replaceVideoDocuments(String videoId, List<TextDocument> documents) {
        try {
            Files.createDirectories(indexPath());
            try (FSDirectory directory = directory();
                 IndexWriter writer = new IndexWriter(directory, writerConfig())) {
                writer.deleteDocuments(new Term("video_id", videoId));
                for (TextDocument textDocument : documents) {
                    writer.addDocument(toLuceneDocument(textDocument));
                }
                writer.commit();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Lucene indexing failed.", exception);
        }
    }

    public void deleteVideo(String videoId) {
        try {
            Files.createDirectories(indexPath());
            try (FSDirectory directory = directory();
                 IndexWriter writer = new IndexWriter(directory, writerConfig())) {
                writer.deleteDocuments(new Term("video_id", videoId));
                writer.commit();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Lucene delete failed.", exception);
        }
    }

    public List<SearchResult> search(String queryText, List<String> requestedFields, int limit) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        Path path = indexPath();
        if (!Files.exists(path)) {
            return List.of();
        }

        try (FSDirectory directory = directory()) {
            if (!DirectoryReader.indexExists(directory)) {
                return List.of();
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query contentQuery = new QueryParser("content", analyzer).parse(QueryParser.escape(queryText.trim()));
            Query sourceFilter = sourceFilter(requestedFields);
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(contentQuery, BooleanClause.Occur.MUST);
            if (sourceFilter != null) {
                builder.add(sourceFilter, BooleanClause.Occur.FILTER);
            }

            TopDocs hits = searcher.search(builder.build(), Math.max(1, limit));
            List<String> matchedTerms = tokenizeQuery(queryText);
            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : hits.scoreDocs) {
                Document document = searcher.doc(scoreDoc.doc);
                Integer timestamp = parseNullableInteger(document.get("timestamp_seconds"));
                String videoId = document.get("video_id");
                String content = document.get("content");
                results.add(new SearchResult(
                        videoId,
                        document.get("title"),
                        document.get("source_type"),
                        document.get("source_id"),
                        timestamp,
                        snippet(content, matchedTerms),
                        content,
                        scoreDoc.score,
                        YouTubeUrls.watchUrl(videoId, timestamp),
                        document.get("thumbnail_url"),
                        matchedTerms
                ));
            }
            return results;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Lucene search failed.", exception);
        }
    }

    private Document toLuceneDocument(TextDocument textDocument) {
        Document document = new Document();
        document.add(new StringField("video_id", textDocument.videoId(), Field.Store.YES));
        document.add(new StringField("source_type", textDocument.sourceType(), Field.Store.YES));
        document.add(new StringField("source_id", textDocument.sourceId(), Field.Store.YES));
        document.add(new StoredField("timestamp_seconds", textDocument.timestampSeconds() == null ? "" : textDocument.timestampSeconds().toString()));
        document.add(new TextField("title", textDocument.title(), Field.Store.YES));
        document.add(new TextField("content", textDocument.content(), Field.Store.YES));
        document.add(new StoredField("thumbnail_url", textDocument.thumbnailUrl()));
        return document;
    }

    private Query sourceFilter(List<String> requestedFields) {
        Set<String> sourceTypes = new LinkedHashSet<>();
        List<String> fields = requestedFields == null || requestedFields.isEmpty()
                ? List.of("title", "description", "comments", "transcript")
                : requestedFields;
        for (String field : fields) {
            switch (field) {
                case "title" -> sourceTypes.add("metadata");
                case "description" -> sourceTypes.add("description");
                case "comments", "comment" -> sourceTypes.add("comment");
                case "transcript" -> sourceTypes.add("transcript");
                default -> {
                }
            }
        }
        if (sourceTypes.isEmpty()) {
            return null;
        }
        return new TermInSetQuery(
                "source_type",
                sourceTypes.stream().map(BytesRef::new).toList()
        );
    }

    private List<String> tokenizeQuery(String queryText) {
        return List.of(queryText.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_-]+"))
                .stream()
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
    }

    private String snippet(String content, List<String> matchedTerms) {
        if (content == null || content.length() <= 420 || matchedTerms.isEmpty()) {
            return content == null ? "" : content;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int first = matchedTerms.stream()
                .mapToInt(lower::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(0);
        int start = Math.max(0, first - 140);
        int end = Math.min(content.length(), start + 420);
        return (start > 0 ? "... " : "") + content.substring(start, end) + (end < content.length() ? " ..." : "");
    }

    private Integer parseNullableInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private IndexWriterConfig writerConfig() {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return config;
    }

    private FSDirectory directory() throws IOException {
        return FSDirectory.open(indexPath());
    }

    private Path indexPath() {
        return Path.of(runtimeConfigService.dataDir(), "lucene");
    }
}
