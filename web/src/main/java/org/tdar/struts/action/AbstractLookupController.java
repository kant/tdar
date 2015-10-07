/**
 * $Id$
 * 
 * @author $Author$
 * @version $Revision$
 */
package org.tdar.struts.action;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.hibernate.search.FullTextQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.core.bean.Indexable;
import org.tdar.core.bean.entity.Creator;
import org.tdar.core.bean.entity.Institution;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.resource.Dataset.IntegratableOptions;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.service.ObfuscationService;
import org.tdar.core.service.SerializationService;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.core.service.resource.ResourceService;
import org.tdar.core.service.search.ReservedSearchParameters;
import org.tdar.core.service.search.SearchService;
import org.tdar.search.index.LookupSource;
import org.tdar.search.query.SearchResultHandler;
import org.tdar.search.query.SortOption;
import org.tdar.search.query.builder.InstitutionQueryBuilder;
import org.tdar.search.query.builder.PersonQueryBuilder;
import org.tdar.search.query.builder.QueryBuilder;
import org.tdar.search.query.part.FieldQueryPart;
import org.tdar.search.query.part.InstitutionAutocompleteQueryPart;
import org.tdar.search.query.part.PersonQueryPart;
import org.tdar.search.query.part.PhraseFormatter;
import org.tdar.search.query.part.QueryGroup;
import org.tdar.search.query.part.QueryPartGroup;
import org.tdar.utils.PaginationHelper;
import org.tdar.utils.json.JsonAdminLookupFilter;
import org.tdar.utils.json.JsonLookupFilter;

import com.opensymphony.xwork2.ActionSupport;

/**
 * @author Adam Brin
 * 
 */
public abstract class AbstractLookupController<I extends Indexable> extends AuthenticationAware.Base implements SearchResultHandler<I> {

    private static final long serialVersionUID = 2357805482356017885L;

    private String callback;
    private ProjectionModel projectionModel;
    private int minLookupLength = 3;
    private int recordsPerPage = getDefaultRecordsPerPage();
    private int startRecord = DEFAULT_START;
    private List<I> results = Collections.emptyList();
    private int totalRecords;
    private SortOption sortField;
    private SortOption defaultSort = SortOption.getDefaultSortOption();
    private SortOption secondarySortField = SortOption.TITLE;
    private boolean debug = false;
    private ReservedSearchParameters reservedSearchParameters = new ReservedSearchParameters();
    private InputStream jsonInputStream;
    private Long id = null;
    private String mode;
    private String searchTitle;
    private String searchDescription;
    // execute a query even if query is empty
    private boolean showAll = false;

    private LookupSource lookupSource;

    private PaginationHelper paginationHelper;

    @Autowired
    private transient ResourceService resourceService;

    @Autowired
    private transient SearchService searchService;

    @Autowired
    private transient AuthorizationService authorizationService;

    @Autowired
    ObfuscationService obfuscationService;

    protected void handleSearch(QueryBuilder q) throws ParseException {
        searchService.handleSearch(q, this, this);
    }

    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    public int getMinLookupLength() {
        return minLookupLength;
    }

    public void addFacets(FullTextQuery ftq) {
        // empty method, overriden if needed
    }

    public void setMinLookupLength(int minLookupLength) {
        this.minLookupLength = minLookupLength;
    }

    @Override
    public int getRecordsPerPage() {
        return recordsPerPage;
    }

    @Override
    public void setRecordsPerPage(int recordsPerPage) {
        this.recordsPerPage = recordsPerPage;
    }

    @Override
    public int getStartRecord() {
        return startRecord;
    }

    @Override
    public void setStartRecord(int startRecord) {
        this.startRecord = startRecord;
    }

    @Override
    public SortOption getSortField() {
        if (sortField == null) {
            sortField = getDefaultSort();
        }
        return sortField;
    }

    @Override
    public void setSortField(SortOption sortField) {
        this.sortField = sortField;
    }

