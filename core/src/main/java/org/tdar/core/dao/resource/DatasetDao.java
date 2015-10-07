package org.tdar.core.dao.resource;

import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.CacheMode;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.CriteriaSpecification;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.core.bean.resource.Dataset;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceProxy;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.bean.resource.datatable.DataTable;
import org.tdar.core.bean.resource.datatable.DataTableColumn;
import org.tdar.core.bean.resource.datatable.DataTableRelationship;
import org.tdar.core.bean.resource.file.VersionType;
import org.tdar.core.dao.NamedNativeQueries;
import org.tdar.core.dao.TdarNamedQueries;
import org.tdar.core.service.RssService;
import org.tdar.core.service.UrlService;
import org.tdar.core.service.resource.dataset.DatasetUtils;
import org.tdar.db.model.abstracts.TargetDatabase;
import org.tdar.search.query.SearchResultHandler;
import org.tdar.utils.PersistableUtils;

import com.redfin.sitemapgenerator.GoogleImageSitemapGenerator;
import com.redfin.sitemapgenerator.GoogleImageSitemapUrl;
import com.redfin.sitemapgenerator.GoogleImageSitemapUrl.ImageTag;

/**
 * $Id$
 * 
 * <p>
 * Provides data integration and access functionality for persistent Datasets.
 * </p>
 * 
 * @author Adam Brin, Allen Lee
 * @version $Revision$
 */
