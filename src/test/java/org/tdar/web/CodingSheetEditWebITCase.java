package org.tdar.web;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.test.annotation.Rollback;
import org.tdar.TestConstants;
import org.tdar.core.configuration.TdarConfiguration;

public class CodingSheetEditWebITCase extends AbstractAdminAuthenticatedWebTestCase {

    private static final String PARENT_PROJECT_ID_FIELD = "projectId";
    private static final Long PARENT_PROJECT_ID_VALUE = 3805L;
    // private static final String PARENT_PROJECT_TITLE =
    // "New Philadelphia Archaeology Project";
    private static final String CODING_SHEET_TITLE_FIELD = "codingSheet.title";
    private static final String CODING_SHEET_TITLE_VALUE = "A Blank Coding sheet that shouldn't be saved";
    private static final String CODING_SHEET_INPUT_METHOD_FIELD = "fileInputMethod";
    private static final String CODING_SHEET_INPUT_METHOD_TEXT = "text";

    // private static final String CODING_SHEET_INPUT_METHOD_FILE = "file";
    // private static final String ERROR_MESSAGE_BLANK_CODING_RULES =
    // "Please enter your coding rules in the text area";

    @Test
    @Rollback
    public void testCreateBlankCodingSheet() {
        // creating a blank coding sheet should return an error
        gotoPage("/coding-sheet/add");
        setInput(PARENT_PROJECT_ID_FIELD, "" + PARENT_PROJECT_ID_VALUE);
        setInput(CODING_SHEET_TITLE_FIELD, CODING_SHEET_TITLE_VALUE);
        setInput("codingSheet.description", "description");
        setInput("codingSheet.date", "1937");
        setInput(CODING_SHEET_INPUT_METHOD_FIELD, CODING_SHEET_INPUT_METHOD_TEXT);
        if (TdarConfiguration.getInstance().getCopyrightMandatory()) {
            setInput(TestConstants.COPYRIGHT_HOLDER_TYPE, "Institution");
            setInput(TestConstants.COPYRIGHT_HOLDER_PROXY_INSTITUTION_NAME, "Elsevier");
        }
        submitFormWithoutErrorCheck("Save");
        assertTextPresent("Please enter your coding rules in the text area below.");
        assertTextPresentInCode("action-error");
        // we should be dumped back to the edit page with (at least) an error
        // message re: the blank coding rules
        logger.info(getPageText());
        // assertTextPresent(ERROR_MESSAGE_BLANK_CODING_RULES);
        assertTrue("nothing to see here... everything works just fine.", true);

    }

}
