package org.tdar.search.service.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.Persistable;
import org.tdar.core.bean.collection.ResourceCollection.CollectionType;
import org.tdar.core.bean.entity.Creator;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.keyword.Keyword;
import org.tdar.core.bean.keyword.KeywordType;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.exception.TdarRecoverableRuntimeException;
import org.tdar.core.service.GenericService;
import org.tdar.core.service.ResourceCreatorProxy;
import org.tdar.search.bean.AdvancedSearchQueryObject;
import org.tdar.search.bean.ReservedSearchParameters;
import org.tdar.search.bean.ResourceLookupObject;
import org.tdar.search.bean.SearchParameters;
import org.tdar.search.query.LuceneSearchResultHandler;
import org.tdar.search.query.QueryFieldNames;
import org.tdar.search.query.builder.QueryBuilder;
import org.tdar.search.query.builder.ResourceCollectionQueryBuilder;
import org.tdar.search.query.builder.ResourceQueryBuilder;
import org.tdar.search.query.part.CategoryTermQueryPart;
import org.tdar.search.query.part.FieldQueryPart;
import org.tdar.search.query.part.HydrateableKeywordQueryPart;
import org.tdar.search.query.part.ProjectIdLookupQueryPart;
import org.tdar.search.query.part.QueryPartGroup;
import org.tdar.utils.MessageHelper;
import org.tdar.utils.PersistableUtils;

import com.opensymphony.xwork2.TextProvider;

