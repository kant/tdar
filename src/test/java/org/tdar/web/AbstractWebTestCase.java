package org.tdar.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.tdar.TestConstants.DEFAULT_BASE_URL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdar.TestConstants;
import org.tdar.core.bean.AbstractIntegrationTestCase;
import org.tdar.core.exception.TdarRecoverableRuntimeException;
import org.w3c.dom.Element;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlHiddenInput;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.google.common.collect.Lists;
import com.opensymphony.module.sitemesh.HTMLPage;
import com.threelevers.css.Selector;

/**
 * @author Adam Brin
 * 
 */
public abstract class AbstractWebTestCase extends AbstractIntegrationTestCase {

    public static final String TABLE_METADATA = "table metadata";
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final WebClient webClient = new WebClient(BrowserVersion.FIREFOX_3_6);
    protected Page internalPage;
    protected HtmlPage htmlPage;
    private HtmlForm _internalForm;
    private HtmlElement documentElement;
    public static String PROJECT_ID_FIELDNAME = "projectId";
    protected Set<String> encodingErrorExclusions = new HashSet<String>();

    @SuppressWarnings("serial")
    private Map<String, Pattern> encodingErrorPatterns = new LinkedHashMap<String, Pattern>() {
        {
            // note that braces are java regex meta instructions and must be escaped (oh and don't forget to escape the escape character... man I hate you java)
            put("possible html encoding inside json, open-brace", Pattern.compile("\\{&quot;"));
            put("possible html encoding inside json, close-brace", Pattern.compile("&quot;\\}"));
            put("possible html encoding inside json, quoted-key", Pattern.compile("\\{&quot;:"));
            put("double-encoded html tag", Pattern.compile("&lt;(.+?)&gt;"));
            put("double-encoded html attribute pair", Pattern.compile("\\w+\\s?=\\s?&quot;\\w+&quot;"));
        }
    };

    // disregard an encoding error if it's in the exclusions set;

    /*
     * override to test with different URL can use this to point at another
     * instance of tDAR instead of running "integration" tests.
     */
    public String getBaseUrl() {
        return System.getProperty("tdar.baseurl", DEFAULT_BASE_URL);
    }

