package org.tdar.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.Test;
import org.springframework.test.annotation.Rollback;
import org.tdar.TestConstants;
import org.tdar.core.bean.Indexable;
import org.tdar.core.bean.resource.Document;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.file.FileAccessRestriction;
import org.tdar.search.bean.SearchParameters;
import org.tdar.search.exception.SearchException;
import org.tdar.search.exception.SearchIndexException;
import org.tdar.search.query.SearchResult;
import org.tdar.utils.PersistableUtils;

public class ResourceFIleAttachmentSearchITCase extends AbstractResourceSearchITCase {

    @Test
    @Rollback(true)
    public void testAttachedFileSearch()
            throws InstantiationException, IllegalAccessException, SearchException, SearchIndexException, IOException, ParseException {
        Document document = createAndSaveNewInformationResource(Document.class, getBasicUser(), _33_CU_314);
        addFileToResource(document, TestConstants.getFile(TestConstants.TEST_DOCUMENT_DIR, "test-file.rtf"));
        searchIndexService.index(document);
        SearchParameters params = new SearchParameters();
        params.getContents().add("fun'");
        SearchResult<Resource> result = doSearch("", null, params, null);
        Long id = document.getId();
        List<Long> ids = PersistableUtils.extractIds(result.getResults());
        logger.info("results:{}", result.getResults());
        assertTrue(ids.contains(id));
        params = new SearchParameters();
        params.getContents().add("have fun digging");
        result = doSearch("", null, params, null);
        logger.info("results:{}", result.getResults());
        ids = PersistableUtils.extractIds(result.getResults());
        assertTrue(ids.contains(id));

        params = new SearchParameters();
        result = doSearch("test-file", null, params, null);
        logger.info("results:{}", result.getResults());
        ids = PersistableUtils.extractIds(result.getResults());
        assertTrue(ids.contains(id));

    }

    @Test
    @Rollback(true)
    public void testConfidentialFileSearch()
            throws InstantiationException, IllegalAccessException, SearchException, SearchIndexException, IOException, ParseException {
        String resourceTitle = _33_CU_314;
        Document document = createAndSaveNewInformationResource(Document.class, getBasicUser(), resourceTitle);
        addFileToResource(document, TestConstants.getFile(TestConstants.TEST_DOCUMENT_DIR + "test-file.rtf"), FileAccessRestriction.CONFIDENTIAL);
        searchIndexService.index(document);
        SearchParameters params = new SearchParameters();
        params.getContents().add("fun");
        SearchResult<Resource> result = doSearch("", null, params, null);
        logger.info("results:{}", result.getResults());
        assertFalse(result.getResults().contains(document));
        params = new SearchParameters();
        params.getContents().add("have fun digging");
        result = doSearch("", null, params, null);
        logger.info("results:{}", result.getResults());
        assertFalse(result.getResults().contains(document));

    }

    @Test
    @Rollback(true)
    public void testFilenameFound() throws InstantiationException, IllegalAccessException, SearchException, SearchIndexException, IOException, ParseException {
        Document doc = createAndSaveDocumentWithFileAndUseDefaultUser();
        searchIndexService.index(doc);
        SearchParameters sp = new SearchParameters();
        sp.getFilenames().add(TestConstants.TEST_DOCUMENT_NAME);
        SearchResult<Resource> result = doSearch(null, null, sp, null);
        boolean seen = false;
        for (Indexable res : result.getResults()) {
            if (res.getId().equals(doc.getId())) {
                seen = true;
            }
        }
        assertTrue(seen);
    }

}
