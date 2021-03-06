/**
 * $Id$
 * 
 * @author $Author$
 * @version $Revision$
 */
package org.tdar.struts.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.datatable.DataTable;
import org.tdar.core.service.resource.DatasetService;
import org.tdar.core.service.resource.dataset.ResultMetadataWrapper;
import org.tdar.struts.action.dataset.DatasetController;
import org.tdar.struts.action.datatable.DataTableBrowseController;

import com.opensymphony.xwork2.Action;

/**
 * @author Adam Brin
 * 
 */
public class DataSetBrowseITCase extends AbstractAdminControllerITCase {

    private static final int RESULTS_PER_PAGE = 2;

    private static final String SRC_TEST_EMPTY_ACCDB = "data_integration_tests/empty.accdb";
    private static final String DOUBLE_DATASET = "coding sheet/double_translation_test_dataset.xlsx";
    private static final String TEXT_DATASET = "coding sheet/csvCodingSheetText.csv";

    @Autowired
    private DatasetService datasetService;

    @Test
    @Rollback
    public void testBrowse() throws Exception {
        // load datasets
        Dataset dataset = setupAndLoadResource(DOUBLE_DATASET, Dataset.class);
        assertNotNull(dataset);
        DataTable dataTable = dataset.getDataTables().iterator().next();
        assertNotNull(dataTable);
        DataTableBrowseController controller = generateNewInitializedController(DataTableBrowseController.class);
        controller.setId(dataTable.getId());
        controller.setRecordsPerPage(RESULTS_PER_PAGE);
        assertEquals(Action.SUCCESS, controller.getDataResults());
        ResultMetadataWrapper resultsWrapper = controller.getResultsWrapper();
        // DEFAULT CASE -- START @ 0
        assertEquals(new Integer(RESULTS_PER_PAGE), resultsWrapper.getRecordsPerPage());
        assertEquals(new Integer(6), resultsWrapper.getTotalRecords());
        assertEquals(new Integer(0), resultsWrapper.getStartRecord());
        assertFalse(resultsWrapper.getResults().isEmpty());
        assertFalse(resultsWrapper.getFields().isEmpty());
        logger.debug("{}", controller.getResultObject());

        // PAGED CASE -- START @ 5
        controller = generateNewInitializedController(DataTableBrowseController.class);
        controller.setId(dataTable.getId());
        controller.setRecordsPerPage(RESULTS_PER_PAGE);
        controller.setStartRecord(5);
        assertEquals(Action.SUCCESS, controller.getDataResults());
        resultsWrapper = controller.getResultsWrapper();
        assertEquals(new Integer(RESULTS_PER_PAGE), resultsWrapper.getRecordsPerPage());
        assertEquals(new Integer(6), resultsWrapper.getTotalRecords());
        assertEquals(new Integer(5), resultsWrapper.getStartRecord());
        assertFalse(resultsWrapper.getResults().isEmpty());
        assertFalse(resultsWrapper.getFields().isEmpty());

        logger.debug("{}", controller.getResultObject());

        // OVER-EXTENDED CASE -- START @ 500
        controller = generateNewInitializedController(DataTableBrowseController.class);
        controller.setId(dataTable.getId());
        controller.setRecordsPerPage(RESULTS_PER_PAGE);
        controller.setStartRecord(500);
        assertEquals(Action.SUCCESS, controller.getDataResults());
        resultsWrapper = controller.getResultsWrapper();
        assertEquals(new Integer(RESULTS_PER_PAGE), resultsWrapper.getRecordsPerPage());
        assertEquals(new Integer(6), resultsWrapper.getTotalRecords());
        assertEquals(new Integer(500), resultsWrapper.getStartRecord());
        assertTrue(resultsWrapper.getResults().isEmpty());
        assertFalse(resultsWrapper.getFields().isEmpty());

        logger.debug("{}", controller.getResultObject());
    }

    @Test
    @Rollback
    public void testSearch() throws Exception {
        // load datasets
        Dataset dataset = setupAndLoadResource(TEXT_DATASET, Dataset.class);
        assertNotNull(dataset);
        DataTable dataTable = dataset.getDataTables().iterator().next();
        assertNotNull(dataTable);
        String term = "Bird";
        ResultMetadataWrapper selectFromDataTable = datasetService.findRowsFromDataTable(dataset, dataTable, 0, 1, true, term, null);
        assertNotEmpty("should have results", selectFromDataTable.getResults());
        for (List<String> result : selectFromDataTable.getResults()) {
            String row = StringUtils.join(result.toArray());
            assertTrue(row.contains(term));
        }

        term = "D";
        selectFromDataTable = datasetService.findRowsFromDataTable(dataset, dataTable, 0, 1, true, term, null);
        assertNotEmpty("should have results", selectFromDataTable.getResults());
        for (List<String> result : selectFromDataTable.getResults()) {
            String row = StringUtils.join(result.toArray());
            assertTrue(row.contains(term));
        }
    }

    @Test
    @Rollback
    // FIXME: am I supposed to be empty?
    public void testTranslate() throws Exception {
        // load datasets
        Dataset dataset = setupAndLoadResource(SRC_TEST_EMPTY_ACCDB, Dataset.class);
        DatasetController controller = generateNewInitializedController(DatasetController.class);
        controller.setId(dataset.getId());
        datasetService.createTranslatedFile(dataset);
        assertNotNull(dataset);
    }

}
