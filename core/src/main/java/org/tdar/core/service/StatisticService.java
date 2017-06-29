package org.tdar.core.service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.Persistable;
import org.tdar.core.bean.billing.BillingAccount;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.file.VersionType;
import org.tdar.core.bean.statistics.AggregateDayViewStatistic;
import org.tdar.core.bean.statistics.AggregateDownloadStatistic;
import org.tdar.core.bean.statistics.AggregateStatistic;
import org.tdar.core.bean.statistics.AggregateStatistic.StatisticType;
import org.tdar.core.dao.AggregateStatisticsDao;
import org.tdar.core.dao.StatisticDao;
import org.tdar.core.dao.StatsResultObject;
import org.tdar.core.dao.resource.ResourceCollectionDao;
import org.tdar.core.dao.resource.stats.DateGranularity;
import org.tdar.utils.Pair;

import com.ibm.icu.util.GregorianCalendar;
import com.opensymphony.xwork2.TextProvider;

/**
 * Helper class for running statistics and working with @link AggregatedStatistic objects
 * 
 * @author abrin
 * 
 */
@Service
public class StatisticService extends ServiceInterface.TypedDaoBase<AggregateStatistic, StatisticDao> {

    @Autowired
    private ResourceCollectionDao resourceCollectionDao;

    @Autowired
    private AggregateStatisticsDao aggregateStatisticsDao;

    private final Date startDate = new GregorianCalendar(2008, 1, 1).getTime();

    /**
     * Get Total @link Resource Statistics (counts) Grouped by week
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Date, Map<StatisticType, Long>> getResourceStatistics() {
        List<StatisticType> types = Arrays.asList(StatisticType.NUM_CODING_SHEET, StatisticType.NUM_DATASET, StatisticType.NUM_DOCUMENT,
                StatisticType.NUM_IMAGE, StatisticType.NUM_ONTOLOGY, StatisticType.NUM_PROJECT, StatisticType.NUM_SENSORY_DATA, StatisticType.NUM_VIDEO,
                StatisticType.NUM_ARCHIVES, StatisticType.NUM_GIS);
        return getDao().getStatistics(startDate, new Date(), types.toArray(new StatisticType[0]));
    }

    /**
     * Get total @link ResourceCollection Statistics (counts) grouped by week
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Date, Map<StatisticType, Long>> getCollectionStatistics() {
        List<StatisticType> types = Arrays.asList(StatisticType.NUM_COLLECTIONS);
        return getDao().getStatistics(startDate, new Date(), types.toArray(new StatisticType[0]));
    }

    /**
     * get real-time @link Resource Statistics
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public Map<ResourceType, List<BigInteger>> getCurrentResourceStats() {
        return getDao().getCurrentResourceStats();
    }

    /**
     * Get current user statistics (grouped by week)
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Date, Map<StatisticType, Long>> getUserStatistics() {
        List<StatisticType> types = Arrays.asList(StatisticType.NUM_USERS, StatisticType.NUM_ACTUAL_CONTRIBUTORS);
        return getDao().getStatistics(startDate, new Date(), types.toArray(new StatisticType[0]));
    }

    /**
     * Get File Average Statistics (for pie chart) by extension
     * 
     * @param types
     * @return
     */
    @Transactional(readOnly = true)
    public Map<String, List<Number>> getFileAverageStats(List<VersionType> types) {
        return getDao().getFileAverageStats(types);
    }

