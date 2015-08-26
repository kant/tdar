package org.tdar.struts.action;

import org.junit.Before;
import org.junit.Test;
import org.tdar.struts.action.admin.AdminController;
import org.tdar.struts.action.admin.AdminUserStatsController;

public class AdminStatsControllerITCase extends AbstractAdminControllerITCase {

    private AdminController controller;

    @Before
    public void setup() {
        controller = generateNewInitializedController(AdminController.class);
    }

    @Test
    public void testIndex() {
        controller.execute();
    }

    @Test
    public void testResourceStats() {
        controller.resourceInfo();
    }

    @Test
    public void testUserStats() {
        AdminUserStatsController ausc = generateNewInitializedController(AdminUserStatsController.class, getAdminUser());
        ausc.userInfo();
        ausc.userMailchipInfo();
    }

}
