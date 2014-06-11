package org.tdar.struts.action.cart;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.convention.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.service.InvoiceService;
import org.tdar.core.service.XmlService;
import org.tdar.struts.data.PricingOption;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.tdar.struts.action.TdarActionSupport.JSONRESULT;


/**
 * Implementation of pricing api
 */
@Component
@Scope("prototype")
public class CartApiController extends ActionSupport {

    private static final long serialVersionUID = -1870193105271895297L;
    private Long lookupMBCount = 0L;
    private Long lookupFileCount = 0L;
    private List<PricingOption> pricingOptions = new ArrayList<>();
    private InputStream resultJson;
    private String callback;

    @Autowired
    XmlService xmlService;

    @Autowired
    private transient InvoiceService cartService;

    @Action(value = "api", results = {
            @Result(name = SUCCESS, type = JSONRESULT, params = { "stream", "resultJson" }) })
    public String api() {
        if (isNotNullOrZero(lookupFileCount) || isNotNullOrZero(lookupMBCount)) {
            addPricingOption(cartService.getCheapestActivityByFiles(lookupFileCount, lookupMBCount, false));
            addPricingOption(cartService.getCheapestActivityByFiles(lookupFileCount, lookupMBCount, true));
            addPricingOption(cartService.getCheapestActivityBySpace(lookupFileCount, lookupMBCount));
        }
        setResultJson(new ByteArrayInputStream(xmlService.convertFilteredJsonForStream(getPricingOptions(), null, getCallback()).getBytes()));

        return SUCCESS;
    }

    void addPricingOption(PricingOption incoming) {
        if (incoming == null) {
            return;
        }
        boolean add = true;

        for (PricingOption option : pricingOptions) {
            if ((option == null) || option.sameAs(incoming)) {
                add = false;
            }
        }
        if (add) {
            pricingOptions.add(incoming);
        }
    }

    boolean isNotNullOrZero(Long num) {
        if ((num == null) || (num < 1)) {
            return false;
        }
        return true;
    }

    public Long getLookupMBCount() {
        return lookupMBCount;
    }

    public void setLookupMBCount(Long lookupMBCount) {
        this.lookupMBCount = lookupMBCount;
    }

    public Long getLookupFileCount() {
        return lookupFileCount;
    }

    public void setLookupFileCount(Long lookupFileCount) {
        this.lookupFileCount = lookupFileCount;
    }

    public List<PricingOption> getPricingOptions() {
        return pricingOptions;
    }

    public void setPricingOptions(List<PricingOption> pricingOptions) {
        this.pricingOptions = pricingOptions;
    }

    public InputStream getResultJson() {
        return resultJson;
    }

    public void setResultJson(InputStream resultJson) {
        this.resultJson = resultJson;
    }


    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }
}
