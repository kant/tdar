package org.tdar.web;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.web.collection.CollectionWebITCase;

public class DashboardWebITCase extends AbstractAdminAuthenticatedWebTestCase {

    private static final String MICHELLE_ELLIOT = "Michelle Elliott";

    @Test
    public void testRightsPage() {
        gotoPage("/dashboard");
        clickLinkOnPage("Export");
    }

    @Test
    public void testCollectionsAndRightsPage() {
        Long id = createResourceFromType(ResourceType.GEOSPATIAL, "test geospatial");
        clickLinkOnPage(CollectionWebITCase.PERMISSIONS);
        setInput("proxies[0].id", "121");
        setInput("proxies[1].id", "5349");
        setInput("proxies[0].permission", GeneralPermissions.MODIFY_RECORD.name());
        setInput("proxies[1].permission", GeneralPermissions.VIEW_ALL.name());
        setInput("proxies[0].displayName", MICHELLE_ELLIOT);
        setInput("proxies[1].displayName", "Joshua Watts");
        submitForm();
        logger.debug(getPageText());
        gotoPage("/dashboard");
        clickLinkOnPage("Collections");
        logger.debug(getPageText());
        assertTrue("page contains Michelle", getPageText().contains(MICHELLE_ELLIOT));
        clickElementWithId("p121");
    }

    @Test
    public void testBookmarksPage() {
        gotoPage("/dashboard");
        clickLinkOnPage("Bookmarks");
    }

    @Test
    public void testProfilePage() {
        gotoPage("/dashboard");
        clickLinkOnPage("My Profile");
    }
}