    /**
     * @param totalRecords
     *            the totalRecords to set
     */
    @Override
    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    /**
     * @return the totalRecords
     */
    @Override
    public int getTotalRecords() {
        return totalRecords;
    }

    /**
     * Return true if the specified string meets the minimum length requirement (or if there is no minimum length requirement). Not to be confused w/
     * checking if the specified string is blank.
     */
    public boolean checkMinString(String value) {
        if (getMinLookupLength() == 0) {
            return true;
        }
        return StringUtils.isNotEmpty(value) && (value.trim().length() >= getMinLookupLength());
    }

    // return true if ALL of the specified strings meet the minimum length. Otherwise false;
    public boolean checkMinString(String... candidates) {
        for (String candidate : candidates) {
            if (!checkMinString(candidate)) {
                return false;
            }
        }
        return true;
    }

    protected void addEscapedWildcardField(QueryGroup q, String field, String value) {
        if (checkMinString(value) && StringUtils.isNotBlank(value)) {
            getLogger().trace("{}:{}", field, value);
            FieldQueryPart<String> fqp = new FieldQueryPart<String>(field, StringUtils.trim(value));
            fqp.setPhraseFormatters(PhraseFormatter.WILDCARD);
            q.append(fqp);
        }
    }

    protected void addQuotedEscapedField(QueryGroup q, String field, String value) {
        if (checkMinString(value)) {
            getLogger().trace("{}:{}", field, value);
            FieldQueryPart<String> fqp = new FieldQueryPart<String>(field, StringUtils.trim(value));
            fqp.setPhraseFormatters(PhraseFormatter.ESCAPE_QUOTED);
            q.append(fqp);
        }
    }

    protected <C> void appendIf(boolean test, QueryGroup q, String field, C value) {
        if (test) {
            q.append(new FieldQueryPart<C>(field, value));
        }
    }

    protected void addResourceTypeQueryPart(QueryGroup q, List<ResourceType> list) {
        if (!CollectionUtils.isEmpty(list)) {
            FieldQueryPart<ResourceType> fqp = new FieldQueryPart<ResourceType>("resourceType", list.toArray(new ResourceType[0]));
            fqp.setOperator(Operator.OR);
            q.append(fqp);
        }
    }

    // deal with the terms that correspond w/ the "narrow your search" section
    // and from facets
    protected QueryPartGroup processReservedTerms(ActionSupport support) {
        authorizationService.initializeReservedSearchParameters(getReservedSearchParameters(), getAuthenticatedUser());
        return getReservedSearchParameters().toQueryPartGroup(support);
    }

    @Override
    public int getNextPageStartRecord() {
        return startRecord + recordsPerPage;
    }

    @Override
    public int getPrevPageStartRecord() {
        return startRecord - recordsPerPage;
    }

    /**
     * @param results
     *            the results to set
     */
    @Override
    public void setResults(List<I> results) {
        this.results = results;
    }

    /**
     * @return the results
     */
    @Override
    public List<I> getResults() {
        return results;
    }

    /*
     * 
     */
    @SuppressWarnings("unchecked")
    public List<Creator> getCreatorResults() {
        return (List<Creator>) results;
    }

    /**
     * @return the debug
     */
    @Override
    public boolean isDebug() {
        return debug;
    }

    /**
     * @param debug
     *            the debug to set
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean isShowAll() {
        return showAll;
    }

    public void setShowAll(boolean ignoringEmptyQuery) {
        this.showAll = ignoringEmptyQuery;
    }

    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id
     *            the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the secondarySortField
     */
    @Override
    public SortOption getSecondarySortField() {
        return secondarySortField;
    }

    /**
     * @param secondarySortField
     *            the secondarySortField to set
     */
    public void setSecondarySortField(SortOption secondarySortField) {
        this.secondarySortField = secondarySortField;
    }

    /**
     * @param useSubmitterContext
     *            the useSubmitterContext to set
     */
    public void setUseSubmitterContext(boolean useSubmitterContext) {
        getReservedSearchParameters().setUseSubmitterContext(useSubmitterContext);
    }

