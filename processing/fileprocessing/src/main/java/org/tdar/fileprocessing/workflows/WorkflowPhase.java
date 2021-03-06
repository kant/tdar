package org.tdar.fileprocessing.workflows;

import org.tdar.locale.HasLabel;
import org.tdar.locale.Localizable;
import org.tdar.utils.MessageHelper;

/**
 * @author Adam Brin
 * 
 */
public enum WorkflowPhase implements HasLabel, Localizable {

    SETUP("Setup"),
    PRE_PROCESS("Pre-Process"),
    CREATE_DERIVATIVE("Create Derivative"),
    CREATE_ARCHIVAL("Create Archival"),
    POST_PROCESS(
            "Post-Process"),
    CLEANUP("Cleanup");

    private String label;

    private WorkflowPhase(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getLocaleKey() {
        return MessageHelper.formatLocalizableKey(this);
    }

}
