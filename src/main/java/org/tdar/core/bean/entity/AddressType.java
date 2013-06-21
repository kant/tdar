package org.tdar.core.bean.entity;

import org.tdar.core.bean.HasLabel;

public enum AddressType implements HasLabel {
    MAILING("Mailing Address"),
    BILLING("Billing Address"),
    OTHER("Other");

    private String label;

    private AddressType(String label) {
        this.setLabel(label);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

}