    public Page getPage(String localPath) {
        try {
            if (localPath.startsWith("http")) {
                return webClient.getPage(localPath);
            } else {
                return webClient.getPage(getBaseUrl() + localPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("couldn't find page at " + localPath, e);
        }
        return null;
    }

    protected WebClient getWebClient() {
        return webClient;
    }

    /**
     * Go to the specified page, with explicit assertions that the server did not return with a 500 error or contain any inline exception text
     * 
     * @param path
     * @return http return code (if no errors found, otherwise assertions fail and method does not return);
     */
    public int gotoPage(String path) {
        int statusCode = gotoPageWithoutErrorCheck(path);
        assertFalse("An error ocurred" + internalPage.getWebResponse().getContentAsString(), statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNoEscapeIssues();
        assertNoErrorTextPresent();
        return statusCode;
    }

    /**
     * Same as gotoPage(), but does not perform any assertions on the server response
     * 
     * @param path
     * @return http return code
     */
    public int gotoPageWithoutErrorCheck(String path) {
        webClient.setThrowExceptionOnFailingStatusCode(false);
        changePage(getPage(path));
        assertNoEscapeIssues();
        webClient.setThrowExceptionOnFailingStatusCode(true);
        return internalPage.getWebResponse().getStatusCode();
    }

    public void assertTextPresentInPage(String text) {
        assertTextPresentInPage(text, true);
    }

    public void assertTextPresentInPage(String text, boolean sensitive) {
        HtmlPage page = (HtmlPage) internalPage;
        if (sensitive) {
            assertTrue("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + page.asText(), page.asText().contains(text));
        } else {
            assertTrue("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + page.asText(),
                    page.asText().toLowerCase().contains(text.toLowerCase()));
        }
    }

    public void assertTextPresentInCode(String text) {
        assertTextPresentInCode(text, true);
    }

    public void assertTextPresentInCode(String text, boolean sensitive) {
        if (sensitive) {
            assertTrue("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + getPageCode(), getPageCode().contains(text));
        } else {
            assertTrue("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + getPageCode(), getPageCode().toLowerCase().contains(text));
        }
    }

    public void duplicateInputByName(String name, String newName) {
        HtmlPage page = (HtmlPage) internalPage;
        HtmlElement elementByName = page.getElementByName(name);
        HtmlElement clone = (HtmlElement) elementByName.cloneNode(true);
        clone.setAttribute("name", newName);
        elementByName.getParentNode().appendChild(clone);
    }

    public void assertTextPresent(String text) {
        assertTrue("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + getPageText(), internalPage.getWebResponse().getContentAsString()
                .contains(text));
    }

    public void assertTextPresentIgnoreCase(String text) {
        assertTrue("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + getPageText(),
                StringUtils.containsIgnoreCase(internalPage.getWebResponse().getContentAsString(), text));
    }

    public void assertTextNotPresentIgnoreCase(String text) {
        assertFalse("looking for [" + text + "] in page:" + internalPage.getUrl() + "\n" + getPageText(),
                StringUtils.containsIgnoreCase(internalPage.getWebResponse().getContentAsString(), text));
    }

    public void assertPageTitleEquals(String expectedTitle) {
        if (internalPage instanceof HtmlPage) {
            HtmlPage page = (HtmlPage) internalPage;
            assertEquals(expectedTitle.toLowerCase(), page.getTitleText().toLowerCase());
        }
        else {
            Assert.fail(String.format("was looking for <title>%s</title> but server response was not a valid html page", expectedTitle));
        }
    }

    public HtmlElement getInput(String name) {
        HtmlPage page = (HtmlPage) internalPage;
        return page.getElementByName(name);
    }

    public void setInput(String name, String value) {
        setInput(name, value, true);
    }

    public void setInput(String name, String value, boolean overrideCreate) {
        HtmlPage page = (HtmlPage) internalPage;
        String id = null;

        // a pattern that describes struts "indexed" form field names (e.g.
        // <input name="mybean[2].myBeanField" /> or <input name="myvar[0]" />)
        String indexedNamePattern = "(.+)\\[(\\d+)\\](\\..+)?";
        HtmlElement input = null;
        try {
            input = page.getElementByName(name);
        } catch (Exception e) {
            logger.trace("no element found: " + name);
        }

        if (input == null && overrideCreate) {
            // test for duplicating fields with the two cases we have (a) struts, or
            // (b) the non-standard file-upload
            if (name.matches(indexedNamePattern)) {
                // clone zeroth collection item (e.g. if we want to create element named 'person[3].firstName' we clone element named 'person[0].firstName')
                String zerothFieldName = name.replaceAll(indexedNamePattern, "$1[0]$3");
                if (!name.equals(zerothFieldName)) {
                    duplicateInputByName(zerothFieldName, name);
                }
            }
            input = page.getElementByName(name);
        }
        assertTrue("could not find input for name: " + name, input != null);

        if (input instanceof HtmlTextArea) {
            HtmlTextArea txt = (HtmlTextArea) input;
            id = txt.getId();
            txt.setText(value);
        } else if (input instanceof HtmlSelect) {
            HtmlSelect sel = (HtmlSelect) input;
            HtmlOption option = null;
            for (HtmlOption option_ : sel.getSelectedOptions()) {
                option_.setSelected(false);

            }
            try {
                option = sel.getOptionByValue(value);
            } catch (ElementNotFoundException enfe) {

            }
            if (option == null) {
                logger.warn("option value " + value + " did not exist, creating it");
                option = (HtmlOption) ((HtmlPage) internalPage).createElement("option");
                option.setValueAttribute(value);
                sel.appendChild(option);
                option.setSelected(true);
            }
            sel.setSelectedAttribute(value, true);
            // assertTrue(sel.getSelectedOptions().get(0).getAttributes().getNamedItem("value").getTextContent() + " should equal " +
            // value,sel.getSelectedOptions().get(0).getAttributes().getNamedItem("value").getTextContent().equals(value));
            id = sel.getId();
        } else if (input instanceof HtmlCheckBoxInput) {
            // if the checkbox's value attribute matches the supplied value, check the box. Otherwise uncheck it.
            HtmlCheckBoxInput chk = (HtmlCheckBoxInput) input;
            id = chk.getId();
            chk.setChecked(chk.getValueAttribute().equalsIgnoreCase(value));
        } else if (input instanceof HtmlRadioButtonInput) {
            // we have a collection of elements with the same name
            id = checkRadioButton(value, page.getElementsByName(name));
        } else {
            HtmlInput inp = (HtmlInput) input;
            id = inp.getId();
            inp.setValueAttribute(value);
        }
        assertTrue("could not find field: " + name, id != null);
        updateMainFormIfNull(id);
    }

    private String checkRadioButton(String value, List<HtmlElement> radioButtons) {
        List<HtmlInput> buttonsFound = new ArrayList<HtmlInput>();
        for (HtmlElement radioButton : radioButtons) {
            if (radioButton.getId().toLowerCase().endsWith(value.toLowerCase())) {
                buttonsFound.add((HtmlInput)radioButton);
            }
        }
        assertTrue("found more than one candidate radiobutton for value " + value, buttonsFound.size() == 1);
        HtmlInput radioButton = buttonsFound.get(0);
        radioButton.setChecked(true);
        return radioButton.getId();
    }

    public void createInput(String inputName, String name, String value) {
        HtmlElement createdElement = ((HtmlPage) internalPage).createElement("input");
        createdElement.setAttribute("type", inputName);
        createdElement.setAttribute("name", name);
        createdElement.setAttribute("value", value);
        if (getForm() != null) {
            getForm().appendChild(createdElement);
        }
    }

    public void createInput(String inputName, String name, Number value) {
        createInput(inputName, name, value.toString());
    }

    public <T> void createTextInput(String name, T value) {
        // treat null as empty string
        String strValue = "" + value;
        createInput("text", name, strValue);
    }

    // create several text inputs. element name will be String.format(nameFormat, listIndex);
    public <T> void createTextInput(String nameFormat, List<T> values) {
        createTextInputs(nameFormat, values, 0);
    }

    public <T> void createTextInputs(String nameFormat, List<T> values, int startingIndex) {
        for (int i = startingIndex; i < startingIndex + values.size(); i++) {
            T value = values.get(i);
            String name = String.format(nameFormat, i);
            createTextInput(name, value);
        }
    }

    public boolean removeElementsByName(String elementName) {
        if (htmlPage == null)
            return false;
        List<HtmlElement> elements = htmlPage.getElementsByName(elementName);
        int count = 0;
        for (HtmlElement element : elements) {
            element.remove();
            count++;
        }
        return count > 0;
    }

    public boolean checkInput(String name, String val) {
        List<HtmlElement> els = getHtmlPage().getElementsByName(name);
        for (HtmlElement el : els) {
            logger.info(String.format("checkinput[%s --> %s] %s", name, val, el.asXml()));
            if (el instanceof HtmlTextArea && ((HtmlTextArea) el).getText().equals(val)) {
                return true;
            } else if (el instanceof HtmlSelect) {
                HtmlSelect sel = (HtmlSelect) el;
                for (HtmlOption o : sel.getSelectedOptions()) {
                    if (o.getValueAttribute().equalsIgnoreCase(val))
                        return true;
                }
            } else if (el instanceof HtmlCheckBoxInput || el instanceof HtmlRadioButtonInput) {
                if (els.size() == 1 && val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) {
                    if (el.getAttribute("value").equalsIgnoreCase(val)) {
                        if (el.hasAttribute("checked") && val.equalsIgnoreCase("true"))
                            return true;
                        if (!el.hasAttribute("checked") && val.equalsIgnoreCase("false"))
                            return true;
                    }
                } else if (el.getAttribute("value").equalsIgnoreCase(val)
                        && (el instanceof HtmlCheckBoxInput && ((HtmlCheckBoxInput) el).isChecked() || el instanceof HtmlRadioButtonInput
                                && ((HtmlRadioButtonInput) el).isChecked()))
                    return true;
            } else if ((el instanceof HtmlTextInput || el instanceof HtmlHiddenInput || el instanceof HtmlPasswordInput)
                    && el.getAttribute("value").equals(val)) {
                return true;
            }
        }
        return false;
    }

    public void setInput(String name, String... values) {
        HtmlPage page = (HtmlPage) internalPage;
        String id = null;
        for (HtmlElement input : page.getElementsByName(name)) {
            if (input instanceof HtmlCheckBoxInput) {
                HtmlCheckBoxInput chk = (HtmlCheckBoxInput) input;
                for (String val : values) {
                    if (chk.getValueAttribute().equalsIgnoreCase(val)) {
                        id = chk.getId();
                        chk.setChecked(true);
                        continue;
                    }
                }
            }
        }
        updateMainFormIfNull(id);
    }

    public void assertButtonPresentWithText(String buttonText) {
        HtmlElement input = getButtonWithName(buttonText);
        assertNotNull(String.format("button with text [%s] not found in form [%s]", buttonText,getForm() ), input);
        assertTrue(input.getAttribute("type").equalsIgnoreCase("submit"));
    }

    public int submitForm() {
        String defaultEditButton = "submitAction";
        HtmlElement buttonWithName = getButtonWithName(defaultEditButton);
        if (buttonWithName == null) {
            return submitForm("Save");
        }
        return submitForm(defaultEditButton);
    }

    public int submitForm(String buttonText) {
        submitFormWithoutErrorCheck(buttonText);
        int statusCode = internalPage.getWebResponse().getStatusCode();
        assertFalse(statusCode == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNoErrorTextPresent();
        assertNoEscapeIssues();
        return statusCode;
    }

    /**
     * similar to submitForm but does not perform any assertions on the server response
     * 
     * @param buttonText
     */
    public void submitFormWithoutErrorCheck(String buttonText) {
        assertButtonPresentWithText(buttonText);
        try {
            HtmlElement buttonByName = getButtonWithName(buttonText);
            changePage(buttonByName.click());
        } catch (IOException iox) {
            logger.error("exception while trying to submit from via button labeled "+ buttonText, iox );
        }
    }

    private HtmlElement getButtonWithName(String buttonText) {
        //get all the likely suspects we consider to be a "button" and return the best match
        logger.info("get button by name, form {}", _internalForm);
        List<HtmlElement> elements = new ArrayList<HtmlElement>();
        elements.addAll(getForm().getButtonsByName(buttonText));
        elements.addAll(getForm().getInputsByValue(buttonText));
        elements.addAll(getHtmlPage().getElementsByName(buttonText));
        
        if(elements.isEmpty()) {
            logger.error("could not find button or element with name or value '{}'", buttonText);
            return null;
        } else {
            return elements.iterator().next();
        }
    }

    public void assertErrorsPresent() {
        assertTextPresent("The following errors were found with your submission");
    }

    public void assertTextNotPresent(String text) {
        String contents = "";
        if (internalPage instanceof HtmlPage) {
            HtmlPage page = (HtmlPage) internalPage;
            contents = page.asText();
        }
        if (internalPage instanceof UnexpectedPage) {
            UnexpectedPage page = (UnexpectedPage) internalPage;
            contents = page.getWebResponse().getContentAsString();
        }
        if (contents.contains(text)) {
            logger.trace("text " + text + " found in " + contents);
        }
        assertFalse("text should not be present [" + text + "] in page:" + internalPage.getUrl(), contents.contains(text));
    }

    /**
     * Assert that the page is not an error page and does or contain any inline stacktraces
     */
    public void assertNoErrorTextPresent() {
        assertTextNotPresent("Exception stack trace: " + getCurrentUrlPath() + ":" + getPageText()); // inline stacktrace (ftl compiles but dies partway through
                                                                                                     // rendering)
        assertTextNotPresentIgnoreCase("HTTP ERROR");
        assertTextNotPresentIgnoreCase("Exception " + getCurrentUrlPath() + ":" + getPageText()); // inline stacktrace (ftl compiles but dies partway through
                                                                                                  // rendering)
        assertFalse("page shouldn't contain action errors " + getCurrentUrlPath() + ":" + getPageText(), getPageCode().contains("class=\"action-error\""));
    }

    public void assertNoEscapeIssues() {
        String html = getPageCode().toLowerCase();
        for (Map.Entry<String, Pattern> entry : encodingErrorPatterns.entrySet()) {
            Matcher matcher = entry.getValue().matcher(html);
            if (matcher.find()) {
                String msg = "encoding issue \"%s\" found at pos[%s,%s] : '%s'";
                int start = matcher.start() - 100;
                int end = matcher.end() + 100;
                int max = getPageCode().length();
                if (start < 0) {
                    start = 0;
                }
                if (end > max) {
                    end = max;
                }
                String matchAndContext = getPageCode().subSequence(start, end).toString();
                String exactMatch = getPageCode().subSequence(matcher.start(), matcher.end()).toString();

                if (!encodingErrorExclusions.contains(exactMatch)) {
                    Assert.fail(String.format(msg, entry.getKey(), matcher.start(), matcher.end(), matchAndContext));
                }
            }
        }
    }

    public HtmlPage getHtmlPage() {
        assertTrue("page is not a HtmlPage", internalPage instanceof HtmlPage);
        HtmlPage page = (HtmlPage) internalPage;
        return page;
    }

    public HtmlAnchor findPageLink(String text) {
        HtmlAnchor anchor = getHtmlPage().getAnchorByText(text);
        assertNotNull(String.format("link with text [%s] not found on page %s", text, getPageCode()), anchor);
        return anchor;
    }

    public void clickLinkWithText(String text) {
        clickLinkOnPage(text);
    }

    public void changePage(Page page) {
        if (page == null) {
            fail("changed to a null page for some reason");
            return;
        }
        internalPage = page;
        _internalForm = null;
        logger.info("CHANGING url TO: " + internalPage.getUrl());
        if (internalPage instanceof HtmlPage) {
            htmlPage = (HtmlPage) internalPage;
            documentElement = htmlPage.getDocumentElement();
            assertNoEscapeIssues();
        }
    }

    public void clickLinkOnPage(String text) {
        HtmlAnchor anchor = findPageLink(text);
        assertNotNull("could not find link with " + text + " on " + getPageText(), anchor);
        try {
            changePage(anchor.click());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPageText() {
        if (internalPage instanceof HTMLPage) {
            HtmlPage page = (HtmlPage) internalPage;
            return page.asText();
        }
        if (internalPage instanceof TextPage) {
            return ((TextPage) internalPage).getContent();
        }

        return internalPage.getWebResponse().getContentAsString();
    }

    public String getPageCode() {
        return internalPage.getWebResponse().getContentAsString();
    }

    public String getCurrentUrlPath() {
        return internalPage.getUrl().getPath() + "?" + internalPage.getUrl().getQuery();
    }

    @Before
    public void prepare() {
        // FIXME: This is far less than ideal, but there's a problem with how
        // the MAC is handling memory
        // and appears to be 'leaking' with jwebunit and gooogle maps. Hence, we
        // need to disable javascript
        // testing on the mac :(
        // if (System.getProperty("os.name").toLowerCase().contains("mac os x"))
        webClient.setJavaScriptEnabled(false);
        webClient.setTimeout(0);
        webClient.setJavaScriptTimeout(0);

        // reset encoding error exclusions for each test
        encodingErrorExclusions = new HashSet<String>();
        // <generated> gets emitted by cglib methods in stacktrace, let's not consider it to be a double encoding error.
        encodingErrorExclusions.add("&lt;generated&gt;");
    }

    public void testOntologyView() {
        gotoPage("/ontology/3029");
        assertTextPresentInPage("Fauna Pathologies - Default Ontology Draft");
        assertTextPresentInPage("Indeterminate");
        assertTextPresentInPage("Fauna");
    }

    public void testCodingSheetView() {
        gotoPage("/coding-sheet/449");
        logger.trace("\n----------- page begin--------\n" + getPageText() + "\n----------- page begin--------\n");
        assertTextPresentInPage("CARP Fauna Proximal-Distal");
        assertTextPresentInPage("Subcategory: Portion/Proximal/Distal");
    }

    public void testProjectView() {
        // this should probably be done @before every test but it would slow things down even more
        searchIndexService.indexAll();

        gotoPage("/project/3805");
        logger.trace(getPageText());
        assertTextPresentInPage("New Philadelphia Archaeology Project");
        assertTextPresentInPage("Block 3, Lot 4");
    }

    public void testDocumentView() {
        gotoPage("/document/" + TestConstants.TEST_DOCUMENT_ID);
        assertTextPresentInPage("2008 New Philadelphia Archaeology Report, Chapter 4, Block 7, Lot 1");
        assertTextPresentInPage("a2008reportchap4.pdf");
        assertTextPresentInPage("New Philadelphia Archaeology project");
        assertTextPresentInPage("17");
    }

    public void testDatasetView() {
        gotoPage("/dataset/3088");
        logger.trace("content of dataset view page: {}", getPageText());
        assertTextPresentInPage("Knowth Stage 8 Fauna Dataset");
        assertTextPresentInPage("Dataset");
        assertTextPresentInPage("dataset_3088_knowthstage8.xls");
    }

    public void testBasicSearchView() {
        gotoPage("/search/basic");
        assertTextPresentInPage("Search");
    }

    public void testAdvancedSearchView() {
        gotoPage("/search/advanced");
        assertTextPresentInPage("Limit by geographic region");
        assertTextPresentInPage("Choose Search Terms");
        assertTextPresentInPage("All Fields");
    }

    @After
    public void cleanup() {
        webClient.closeAllWindows();
    }

    public Long extractTdarIdFromCurrentURL() {
        String url = internalPage.getUrl().toString();
        while (url.indexOf("/") != -1) {
            String part = url.substring(url.lastIndexOf("/"));
            part = part.replace("/", "");
            url = url.substring(0, url.lastIndexOf("/"));
            logger.trace("evaluating {} : {}", url, part);
            if (StringUtils.isNotBlank(part) && StringUtils.isNumeric(part)) {
                return Long.parseLong(part);
            }
        }
        throw new TdarRecoverableRuntimeException("could not find tDAR ID in URL" + internalPage.getUrl().toString());
    }

    public void assertCurrentUrlEquals(String url) {
        String msg = String.format("actual page: %s; assumed page: %s; status: %s", internalPage.getUrl(), url, internalPage.getWebResponse().getStatusCode());
        assertEquals(msg, internalPage.getUrl().toString(), url);
    }

    public void assertCurrentUrlContains(String url) {
        String msg = String.format("actual page: %s; assumed page should have in URL: %s; status: %s", internalPage.getUrl(), url, internalPage
                .getWebResponse().getStatusCode());
        assertTrue(msg, internalPage.getUrl().toString().contains(url));
    }

    // get the "main" form. it's pretty much a guess, so if you encounter a page w/ multiple forms you might wanna specify it outright
    public HtmlForm getForm() {
        logger.trace("FORM{} OTHERS: {}", _internalForm, getHtmlPage().getForms());
        if (_internalForm == null) {
            HtmlForm htmlForm = null;
            if (getHtmlPage().getForms().size() == 1) {
                htmlForm = getHtmlPage().getForms().get(0);
                logger.trace("only one form: " + htmlForm.getNameAttribute());
            } else {
                for (HtmlForm form : getHtmlPage().getForms()) {
                    if (StringUtils.isNotBlank(form.getActionAttribute()) && !form.getNameAttribute().equalsIgnoreCase("autosave") &&
                            !form.getNameAttribute().equalsIgnoreCase("searchheader")) {
                        htmlForm = form;
                        logger.trace("using form: " + htmlForm.getNameAttribute());
                        break;
                    }
                }
            }
            _internalForm = htmlForm;
        }

        return _internalForm;
    }

    public HtmlForm getForm(String formName) {
        return getHtmlPage().getFormByName(formName);
    }

    protected void setMainForm(HtmlForm form) {
        _internalForm = form;
    }

    protected void setMainForm(String formName) {
        _internalForm = getHtmlPage().getFormByName(formName);
    }

    // set the main form to be first form that contains a child element with the specified id
    private void updateMainFormIfNull(String id) {
        if (_internalForm != null || StringUtils.isBlank(id))
            return;
        for (HtmlForm form : getHtmlPage().getForms()) {
            if (form.getFirstByXPath("descendant-or-self::*[contains(@id,'"+id+"')]")  != null) {
                logger.info("updating main for for id: " + id + " to form: " +form);
                setMainForm(form);
                return;
            }
        }
        logger.warn("No form found containing id '{}'", id);
    }

    protected List<Element> querySelectorAll(String cssSelector) {
        Iterable<Element> elements = Selector.from(documentElement).select(cssSelector);
        List<Element> elementList = Lists.newArrayList(elements);
        return elementList;
    }

    protected Element querySelector(String cssSelector) {
        return Selector.from(documentElement).select(cssSelector).iterator().next();
    }

}
