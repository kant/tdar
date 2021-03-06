package org.tdar.struts.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.configuration.TdarConfiguration;
import org.tdar.core.bean.DisplayOrientation;
import org.tdar.core.bean.SortOption;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.coverage.LatitudeLongitudeBox;
import org.tdar.core.bean.entity.ResourceCreatorRole;
import org.tdar.core.bean.keyword.Keyword;
import org.tdar.core.bean.resource.DocumentType;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceAccessType;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.exception.StatusCode;
import org.tdar.core.service.BookmarkedResourceService;
import org.tdar.core.service.UrlService;
import org.tdar.exception.TdarRecoverableRuntimeException;
import org.tdar.search.bean.AdvancedSearchQueryObject;
import org.tdar.search.bean.ObjectType;
import org.tdar.search.bean.SearchFieldType;
import org.tdar.search.bean.SearchParameters;
import org.tdar.search.exception.SearchException;
import org.tdar.search.exception.SearchPaginationException;
import org.tdar.search.index.LookupSource;
import org.tdar.search.query.ProjectionModel;
import org.tdar.search.service.query.ResourceSearchService;
import org.tdar.struts_base.action.TdarActionException;
import org.tdar.struts_base.action.TdarActionSupport;
import org.tdar.struts_base.interceptor.annotation.DoNotObfuscate;
import org.tdar.utils.PersistableUtils;

public abstract class AbstractAdvancedSearchController extends AbstractLookupController<Resource> {

    private static final long serialVersionUID = -1673127898112301380L;

    private static final String SEARCH_RSS = "/search/rss";
    private boolean hideFacetsAndSort = false;

    @Autowired
    private transient ResourceSearchService resourceSearchService;

    @Autowired
    private transient BookmarkedResourceService bookmarkedResourceService;

    private DisplayOrientation orientation;

    // error message of last resort. User entered something we did not
    // anticipate, and we ultimately translated it into query that lucene can't
    // parse

    private List<SearchFieldType> allSearchFieldTypes = SearchFieldType.getSearchFieldTypesByGroup();
    // basic searches go in "query"
    private String query = "";

    private List<SearchParameters> groups = new ArrayList<SearchParameters>();
    private Operator topLevelOperator = Operator.AND;

    private List<SortOption> sortOptions = SortOption.getOptionsForContext(Resource.class);

    private String latLongBox;
    // we plan to support some types of legacy requests. For example, the old
    // querystring format for id searches, basic search, and search by keyword
    // we will do this by having the same setter names as the old search
    // controller for these search types, but we will stuff them in a
    // searchParams instance

    private Long projectId;
    private Long collectionId;

    private SearchParameters legacySearchParameters = new SearchParameters();

    // SearchParams.toQueryGroup only returns 'dehydrated' query parts. after
    // the search they will (potentially) be hydrated.
    // let's hang on to that state for the search phrase

    // support for "explore" requests
    private boolean explore = false;
    private String letter;

    private boolean collectionSearchBoxVisible = false;

    private AdvancedSearchQueryObject asqo = new AdvancedSearchQueryObject();

