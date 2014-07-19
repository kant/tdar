package org.tdar;

/**
 * @author Adam Brin
 * 
 */
public interface URLConstants {

    String HOME = "/";
    String DASHBOARD = "/dashboard";
    String WORKSPACE = "/workspace/list";
    String ADMIN = "/admin/internal";
    String PAGE_NOT_FOUND = "/page-not-found";
    String BOOKMARKS = DASHBOARD + "#bookmarks";
    String ENTITY_NAMESPACE = "browse/creators";
    String COLUMNS_RESOURCE_ID = "columns?id=${resource.id}&startRecord=${startRecord}&recordsPerPage=${recordsPerPage}";
    String VIEW_RESOURCE_ID = "view?id=${resource.id}";
    String MY_PROFILE = "/entity/person/myprofile";

    String CART_ADD = "/cart/new";
    String CART_REVIEW_PURCHASE = "/cart/review";
    String CART_REVIEW_UNAUTHENTICATED = "/cart/review-unauthenticated";

    public static final String CART_PROCESS_PAYMENT_REQUEST = "/cart/process-payment-request";
    public static final String CART_NEW_LOCATION = "/cart/new";
}