@Service
@Transactional
public class ResourceSearchService extends AbstractSearchService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final GenericService genericService;
    private final SearchService<Resource> searchService;

    @Autowired
    public ResourceSearchService(SearchService<Resource> searchService, GenericService genericService) {
        this.searchService = searchService;
        this.genericService = genericService;
    }


    public LuceneSearchResultHandler<Resource> buildCollectionResourceSearch(LuceneSearchResultHandler<Resource> result, TextProvider provider) throws ParseException, SolrServerException, IOException {
        QueryBuilder qb = new ResourceCollectionQueryBuilder();
        qb.append(new FieldQueryPart<CollectionType>(QueryFieldNames.COLLECTION_TYPE, CollectionType.SHARED));
        qb.append(new FieldQueryPart<Boolean>(QueryFieldNames.COLLECTION_HIDDEN, Boolean.FALSE));
        qb.append(new FieldQueryPart<Boolean>(QueryFieldNames.TOP_LEVEL, Boolean.TRUE));
        searchService.handleSearch(qb, result, provider);
        return result;
    }

    /**
     * Shared logic to find all direct children of container resource (ResourceCollections and Projects)
     *
     * @param fieldName
     * @param indexable
     * @param user
     * @return
     * @throws IOException 
     * @throws SolrServerException 
     * @throws ParseException 
     */
    public <P extends Persistable> LuceneSearchResultHandler<Resource> buildResourceContainedInSearch(String fieldName, P indexable, TdarUser user, LuceneSearchResultHandler<Resource> result, TextProvider provider) throws ParseException, SolrServerException, IOException {
        ResourceQueryBuilder qb = new ResourceQueryBuilder();
        ReservedSearchParameters reservedSearchParameters = new ReservedSearchParameters();
        initializeReservedSearchParameters(reservedSearchParameters, user);
        qb.append(reservedSearchParameters, provider);
        qb.setOperator(Operator.AND);
        qb.append(new FieldQueryPart<>(fieldName, indexable.getId()));
        
        searchService.handleSearch(qb, result, provider);
        return result;
    }

    public LuceneSearchResultHandler<Resource> lookupResource(TdarUser user, ResourceLookupObject look, LuceneSearchResultHandler<Resource> result, TextProvider support) throws ParseException, SolrServerException, IOException {
        ResourceQueryBuilder q = new ResourceQueryBuilder();
        if (StringUtils.isNotBlank(look.getTerm()) || look.getCategoryId() != null) {
            q.append(new CategoryTermQueryPart(look.getTerm(), look.getCategoryId()));
        }

        if (PersistableUtils.isNotNullOrTransient(look.getProjectId())) {
            q.append(new ProjectIdLookupQueryPart(look.getProjectId()));
        }

        String colQueryField = QueryFieldNames.RESOURCE_COLLECTION_SHARED_IDS;
        if (look.getIncludeParent()  == Boolean.FALSE || look.getIncludeParent() == null) {
            colQueryField = QueryFieldNames.RESOURCE_COLLECTION_DIRECT_SHARED_IDS;
        }

        if (PersistableUtils.isNotNullOrTransient(look.getCollectionId())) {
            q.append(new FieldQueryPart<Long>(colQueryField, look.getCollectionId()));
        }

        ReservedSearchParameters reservedSearchParameters = look.getReservedSearchParameters();
        initializeReservedSearchParameters(reservedSearchParameters, user);
        q.append(reservedSearchParameters.toQueryPartGroup(support));
        q.appendFilter(reservedSearchParameters.getFilters());

        searchService.handleSearch(q, result, MessageHelper.getInstance());
        return result;

    }

    public LuceneSearchResultHandler<Resource> buildKeywordQuery(Keyword keyword, KeywordType keywordType, LuceneSearchResultHandler<Resource> result, TextProvider provider) throws ParseException, SolrServerException, IOException {
        ResourceQueryBuilder rqb = new ResourceQueryBuilder();
        rqb.append(new HydrateableKeywordQueryPart<Keyword>(keywordType, Arrays.asList(keyword)));
        rqb.append(new FieldQueryPart<Status>(QueryFieldNames.STATUS, Status.ACTIVE));
        searchService.handleSearch(rqb, result, provider);
        return result;
    }

    public LuceneSearchResultHandler<Resource> buildAdvancedSearch(AdvancedSearchQueryObject asqo, TdarUser authenticatedUser,
            LuceneSearchResultHandler<Resource> result, TextProvider provider) throws SolrServerException, IOException, ParseException {
        QueryBuilder queryBuilder = new ResourceQueryBuilder();
        queryBuilder.setOperator(Operator.AND);
        QueryPartGroup topLevelQueryPart;
        QueryPartGroup reservedQueryPart;

        topLevelQueryPart = new QueryPartGroup(asqo.getOperator());

        for (SearchParameters group : asqo.getSearchParameters()) {
            if (group == null) {
                continue;
            }
            group.setExplore(asqo.isExplore());
            try {
                searchService.updateResourceCreators(group, 20);
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            topLevelQueryPart.append(group.toQueryPartGroup(provider));
        }
        queryBuilder.append(topLevelQueryPart);

        asqo.setSearchPhrase(topLevelQueryPart.getDescription(provider));

        if (topLevelQueryPart.isEmpty() || CollectionUtils.isNotEmpty(asqo.getAllGeneralQueryFields())) {
            asqo.setCollectionSearchBoxVisible(true);
        }
        reservedQueryPart = processReservedTerms(asqo.getReservedParams(), authenticatedUser,provider);
        asqo.setRefinedBy(reservedQueryPart.getDescription(provider));
        queryBuilder.append(reservedQueryPart);
        // TODO Auto-generated method stub
        searchService.handleSearch(queryBuilder, result, provider);
        return result;
    }

    // deal with the terms that correspond w/ the "narrow your search" section
    // and from facets
    protected QueryPartGroup processReservedTerms(ReservedSearchParameters reserved, TdarUser tdarUser, TextProvider provider) {
        initializeReservedSearchParameters(reserved, tdarUser);
        return reserved.toQueryPartGroup(provider);
    }
    
    /**
     * Take any of the @link SearchParameter properties that can support skeleton resources and inflate them so we can display something in the search title /
     * description that isn't just creatorId=4
     *
     * @param searchParameters
     */
    public void inflateSearchParameters(SearchParameters searchParameters) {
        // FIXME: refactor to ue genericService.populateSparseObjectsById() which optimizes the qeries to the DB
        // Also, consider moving into genericService
        List<List<? extends Persistable>> lists = searchParameters.getSparseLists();
        for (List<? extends Persistable> list : lists) {
            logger.debug("inflating list of sparse objects: {}", list);
            // making unchecked cast so compiler accepts call to set()
            @SuppressWarnings("unchecked")
            ListIterator<Persistable> itor = (ListIterator<Persistable>) list.listIterator();
            while (itor.hasNext()) {
                Persistable sparse = itor.next();
                if (sparse != null) {
                    Persistable persistable = genericService.find(sparse.getClass(), sparse.getId());
                    logger.debug("\t inflating {}({}) -> {}", sparse.getClass().getSimpleName(), sparse.getId(), persistable);
                    itor.set(persistable);
                }
            }
        }
    }



    /**
     * Generates a query for resources created by or releated to in some way to a @link Creator given a creator and a user
     *
     * @param creator
     * @param user
     * @return
     * @throws IOException 
     * @throws SolrServerException 
     * @throws ParseException 
     */
    public LuceneSearchResultHandler<Resource> generateQueryForRelatedResources(Creator<?> creator, TdarUser user, LuceneSearchResultHandler<Resource> result, TextProvider provider) throws ParseException, SolrServerException, IOException {
        QueryBuilder queryBuilder = new ResourceQueryBuilder();
        result.setRecordsPerPage(MAX_FTQ_RESULTS);
        queryBuilder.setOperator(Operator.AND);
        SearchParameters params = new SearchParameters(Operator.AND);
        params.setCreatorOwner(new ResourceCreatorProxy(creator, null));
        queryBuilder.append(params, provider);
        ReservedSearchParameters reservedSearchParameters = new ReservedSearchParameters();
        initializeReservedSearchParameters(reservedSearchParameters, user);
        queryBuilder.append(reservedSearchParameters, provider);
        searchService.handleSearch(queryBuilder, result, provider);
        return result;
    }

    /*
     * The @link AdvancedSearchController's ReservedSearchParameters is a proxy object for handling advanced boolean searches. We initialize it with the search
     * parameters
     * that are AND-ed with the user's search to ensure appropriate search results are returned (such as a Resource's @link Status).
     */
    protected void initializeReservedSearchParameters(ReservedSearchParameters reservedSearchParameters, TdarUser user) {
        if (reservedSearchParameters == null) {
            return;
        }
        reservedSearchParameters.setAuthenticatedUser(user);
        reservedSearchParameters.setTdarGroup(authenticationService.findGroupWithGreatestPermissions(user));
        Set<Status> allowedSearchStatuses = authorizationService.getAllowedSearchStatuses(user);
        List<Status> statuses = reservedSearchParameters.getStatuses();
        statuses.removeAll(Collections.singletonList(null));

        if (CollectionUtils.isEmpty(statuses)) {
            statuses = new ArrayList<>(Arrays.asList(Status.ACTIVE, Status.DRAFT));
        }

        statuses.retainAll(allowedSearchStatuses);
        reservedSearchParameters.setStatuses(statuses);
        if (statuses.isEmpty()) {
            throw (new TdarRecoverableRuntimeException("auth.search.status.denied"));
        }

    }
}