    /**
     * Get the repository size grouped by week
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Date, Map<StatisticType, Long>> getRepositorySizes() {
        List<StatisticType> types = Arrays.asList(StatisticType.REPOSITORY_SIZE);
        return getDao().getStatistics(startDate, new Date(), types.toArray(new StatisticType[0]));
    }

    /**
     * Get current @link Resource Counts limited to those with files (grouped by week)
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Date, Map<StatisticType, Long>> getResourceStatisticsWithFiles() {
        List<StatisticType> types = Arrays.asList(StatisticType.NUM_CODING_SHEET_WITH_FILES, StatisticType.NUM_DATASET_WITH_FILES,
                StatisticType.NUM_DOCUMENT_WITH_FILES,
                StatisticType.NUM_IMAGE_WITH_FILES, StatisticType.NUM_ONTOLOGY_WITH_FILES, StatisticType.NUM_PROJECT,
                StatisticType.NUM_SENSORY_DATA_WITH_FILES, StatisticType.NUM_VIDEO_WITH_FILES,
                StatisticType.NUM_GIS_WITH_FILES, StatisticType.NUM_ARCHIVES_WITH_FILES);
        return getDao().getStatistics(startDate, new Date(), types.toArray(new StatisticType[0]));
    }

    /**
     * Get user Login stats (# of logins by # of users)
     * 
     * @return
     */
    @Transactional(readOnly = true)
    public List<Pair<Long, Long>> getUserLoginStats() {
        return getDao().getUserLoginStats();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getFileStats(List<VersionType> types) {
        return getDao().getFileStats(types);
    }

    @Transactional(readOnly=false)
    public void cleanupOldDailyStats(Date date) {
        aggregateStatisticsDao.cleanupOldDailyStats(date);
        
    }

    @Transactional
    public void generateAggregateDailyDownloadData(Date date) {
        aggregateStatisticsDao.generateAggregateDailyDownloadData(date);
    }

    @Transactional
    public Number countWeeklyEmails() {
        return getDao().countWeeklyEmails();
    }

    @Transactional(readOnly = true)
    public StatsResultObject getStatsForCollection(ResourceCollection collection, TextProvider provider, DateGranularity granularity) {
        if (collection != null) {
            return getStats(collection, provider, granularity);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public StatsResultObject getStatsForAccount(BillingAccount account, TextProvider provider, DateGranularity granularity) {
        if (account != null && CollectionUtils.isNotEmpty(account.getResources())) {
            return getStats(account, provider, granularity);
        }
        return null;
    }

    private StatsResultObject getStats(Persistable p, TextProvider provider, DateGranularity granularity) {
        switch (granularity) {
            case DAY:
                return aggregateStatisticsDao.getDailyStats(p, provider);
            case MONTH:
                return aggregateStatisticsDao.getMonthlyStats(p, provider);
            case YEAR:
                return aggregateStatisticsDao.getAnnualStats(p, provider);
            default:
                return null;
        }
    }


    @Transactional(readOnly=false)
    public void generateMonthlyResourceStats(DateTime date) {
        aggregateStatisticsDao.updateMonthly(date);
        
    }

    @Transactional(readOnly=false)
    public void initializeNewAggregateEntries(DateTime date) {
        aggregateStatisticsDao.createNewAggregateEntries(date);
        aggregateStatisticsDao.resetAnnualTable(date);
    }



    /**
     * Find the count of downloads for a specified @link InformationResourceFile for a given date range, limited by the minimum occurrence count.
     * 
     * @param granularity
     * @param start
     * @param end
     * @param minCount
     * @param iRFileId
     * @return
     */
    @Transactional(readOnly=true)
    public List<AggregateDownloadStatistic> getAggregateDownloadStatsForFile(DateGranularity granularity, Date start, Date end, Long minCount, Long iRFileId) {
        return aggregateStatisticsDao.getDownloadStatsForFile(granularity, start, end, minCount, iRFileId);
    }

    /**
     * Find the count of views for the specified resources for a given date range, limited by the minimum occurrence count.
     * 
     * @param granularity
     * @param start
     * @param end
     * @param minCount
     * @param resourceIds
     * @return
     */
    @Transactional(readOnly=true)
    public List<AggregateDayViewStatistic> getUsageStatsForResource(Resource resource) {
        return aggregateStatisticsDao.getUsageStatsForResource(resource);
    }

}
