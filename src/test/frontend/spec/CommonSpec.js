/* global jasmine, describe, it, expect, loadFixtures, $j, $, beforeEach, afterEach */
describe("TDAR.common: edit page tests", function () {

    it("initializes the edit page", function () {
        var form = null;
        var props = {
            formSelector: "#metadataForm",
            includeInheritance: true,
            acceptFileTypes: /\.(pdf|doc|docx|rtf|txt)$/i,
            multipleUpload: true,
            validExtensions: "pdf|doc|docx|rtf|txt",
            validExtensionsWarning: "Please enter a valid file (pdf, doc, docx, rtf, txt)",
            ableToUpload: true,
            dataTableEnabled: false
        };

        loadFixtures("document-add-form.html", "fileupload-templates.html");
        expect($j("#template-upload")).toHaveLength(1);
        expect($j("#metadataForm")).toHaveLength(1);
        form = document.getElementById('metadataForm');
        var result = TDAR.common.initEditPage(form, props);

    });

    it("initializes form validation", function () {
        loadFixtures("document-add-form.html", "fileupload-templates.html");
        var $mapdiv = $j(jasmine.getFixtures().read("map-div.html"))

        //add a mapdiv to implicitly load gmap api and perform init
        $j("#divSpatialInformation").append($mapdiv);

        expect($j('#divSpatialInformation .google-map')).toHaveLength(1);
        form = document.getElementById('metadataForm');
        var result = TDAR.common.initFormValidation(form);
    });
});


    describe("TDAR.common functions that utilize ajax", function() {

        beforeEach(function() {
            jasmine.Ajax.install();
        });

        afterEach(function() {
            jasmine.Ajax.uninstall();
        });

        it("updates the subcategory options when you select a category", function () {
            //create a simple cat/subcat form.
            var $categoryIdSelect = $j('<select id="cat"><option>foo</option></select>');
            var $subCategoryIdSelect = $j('<select id="subcat"></select>');

            //user selects the 'foo' category
            setFixtures($categoryIdSelect);
            $categoryIdSelect.val('foo');
            appendSetFixtures($subCategoryIdSelect);
            $expect('select').toHaveLength(2);
            TDAR.common.changeSubcategory($categoryIdSelect, $subCategoryIdSelect);

            //server responsds with array containing the 'bar' subcategory
            jasmine.Ajax.requests.mostRecent().respondWith({
                status: 200,
                contentType: 'text/json',
                responseText: JSON.stringify([{id:123, label:'bar'}])
            });

            expect($subCategoryIdSelect.val()).toBe('123');
            expect($subCategoryIdSelect.find('option').text()).toBe('bar');
        });

        it("updates the server when you add/remove a bookmark", function () {
            var $elem = $('<span resource-id="12345" bookmark-state="bookmark">click me</span>');
            var $elem2 = $('<span resource-id="12345" bookmark-state="bookmarked">click me</span>');
            setFixtures($elem);

            TDAR.common.applyBookmarks.call($elem);
            expect(jasmine.Ajax.requests.mostRecent().url).toContain('resource/bookmarkAjax?resourceId=12345');            

            TDAR.common.applyBookmarks.call($elem2);
            expect(jasmine.Ajax.requests.mostRecent().url).toContain('resource/removeBookmarkAjax?resourceId=12345');            
        });
    });

describe("TDAR.common: session timeout tests", function() {
    var sessionTimeout;
    beforeEach(function(){
        //intercept calls to setTimeout() and $.fn.dialog(). Confirm that our function called them later. 
        spyOn(window, 'setTimeout');
        spyOn($.fn, 'dialog');
        setFixtures('<div id="timeoutDialog"></div>');
        sessionTimeout = TDAR.common.sessionTimeout;
    });

    afterEach(function() {
        TDAR.common.sessionTimeout = sessionTimeout;
    });

    it("shows a warning when you your session has nearly expired", function() {
        TDAR.common.sessionTimeout = 960;
        TDAR.common.currentTime =  600;
        TDAR.common.sessionTimeoutWarning();
        expect($.fn.dialog).toHaveBeenCalled();
        expect(window.setTimeout).toHaveBeenCalled();

    });

    it("notifies user when session has timed out", function () {
        TDAR.common.sessionTimeout = 90 * 60;
        TDAR.common.currentTime =  90 * 60;
        TDAR.common.sessionTimeoutWarning();
        expect($('#timeoutDialog').text()).toContain('Your Session has timed out');
        expect(window.setTimeout).not.toHaveBeenCalled();
    });

});

