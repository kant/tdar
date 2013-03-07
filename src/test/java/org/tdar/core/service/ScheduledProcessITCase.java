package org.tdar.core.service;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.tdar.core.bean.AbstractIntegrationTestCase;
import org.tdar.core.bean.resource.CodingSheet;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.Document;
import org.tdar.core.bean.resource.Image;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.Ontology;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.SensoryData;
import org.tdar.core.bean.statistics.AggregateStatistic;
import org.tdar.core.bean.statistics.AggregateStatistic.StatisticType;
import org.tdar.core.bean.util.ScheduledBatchProcess;
import org.tdar.core.service.processes.SitemapGeneratorProcess;

import static org.junit.Assert.*;

/**
 * $Id$
 * 
 * @author Adam Brin
 * @version $Revision$
 */
public class ScheduledProcessITCase extends AbstractIntegrationTestCase {

    @Autowired
    private ScheduledProcessService scheduledProcessService;
    private static final int MOCK_NUMBER_OF_IDS = 2000;

    private class MockScheduledProcess extends ScheduledBatchProcess<Dataset> {

        private static final long serialVersionUID = 1L;

        @Override
        public String getDisplayName() {
            return "Mock scheduled dataset process";
        }

        @Override
        public Class<Dataset> getPersistentClass() {
            return Dataset.class;
        }

        @Override
        public List<Long> findAllIds() {
            return new LinkedList<Long>(Collections.nCopies(MOCK_NUMBER_OF_IDS, Long.valueOf(37)));
        }

        @Override
        public void processBatch(List<Long> batch) {
            // FIXME: this is dependent on TdarConfiguration's batch size being an even multiple of MOCK_NUMBER_OF_IDS
            assertEquals(batch.size(), getTdarConfiguration().getScheduledProcessBatchSize());
        }

        @Override
        public void process(Dataset persistable) {
            fail("this should not be invoked");
        }
    }

    @Test
    public void testCleanup() throws Exception {
        MockScheduledProcess mock = new MockScheduledProcess();
        do {
            mock.execute();
        } while (!mock.isCompleted());
        assertNotNull(mock.getLastId());
        assertTrue(mock.getNextBatch().isEmpty());
        assertTrue(mock.getBatchIdQueue().isEmpty());
        mock.cleanup();
        assertFalse("ScheduledBatchProcess should be reset now", mock.isCompleted());
    }

    @Autowired
    private SitemapGeneratorProcess sitemap;

    @Test
    public void testSitemapGen() {
        sitemap.execute();
    }

    @Test
    public void testBatchProcessing() {
        MockScheduledProcess mock = new MockScheduledProcess();
        List<Long> batch = mock.getNextBatch();
        assertEquals(MOCK_NUMBER_OF_IDS, mock.getBatchIdQueue().size() + batch.size());
        int numberOfRuns = MOCK_NUMBER_OF_IDS / getTdarConfiguration().getScheduledProcessBatchSize();
        assertNotSame(numberOfRuns, 0);
        while (CollectionUtils.isNotEmpty(mock.getBatchIdQueue())) {
            int initialSize = mock.getBatchIdQueue().size();
            batch = mock.getNextBatch();
            assertEquals(initialSize, mock.getBatchIdQueue().size() + batch.size());
            numberOfRuns--;
            if (numberOfRuns < 0) {
                fail("MockScheduledProcess should have been batched " + numberOfRuns + " times but didn't.");
            }
        }
        assertEquals("id queue should be empty", 0, mock.getBatchIdQueue().size());
        assertSame("number of runs should be 1 now", 1, numberOfRuns);

    }

    @SuppressWarnings("deprecation")
    @Test
    @Rollback(true)
    public void testStats() throws InstantiationException, IllegalAccessException {
        Number docs = resourceService.countActiveResources(ResourceType.DOCUMENT);
        Number datasets = resourceService.countActiveResources(ResourceType.DATASET);
        Number images = resourceService.countActiveResources(ResourceType.IMAGE);
        Number sheets = resourceService.countActiveResources(ResourceType.CODING_SHEET);
        Number ontologies = resourceService.countActiveResources(ResourceType.ONTOLOGY);
        Number sensory = resourceService.countActiveResources(ResourceType.SENSORY_DATA);
        Number people = entityService.findAllRegisteredUsers(null).size();
        createAndSaveNewInformationResource(Document.class, false);
        createAndSaveNewInformationResource(Dataset.class, false);
        createAndSaveNewInformationResource(Image.class, false);
        createAndSaveNewInformationResource(CodingSheet.class, false);
        createAndSaveNewInformationResource(Ontology.class, false);
        createAndSaveNewInformationResource(SensoryData.class, true);
        InformationResource generateInformationResourceWithFile = generateInformationResourceWithFileAndUser();
        scheduledProcessService.generateWeeklyStats();
        flush();
        List<AggregateStatistic> allStats = genericService.findAll(AggregateStatistic.class);
        Map<AggregateStatistic.StatisticType, AggregateStatistic> map = new HashMap<AggregateStatistic.StatisticType, AggregateStatistic>();
        for (AggregateStatistic stat : allStats) {
            logger.info(stat.getRecordedDate() + " " + stat.getValue() + " " + stat.getStatisticType());
            map.put(stat.getStatisticType(), stat);
        }
        Date current = new Date();

        Date date = map.get(StatisticType.NUM_CODING_SHEET).getRecordedDate();
        Calendar cal = new GregorianCalendar(current.getYear(), current.getMonth(), current.getDay());
        Calendar statDate = new GregorianCalendar(date.getYear(), date.getMonth(), date.getDay());
        assertEquals(cal, statDate);
        // assertEquals(11L, map.get(StatisticType.NUM_PROJECT).getValue().longValue());
        assertEquals(datasets.longValue() + 1, map.get(StatisticType.NUM_DATASET).getValue().longValue());
        assertEquals(docs.longValue() + 2, map.get(StatisticType.NUM_DOCUMENT).getValue().longValue());
        assertEquals(images.longValue() + 1, map.get(StatisticType.NUM_IMAGE).getValue().longValue());
        assertEquals(sheets.longValue() + 1, map.get(StatisticType.NUM_CODING_SHEET).getValue().longValue());
        assertEquals(sensory.longValue() + 1, map.get(StatisticType.NUM_SENSORY_DATA).getValue().longValue());
        assertEquals(ontologies.longValue() + 1, map.get(StatisticType.NUM_ONTOLOGY).getValue().longValue());
        assertEquals(people.longValue() + 1, map.get(StatisticType.NUM_USERS).getValue().longValue());
        assertFalse(map.get(StatisticType.REPOSITORY_SIZE).getValue().longValue() == 0);
    }
}