    /**
     * @return the useSubmitterContext
     */
    public boolean isUseSubmitterContext() {
        return getReservedSearchParameters().isUseSubmitterContext();
    }

    /**
     * @return the mode
     */
    @Override
    public String getMode() {
        return mode;
    }

    /**
     * @param mode
     *            the mode to set
     */
    // TODO: method needs better name... this is just metadata used to describe the caller of handleSearch()
    @Override
    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public String getSearchDescription() {
        return searchDescription;
    }

    public void setSearchDescription(String searchDescription) {
        this.searchDescription = searchDescription;
    }

    @Override
    public String getSearchTitle() {
        return searchTitle;
    }

    public void setSearchTitle(String searchTitle) {
        this.searchTitle = searchTitle;
    }

    public ReservedSearchParameters getReservedSearchParameters() {
        return reservedSearchParameters;
    }

    public void setReservedSearchParameters(ReservedSearchParameters reservedSearchParameters) {
        this.reservedSearchParameters = reservedSearchParameters;
    }

    public List<Status> getAllStatuses() {
        return new ArrayList<Status>(Arrays.asList(Status.values()));
    }

    public List<Status> getIncludedStatuses() {
        return getReservedSearchParameters().getStatuses();
    }

    public void setIncludedStatuses(List<Status> statuses) {
        getReservedSearchParameters().setStatuses(statuses);
    }

    public List<ResourceType> getResourceTypes() {
        return getReservedSearchParameters().getResourceTypes();
    }

    public List<ResourceType> getAllResourceTypes() {
        return resourceService.getAllResourceTypes();
    }

    public List<IntegratableOptions> getIntegratableOptions() {
        return getReservedSearchParameters().getIntegratableOptions();
    }

    public void setIntegratableOptions(List<IntegratableOptions> integratableOptions) {
        getReservedSearchParameters().setIntegratableOptions(integratableOptions);
    }

    // REQUIRED IF YOU WANT FACETING TO ACTUALLY WORK
    public void setResourceTypes(List<ResourceType> resourceTypes) {
        getReservedSearchParameters().setResourceTypes(resourceTypes);
    }

    public String findPerson(String firstName, String term, String lastName, String institution, String email, String registered) {
        this.setLookupSource(LookupSource.PERSON);
        QueryBuilder q = new PersonQueryBuilder(Operator.AND);
        boolean valid = false;

        Person incomingPerson = new Person();
        if (checkMinString(firstName)) {
            incomingPerson.setFirstName(firstName);
            valid = true;
        }

        if (checkMinString(lastName)) {
            incomingPerson.setLastName(lastName);
            valid = true;
        }

        if (StringUtils.isEmpty(firstName) && StringUtils.isEmpty(lastName) && checkMinString(term)) {
            incomingPerson.setWildcardName(term);
            valid = true;
        }

        if (checkMinString(institution)) {
            valid = true;
            Institution incomingInstitution = new Institution(institution);
            incomingPerson.setInstitution(incomingInstitution);
            // FIXME: I believe this detach is unnecessary - object was never on the session
            // AB: OpenSessionInView makes me nervous with this, don't want to take a chance
            getGenericService().detachFromSessionAndWarn(incomingInstitution);
        }

        // ignore email field for unauthenticated users.
        if (isAuthenticated() && checkMinString(email)) {
            incomingPerson.setEmail(email);
            valid = true;
        }
        // FIXME: I believe this detach is unnecessary - object was never on the session
        // AB: OpenSessionInView makes me nervous with this, don't want to take a chance
        getGenericService().detachFromSessionAndWarn(incomingPerson);

        PersonQueryPart pqp = new PersonQueryPart();
        pqp.add(incomingPerson);
        q.append(pqp);
        getLogger().debug("{}", pqp.toString());
        q.append(new FieldQueryPart<Status>("status", Status.ACTIVE));
        if (valid || (getMinLookupLength() == 0)) {
            if (StringUtils.isNotBlank(registered)) {
                try {
                    pqp.setRegistered(Boolean.parseBoolean(registered));
                } catch (Exception e) {
                    addActionErrorWithException(getText("abstractLookupController.invalid_syntax"), e);
                    return ERROR;
                }
            }

            try {
                handleSearch(q);
                // sanitize results if the user is not logged in
            } catch (ParseException e) {
                addActionErrorWithException(getText("abstractLookupController.invalid_syntax"), e);
                return ERROR;
            }
        }
        if (isEditor()) {
            jsonifyResult(JsonAdminLookupFilter.class);
        } else {
            jsonifyResult(JsonLookupFilter.class);
        }
        return SUCCESS;
    }

