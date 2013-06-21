package org.tdar.core.dao.external.payment.nelnet;

import java.util.Map;

import org.apache.commons.httpclient.URIException;
import org.tdar.core.bean.billing.Invoice;

public interface PaymentTransactionProcessor {

    public abstract void initializeTransaction();

    public abstract String getTransactionPostUrl();

    public abstract String prepareRequest(Invoice invoice) throws URIException;

    public abstract NelNetTransactionResponseTemplate processResponse(Map<String, String[]> parameters);

    public abstract boolean validateResponse(TransactionResponse response);

    public abstract Invoice locateInvoice(TransactionResponse response);

    public abstract void updateInvoiceFromResponse(TransactionResponse response, Invoice invoice);

    public abstract TransactionResponse setupTransactionResponse(Map<String, String[]> map);

}