    /**
     * There are certain types of requests that require special processing
     * before we execute our search. For example: results?id=5
     * results?resourceTypes=DOCUMENT
     * results?query=olsend+standard+book+of+british+birds
     * 
     * For these requests we translate the provided arguments into a data
     * structure that can be understood by "new search".
     * 
     * @return true if this method translated a legacy search, false if this is
     *         not a legacy search
     */
    protected boolean processLegacySearchParameters() {
        // assumption: it's okay to wipe out the groups[] if we detect a legacy
        // request, and that you can't combine two different types (for
        // example: an id search combined with a uncontrolledCultureKeyword
        // search on the same querystring)

        setResourceTypes(cleanupFacetOptions(getResourceTypes()));
        setIntegratableOptions(cleanupFacetOptions(getIntegratableOptions()));
        // reset legacy resourceType for modern objectType
        for (ResourceType rt : getResourceTypes()) {
            getObjectTypes().add(ObjectType.from(rt));
        }
        getResourceTypes().clear();

        // legacy search by id?
        if (PersistableUtils.isNotNullOrTransient(getId())) {
            getLogger().trace("legacy api:  tdar id");
            groups.clear();
            groups.add(new SearchParameters());
            getFirstGroup().getResourceIds().add(getId());
            getResourceTypes().clear();
            getObjectTypes().clear();
            return true;
        }

        LatitudeLongitudeBox latLong = getParsedLatLongBox();
        if (latLong != null) {
            setMap(latLong);
        }

        LatitudeLongitudeBox ll = getMap();
        if ((ll == null) || !ll.isInitializedAndValid()) {
            if (CollectionUtils.isNotEmpty(getGroups()) && getGroups().get(0) != null
                    && CollectionUtils.isNotEmpty(getFirstGroup().getLatitudeLongitudeBoxes())) {
                ll = getFirstGroup().getLatitudeLongitudeBoxes().get(0);
            }
        }

        // legacy search by keyword
        // at the time of this writing the view layer only created links for
        // culture, site type, and siteName keywords. everything else
        // was rendered as a ?query= search
        if (!getSiteNameKeywords().isEmpty() || !getUncontrolledCultureKeywords().isEmpty()
                || !getUncontrolledMaterialKeywords().isEmpty() || !getUncontrolledSiteTypeKeywords().isEmpty()
                || !getGeographicKeywords().isEmpty()) {
            getLogger().trace("legacy api: uncontrolled keyword");
            groups.clear();
            setLegacyFieldtypes(SearchFieldType.FFK_SITE, getSiteNameKeywords());
            setLegacyFieldtypes(SearchFieldType.FFK_CULTURAL, getUncontrolledCultureKeywords());
            setLegacyFieldtypes(SearchFieldType.FFK_MATERIAL, getUncontrolledMaterialKeywords());
            setLegacyFieldtypes(SearchFieldType.FFK_SITE_TYPE, getUncontrolledSiteTypeKeywords());
            setLegacyFieldtypes(SearchFieldType.FFK_GEOGRAPHIC, getGeographicKeywords());
            groups.add(legacySearchParameters);
            return true;
        }

        return false;
    }

    private LatitudeLongitudeBox getParsedLatLongBox() {
        if (StringUtils.isNotBlank(getLatLongBox())) {
            String[] latLong = StringUtils.split(getLatLongBox(), ",");
            if ((latLong == null) || (latLong.length < 4)) {
                return null;
            }
            for (String num : latLong) {
                if (!NumberUtils.isNumber(num)) {
                    return null;
                }
            }

            LatitudeLongitudeBox box = new LatitudeLongitudeBox();
            box.setMinx(Double.parseDouble(latLong[0]));
            box.setMiny(Double.parseDouble(latLong[1]));
            box.setMaxx(Double.parseDouble(latLong[2]));
            box.setMaxy(Double.parseDouble(latLong[3]));
            return box;
        }
        return null;
    }

    private String advancedSearch() throws TdarActionException, SolrServerException, IOException {
        prepareAdvancedSearchQueryObject();

        try {
            resourceSearchService.buildAdvancedSearch(getAsqo(), getAuthenticatedUser(), this, this);
            addActionMessages();
            updateDisplayOrientationBasedOnSearchResults();
        } catch (SearchPaginationException spe) {
            getLogger().debug("pagination issue: {}", spe.getMessage());
            throw new TdarActionException(StatusCode.NOT_FOUND, TdarActionSupport.NOT_FOUND,
                    TdarActionSupport.NOT_FOUND);
        } catch (TdarRecoverableRuntimeException | SearchException tdre) {
            getLogger().warn("search parse exception: {}", tdre.getMessage());
            addActionError(tdre.getMessage());
        }
        bookmarkedResourceService.applyTransientBookmarked(getResults(), getAuthenticatedUser());

        if (getActionErrors().isEmpty()) {
            return SUCCESS;
        } else {
            return INPUT;
        }
    }

