package org.tdar.struts.action;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Actions;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.resource.Project;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.service.RssService;
import org.tdar.core.service.collection.ResourceCollectionService;
import org.tdar.filestore.FilestoreObjectType;
import org.tdar.filestore.VersionType;
import org.tdar.struts.interceptor.annotation.HttpOnlyIfUnauthenticated;
import org.tdar.struts.interceptor.annotation.HttpsOnly;
import org.tdar.struts_base.action.TdarActionSupport;
import org.tdar.utils.PersistableUtils;
import org.tdar.web.service.HomepageDetails;
import org.tdar.web.service.HomepageService;

import com.rometools.rome.feed.synd.SyndEntry;

/**
 * $Id$
 * 
 * <p>
 * Action for the root namespace.
 * 
 * 
 * @author <a href='mailto:Allen.Lee@asu.edu'>Allen Lee</a>
 * @version $Rev$
 */
@Namespace("/")
@ParentPackage("default")
@Component
@Scope("prototype")
@HttpsOnly
public class IndexAction extends AbstractAuthenticatableAction {

    private static final String PAGE_NOT_FOUND = "page-not-found";

    private static final long serialVersionUID = -4095866074424122972L;

    private Project featuredProject;

    private List<Resource> featuredResources = new ArrayList<>();

    private ResourceCollection featuredCollection;

    private String sitemapFile = "sitemap_index.xml";

    @Autowired
    private RssService rssService;

    @Autowired
    private HomepageService homepageService;
    @Autowired
    private ResourceCollectionService resourceCollectionService;

    private List<SyndEntry> rssEntries;

    private HomepageDetails homepageGraphs;

    private boolean homepage = true;

    @Actions(value = {
            @Action(value = "", results = { @Result(name = SUCCESS, location = "about.ftl") }),
            @Action(value = "about", results = { @Result(name = SUCCESS, location = "about.ftl") }),

    })
    @SkipValidation
    @HttpOnlyIfUnauthenticated
    public String about() {
        setHomepageGraphs(homepageService.getHomepageGraphs(getAuthenticatedUser(), null, isBot(), this));
        featuredResources = new ArrayList<>(homepageService.featuredItems(getAuthenticatedUser()));
        featuredResources.forEach(r -> {
            if (r.getFirstLatitudeLongitudeBox() != null) {
                r.getFirstLatitudeLongitudeBox().obfuscateAll();
            }
        });
        try {
            setFeaturedCollection(resourceCollectionService.getRandomFeaturedCollection());
        } catch (Exception e) {
            getLogger().error("exception in setting up homepage: {}", e, e);
        }
        try {
            setRssEntries(rssService.parseFeed(new URL(getTdarConfiguration().getNewsRssFeed())));
        } catch (Exception e) {
            getLogger().warn("RssParsingException happened", e);
        }
        return SUCCESS;
    }

    @Action(value = PAGE_NOT_FOUND, results = { @Result(name = PAGE_NOT_FOUND, type = TdarActionSupport.FREEMARKERHTTP,
            location = "/WEB-INF/content/errors/page-not-found.ftl", params = { "status", "404" }) })
    public String execute() {
        homepage = false;
        return PAGE_NOT_FOUND;
    }

    public Project getFeaturedProject() {
        return featuredProject;
    }

    public void setFeaturedProject(Project featuredProject) {
        this.featuredProject = featuredProject;
    }

    public List<Resource> getFeaturedResources() {
        return featuredResources;
    }

    public void setFeaturedResources(List<Resource> featuredResources) {
        this.featuredResources = featuredResources;
    }

    public List<SyndEntry> getRssEntries() {
        return rssEntries;
    }

    public void setRssEntries(List<SyndEntry> rssEntries) {
        this.rssEntries = rssEntries;
    }

    public String getSitemapFile() {
        return sitemapFile;
    }

    public void setSitemapFile(String sitemapFile) {
        this.sitemapFile = sitemapFile;
    }

    public ResourceCollection getFeaturedCollection() {
        return featuredCollection;
    }

    public void setFeaturedCollection(ResourceCollection featuredCollection) {
        this.featuredCollection = featuredCollection;
    }

    public boolean isLogoAvailable() {
        if (PersistableUtils.isNullOrTransient(getFeaturedCollection())) {
            return false;
        }
        return checkLogoAvailable(FilestoreObjectType.COLLECTION, getFeaturedCollection().getId(), VersionType.WEB_SMALL);
    }

    @Override
    public boolean isNavSearchBoxVisible() {
        return false;
    }

    public boolean isHomepage() {
        return homepage;
    }

    @Override
    public boolean isSubnavEnabled() {
        return false;
    }

    public HomepageDetails getHomepageGraphs() {
        return homepageGraphs;
    }

    public void setHomepageGraphs(HomepageDetails homepageGraphs) {
        this.homepageGraphs = homepageGraphs;
    }
}
