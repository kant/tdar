package org.tdar.struts.action.browse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.keyword.CultureKeyword;
import org.tdar.core.bean.keyword.InvestigationType;
import org.tdar.core.bean.keyword.MaterialKeyword;
import org.tdar.core.bean.keyword.SiteTypeKeyword;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.cache.BrowseDecadeCountCache;
import org.tdar.core.cache.BrowseYearCountCache;
import org.tdar.core.service.GenericKeywordService;
import org.tdar.core.service.GenericService;
import org.tdar.core.service.SerializationService;
import org.tdar.core.service.resource.InformationResourceService;
import org.tdar.core.service.resource.ResourceService;
import org.tdar.search.exception.SearchException;
import org.tdar.search.service.query.SearchService;
import org.tdar.struts.action.AbstractAuthenticatableAction;
import org.tdar.struts.interceptor.annotation.HttpsOnly;
import org.tdar.web.service.HomepageDetails;
import org.tdar.web.service.HomepageService;

/**
 * $Id$
 * 
 * Controller for browsing resources.
 * 
 * 
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Rev$
 */
@Namespace("/browse")
@ParentPackage("default")
@Component
@Scope("prototype")
@HttpsOnly
public class ExploreController extends AbstractAuthenticatableAction {

    private static final long serialVersionUID = -2826087034735486222L;

    public static final String EXPLORE = "explore";

    private List<InvestigationType> investigationTypes = new ArrayList<InvestigationType>();
    private List<CultureKeyword> cultureKeywords = new ArrayList<CultureKeyword>();
    private List<SiteTypeKeyword> siteTypeKeywords = new ArrayList<SiteTypeKeyword>();
    private List<MaterialKeyword> materialTypes = new ArrayList<MaterialKeyword>();
    private List<String> alphabet = new ArrayList<String>(Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q",
            "R", "S", "T", "U", "V", "W", "X", "Y", "Z"));
    private List<BrowseYearCountCache> scholarData;
    private String timelineData;

    private List<Resource> featuredResources = new ArrayList<Resource>();
    private List<Resource> recentResources = new ArrayList<Resource>();
    private List<ResourceType> resourceTypes = ResourceType.activeValues();
    @Autowired
    private transient SerializationService serializationService;
    @Autowired
    private transient HomepageService homepageService;

    @Autowired
    private transient GenericKeywordService genericKeywordService;

    @Autowired
    private transient GenericService genericService;

    @Autowired
    private transient SearchService<Resource> searchService;

    @Autowired
    private transient ResourceService resourceService;

    @Autowired
    private transient InformationResourceService informationResourceService;

    private HomepageDetails homepageGraphs;

    @Action(EXPLORE)
    public String explore() throws IOException {
        setHomepageGraphs(homepageService.getHomepageGraphs(getAuthenticatedUser(), null, isBot(), this));

        setMaterialTypes(genericService.findAll(MaterialKeyword.class));
        setInvestigationTypes(genericService.findAll(InvestigationType.class));
        setCultureKeywords(genericKeywordService.findAllApproved(CultureKeyword.class));
        setSiteTypeKeywords(genericKeywordService.findAllApproved(SiteTypeKeyword.class));
        setupTimelineData();
        setScholarData(informationResourceService.findResourceCountsByYear());

        getFeaturedResources().addAll(resourceService.getWeeklyPopularResources());
        try {
            getRecentResources().addAll(searchService.findMostRecentResources(10, getAuthenticatedUser(), this));
        } catch (SearchException pe) {
            getLogger().debug("parse exception", pe);
        }
        return SUCCESS;
    }

    private void setupTimelineData() throws IOException {
        List<BrowseDecadeCountCache> findResourcesByDecade = new ArrayList<>(informationResourceService.findResourcesByDecade());
        Iterator<BrowseDecadeCountCache> iterator = findResourcesByDecade.iterator();
        while (iterator.hasNext()) {
            BrowseDecadeCountCache decade = iterator.next();
            if (decade.getKey() < 1 || decade.getKey() > DateTime.now().getYear()) {
                iterator.remove();
            }
        }
        setTimelineData(serializationService.convertToJson(findResourcesByDecade));
    }

    public List<SiteTypeKeyword> getSiteTypeKeywords() {
        return siteTypeKeywords;
    }

    public void setSiteTypeKeywords(List<SiteTypeKeyword> siteTypeKeywords) {
        this.siteTypeKeywords = siteTypeKeywords;
    }

    public List<CultureKeyword> getCultureKeywords() {
        return cultureKeywords;
    }

    public void setCultureKeywords(List<CultureKeyword> cultureKeywords) {
        this.cultureKeywords = cultureKeywords;
    }

    public List<InvestigationType> getInvestigationTypes() {
        return investigationTypes;
    }

    public void setInvestigationTypes(List<InvestigationType> investigationTypes) {
        this.investigationTypes = investigationTypes;
    }

    public List<MaterialKeyword> getMaterialTypes() {
        return materialTypes;
    }

    public void setMaterialTypes(List<MaterialKeyword> materialTypes) {
        this.materialTypes = materialTypes;
    }

    public List<String> getAlphabet() {
        return alphabet;
    }

    public void setAlphabet(List<String> alphabet) {
        this.alphabet = alphabet;
    }

    public String getTimelineData() {
        return timelineData;
    }

    public void setTimelineData(String list) {
        this.timelineData = list;
    }

    public List<BrowseYearCountCache> getScholarData() {
        return scholarData;
    }

    public void setScholarData(List<BrowseYearCountCache> scholarData) {
        this.scholarData = scholarData;
    }

    public List<Resource> getFeaturedResources() {
        return featuredResources;
    }

    public void setFeaturedResources(List<Resource> featuredResources) {
        this.featuredResources = featuredResources;
    }

    public List<Resource> getRecentResources() {
        return recentResources;
    }

    public void setRecentResources(List<Resource> recentResources) {
        this.recentResources = recentResources;
    }

    public HomepageDetails getHomepageGraphs() {
        return homepageGraphs;
    }

    public void setHomepageGraphs(HomepageDetails homepageGraphs) {
        this.homepageGraphs = homepageGraphs;
    }

    public List<ResourceType> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(List<ResourceType> resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

}