describe("TDAR.common: tests that override Modernizer", function() {
    var placeholder;

    beforeEach(function(){
        var placeholder = Modernizr.input.placeholder;
    });

    afterEach(function(){
        Modernizr.input.placeholder = placeholder
    });

    it("applies watermarks using jquery when not supported by browser", function () {
        Modernizr.input.placeholder = false;
        setFixtures('<input type="text" placeholder="howdy" value="" name="field1">');
        spyOn($.fn, 'watermark');
        TDAR.common.applyWatermarks();
        expect($.fn.watermark).toHaveBeenCalledWith('howdy');
    });
});

describe("TDAR.common: miscellaneaous tests", function () {
    beforeEach(function(){
        window._gaq = [];
    });

    afterEach(function(){
        delete(window_gaq);
    });


    it("should work when we call applyTreeviews", function () {

        loadFixtures('treeview.html');
        //before calling treeview, none of the lists are decorated
        expect($j(".hitarea, .treeview")).toHaveLength(0);

        var result = TDAR.common.applyTreeviews();

        //after treeview(), parent nodes will have .hitarea and the top-level node will have .treeview class
        expect($j(".hitarea")).toHaveLength(1);
        expect($j(".treeview")).toHaveLength(1);
    });

    it("initializes a the search box on view page", function () {
        loadFixtures('searchheader.html');
        var result = TDAR.common.initializeView();
        $expect('.active').not.toBeInDOM();
        $j(".searchbox").focus();
        $expect('.active').toBeInDOM();
    });

    it("should register the validation form when we call initRegformValidation", function () {
        loadFixtures("registration-form.html");
        var form = document.getElementById('accountForm')

        var result = TDAR.common.initRegformValidation(form);
        var validator = $(form).validate();
        //before we validate we should have no errors
        expect(validator.errorList).toHaveLength(0);

        //since all fields are blank, we should have at least a couple validation errors
        $(form).valid();
        expect(validator.errorList.length).toBeGreaterThan(0);
    });

    it("should work when we call determineResponsiveClass", function () {
        var f =  TDAR.common.determineResponsiveClass.bind(TDAR.common);
        expect(f(1200 + 1)).toBe('responsive-large-desktop');
        expect(f(979 + 1)).toBe('responsive-desktop');
        expect(f(767 + 1)).toBe('responsive-tablet');
        expect(f(500 + 1)).toBe('responsive-phone');
        expect(f(1 + 1)).toBe('responsive-phone-portrait');
    });

    it("should work when we call ellipsify", function () {
        var text = "It's Monty Pythons Flying circus";
        var n = 6;
        var useWordBoundary = false;
        expect(TDAR.ellipsify(text, n, useWordBoundary)).toBe("It's ..."); 
        var useWordBoundary = true;
        expect(TDAR.ellipsify(text, n, useWordBoundary)).toBe("It's...");
    });

    //note use of $j and $ is intentional
    it("should work when we call populateTarget", function () {
        var obj = {id:'12345', title:'a parent title'};

        var $container = $j('<div id="adhocTarget"></div>');
        $container.append($j('<input type="hidden" name="parentId" value="">'
            + '<input type="text" name="parentTitle" value="">'));
        setFixtures($container);
        //sanity check: did we really add this to dom?
        $expect('input').toHaveLength(2);

        $('body').data('adhocTarget', $container[0]);
        expect($container).toHaveLength(1);
        TDAR.common.populateTarget(obj);

        expect($container.find('[type=hidden]').val()).toBe(obj.id);
        expect($container.find('[type=text]').val()).toBe(obj.title);
    });

    it("shouldn't leak data when run our populateTarget test", function() {
        expect($j('body').data("adhocTarget")).not.toBeDefined();
    });

    it("should work when we call prepareDateFields", function () {
        var $form = $('<form>' + readFixtures('coverage-dates.html') + '</form>');
        var sel = $form.find('select')[0];
        var validator = $form.validate();
        setFixtures($form);

        //validation: everything blank - no errors 
        $(sel).val('NONE');
        TDAR.common.prepareDateFields(sel);
        $form.valid();
        expect(validator.errorList.length).toBe(0);
        
        //validation: coverage date incomplete 
        $(sel).val('CALENDAR_DATE');
        TDAR.common.prepareDateFields(sel);
        $form.find('.coverageStartYear').val('2001');
        $form.valid();
        expect(validator.errorList.length).toBeGreaterThan(0);

        //validation: 2001-1999 is an invalid calendar date...
        $(sel).val('CALENDAR_DATE');
        TDAR.common.prepareDateFields(sel);
        $form.find('.coverageStartYear').val('2001');
        $form.find('.coverageEndYear').val('1999');
        $form.valid();
        expect(validator.errorList.length).toBeGreaterThan(0);

        //validation: ...but it's a valid radiocarbon date
        $(sel).val('RADIOCARBON_DATE');
        TDAR.common.prepareDateFields(sel);
        $form.valid();
        expect(validator.errorList.length).toBe(0);
    });

    it("should set setAdhocTarget", function () {

        var $container = $j('<div id="adhocTarget"></div>');
        $container.append(
            $j('<input type="hidden" id="hiddenParentId" name="parentId" value="">'
            + '<input type="text" name="parentTitle" value="">'));
        setFixtures($container);

        var selector = "#adhocTarget"
        TDAR.common.setAdhocTarget($('#hiddenParentId')[0], selector )

        expect($('body').data()).toBeDefined()
        expect($('body').data('adhocTarget').html()).toBe($container.html())
    });

    it("registers download links", function () {
        var url = 'http://insanity.today';
        var tdarId = 110102;
        var expectedVal = null;

        //ignore '_trackEvent failed'; we only care that the message is put on the queue
        var result = TDAR.common.registerDownload(url, tdarId); 
        expect(_gaq).toHaveLength(1);
    });

    it("registers 'share' buttons", function () {
        var service = 'instagram';
        var url = 'http://insane.solutions';
        var tdarId = 1234;
        //ignore '_trackEvent failed'; we only care that the message is put on the queue
        var result = TDAR.common.registerShare(service, url, tdarId);
        expect(_gaq).toHaveLength(1);
        expect(_gaq[0][1]).toBe(service);
    });

    it("queues google analytics events", function () {
        var expectedVal = null;

        //ignore '_trackEvent failed'; we only care that the message is put on the queue
        var result = TDAR.common.gaevent('one', 'two', 'three');
        expect(_gaq[0][1]).toBe('one');
        expect(_gaq[0][2]).toBe('two');
        expect(_gaq[0][3]).toBe('three');

    });

    it("registers outbound link clicks", function () {
        var elem = document.createElement('A');
        elem.href = 'http://www.cnn.com'
        var expectedVal = null;

        //ignore '_trackEvent failed'; we only care that the message is put on the queue
        var result = TDAR.common.outboundLink(elem);
        expect(_gaq).toHaveLength(1);
        expect(_gaq[0][2]).toBe(elem.href);
    });

    it("should initialize the coding-sheet / ontology  validation rules", function () {
        var form = $j('<form></form>');
        form.append(readFixtures('supporting-resource-upload.html'));
        setFixtures(form);
        $('form').validate();
        TDAR.common.setupSupportingResourceForm(1, 'coding-sheet');
    });

    it("should apply coding-sheet & ontology validation rules", function() {
        setFixtures('<form>' + readFixtures('supporting-resource-upload.html') + '</form>');
        var validator = $('form').validate();
        TDAR.common.setupSupportingResourceForm(1, 'coding-sheet');
        $('form').valid()
        expect(validator.errorList).toHaveLength(0);

        TDAR.common.setupSupportingResourceForm(0, 'coding-sheet');
        $('form').valid()
        expect(validator.errorList).toHaveLength(2);
    });

    it("field visibility changes depending on documenttype", function () {
        var $form = $('<form></form>');
        var $container = $('<div id="divContainer"></div>');
        $form
            .append('<input id="fakeRadio" type="hidden" name="documentType" value="">')
            .append($container);
        $container
            .append('<div class="typeToggle type1">type1</div>')
            .append('<div class="typeToggle type2">type2</div>')
            .append('<div class="typeToggle type3">type3</div>')
            .append('<div class="typeToggle type4 type5 type6">type456</div>');
        setFixtures($form);
        var $fakeRadio = $('#fakeRadio');

        for(var i = 1; i <= 6; i++) {
            $fakeRadio.val('type' + i);
            TDAR.common.switchType($fakeRadio, $container);
            if(i < 4) {
                //if section doesn't have a class that corresponds to selected doctype that section should be invisible.
                expect($('.typeToggle:visible').length).toBe(1);
                expect($('.typeToggle:not(visible)').length).toBe(4);

            } else {
                //doctype fields are not mutually exclusive.  a section may be visible for more than one doctype
                expect($('.typeToggle:visible')).toHaveClass('type4');
                expect($('.typeToggle:visible')).toHaveClass('type5');
                expect($('.typeToggle:visible')).toHaveClass('type6');
            }
        }
    });

    it("sets up the documentType switcher", function () {
        var $form = $('<form class="doctype" id="citationInformation"></form>');
        var $container = $('<div id="divContainer"></div>');
        $form
            .append('<input type="radio" class="" name="dts" value="type1">opt1 ' + 
                '<input type="radio" class="" name="dts" value="type2" checked="checked">')
            .append($container);
        $container
            .append('<div class="doctypeToggle type1">type1</div>')
            .append('<div class="doctypeToggle type2">type2</div>');
        setFixtures($form);

        TDAR.common.setupDocumentEditForm();
        expect($('.doctypeToggle:visible')).toHaveLength(1);
        expect($('.doctypeToggle:visible')).toHaveText('type2');
    });



    it("expands template strings with sprintf()", function () {

        var format = "Hello {0}, good {1}! How are things in {0}-land?"
        var result = TDAR.common.sprintf(format, 'bob', 'morning');
        expect(result).toBe("Hello bob, good morning! How are things in bob-land?")

    });

    it("decodes html strings", function () {
        expect(TDAR.common.htmlDecode('&amp;')).toBe('&'); 
    });

    it("encode strings into html", function () {

        expect(TDAR.common.htmlEncode('&')).toBe('&amp;'); 
    });

    it("should work when we call htmlDoubleEncode", function () {
        var value = null;
        var expectedVal = null;

        //var result = TDAR.common.htmlDoubleEncode(value);
        expect(TDAR.common.htmlDoubleEncode('&')).toBe('&amp;amp;');
    });

    xit("should work when we call coordinatesCheckboxClicked", function () {
        var elem = null;
        var expectedVal = null;

        //var result = TDAR.common.coordinatesCheckboxClicked(elem);
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call refreshInputDisplay", function () {
        var expectedVal = null;

        //var result = TDAR.common.refreshInputDisplay();
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call tmpl", function () {
        var a = null;
        var c = null;
        var expectedVal = null;

        //var result = TDAR.common.tmpl(a, c);
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call validateProfileImage", function () {
        var expectedVal = null;

        //var result = TDAR.common.validateProfileImage();
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call collectionTreeview", function () {
        var expectedVal = null;

        //var result = TDAR.common.collectionTreeview();
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call humanFileSize", function () {
        var bytes = null;
        var si = null;
        var expectedVal = null;

        //var result = TDAR.common.humanFileSize(bytes, si);
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call initImageGallery", function () {
        var expectedVal = null;

        //var result = TDAR.common.initImageGallery();
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call formatNumber", function () {
        var num = null;
        var expectedVal = null;

        //var result = TDAR.common.formatNumber(num);
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call registerAjaxStatusContainer", function () {
        var expectedVal = null;

        //var result = TDAR.common.registerAjaxStatusContainer();
        expect(true).toBe(false); //fixme: implement this test
    });

    xit("should work when we call suppressKeypressFormSubmissions", function () {
        var $form = null;
        var expectedVal = null;

        //var result = TDAR.common.suppressKeypressFormSubmissions($form);
        expect(true).toBe(false); //fixme: implement this test
    });

});
