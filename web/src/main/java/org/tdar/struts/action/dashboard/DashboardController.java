package org.tdar.struts.action.dashboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Actions;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.SortOption;
import org.tdar.core.bean.billing.BillingAccount;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.notification.UserNotification;
import org.tdar.core.bean.resource.InformationResource;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.bean.resource.file.FileStatus;
import org.tdar.core.dao.external.auth.InternalTdarRights;
import org.tdar.core.dao.resource.ResourceTypeStatusInfo;
import org.tdar.core.service.EntityService;
import org.tdar.core.service.SerializationService;
import org.tdar.core.service.UserNotificationService;
import org.tdar.core.service.billing.BillingAccountService;
import org.tdar.core.service.collection.ResourceCollectionService;
import org.tdar.core.service.external.AuthorizationService;
import org.tdar.core.service.resource.InformationResourceFileService;
import org.tdar.core.service.resource.ProjectService;
import org.tdar.core.service.resource.ResourceService;
import org.tdar.search.bean.AdvancedSearchQueryObject;
import org.tdar.search.exception.SearchException;
import org.tdar.search.query.QueryFieldNames;
import org.tdar.search.query.SearchResult;
import org.tdar.search.query.facet.Facet;
import org.tdar.search.query.facet.FacetWrapper;
import org.tdar.search.query.facet.FacetedResultHandler;
import org.tdar.search.service.query.ResourceSearchService;
import org.tdar.search.service.query.SearchService;
import org.tdar.struts.action.AbstractAuthenticatableAction;
import org.tdar.struts.action.DataTableResourceDisplay;
import org.tdar.struts_base.interceptor.annotation.DoNotObfuscate;
import org.tdar.utils.PersistableUtils;
import org.tdar.utils.TitleSortComparator;

/**
 * $Id$
 * 
 * Manages requests to create/delete/edit a Project and its associated metadata
 * (including Datasets, etc).
 * 
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Revision$
 */
@ParentPackage("secured")
@Namespace("/")
@Component
@Scope("prototype")
public class DashboardController extends AbstractAuthenticatableAction implements DataTableResourceDisplay {

    private static final long serialVersionUID = -2959809512424441740L;
    private List<Resource> recentlyEditedResources = new ArrayList<Resource>();
    private List<Project> emptyProjects = new ArrayList<Project>();
    private List<Resource> bookmarkedResources;
    private Integer activeResourceCount = 0;
    private int maxRecentResources = 5;
    private List<Resource> filteredFullUserProjects;
    private List<Resource> fullUserProjects;
    private List<ResourceCollection> allResourceCollections = new ArrayList<>();
    private Set<BillingAccount> overdrawnAccounts = new HashSet<BillingAccount>();
    private List<InformationResource> resourcesWithErrors;

    @Autowired
    private transient AuthorizationService authorizationService;

    @Autowired
    private transient ResourceCollectionService resourceCollectionService;
    @Autowired
    private transient ResourceSearchService resourceSearchService;
    @Autowired
    private transient ProjectService projectService;
    @Autowired
    private transient InformationResourceFileService informationResourceFileService;
    @Autowired
    private transient BillingAccountService accountService;
    @Autowired
    private transient SearchService<?> searchService;
    @Autowired
    private transient EntityService entityService;
    @Autowired
    private transient ResourceService resourceService;
    @Autowired
    private transient SerializationService serializationService;
    @Autowired
    private transient UserNotificationService userNotificationService;

    private List<Project> allSubmittedProjects;
    private List<Resource> featuredResources = new ArrayList<Resource>();
    private List<Resource> recentResources = new ArrayList<Resource>();
    private List<UserNotification> currentNotifications;
    private String statusData;
    private String resourceTypeData;

    @Override
    public void validate() {
        if (PersistableUtils.isNullOrTransient(getAuthenticatedUser())) {
            addActionError(getText("dashboardController.user_must_login"));
        }
        super.validate();
    }

    private void setupRecentResources() throws SolrServerException, IOException {
        int count = 10;
        try {
            getFeaturedResources().addAll(searchService.findMostRecentResources(count, getAuthenticatedUser(), this));
        } catch (SearchException pe) {
            getLogger().debug("parse exception", pe);
        }

    }

