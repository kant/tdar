package org.tdar.struts.action.cart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Actions;
import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.UrlConstants;
import org.tdar.core.bean.billing.BillingActivity;
import org.tdar.core.bean.billing.BillingItem;
import org.tdar.core.bean.billing.Invoice;
import org.tdar.core.bean.entity.AuthorizedUser;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.entity.UserAffiliation;
import org.tdar.core.dao.external.payment.PaymentMethod;
import org.tdar.core.service.billing.InvoiceService;
import org.tdar.core.service.billing.PricingOption.PricingType;
import org.tdar.struts.action.AbstractCartController;
import org.tdar.struts.interceptor.annotation.HttpsOnly;
import org.tdar.struts_base.interceptor.annotation.DoNotObfuscate;
import org.tdar.struts_base.interceptor.annotation.PostOnly;

/**
 * Manages all aspects of creating and updating an invoice for files and storage for unauthenticated or authenticated users.
 * 
 * Hands off to the CartController for final payment, or the CartBillingAccountController for updating billing information.
 */
@Component
@Scope("prototype")
@HttpsOnly
@Namespace("/cart")
public class InvoiceController extends AbstractCartController {

    private static final long serialVersionUID = -9156927670405819626L;

    public static final String PENDING_INVOICE_ID_KEY = "pending_invoice_id";
    private List<UserAffiliation> affiliations = UserAffiliation.getUserSubmittableAffiliations();

    private List<BillingActivity> activities = new ArrayList<>();

    private List<Long> extraItemIds = new ArrayList<>();
    private List<Integer> extraItemQuantities = new ArrayList<>();

    private String code;

    private PricingType pricingType = null;
    private Long accountId;

    @Autowired
    private transient InvoiceService invoiceService;

    private Collection<BillingItem> extraBillingItems;

    /**
     * This is the first step of the purchase process. The user specifies the number of files/mb or chooses a
     * predefined small/medium/large bundle. The user may also specify a voucher/discount code.
     * <p/>
     * Re: the client page. This is actually a form. You can submit the form by clicking the "Next/Save" button. or by clicking on one of the small/medium/large
     * buttons. In either case the form submits a POST request to /cart/process-choice.
     * 
     * @return
     */
    @Actions(value = {
            @Action("add")
    })
    @SkipValidation
    public String execute() {
        // just in case a user comes through and explicitly wants to start over
        clearPendingInvoice();
        return SUCCESS;
    }

    @Actions(value = {
            @Action(value = "modify", results = { @Result(name = SUCCESS, location = "add.ftl") })
    })
    @SkipValidation
    // @GetOnly
    public String modify() {
        return SUCCESS;
    }

    /**
     * process new/updated invoice request
     * 
     * @return
     */
    @Action(value = "process-choice",
            results = {
                    @Result(name = INPUT, location = "add.ftl"),
                    @Result(name = SUCCESS, type = TDAR_REDIRECT, location = UrlConstants.CART_REVIEW_PURCHASE),
                    @Result(name = SUCCESS_UNAUTHENTICATED, type = TDAR_REDIRECT, location = UrlConstants.CART_REVIEW_UNAUTHENTICATED)
            })
    @DoNotObfuscate(reason = "unnecessary")
    @PostOnly
    public String processInvoice() {
        try {
            setInvoice(invoiceService.processInvoice(getInvoice(), getAuthenticatedUser(), code, extraBillingItems, pricingType, accountId));
        } catch (Exception trex) {
            addActionError(trex.getLocalizedMessage());
            return INPUT;
        }
        storePendingInvoice(getInvoice());
        return isAuthenticated() ? SUCCESS : SUCCESS_UNAUTHENTICATED;
    }

    /**
     * Show the pending invoice review page, potentially show login/auth form.
     * 
     * @return
     */
    @Action("review-unauthenticated")
    public String showInvoice() {
        if (getInvoice() == null) {
            return "redirect-start";
        }

        return SUCCESS;
    }

