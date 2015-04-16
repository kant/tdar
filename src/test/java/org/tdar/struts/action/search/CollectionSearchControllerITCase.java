package org.tdar.struts.action.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.collection.ResourceCollection;
import org.tdar.core.bean.entity.AuthorizedUser;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.core.bean.resource.Document;
import org.tdar.search.index.LookupSource;
import org.tdar.struts.action.AbstractControllerITCase;
import org.tdar.utils.MessageHelper;

@Transactional
public class CollectionSearchControllerITCase extends AbstractControllerITCase {

    @Autowired
    CollectionSearchAction controller;
    
    @Test
    @Rollback(true)
    public void testFindAllSearchPhrase() {
        controller = generateNewInitializedController(CollectionSearchAction.class);
        doSearch("");
        assertEquals(MessageHelper.getMessage("advancedSearchController.title_all_collections"), controller.getSearchSubtitle());
    }

    @Test
    @Rollback
    public void testSearchForPublicReosurceCollection() throws InstantiationException, IllegalAccessException {
        ResourceCollection collection = setupCollection(false, null);
        assertTrue(controller.getResults().contains(collection));
    }

    @Test
    @Rollback
    public void testSearchForPrivateCollectionAnonymous() throws InstantiationException, IllegalAccessException {
        ResourceCollection collection = setupCollection(true, null);
        assertFalse(controller.getResults().contains(collection));
    }

    @Test
    @Rollback
    public void testSearchForPrivateCollectionAsBasicUserWithRights() throws InstantiationException, IllegalAccessException {
        ResourceCollection collection = setupCollection(true, getBasicUser(), true);
        assertTrue(controller.getResults().contains(collection));
    }

    @Test
    @Rollback
    public void testSearchForPrivateCollectionAsBasicUserWithoutRights() throws InstantiationException, IllegalAccessException {
        ResourceCollection collection = setupCollection(true, getBasicUser());
        assertFalse(controller.getResults().contains(collection));
    }

    @Test
    @Rollback
    public void testSearchForPrivateCollectionAsAdmin() throws InstantiationException, IllegalAccessException {
        ResourceCollection collection = setupCollection(true, getAdminUser());
        assertTrue(controller.getResults().contains(collection));
    }

    private ResourceCollection setupCollection(boolean visible, TdarUser user) {
        return setupCollection(visible, user, false);
    }

    private ResourceCollection setupCollection(boolean visible, TdarUser user, boolean createAuthUser) {
        assertEquals(getUser(), getAdminUser());
        ResourceCollection collection = createAndSaveNewResourceCollection("Hohokam Archaeology along the Salt-Gila Aqueduct Central Arizona Project");
        Document doc = createAndSaveNewResource(Document.class);

        collection.setDescription("test");
        collection.setHidden(visible);
        collection.getResources().add(doc);
        genericService.saveOrUpdate(collection);
        if (createAuthUser) {
            AuthorizedUser authuser = new AuthorizedUser(user, GeneralPermissions.ADMINISTER_GROUP);
            collection.getAuthorizedUsers().add(authuser);
            genericService.saveOrUpdate(collection);
        }
        searchIndexService.index(collection);
        controller = generateNewController(CollectionSearchAction.class);
        init(controller, user);
        doSearch("Hohokam Archaeology");
        return collection;
    }

    protected void doSearch(String query) {
        controller.setQuery(query);
        AbstractSearchControllerITCase.doSearch(controller, LookupSource.COLLECTION);
        logger.info("search (" + controller.getQuery() + ") found: " + controller.getTotalRecords());
    }

    @Override
    protected void reindex() {
        searchIndexService.purgeAll();
        searchIndexService.indexAll(getAdminUser(), ResourceCollection.class);
    }

    @Override
    protected TdarUser getUser() {
        return getAdminUser();
    }
}