    @Override
    @Actions({
            // fixme: there's a less-verbose way to define these three mappings.
            @Action(value = "dashboard", results = { @Result(name = SUCCESS, location = "dashboard/dashboard.ftl") }),
            @Action(value = "dashboard/", results = { @Result(name = SUCCESS, location = "dashboard/dashboard.ftl") }),
            @Action(value = "dashboard/resources", results = { @Result(name = SUCCESS, location = "dashboard/dashboard.ftl") }) })
    public String execute() throws SolrServerException, IOException {
        getLogger().trace("recent resources");
        setupRecentResources();
        getLogger().trace("notifications");
        setCurrentNotifications(userNotificationService.getCurrentNotifications(getAuthenticatedUser()));
        getLogger().trace("find recently edited resources");
        setRecentlyEditedResources(
                projectService.findRecentlyEditedResources(getAuthenticatedUser(), maxRecentResources));
        getLogger().trace("find empty projects");
        setEmptyProjects(projectService.findEmptyProjects(getAuthenticatedUser()));
        getLogger().trace("trees");
        setupResourceCollectionTreesForDashboard();
        getLogger().trace("errors");
        setResourcesWithErrors(informationResourceFileService.findInformationResourcesWithFileStatus(
                getAuthenticatedUser(), Arrays.asList(Status.ACTIVE, Status.DRAFT),
                Arrays.asList(FileStatus.PROCESSING_ERROR, FileStatus.PROCESSING_WARNING)));
        getLogger().trace("billing accounts");
        for (BillingAccount account : accountService.listAvailableAccountsForUser(getAuthenticatedUser(), Status.ACTIVE, Status.FLAGGED_ACCOUNT_BALANCE)) {
            if (account.getStatus() == Status.FLAGGED_ACCOUNT_BALANCE) {
                overdrawnAccounts.add(account);
            }
        }

        getLogger().trace("projects");
        prepareProjectStuff();
        getLogger().trace("counts for graphs");
        initCounts();
        return SUCCESS;
    }

    private void setupResourceCollectionTreesForDashboard() {
        getLogger().trace("parent/ owner collections");
        TreeSet<ResourceCollection> colls = new TreeSet<>(new TitleSortComparator());
        for (ResourceCollection rc : resourceCollectionService.findParentOwnerCollections(getAuthenticatedUser())) {
            colls.add(rc);
        }

        allResourceCollections.addAll(colls);
    }

    /**
     * @param activeResourceCount
     *            the activeResourceCount to set
     */
    public void setActiveResourceCount(Integer activeResourceCount) {
        this.activeResourceCount = activeResourceCount;
    }

    /**
     * @return the activeResourceCount
     */
    public Integer getActiveResourceCount() {
        return activeResourceCount;
    }

    private void initCounts() {
        AdvancedSearchQueryObject advancedSearchQueryObject = new AdvancedSearchQueryObject();
        advancedSearchQueryObject.getReservedParams().setUseSubmitterContext(true);
        advancedSearchQueryObject.getReservedParams().setDasboardQuery(true);
        advancedSearchQueryObject.getReservedParams()
                .setStatuses(new ArrayList<>(Arrays.asList(Status.ACTIVE, Status.DRAFT, Status.FLAGGED, Status.FLAGGED_ACCOUNT_BALANCE)));
        SearchResult<Resource> request = new SearchResult<>();
        request.setFacetWrapper(new FacetWrapper());
        request.setRecordsPerPage(0);
        request.getFacetWrapper().facetBy(QueryFieldNames.RESOURCE_TYPE, ResourceType.class);
        request.getFacetWrapper().facetBy(QueryFieldNames.STATUS, Status.class);

        ResourceTypeStatusInfo info = new ResourceTypeStatusInfo();
        try {
            FacetedResultHandler<Resource> result = (FacetedResultHandler<Resource>) resourceSearchService.buildAdvancedSearch(advancedSearchQueryObject,
                    getAuthenticatedUser(), request, this);
            activeResourceCount = result.getTotalRecords();
            FacetWrapper facetWrapper = result.getFacetWrapper();
            if (facetWrapper != null && MapUtils.isNotEmpty(facetWrapper.getFacetResults())) {
                Map<String, List<Facet>> facetResults = facetWrapper.getFacetResults();
                if (CollectionUtils.isNotEmpty(facetResults.get(QueryFieldNames.RESOURCE_TYPE))) {
                    facetResults.get(QueryFieldNames.RESOURCE_TYPE).forEach(facet -> {
                        info.getResourceMap().put(ResourceType.valueOf(facet.getRaw()), facet.getCount().intValue());
                    });
                }
                if (CollectionUtils.isNotEmpty(facetResults.get(QueryFieldNames.STATUS))) {
                    facetWrapper.getFacetResults().get(QueryFieldNames.STATUS).forEach(facet -> {
                        info.getStatusMap().put(Status.valueOf(facet.getRaw()), facet.getCount().intValue());
                    });
                }
            }
        } catch (SearchException | IOException e1) {
            getLogger().error("issue generating map search", e1);
        }

        try {
            setStatusData(serializationService.convertToJson(info.getStatusData()));
            setResourceTypeData(serializationService.convertToJson(info.getResourceTypeData()));
        } catch (IOException e) {
            getLogger().error("e", e);
        }

    }