    protected void prepareAdvancedSearchQueryObject() {
        determineSearchTitle();
        setMode("SEARCH");
        // beforeSearch();

        processCollectionProjectLimit();
        getAsqo().setExplore(explore);
        getAsqo().setAllGeneralQueryFields(getAllGeneralQueryFields());
        getAsqo().setQuery(query);
        getAsqo().getSearchParameters().addAll(groups);
        getAsqo().setReservedParams(getReservedSearchParameters());
    }

    private void addActionMessages() {
        for (SearchParameters sp : groups) {
            for (String msg : sp.getActionMessages()) {
                getLogger().debug("adding actionMessage:{}", msg);
                addActionMessage(msg);
            }
        }
        for (String msg : getReservedSearchParameters().getActionMessages()) {
            getLogger().debug("adding actionMessage:{}", msg);
            addActionMessage(msg);
        }
    }

    protected void updateDisplayOrientationBasedOnSearchResults() {
    }

    private boolean processedLimits = false;

    public void processCollectionProjectLimit() {
        if (StringUtils.isNotBlank(query) && !resetSearch) {
            SearchParameters terms = new SearchParameters();
            terms.setOperator(Operator.AND);
            terms.getAllFields().add(query);
            terms.getFieldTypes().add(SearchFieldType.ALL_FIELDS);
            groups.add(terms);
        }

        if (processedLimits) {
            return;
        }

        processedLimits = true;

        // contextual search: resource collection
        if (PersistableUtils.isNotNullOrTransient(collectionId)) {
            SearchParameters terms_ = new SearchParameters();
            getLogger().debug("contextual search: collection {}", collectionId);
            ResourceCollection rc = getGenericService().find(ResourceCollection.class, collectionId);
            terms_.getFieldTypes().add(0, SearchFieldType.COLLECTION);
            terms_.getCollections().add((ResourceCollection) rc);
            terms_.getAllFields().add(0, null);
            groups.add(terms_);

            // contextual search: project
        } else if (PersistableUtils.isNotNullOrTransient(projectId)) {
            SearchParameters terms_ = new SearchParameters();
            getLogger().debug("contextual search: project {}", projectId);
            Project project = getGenericService().find(Project.class, projectId);
            terms_.getFieldTypes().add(0, SearchFieldType.PROJECT);
            terms_.getProjects().add(project);
            terms_.getAllFields().add(0, null);
            groups.add(terms_);
        }

    }

    @DoNotObfuscate(reason = "user submitted map")
    public LatitudeLongitudeBox getMap() {
        if (CollectionUtils.isNotEmpty(getReservedSearchParameters().getLatitudeLongitudeBoxes())) {
            return getReservedSearchParameters().getLatitudeLongitudeBoxes().get(0);
        }
        return null;
    }

    public void setMap(LatitudeLongitudeBox box) {
        getReservedSearchParameters().getLatitudeLongitudeBoxes().clear();
        getReservedSearchParameters().getLatitudeLongitudeBoxes().add(box);
    }

    public List<ResourceCreatorRole> getAllResourceCreatorRoles() {
        ArrayList<ResourceCreatorRole> roles = new ArrayList<ResourceCreatorRole>();
        roles.addAll(ResourceCreatorRole.getAll());
        roles.addAll(ResourceCreatorRole.getOtherRoles());
        return roles;
    }

    public List<SortOption> getSortOptions() {
        return sortOptions;
    }

    public void setSortOptions(List<SortOption> sortOptions) {
        this.sortOptions = sortOptions;
    }

    private Keyword exploreKeyword;

    private boolean resetSearch = false;

    public List<SearchParameters> getGroups() {
        return groups;
    }

    public List<SearchParameters> getG() {
        return groups;
    }

    public String getRssUrl() {
        StringBuilder urlBuilder = new StringBuilder();
        if (getServletRequest() != null) {
            urlBuilder.append(UrlService.getBaseUrl()).append(getServletRequest().getContextPath()).append(SEARCH_RSS)
                    .append("?").append(getServletRequest().getQueryString());
        }
        return urlBuilder.toString();

    }