@Component
public class DatasetDao extends ResourceDao<Dataset> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public DatasetDao() {
        super(Dataset.class);
    }

    @Autowired
    private TargetDatabase tdarDataImportDatabase;

    public String normalizeTableName(String name) {
        return tdarDataImportDatabase.normalizeTableOrColumnNames(name);
    }

    public Map<DataTableColumn, String> getMappedDataForInformationResource(InformationResource resource) {
        String key = resource.getMappedDataKeyValue();
        DataTableColumn column = resource.getMappedDataKeyColumn();
        if (StringUtils.isBlank(key) || (column == null)) {
            return null;
        }
        final DataTable table = column.getDataTable();
        ResultSetExtractor<Map<DataTableColumn, String>> resultSetExtractor = new ResultSetExtractor<Map<DataTableColumn, String>>() {
            @Override
            public Map<DataTableColumn, String> extractData(ResultSet rs) throws SQLException, DataAccessException {
                while (rs.next()) {
                    Map<DataTableColumn, String> results = DatasetUtils.convertResultSetRowToDataTableColumnMap(table, rs, false);
                    return results;
                }
                return null;
            }
        };

        return tdarDataImportDatabase.selectAllFromTableCaseInsensitive(column, key, resultSetExtractor);
    }

    public boolean canLinkDataToOntology(Dataset dataset) {
        if (dataset == null) {
            return false;
        }
        Query query = getCurrentSession().getNamedQuery(QUERY_DATASET_CAN_LINK_TO_ONTOLOGY);
        query.setLong("datasetId", dataset.getId());
        return !query.list().isEmpty();
    }

    public long countResourcesForUserAccess(Person user) {
        if (user == null) {
            return 0;
        }
        Query query = getCurrentSession().getNamedQuery(QUERY_USER_GET_ALL_RESOURCES_COUNT);
        query.setLong("userId", user.getId());
        query.setParameterList("resourceTypes", Arrays.asList(ResourceType.values()));
        query.setParameterList("statuses", Status.values());
        query.setParameter("allStatuses", true);
        query.setParameter("effectivePermission", GeneralPermissions.MODIFY_METADATA.getEffectivePermissions() - 1);
        query.setParameter("allResourceTypes", true);
        query.setParameter("admin", false);
        return (Long) query.iterate().next();
    }

    /**
     * Finds all resource modified in the last X days
     * 
     * @param days
     * @return List<Resource>
     */
    @SuppressWarnings("unchecked")
    public List<Resource> findRecentlyUpdatedItemsInLastXDays(int days) {
        Query query = getCurrentSession().getNamedQuery(QUERY_RECENT);
        query.setDate("updatedDate", new Date(System.currentTimeMillis() - (86400000l * days)));
        return query.list();
    }

    /**
     * Finds all resource modified in the last X days or that have an empty externalId id field AND
     * that have at least one file
     * 
     * @param days
     * @return List<Resource>
     */
    @SuppressWarnings("unchecked")
    public List<Long> findRecentlyUpdatedItemsInLastXDaysForExternalIdLookup(int days) {
        Query query = getCurrentSession().getNamedQuery(QUERY_EXTERNAL_ID_SYNC);
        query.setDate("updatedDate", new Date(System.currentTimeMillis() - (86400000l * days)));
        return query.list();
    }
    
    public void resetColumnMappings(Project project) {
        String sql = String.format("update information_resource set mappeddatakeyvalue=null,mappeddatakeycolumn_id=null where project_id=%s", project.getId());
        getCurrentSession().createSQLQuery(sql).executeUpdate();
    }

    /*
     * Take the distinct column values mapped and associate them with files in tDAR based on:
     * - shared project
     * - filename matches column value either (a) with extension or (b) with separator eg: file1.jpg;file2.jpg
     * 
     * Using a raw SQL update statement to try and simplify the execution here to use as few loops as possible...
     */
    public void mapColumnToResource(DataTableColumn column, List<String> distinctValues) {
        Project project = column.getDataTable().getDataset().getProject();
        // for each distinct column value

        long timestamp = System.currentTimeMillis();
        String sql = String.format("CREATE TEMPORARY TABLE MATCH%s (id bigserial, primary key(id), key varchar(255),actual varchar(255))", timestamp);
        SQLQuery create = getCurrentSession().createSQLQuery(sql);
        logger.debug(sql);
        create.executeUpdate();
        int count = 0;
        for (String columnValue : distinctValues) {
            List<String> valuesToMatch = new ArrayList<String>();

            // split on delimiter if specified
            if (StringUtils.isNotBlank(column.getDelimiterValue())) {
                valuesToMatch.addAll(Arrays.asList(columnValue.split(column.getDelimiterValue())));
            } else {
                valuesToMatch.add(columnValue);
            }

            // clean up filename and join if delimeter specified
            if (column.isIgnoreFileExtension()) {
                for (int i = 0; i < valuesToMatch.size(); i++) {
                    valuesToMatch.set(i, FilenameUtils.getBaseName(valuesToMatch.get(i).toLowerCase()) + ".");
                }
            }
            for (String match : valuesToMatch) {
                if (StringUtils.isBlank(match)) {
                    continue;
                }
                String format = String.format("insert into MATCH%s (key,actual) values('%s','%s')", timestamp,
                        org.apache.commons.lang.StringEscapeUtils.escapeSql(match.toLowerCase()),
                        org.apache.commons.lang.StringEscapeUtils.escapeSql(columnValue.toLowerCase()));
                SQLQuery insert = getCurrentSession().createSQLQuery(format);
                insert.executeUpdate();
                if (count % 250 == 0) {
                    logger.trace(format);
                }
                count++;
            }
        }
        List<VersionType> types = Arrays.asList(VersionType.UPLOADED, VersionType.ARCHIVAL, VersionType.UPLOADED_ARCHIVAL);
        String filenameCheck = "lower(irfv.filename)";
        if (column.isIgnoreFileExtension()) {
            filenameCheck = "substring(lower(irfv.filename), 0, length(irfv.filename) - length(irfv.extension) + 1)";
        }
        String format = String.format(
                "update information_resource ir_ set mappeddatakeycolumn_id=%s, mappedDataKeyValue=actual from MATCH%s, information_resource ir inner join "
                        + "information_resource_file irf on ir.id=irf.information_resource_id " +
                        "inner join information_resource_file_version irfv on irf.id=irfv.information_resource_file_id " +
                        "WHERE ir.project_id=%s and lower(key)=%s and irfv.internal_type in ('%s') and ir.id=ir_.id and ir.mappedDataKeyValue is null",
                column.getId(), timestamp, project.getId(), filenameCheck, StringUtils.join(types, "','"));
        SQLQuery matching = getCurrentSession().createSQLQuery(format);
        logger.debug(format);
        int executeUpdate = matching.executeUpdate();
        logger.debug("{} rows updated", executeUpdate);
    }

    public void unmapAllColumnsInProject(Long projectId, List<Long> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        String rawsql = NamedNativeQueries.removeDatasetMappings(projectId, columns);
        logger.trace(rawsql);
        Query query = getCurrentSession().createSQLQuery(rawsql);
        int executeUpdate = query.executeUpdate();
        logger.debug("{} resources unmapped", executeUpdate);
    }

    public Number getAccessCount(Resource resource) {
        String sql = String.format(TdarNamedQueries.RESOURCE_ACCESS_COUNT_SQL, resource.getId(), new Date());
        return (Number) getCurrentSession().createSQLQuery(sql).uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public List<Long> findAllResourceIdsWithFiles() {
        Query query = getCurrentSession().getNamedQuery(QUERY_INFORMATIONRESOURCES_WITH_FILES);
        return query.list();
    }

    @SuppressWarnings("unchecked")
    public List<Resource> findAllSparseActiveResources() {
        Query query = getCurrentSession().getNamedQuery(QUERY_SPARSE_ACTIVE_RESOURCES);
        return query.list();
    }

    @SuppressWarnings("unchecked")
    public List<Resource> findSkeletonsForSearch(boolean trustCache, Long... ids) {
        Session session = getCurrentSession();
        // distinct prevents duplicates
        // left join res.informationResourceFiles
        long time = System.currentTimeMillis();
        Query query = session.getNamedQuery(QUERY_PROXY_RESOURCE_SHORT);
        // if we have more than one ID, then it's faster to do a deeper query (fewer follow-ups)
        if (ids.length > 1) {
            query = session.getNamedQuery(QUERY_PROXY_RESOURCE_FULL);
        }
        if (!trustCache) {
            query.setCacheable(false);
            query.setCacheMode(CacheMode.REFRESH);
        }
        query.setParameterList("ids", Arrays.asList(ids));
        query.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);

        List<ResourceProxy> results = query.list();
        long queryTime = System.currentTimeMillis() - time;
        time = System.currentTimeMillis();
        List<Resource> toReturn = new ArrayList<>();
        Map<Long, Resource> resultMap = new HashMap<>();
        logger.trace("convert proxy to resource");
        for (ResourceProxy prox : results) {
            try {
                resultMap.put(prox.getId(), prox.generateResource());
            } catch (Exception e) {
                logger.error("{}", e);
            }
        }
        logger.trace("resorting results");
        for (Long id : ids) {
            toReturn.add(resultMap.get(id));
        }
        time = System.currentTimeMillis() - time;
        logger.trace("Query: {} ; generation: {} ", queryTime, time);

        return toReturn;
    }

    @SuppressWarnings("unchecked")
    public List<Resource> findOld(Long[] ids) {
        Session session = getCurrentSession();
        long time = System.currentTimeMillis();
        Query query = session.getNamedQuery(QUERY_RESOURCE_FIND_OLD_LIST);
        query.setParameterList("ids", Arrays.asList(ids));
        List<Resource> results = query.list();
        logger.info("query took: {} ", System.currentTimeMillis() - time);
        return results;
    }

    @SuppressWarnings("unchecked")
    public int findAllResourcesWithPublicImagesForSitemap(GoogleImageSitemapGenerator gisg) {
        Query query = getCurrentSession().createSQLQuery(SELECT_RAW_IMAGE_SITEMAP_FILES);
        query.setCacheMode(CacheMode.IGNORE);
        int count = 0;
        logger.trace(SELECT_RAW_IMAGE_SITEMAP_FILES);
        ScrollableResults scroll = query.scroll(ScrollMode.FORWARD_ONLY);
        while (scroll.next()) {
            // select r.id, r.title, r.description, r.resource_type, irf.description, irfv.id
            if (count % 500 == 0) {
                clearCurrentSession();
            }
            Number id = (Number) scroll.get(0);
            String title = (String) scroll.get(1);
            String description = (String) scroll.get(2);
            ResourceType resourceType = ResourceType.valueOf((String) scroll.get(3));
            String fileDescription = (String) scroll.get(4);
            Number imageId = (Number) scroll.get(5);
            Resource res = new Resource(id.longValue(), title, resourceType);
            markReadOnly(res);
            String resourceUrl = UrlService.absoluteUrl(res);
            String imageUrl = UrlService.thumbnailUrl(imageId.longValue());
            if (StringUtils.isNotBlank(fileDescription)) {
                description = fileDescription;
            }
            try {
                ImageTag tag = new GoogleImageSitemapUrl.ImageTag(new URL(imageUrl)).title(cleanupXml(title)).caption(cleanupXml(description));
                GoogleImageSitemapUrl iurl = new GoogleImageSitemapUrl.Options(new URL(resourceUrl)).addImage(tag).build();
                gisg.addUrl(iurl);
                count++;
            } catch (Exception e) {
                logger.error("error in url generation for sitemap {}", e);
            }
        }
        return count;
    }

    private String cleanupXml(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }
        return StringEscapeUtils.escapeXml11(RssService.stripInvalidXMLCharacters(text));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<Resource> findByTdarYear(SearchResultHandler handler, int year) {
        Query query = getCurrentSession().getNamedQuery(TdarNamedQueries.FIND_BY_TDAR_YEAR);
        if (handler.getRecordsPerPage() < 0) {
            handler.setRecordsPerPage(250);
        }
        query.setMaxResults(handler.getRecordsPerPage());
        if (handler.getStartRecord() < 0) {
            handler.setStartRecord(0);
        }
        query.setFirstResult(handler.getStartRecord());

        DateTime dt = new DateTime(year, 1, 1, 0, 0, 0, 0);
        query.setParameter("year_start", dt.toDate());
        query.setParameter("year_end", dt.plusYears(1).toDate());
        Query query2 = getCurrentSession().getNamedQuery(TdarNamedQueries.FIND_BY_TDAR_YEAR_COUNT);
        query2.setParameter("year_start", dt.toDate());
        query2.setParameter("year_end", dt.plusYears(1).toDate());
        Number max = (Number) query2.uniqueResult();
        handler.setTotalRecords(max.intValue());

        return query.list();
    }

    public void deleteRelationships(Set<DataTableRelationship> relationshipsToRemove) {
        List<Long> ids = PersistableUtils.extractIds(relationshipsToRemove);
        ids.removeAll(Collections.singleton(null));
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        Query query = getCurrentSession().getNamedQuery(TdarNamedQueries.DELETE_DATA_TABLE_COLUMN_RELATIONSHIPS);
        query.setParameterList("ids", ids);
        query.executeUpdate();
        Query query2 = getCurrentSession().getNamedQuery(TdarNamedQueries.DELETE_DATA_TABLE_RELATIONSHIPS);
        query2.setParameterList("ids", ids);
        query2.executeUpdate();
    }

    public void cleanupUnusedTablesAndColumns(Dataset dataset, Collection<DataTable> tablesToRemove, Collection<DataTableColumn> columnsToRemove) {
        logger.info("deleting unmerged tables: {}", tablesToRemove);
        ArrayList<DataTableColumn> columnsToUnmap = new ArrayList<DataTableColumn>();
        if (CollectionUtils.isNotEmpty(columnsToRemove)) {
            for (DataTableColumn column : columnsToRemove) {
                columnsToUnmap.add(column);
            }
        }
        // first unmap all columns from the removed tables
        unmapAllColumnsInProject(dataset.getProject().getId(), PersistableUtils.extractIds(columnsToUnmap));

        delete(columnsToRemove);
        if (CollectionUtils.isNotEmpty(tablesToRemove)) {
            dataset.getDataTables().removeAll(tablesToRemove);
        }
        
    }

}