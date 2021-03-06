package org.tdar.core.dao.resource.stats;

import org.tdar.locale.Localizable;
import org.tdar.utils.MessageHelper;

public enum DateGranularity implements Localizable {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    @Override
    public String getLocaleKey() {
        return MessageHelper.formatLocalizableKey(this);
    }
}
