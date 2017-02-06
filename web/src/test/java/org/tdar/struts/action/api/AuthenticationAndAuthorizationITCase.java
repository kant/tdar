package org.tdar.struts.action.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.tdar.core.bean.AuthNotice;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.resource.Image;
import org.tdar.core.configuration.TdarConfiguration;
import org.tdar.core.dao.external.auth.AuthenticationProvider;
import org.tdar.core.dao.external.auth.CrowdRestDao;
import org.tdar.junit.MultipleTdarConfigurationRunner;
import org.tdar.junit.RunWithTdarConfiguration;
import org.tdar.struts.action.AbstractIntegrationControllerTestCase;
import org.tdar.struts.action.UserAgreementController;
import org.tdar.struts.action.account.UserAccountController;
import org.tdar.utils.PersistableUtils;

import com.opensymphony.xwork2.Action;

@RunWith(MultipleTdarConfigurationRunner.class)
@RunWithTdarConfiguration(runWith = { RunWithTdarConfiguration.TOS_CHANGE })
public class AuthenticationAndAuthorizationITCase extends AbstractIntegrationControllerTestCase {

    int tosLatestVersion = TdarConfiguration.getInstance().getTosLatestVersion();
    int contributorAgreementLatestVersion = TdarConfiguration.getInstance().getContributorAgreementLatestVersion();

    @Test
    @Rollback(false)
    public void testSatisfyPrerequisiteWithSession() throws Exception {
        // a contributor that hasn't signed on since updated TOS and creator agreement
        UserAgreementController controller = generateNewController(UserAgreementController.class);
        TdarUser user = getBasicUser();
        user.setContributorAgreementVersion(0);
        init(controller, user);
        assertThat(authenticationService.getUserRequirements(user), hasItem(AuthNotice.CONTRIBUTOR_AGREEMENT));
        List<AuthNotice> list = new ArrayList<>();
        list.add(AuthNotice.CONTRIBUTOR_AGREEMENT);
        list.add(AuthNotice.TOS_AGREEMENT);
        logger.info("userId: {}", controller.getSessionData().getTdarUserId());
        authenticationService.satisfyUserPrerequisites(controller.getSessionData(), list);
        assertThat(authenticationService.getUserRequirements(user), not(hasItem(AuthNotice.CONTRIBUTOR_AGREEMENT)));
        evictCache();
        setVerifyTransactionCallback(new TransactionCallback<Image>() {
            @Override
            public Image doInTransaction(TransactionStatus status) {
                TdarUser user = getBasicUser();
                assertThat(authenticationService.getUserRequirements(user), not(hasItem(AuthNotice.CONTRIBUTOR_AGREEMENT)));
                user.setContributorAgreementVersion(0);
                genericService.saveOrUpdate(user);
                return null;

            }
        });

    }

    @Test
    @Rollback
    public void testCrowdDisconnected() {
        // Create a user ... replace crowd witha "broken crowd" and then
        TdarUser person = new TdarUser("Thomas", "Angell", "tangell@pvd.state.ri.us");
        person.setUsername(person.getEmail());
        person.setContributor(true);

        AuthenticationProvider oldProvider = authenticationService.getProvider();
        authenticationService.getAuthenticationProvider().deleteUser(person);
        Properties crowdProperties = new Properties();
        crowdProperties.put("application.name", "tdar.test");
        crowdProperties.put("application.password", "tdar.test");
        crowdProperties.put("application.login.url", "http://localhost/crowd");
        crowdProperties.put("crowd.server.url", "http://localhost/crowd");

        authenticationService.setProvider(new CrowdRestDao(crowdProperties));

        String password = "super.secret";
        UserAccountController controller = generateNewInitializedController(UserAccountController.class);

        // create account, making sure the controller knows we're legit.
        controller.getH().setTimeCheck(System.currentTimeMillis() - 10000);
        controller.getRegistration().setRequestingContributorAccess(true);
        controller.getRegistration().setPassword(password);
        controller.getRegistration().setConfirmPassword(password);
        controller.getRegistration().setConfirmEmail(person.getEmail());
        controller.getRegistration().setPerson(person);
        controller.getRegistration().setAcceptTermsOfUse(true);
        controller.setServletRequest(getServletPostRequest());
        controller.setServletResponse(getServletResponse());
        String execute = null;
        setIgnoreActionErrors(true);
        try {
            controller.validate();
            // technically this is more appropriate -- only call create if validate passes
            if (CollectionUtils.isEmpty(controller.getActionErrors())) {
                execute = controller.create();
            } else {
                logger.error("errors: {} ", controller.getActionErrors());
            }
        } catch (Exception e) {
            logger.error("{}", e);
        } finally {
            authenticationService.setProvider(oldProvider);
        }
        logger.info("errors: {}", controller.getActionErrors());
        assertEquals("result is not input :" + execute, execute, Action.INPUT);
        logger.info("person:{}", person);
        assertTrue("person should not have an id", PersistableUtils.isTransient(person));
    }

    private Properties getCrowdProperties() {
        Properties crowdProperties = new Properties();
//        crowdProperties.put("application.name", "tdar.test");
//        crowdProperties.put("application.password", "tdar.test");
//        crowdProperties.put("application.login.url", "http://localhost/crowd");
//        crowdProperties.put("crowd.server.url", "http://localhost/crowd");
        try {
            crowdProperties.load(new FileReader(new File("src/test/resources/crowd.properties")));
        } catch (IOException e) {
            logger.error("couldn't load properties", e);
        }

        return crowdProperties;
    }

    @Test
    public void testUserJsonConversion() throws IOException {
        CrowdRestDao dao  = new CrowdRestDao(getCrowdProperties());

        TdarUser user = new TdarUser();
        user.setUsername("foobar");
        user.setFirstName("jon");
        user.setLastName("dow");
        user.setEmail("jdoe123@example.com");

        String json = dao.getUserJson(user);
        assertThat( json, is( not( nullValue())));
        assertThat( json, containsString("first-name"));
        assertThat( json, containsString(user.getEmail()));
    }

    @Test
    @Ignore("This test *will* make updates to the tdar.test crowd database.  You should only run it manually until we create an undo-able version of this test")
    public void testCrowdRestUserUpdate() {
        CrowdRestDao dao  = new CrowdRestDao(getCrowdProperties());
        TdarUser user = getUser();
        String originalEmail = user.getEmail();
        user.setEmail("testcrowduserupdate@example.com");  //original email for test user should be test@tdar.org
        logger.debug("original email address was:{}", originalEmail);
        dao.updateUserInformation(user);
    }

    @Test
    public void testCrowdRestUserUpdate2() {
        CrowdRestDao dao  = new CrowdRestDao(getCrowdProperties());
        TdarUser user = getUser();
        String originalEmail = user.getEmail();
        user.setEmail("testcrowduserupdate@example.com");  //original email for test user should be test@tdar.org
        logger.debug("original email address was:{}", originalEmail);
        dao.updateUserInformation2(user);
    }

}
