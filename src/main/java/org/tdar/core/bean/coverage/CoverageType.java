package org.tdar.core.bean.coverage;

import org.tdar.core.bean.HasLabel;
import org.tdar.utils.MessageHelper;

public enum CoverageType implements HasLabel {

    CALENDAR_DATE(MessageHelper.getMessage("coverageType.calendar")),
    RADIOCARBON_DATE(MessageHelper.getMessage("coverageType.radiocarbon")),
    NONE(MessageHelper.getMessage("coverageType.none"));

    private String label;

    CoverageType(String label) {
        setLabel(label);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public boolean validate(Integer start, Integer end) {
        if (start == null && end == null)
            return false;

        switch (this) {
            case CALENDAR_DATE:
                return (start <= end);
            case RADIOCARBON_DATE:
                return (start >= end && end > 0);
            case NONE:
                return true;
            default:
                return false;
        }
    }
}
