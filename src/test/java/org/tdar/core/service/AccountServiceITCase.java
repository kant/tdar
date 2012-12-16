package org.tdar.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.tdar.core.bean.AbstractIntegrationTestCase;
import org.tdar.core.bean.billing.Account;
import org.tdar.core.bean.billing.AccountGroup;
import org.tdar.core.bean.billing.BillingActivity;
import org.tdar.core.bean.billing.BillingActivityModel;
import org.tdar.core.bean.billing.BillingItem;
import org.tdar.core.bean.billing.Invoice;
import org.tdar.core.bean.billing.ResourceEvaluator;
import org.tdar.core.bean.billing.Account.AccountAdditionStatus;
import org.tdar.core.bean.billing.Invoice.TransactionStatus;
import org.tdar.core.bean.entity.Person;
import org.tdar.core.bean.resource.Document;
import org.tdar.core.bean.resource.Resource;
import org.tdar.core.bean.resource.Status;
import org.tdar.struts.action.TdarActionException;

public class AccountServiceITCase extends AbstractIntegrationTestCase {

    @Autowired
    AccountService accountService;

    @Autowired
    GenericService genericService;

    @Test
    @Rollback
    public void testAccountList() throws TdarActionException {
        Person p = createAndSaveNewPerson();
        Account account = setupAccountForPerson(p);
        Account accountWithPermissions = new Account("my account");
        Person p2 = createAndSaveNewPerson("a@aas", "bb");
        accountWithPermissions.setOwner(p2);
        accountWithPermissions.markUpdated(getUser());
        accountWithPermissions.setStatus(Status.ACTIVE);
        accountWithPermissions.getAuthorizedMembers().add(p);
        genericService.saveOrUpdate(accountWithPermissions);

        Set<Account> accountsForUser = accountService.listAvailableAccountsForUser(p);
        assertTrue(accountsForUser.contains(account));
        assertTrue(accountsForUser.contains(accountWithPermissions));

        accountsForUser = accountService.listAvailableAccountsForUser(getUser());
        assertFalse(accountsForUser.contains(account));
        assertFalse(accountsForUser.contains(accountWithPermissions));
    }

    @Test
    @Rollback
    public void testAccountGroups() throws TdarActionException {
        AccountGroup group = new AccountGroup();
        group.setName("my account group");
        group.markUpdated(getBasicUser());
        Account accountForPerson = setupAccountForPerson(getBasicUser());
        Account accountForPerson2 = setupAccountForPerson(createAndSaveNewPerson());
        accountForPerson2.getAuthorizedMembers().add(getBasicUser());
        group.getAccounts().add(accountForPerson);
        group.getAccounts().add(accountForPerson2);
        genericService.saveOrUpdate(group);
        assertEquals(accountService.getAccountGroup(accountForPerson), group);
    }

    @Test
    @Rollback
    public void updateOverdrawnAccountTest() throws InstantiationException, IllegalAccessException {
        Account account = setupAccountForPerson(getBasicUser());
        BillingActivityModel model = new BillingActivityModel();
        model.setCountingResources(false);
        model.setCountingFiles(true);
        model.setCountingSpace(false);
        ResourceEvaluator re = new ResourceEvaluator(model);
        Document resource = generateInformationResourceWithFileAndUser();
        resource.setAccount(account);
        genericService.saveOrUpdate(resource);
        AccountAdditionStatus updateQuota = accountService.updateQuota(re, account, resource);
        assertEquals(AccountAdditionStatus.NOT_ENOUGH_FILES, updateQuota);

        Invoice invoice = new Invoice();
        invoice.markUpdated(getBasicUser());
        invoice.getItems().add(new BillingItem(new BillingActivity("6 mb", 10f, 0, 0L, 1L, 10L, model), 1));
        BillingActivity activity = invoice.getItems().get(0).getActivity();
        invoice.setTransactionStatus(TransactionStatus.TRANSACTION_SUCCESSFUL);
        invoice.resetTransientValues();
        invoice.finalize();
        activity.setModel(model);
        account.getInvoices().add(invoice);
        genericService.saveOrUpdate(model);
        genericService.saveOrUpdate(activity);
        genericService.saveOrUpdate(invoice);
        genericService.saveOrUpdate(account);
        accountService.updateQuotaAndResetResourcesToStatus(account,Status.ACTIVE);
        
        assertEquals(Status.ACTIVE, resource.getStatus());
    }

    @Test
    @Rollback
    public void testAccountGroupPermissions() throws TdarActionException {
        AccountGroup group = new AccountGroup();
        group.setName("my account group");
        group.markUpdated(getBasicUser());
        Account accountForPerson = setupAccountForPerson(getBasicUser());
        Account accountForPerson2 = setupAccountForPerson(getBasicUser());
        accountForPerson2.getAuthorizedMembers().add(getBasicUser());
        Person person = createAndSaveNewPerson();
        group.getAuthorizedMembers().add(person);
        group.getAccounts().add(accountForPerson);
        group.getAccounts().add(accountForPerson2);
        genericService.saveOrUpdate(group);
        assertEquals(accountService.getAccountGroup(accountForPerson), group);
        assertTrue(accountService.listAvailableAccountsForUser(person).contains(accountForPerson));
        assertTrue(accountService.listAvailableAccountsForUser(person).contains(accountForPerson2));
    }

    @Test
    @Rollback
    public void testAvaliableActivities() throws TdarActionException {
        BillingActivityModel model = new BillingActivityModel();
        BillingActivity disabledDctivity = new BillingActivity();
        disabledDctivity.setEnabled(false);
        disabledDctivity.setName("not active");
        genericService.saveOrUpdate(model);
        disabledDctivity.setModel(model);
        genericService.saveOrUpdate(disabledDctivity);

        BillingActivity ctivity = new BillingActivity("test", 1f, model);
        ctivity.setEnabled(true);
        ctivity.setName("active");
        genericService.saveOrUpdate(ctivity);

        List<BillingActivity> activeBillingActivities = accountService.getActiveBillingActivities();
        assertTrue(activeBillingActivities.contains(ctivity));
        assertFalse(activeBillingActivities.contains(disabledDctivity));
    }

}