    @Autowired
    SerializationService serializationService;

    private Map<String, Object> result = new HashMap<>();

    public void jsonifyResult(Class<?> filter) {
        prepareResult();
        jsonInputStream = new ByteArrayInputStream(serializationService.convertFilteredJsonForStream(getResult(), filter, callback).getBytes());
    }

    protected void prepareResult() {
        List<I> actual = new ArrayList<>();
        for (I obj : results) {
            if (obj == null) {
                continue;
            }
            obfuscationService.obfuscateObject(obj, getAuthenticatedUser());
            actual.add(obj);
        }
        Map<String, Object> status = new HashMap<>();
        getResult().put(getResultsKey(), actual);
        getResult().put("status", status);
        status.put("recordsPerPage", getRecordsPerPage());
        status.put("startRecord", getStartRecord());
        status.put("totalRecords", getTotalRecords());
        status.put("sortField", getSortField());
    }

    protected String getResultsKey() {
        return getLookupSource().getCollectionName();
    }

    public String findInstitution(String institution) {
        this.setLookupSource(LookupSource.INSTITUTION);
        QueryBuilder q = new InstitutionQueryBuilder(Operator.AND);
        if (checkMinString(institution)) {
            InstitutionAutocompleteQueryPart iqp = new InstitutionAutocompleteQueryPart();
            Institution testInstitution = new Institution(institution);
            if (StringUtils.isNotBlank(institution)) {
                iqp.add(testInstitution);
                q.append(iqp);
            }
            q.append(new FieldQueryPart<Status>("status", Status.ACTIVE));
            try {
                handleSearch(q);
            } catch (ParseException e) {
                addActionErrorWithException(getText("abstractLookupController.invalid_syntax"), e);
                return ERROR;
            }
        }
        jsonifyResult(JsonLookupFilter.class);
        return SUCCESS;
    }

    public LookupSource getLookupSource() {
        return lookupSource;
    }

    public void setLookupSource(LookupSource lookupSource) {
        this.lookupSource = lookupSource;
    }

    public PaginationHelper getPaginationHelper() {
        if (paginationHelper == null) {
            paginationHelper = PaginationHelper.withSearchResults(this);
        }
        return paginationHelper;
    }

    /**
     * indicates whether view layer should hide facet + sort controls
     * 
     * @return
     */
    public boolean isHideFacetsAndSort() {
        return true;
    }

    public SortOption getDefaultSort() {
        return defaultSort;
    }

    public void setDefaultSort(SortOption defaultSort) {
        this.defaultSort = defaultSort;
    }

    @Override
    public ProjectionModel getProjectionModel() {
        return projectionModel;
    }

    public void setProjectionModel(ProjectionModel projectionModel) {
        this.projectionModel = projectionModel;
    }

    public InputStream getJsonInputStream() {
        return jsonInputStream;
    }

    public void setJsonInputStream(InputStream jsonInputStream) {
        this.jsonInputStream = jsonInputStream;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    @Override
    public int getDefaultRecordsPerPage() {
        return DEFAULT_RESULT_SIZE;
    }

    protected boolean isFindAll(String query) {
        if (StringUtils.isBlank(query)) {
            return true;
        }
        return StringUtils.equals(StringUtils.trim(query), "*");
    }
}