package org.istanbus.core.service.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.istanbus.core.model.SearchResult;
import org.istanbus.core.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);
    private Map<String, IndexSearcher> searchers;
    private SearchIndexServiceImpl searchIndexService;
    private String indexRoot;

    @Inject
    public SearchServiceImpl(@Named("search.index.root.path") String indexRoot) {
        this.indexRoot = indexRoot;

        IndexSearcher stopSearcher = initSearcher("stop");
        IndexSearcher busSearcher = initSearcher("bus");

        searchers = new HashMap<String, IndexSearcher>();
        searchers.put("stop", stopSearcher);
        searchers.put("bus", busSearcher);
    }

    private IndexSearcher initSearcher(String index) {
        File indexFolder = new File(indexRoot + index);
        if (!indexFolder.exists()) {
            indexFolder.mkdirs();
        }

        FSDirectory directory = null;
        try {
            directory = FSDirectory.open(indexFolder);
        } catch (IOException e) {
            logger.error("exception while opening index folder", e);
        }
        IndexReader indexReader = null;
        try {
            indexReader = IndexReader.open(directory);
        }
        catch (IndexNotFoundException e) {
            logger.error("index not found", e);
        }
        catch (IOException e) {
            logger.error("error while opening index reader", e);
        }
        return new IndexSearcher(indexReader);
    }

    @Override
    public List<SearchResult> search(String index, String keyword) {
        QueryParser queryParser = new QueryParser(Version.LUCENE_36, "text", new StandardAnalyzer(Version.LUCENE_36));
        Query query = null;
        try {
            query = queryParser.parse(keyword + "*");
        } catch (ParseException e) {
            logger.error("error while parsing query", e);
        }
        Sort sort = new Sort(new SortField("busCount", SortField.INT, true));
        IndexSearcher searcher = searchers.get(index);
        if (searcher == null)
        {
            return Collections.emptyList();
        }
        TopDocs hits = null;
        logger.info("searching {} for keyword {}", index, keyword);
        try {
            hits = searcher.search(query, 5, sort);
        } catch (IOException e) {
            logger.error("error while searching", e);
        }

        ScoreDoc[] scoreDocs = hits.scoreDocs;
        List<SearchResult> results = getResultsFromDocs(searcher, scoreDocs);

        return results;
    }

    private List<SearchResult> getResultsFromDocs(IndexSearcher searcher, ScoreDoc[] docs) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        for (ScoreDoc doc : docs) {
            SearchResult result = getResultFromDoc(searcher, doc);
            results.add(result);
        }
        return results;
    }

    private SearchResult getResultFromDoc(IndexSearcher searcher, ScoreDoc scoreDoc) {
        Document doc = null;
        try {
            doc = searcher.doc(scoreDoc.doc);
        } catch (IOException e) {
            logger.error("error while getting doc from stopSearcher", e);
        }

        SearchResult result = null;
        if (doc != null) {
            String id = doc.get("id");
            String name = doc.get("name");

            result = new SearchResult(id, name);
        }

        return result;
    }
}