    public List<BillingActivity> getActivities() {
        return activities;
    }

    public PricingType getPricingType() {
        return pricingType;
    }

    public void setPricingType(PricingType pricingType) {
        this.pricingType = pricingType;
    }

    public List<Long> getExtraItemIds() {
        return extraItemIds;
    }

    public void setExtraItemIds(List<Long> extraItemIds) {
        this.extraItemIds = extraItemIds;
    }

    public List<Integer> getExtraItemQuantities() {
        return extraItemQuantities;
    }

    public void setExtraItemQuantities(List<Integer> extraItemQuantities) {
        this.extraItemQuantities = extraItemQuantities;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public void prepare() {
        setupActivities();
        // look for pending invoice in the session.
        Invoice persistedInvoice = loadPendingInvoice();
        if (persistedInvoice != null) {
            if (persistedInvoice.isModifiable()) {
                if (getInvoice() != null) {
                    // overwrite values from invoice (mimic params-prepare-params)
                    persistedInvoice.setNumberOfFiles(getInvoice().getNumberOfFiles());
                    persistedInvoice.setNumberOfMb(getInvoice().getNumberOfMb());
                    persistedInvoice.setOwner(getInvoice().getOwner());
                    persistedInvoice.setOtherReason(getInvoice().getOtherReason());
                    persistedInvoice.setPaymentMethod(getInvoice().getPaymentMethod());
                }
                setInvoice(persistedInvoice);
            } else {
                // if invoice is not modifiable, we assume user is creating multiple invoices in the same session (which is rare but legit)
                clearPendingInvoice();
            }
        }

        // set default payment method
        if (getInvoice() != null) {
            getInvoice().setDefaultPaymentMethod();
        }

        extraBillingItems = invoiceService.lookupExtraBillingActivities(extraItemIds, extraItemQuantities);

    }

    public AuthorizedUser getBlankAuthorizedUser() {
        AuthorizedUser user = new AuthorizedUser();
        user.setUser(new TdarUser());
        return user;
    }

    public List<PaymentMethod> getAllPaymentMethods() {
        if (isBillingManager()) {
            return Arrays.asList(PaymentMethod.values());
        } else {
            return Arrays.asList(PaymentMethod.CREDIT_CARD);
        }
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    void setupActivities() {
        // we only care about the production+active activities
        for (BillingActivity act : invoiceService.getActiveBillingActivities()) {
            if (act.isProduction() || isEditor()) {
                getActivities().add(act);
            }
        }
    }

    /**
     * For all actions in this controller, we only want to deal with non-finalized invoices (e.g. invoice.isModifiable() == false). If we detect that
     * the invoice is finalized we consider this action to be invalid.
     */
    @Override
    public void validate() {
        if (!validateInvoice()) {
            return;
        }

        Long mb = getInvoice().getNumberOfMb();
        Long files = getInvoice().getNumberOfFiles();
        if (mb == null) {
            mb = 0L;
        }
        if (files == null) {
            files = 0L;
        }

        // rule: invoice must not be finalized
        if (!getInvoice().isModifiable()) {
            addActionError(getText("cartController.cannot_modify"));
        }

        // rule: invoice.paymentMethod required (prepare() should set automatically if only one option exists)
        if (getInvoice().getPaymentMethod() == null) {
            addActionError(getText("cartController.valid_payment_method_is_required"));
        }

        // if we're an admin and there are extra billing items, don't validate files and MB
        if (isBillingManager() && CollectionUtils.isNotEmpty(extraBillingItems)) {
            return;
        }
        if (mb == 0L && files == 0L) {
            if (StringUtils.isBlank(getCode())) {
                addActionError(getText("cartController.specify_mb_or_files"));
            }
        }
    }

    public List<UserAffiliation> getAffiliations() {
        return affiliations;
    }

    public void setAffiliations(List<UserAffiliation> affiliations) {
        this.affiliations = affiliations;
    }

}
