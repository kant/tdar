package org.tdar.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.entity.permissions.GeneralPermissions;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.Status;
import org.tdar.core.service.EntityService;
import org.tdar.core.service.GenericService;

public class CollectionWebITCase extends AbstractAdminAuthenticatedWebTestCase {

    @Autowired
    GenericService genericService;

    @Autowired
    EntityService entityService;

    @Test
    // crate a collection with some resources, then edit it by adding some authorized users and removing a few resources
    public void testCreateThenEditCollection() {
        assertNotNull(genericService);
        String name = "my fancy collection: " + System.currentTimeMillis();
        String desc = "description goes here: "+ System.currentTimeMillis();
        List<? extends Resource> someResources = getSomeResources();
        createTestCollection(name, desc, someResources);
        assertTextPresent(name);
        assertTextPresent(desc);
        logger.trace(getHtmlPage().asText());
        String currentUrlPath = getCurrentUrlPath();
        for (Resource resource : someResources) {
            if (resource.getStatus() == Status.ACTIVE || resource.getStatus() == Status.DRAFT) {
                assertTextPresent(resource.getTitle());
            }
        }

        // now go back to the edit page, add some users and remove some of the resources
        List<Person> registeredUsers = getSomeUsers();
        clickLinkWithText("edit");
        int i = 1; // start at row '2' of the authorized user list, leaving the first entry blank.
        for (Person user : registeredUsers) {
            if (StringUtils.containsIgnoreCase(user.getProperName(), "user"))
                continue;
            createUserWithPermissions(i, user,GeneralPermissions.VIEW_ALL);
            i++;
        }

        // remove the first 2 resources
        int removeCount = 2;
        Assert.assertTrue("this test needs at least 2 resources in the test DB", someResources.size() > removeCount);
        List<Resource> removedResources = new ArrayList<Resource>();
        for (i = 0; i < removeCount; i++) {
            htmlPage.getElementById("hrid" + someResources.get(i).getId()).remove();
            removedResources.add(someResources.remove(i));
        }

        submitForm();

        // we should be on the view page now
        logger.trace("now on page {}", getCurrentUrlPath());
        logger.trace("page contents: {}", getPageText());
        // assert all the added names are on the view page
        for (Person user : registeredUsers) {
            if (StringUtils.containsIgnoreCase(user.getProperName(), "user"))
                continue;
            assertTextPresent(user.getProperName()); // let's assume the view page uses tostring to format the user names.
        }

        // assert the removed resources are *not* present on the view page
        for (Resource resource : removedResources) {
            assertTextNotPresent(resource.getTitle());
        }

        logout();

        gotoPage(currentUrlPath);
        assertTextNotPresent("collection is not accessible");
    }

    // assign a parent collection, then go back to dashboard
    @Test
    public void testCreateChildCollection() {
        // get a shared collection id - we don't have one in init-db so just rerun the previous test
        testCreateThenEditCollection();
        // previous test logged us out
        loginAdmin();
        Long parentId = resourceCollectionService.findAllResourceCollections().iterator().next().getId();

        gotoPage("/collection/add");
        String name = "testCreateChildCollection";
        String desc = "lame child colllection";

        setInput("resourceCollection.name", name);
        setInput("resourceCollection.description", desc);
        setInput("parentId", "" + parentId);
        submitForm();
        assertTextPresentInPage(name);
        assertTextPresentInPage(desc);

        // now look for the collection on the dashboard (implicitly test encoding errors also)
        gotoPage("/dashboard");
        assertTextPresentInPage(name);
    }