    /**
     * @param recentlyEditedResources
     *            the recentlyEditedResources to set
     */
    public void setRecentlyEditedResources(List<Resource> recentlyEditedResources) {
        this.recentlyEditedResources = recentlyEditedResources;
    }

    /**
     * @return the recentlyEditedResources
     */
    public List<Resource> getRecentlyEditedResources() {
        return recentlyEditedResources;
    }

    /**
     * @param emptyProjects
     *            the emptyProjects to set
     */
    public void setEmptyProjects(List<Project> emptyProjects) {
        this.emptyProjects = emptyProjects;
    }

    /**
     * @return the emptyProjects
     */
    public List<Project> getEmptyProjects() {
        return emptyProjects;
    }

    public List<Resource> getBookmarkedResources() {
        return bookmarkedResources;
    }

    public void setBookmarkedResource(List<Resource> bookmarks) {
        this.bookmarkedResources = bookmarks;
    }

    public List<Project> getAllSubmittedProjects() {
        return allSubmittedProjects;
    }

    public List<Resource> getFullUserProjects() {
        return fullUserProjects;
    }

    public void setFullUserProjects(List<Resource> projects) {
        fullUserProjects = projects;
    }

    public void setAllSubmittedProjects(List<Project> projects) {
        allSubmittedProjects = projects;
    }

    public void setFilteredFullUserProjects(List<Resource> projects) {
        filteredFullUserProjects = projects;
    }

    public void setEditableProjects(Set<Resource> projects) {
        editableProjects = projects;
    }

    public List<Resource> getFilteredFullUserProjects() {
        return filteredFullUserProjects;
    }

    private Set<Resource> editableProjects = new HashSet<>();

    private void prepareProjectStuff() {
        boolean canEditAnything = authorizationService.can(InternalTdarRights.EDIT_ANYTHING, getAuthenticatedUser());
        editableProjects = new TreeSet<Resource>(
                projectService.findSparseTitleIdProjectListByPerson(getAuthenticatedUser(), canEditAnything));

        fullUserProjects = new ArrayList<Resource>(editableProjects);
        getLogger().trace("find all submitted projects");
        allSubmittedProjects = projectService.findBySubmitter(getAuthenticatedUser());
        Collections.sort(allSubmittedProjects);
        getLogger().trace("remove dups");
        fullUserProjects.removeAll(getAllSubmittedProjects());
        filteredFullUserProjects = new ArrayList<Resource>(getFullUserProjects());
    }

    public Set<Resource> getEditableProjects() {
        return editableProjects;
    }

    public void prepare() {
    }

    public List<Status> getStatuses() {
        return new ArrayList<Status>(authorizationService.getAllowedSearchStatuses(getAuthenticatedUser()));
    }

    public List<ResourceType> getResourceTypes() {
        return resourceService.getAllResourceTypes();
    }

    public List<SortOption> getResourceDatatableSortOptions() {
        return SortOption.getOptionsForContext(Resource.class);
    }

    @DoNotObfuscate(reason = "not needed / performance test")
    public List<ResourceCollection> getAllResourceCollections() {
        return allResourceCollections;
    }

    public void setAllResourceCollections(List<ResourceCollection> resourceCollections) {
        this.allResourceCollections = resourceCollections;
    }

    public Set<BillingAccount> getOverdrawnAccounts() {
        return overdrawnAccounts;
    }

    public void setOverdrawnAccounts(Set<BillingAccount> overdrawnAccounts) {
        this.overdrawnAccounts = overdrawnAccounts;
    }

    public List<InformationResource> getResourcesWithErrors() {
        return resourcesWithErrors;
    }

    public void setResourcesWithErrors(List<InformationResource> resourcesWithErrors) {
        this.resourcesWithErrors = resourcesWithErrors;
    }

    public List<Resource> getRecentResources() {
        return recentResources;
    }

    public void setRecentResources(List<Resource> recentResources) {
        this.recentResources = recentResources;
    }

    public List<Resource> getFeaturedResources() {
        return featuredResources;
    }

    public void setFeaturedResources(List<Resource> featuredResources) {
        this.featuredResources = featuredResources;
    }

    public List<UserNotification> getCurrentNotifications() {
        return currentNotifications;
    }

    public void setCurrentNotifications(List<UserNotification> currentNotifications) {
        this.currentNotifications = currentNotifications;
    }

    public String getResourceTypeData() {
        return resourceTypeData;
    }

    public void setResourceTypeData(String resourceTypeData) {
        this.resourceTypeData = resourceTypeData;
    }

    public String getStatusData() {
        return statusData;
    }

    public void setStatusData(String statusData) {
        this.statusData = statusData;
    }

    public List<Person> getUserSuggestions() {
        return entityService.findSimilarPeople(getAuthenticatedUser());
    }

    @Override
    public boolean isRightSidebar() {
        return false;
    }
}