    public Integer getMaxDownloadRecords() {
        return TdarConfiguration.getInstance().getSearchExcelExportRecordMax();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSearchSubtitle() {
        return getSearchTitle();
    }

    // TODO: support multiple groups

    public String getSearchPhrase() {
        StringBuilder sb = new StringBuilder();
        String searchingFor = getAsqo().getSearchPhrase();
        if (groups.isEmpty() || StringUtils.isBlank(searchingFor)) {
            sb.append(getText("advancedSearchController.showing_all_resources"));
        } else {
            sb.append(searchingFor);
        }
        // THIS SHOULD BE LESS BRITTLE THAN CALLING isEmpty()
        String narrowedBy = getAsqo().getRefinedBy();
        if ((narrowedBy != null) && StringUtils.isNotBlank(narrowedBy.trim())) {
            sb.append(" ").append(getText("advancedSearchController.narrowed_by"));
            sb.append(narrowedBy);
        }
        return sb.toString();
    }

    public String getSearchPhraseHtml() {
        return StringEscapeUtils.escapeHtml4(getSearchPhrase());
    }

    public void setRawQuery(String rawQuery) {
        throw new NotImplementedException(getText("advancedSearchController.admin_not_implemented"));
    }

    // alias for faceted search.
    public void setDocumentType(DocumentType doctype) {
        if (doctype == null) {
            return;
        }
        getReservedSearchParameters().getDocumentTypes().clear();
        getReservedSearchParameters().getDocumentTypes().add(doctype);
    }

    // when translating legacysearch, we need to set the field types so that the
    // 'refine your search' feature works
    private void setLegacyFieldtypes(SearchFieldType fieldType, List<?> list) {
        if (list.size() == 0) {
            return;
        }
        legacySearchParameters.getFieldTypes().clear();
        for (int i = 0; i < list.size(); i++) {
            legacySearchParameters.getFieldTypes().add(fieldType);
        }
    }

    // legacy keyword lookup support
    public void setSiteNameKeywords(List<String> kwds) {
        legacySearchParameters.setSiteNames(kwds);
    }

    // legacy keyword lookup support
    public void setUncontrolledSiteTypeKeywords(List<String> kwds) {
        legacySearchParameters.setUncontrolledSiteTypes(kwds);
    }

    // legacy keyword lookup support
    public void setUncontrolledCultureKeywords(List<String> kwds) {
        legacySearchParameters.setUncontrolledCultureKeywords(kwds);
    }

    // legacy keyword lookup support
    public void setMaterialCultureKeywords(List<String> kwds) {
        legacySearchParameters.setUncontrolledMaterialKeywords(kwds);
    }

    // setter's are required here
    public void setGeographicKeywords(List<String> kwds) {
        legacySearchParameters.setGeographicKeywords(kwds);
    }

    public DocumentType getDocumentType() {
        if (getReservedSearchParameters().getDocumentTypes().size() > 0) {
            return getReservedSearchParameters().getDocumentTypes().get(0);
        }
        return null;
    }

    // alias for faceted search.
    public void setFileAccess(ResourceAccessType fileAccess) {
        if (fileAccess == null) {
            return;
        }
        getReservedSearchParameters().getResourceAccessTypes().clear();
        getReservedSearchParameters().getResourceAccessTypes().add(fileAccess);
    }

    public ResourceAccessType getFileAccess() {
        if (getReservedSearchParameters().getResourceAccessTypes().size() > 0) {
            return getReservedSearchParameters().getResourceAccessTypes().get(0);
        }
        return null;
    }

    public List<SearchFieldType> getAllSearchFieldTypes() {
        return allSearchFieldTypes;
    }

    private void determineSearchTitle() {
        setSearchTitle(getText("advancedSearchController.title_all_records")); // if
                                                                               // all
                                                                               // else
                                                                               // fails.
        if (getId() != null) {
            setSearchTitle(getText("advancedSearchController.title_by_tdar_id")); // accurate
        } else if (StringUtils.isNotBlank(getQuery())) {
            setSearchTitle(getQuery());
        } else if (groups.size() > 0) {
            if (StringUtils.isNotBlank(getFirstGroup().getStartingLetter())) {
                setSearchTitle(getText("advancedSearchController.title_beginning_with_s",
                        Arrays.asList(getFirstGroup().getStartingLetter())));
                // FIXME: only supports 1
            } else if (CollectionUtils.isNotEmpty(getFirstGroup().getCreationDecades())) {
                setSearchTitle(getText("advancedSearchController.created_in_the_decade_s",
                        Arrays.asList(getFirstGroup().getCreationDecades().get(0))));
            }
        } else if (isKeywordSearch()) {
            setSearchTitle(getText("advancedSearchController.title_filtered_by_keyword"));
        }

    }

    private boolean isKeywordSearch() {
        // FIXME: not always false...
        return false;
    }

    // legacy keyword lookup support
    public List<String> getSiteNameKeywords() {
        return legacySearchParameters.getSiteNames();
    }

    // legacy keyword lookup support
    public List<String> getUncontrolledSiteTypeKeywords() {
        return legacySearchParameters.getUncontrolledSiteTypes();
    }

    // legacy keyword lookup support
    public List<String> getUncontrolledCultureKeywords() {
        return legacySearchParameters.getUncontrolledCultureKeywords();
    }

    // legacy keyword lookup support
    public List<String> getUncontrolledMaterialKeywords() {
        return legacySearchParameters.getUncontrolledMaterialKeywords();
    }

    // legach keyword lookup support
    public List<String> getGeographicKeywords() {
        return legacySearchParameters.getGeographicKeywords();
    }

    public String getLetter() {
        return letter;
    }

    public void setLetter(String letter) {
        this.letter = letter;
    }

    protected SearchParameters getFirstGroup() {
        if (groups.size() > 0) {
            return groups.get(0);
        }
        return null;
    }

    @Override
    public DisplayOrientation getOrientation() {
        return orientation;
    }

    public void setOrientation(DisplayOrientation orientation) {
        this.orientation = orientation;
    }

    @Override
    public boolean isHideFacetsAndSort() {
        return hideFacetsAndSort;
    }

    public void setHideFacetsAndSort(boolean hideFacetsAndSort) {
        this.hideFacetsAndSort = hideFacetsAndSort;
    }

    public String getLatLongBox() {
        return latLongBox;
    }

    public void setLatLongBox(String latLongBox) {
        this.latLongBox = latLongBox;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    /**
     * Indicates whether current search is "contextual search", i.e. is the
     * search implicitly filtered by project or filtered by collection.
     * 
     * @return
     */
    public boolean isContextualSearch() {
        return (collectionId != null) || (projectId != null);
    }

    public boolean isCollectionSearchBoxVisible() {
        return collectionSearchBoxVisible;
    }

    public String performResourceSearch() throws TdarActionException, SolrServerException, IOException {
        setLookupSource(LookupSource.RESOURCE);
        // we need this for tests to be able to change the projection model so
        // we get full objects
        if (getProjectionModel() == null) {
            setProjectionModel(ProjectionModel.LUCENE);
        }

        resetSearch = processLegacySearchParameters();

        return advancedSearch();

    }

    public List<String> getAllGeneralQueryFields() {
        List<String> allFields = new ArrayList<>();
        for (SearchParameters param : groups) {
            if (param == null) {
                continue;
            }
            for (String val : param.getAllFields()) {
                if (StringUtils.isNotBlank(val)) {
                    allFields.add(val);
                }
            }
        }
        if (StringUtils.isNotBlank(getQuery())) {
            allFields.add(getQuery());
        }
        return allFields;
    }

    public AdvancedSearchQueryObject getAsqo() {
        return asqo;
    }

    public void setAsqo(AdvancedSearchQueryObject asqo) {
        this.asqo = asqo;
    }

    public void setObjectTypes(List<ObjectType> objectTypes) {
        getReservedSearchParameters().setObjectTypes(objectTypes);
    }

    public List<ObjectType> getAllObjectTypes() {
        List<ObjectType> types = new ArrayList<>(Arrays.asList(ObjectType.values()));
        types.remove(ObjectType.ARCHIVE);
        types.remove(ObjectType.AUDIO);
        types.remove(ObjectType.VIDEO);
        return types;
    }

    public List<ObjectType> getObjectTypes() {
        return getReservedSearchParameters().getObjectTypes();
    }

}