    @Test
    public void testAssignNonUserToCollection() {
        // try to create a collection and assign it to a person that is not a registered user.
        gotoPage("/collection/add");

        // first lets start populating the person fields with a person that does not yet exist. tDAR should not create the person record on the fly, and
        // should not assign to the collection.
        String name = "my fancy collection";
        String desc = "description goes here";
        setInput("resourceCollection.name", name);
        setInput("resourceCollection.description", desc);

        List<? extends Resource> someResources = getSomeResources();
        for (int i = 0; i < someResources.size(); i++) {
            Resource resource = someResources.get(i);
            // FIXME: we don't set id's in the form this way but setInput() doesn't understand 'resources.id' syntax. fix it so that it can.
            String fieldName = "resources[" + i + "].id";
            String fieldValue = "" + resource.getId();
            logger.debug("setting  fieldName:{}\t value:{}", fieldName, fieldValue);
            createInput("hidden", "resources.id", fieldValue);
        }

        Person user = new Person("joe", "blow", "testAssignNonUserToCollection@mailinator.com");

        createInput("hidden", String.format(FMT_AUTHUSERS_ID, 1), ""); // leave the id blank
        createInput("text", String.format(FMT_AUTHUSERS_LASTNAME, 1), user.getLastName());
        createInput("text", String.format(FMT_AUTHUSERS_FIRSTNAME, 1), user.getFirstName());
        createInput("text", String.format(FMT_AUTHUSERS_EMAIL, 1), user.getEmail());
        createInput("text", String.format(FMT_AUTHUSERS_PERMISSION, 1), GeneralPermissions.VIEW_ALL.toString());

        submitForm();

        // assertTrue("we should  be on the INPUT page. current page: " + getCurrentUrlPath(), getCurrentUrlPath().contains("/collection/save.action"));

        Person person = entityService.findByEmail(user.getEmail());
        assertNull("person from form should not be persisted", person);
    }

    @Test
    public void testAssignNonUserToCollection2() {
        assertNotNull(genericService);
        gotoPage("/collection/add");
        String name = "my fancy collection";
        String desc = "description goes here";
        setInput("resourceCollection.name", name);
        setInput("resourceCollection.description", desc);

        List<? extends Resource> someResources = getSomeResources();

        for (int i = 0; i < someResources.size(); i++) {
            Resource resource = someResources.get(i);
            // FIXME: we don't set id's in the form this way but setInput() doesn't understand 'resources.id' syntax. fix it so that it can.
            String fieldName = "resources[" + i + "].id";
            String fieldValue = "" + resource.getId();
            logger.debug("setting  fieldName:{}\t value:{}", fieldName, fieldValue);
            createInput("hidden", "resources.id", fieldValue);
        }

        List<Person> nonUsers = getSomePeople();
        int i = 1; // start at row '2' of the authorized user list, leaving the first entry blank.
        for (Person person : nonUsers) {
            if (StringUtils.containsIgnoreCase(person.getProperName(), "user"))
                continue;
            createInput("hidden", String.format(FMT_AUTHUSERS_ID, i), person.getId());
            createInput("text", String.format(FMT_AUTHUSERS_LASTNAME, i), person.getLastName());
            createInput("text", String.format(FMT_AUTHUSERS_FIRSTNAME, i), person.getFirstName());
            if (StringUtils.isNotBlank(person.getEmail())) {
                createInput("text", String.format(FMT_AUTHUSERS_EMAIL, i), person.getEmail());
            } else {
                createInput("text", String.format(FMT_AUTHUSERS_EMAIL, i), "");
            }
            if (StringUtils.isNotBlank(person.getInstitutionName())) {
                createInput("text", String.format(FMT_AUTHUSERS_INSTITUTION, i), person.getInstitutionName());

            } else {
                createInput("text", String.format(FMT_AUTHUSERS_INSTITUTION, i), "");
            }
            createInput("text", String.format(FMT_AUTHUSERS_PERMISSION, i), GeneralPermissions.VIEW_ALL.toString());
            i++;
        }

        submitForm();

        assertFalse("expecting to be on the view page", getCurrentUrlPath().contains("/collection/add"));
        assertFalse("expecting to be on the view page", getCurrentUrlPath().contains("/collection/save.action"));

        assertTextPresent("my fancy collection");
        for (Person person : nonUsers) {
            if (StringUtils.containsIgnoreCase(person.getProperName(), "user"))
                continue;
            assertTextNotPresent(person.getLastName());
        }

    }
}
