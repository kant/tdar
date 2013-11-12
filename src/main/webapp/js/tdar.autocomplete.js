/**
 * Autocomplete Support
 */
//HACK: jtd remove this line
$(function() {$(window).unbind("beforeunload")});

TDAR.namespace("autocomplete");
TDAR.autocomplete = (function() {
    "use strict";

    //when a user creates a record manually instead of choosing a menu-item from the autocomplete dropdown, this module
    //stores the record in the object cache.  If the user later fills out similar autocomplete fields,   we add
    //these cached records to the autocomplete dropdown.  This allows the user to save some time in situations where
    //a new value may appear several times on a form (e.g. a new person record  that should be listed as an 'author',
    // 'editor', and 'contact'.
    var _caches = {};
    function ObjectCache(acOptions) {
        this.acOptions = acOptions;
        this.namespace = acOptions.url || "root";
        this.parentMap = {};
        this.objectMapper = acOptions.objectMapper || _objectFromAutocompleteParent;

        //_caches[this.namespace] = this;
    };

    ObjectCache.prototype = {
        put: function(val) {
            this.cache.push(val);
            console.log("adding val to %s cache", this.namespace, val);
        },

        //register the fields inside this parent as an 'extra record'. When caller invokes getValues(), this class
        //will generate records based for all the registeredRecords
        register: function(parentElem) {
            //prevent dupe registration
            if($(parentElem).hasClass("autocomplete-new-record")) {
                return;
            }
            var self = this,
                parentId = parentElem.id;
            this.parentMap[parentId] = parentElem;

            $(parentElem).addClass("autocomplete-new-record");

            //if user removes the row then unregister the associated record
            $(parentElem).bind("remove", function() {
                self.unregister(parentId);
            });
        },

        unregister: function(parentId) {
            delete this.parentMap[parentId];
        },

        getValues: function() {
            //var keys = Object.keys(this.parentMap).sort();
            var values = [];
            for(var parentId in this.parentMap) {
                var elem = this.parentMap[parentId];
                values.push(this.objectMapper(elem));
            }
            return values;
        },

        search: function(term) {
            //get current state of the new records;
            return this.cache;
        }
    };

    //return subset of getValues() for any partial matches of term in object[key].  If key not supplied,  search
    //all fields in each object for a partial match
    ObjectCache.basicSearch = function(term, key) {
        var values = this.getValues();
        var ret = $.grep(values, function(obj) {
            var keys = Object.keys(obj);
            if(key) keys = [key];
            for(var i = 0; i < keys.length; i++) {
                var val = obj[keys[i]];
                if(val) {
                    if(val.toLowerCase().indexOf(term) > -1 ) {
                        return true;
                    }
                }
            }
        });
        return ret;
    };

    //grab cache for specified url or create one
    function _getCache(options) {
        if(!_caches[options.url]) {
            _caches[options.url] = new ObjectCache(options);
        }
        return _caches[options.url];
    }

function _buildRequestData(element) {
    var data = {};
    //    console.log("autocompleteParentElement: " + element.attr("autocompleteParentElement"));
    if (element.attr("autocompleteParentElement")) {
        $("[autocompleteName]", element.attr("autocompleteParentElement")).each(function(index, elem) {
            var $elem = $(elem);
            data[$elem.attr("autocompleteName")] = $.trim($(elem).val());
            //                            console.log("autocompleteName: " + $val.attr("autocompleteName") + "==" + $val.val());
        });
    }
    return data;
}

    /**
     * translate item property values to form fiends contained in a autocompleteParentElement
     * @param element any .ui-autocomplete-input field contained by the autocompleteParent element
     * @param item the source object. the function copies the item property values to the input fields under the parent
     * @private
     */
function _applyDataElements(element, item) {
    var $element = $(element);
    if ($element.attr("autocompleteParentElement") != undefined) {
        $("[autocompleteName]", $element.attr("autocompleteParentElement")).each(function(index, val) {
            var $val = $(val);
            var newvalue = item[$val.attr("autocompleteName")];
            if (newvalue != undefined) {
                var valueToSet = newvalue;
                if (newvalue.constructor === String) {

                } else {
                    if (newvalue['name'] != undefined) {
                        valueToSet = newvalue['name'];
                    }
                    if (newvalue['label'] != undefined) {
                        valueToSet = newvalue['label'];
                    }
                }

                $val.val(valueToSet);
                //                         console.log("setting: " + val.name +  "["+$val.attr("autocompleteName")+"]" + ":" + valueToSet);
                $val.attr("autoVal", valueToSet);
            }
        });
        if ($element.attr("autocompleteName") != undefined) {
            item.value = $element.attr("autoVal");
        }
    }

    //if id element defined,  set it's value
    if ($element.attr("autocompleteIdElement")) {
        var $idElement = $($element.attr("autocompleteIdElement"));

        if (item["id"] != undefined) {
            $idElement.val(item["id"]);
        }
    } else {
        //TODO:  confirm  $element.closest('.autocomplete-id-element') will work for all use cases. 
    }

}

function _renderPerson(ul, item) {
    var htmlDoubleEncode = TDAR.common.htmlDoubleEncode,
        encProperName = htmlDoubleEncode(item.properName),
        encEmail = htmlDoubleEncode(item.email),
        institution = item.institution ? item.institution.name || "" : "";

    //double-encode on custom render
    //FIXME: use tmpl maybe?
    var htmlSnippet = "<p style='min-height:4em'><img class='silhouette pull-left' src=\"" + getBaseURI() +
        "images/man_silhouette_clip_art_9510.jpg\" />" + "<span class='name'>" + encProperName + "</span><span class='email'>(" +
        encEmail + ")</span><br/><span class='institution'>" + htmlDoubleEncode(institution) + "</span></p>";
    if (item.id == -1 && item.showCreate) {
        htmlSnippet = "<p style='min-height:4em'><img class='silhouette pull-left' src=\"" + getURI("images/man_silhouette_clip_art_9510.jpg") + "\" />" +
            "<span class='name'><em>Create a new person record</em></span> </p>";
    }
    return $("<li></li>").data("item.autocomplete", item).append("<a>" + htmlSnippet + "</a>").appendTo(ul);
};

function _evaluateAutocompleteRowAsEmpty(element, minCount) {
    var req = _buildRequestData($(element));
    var total = 0;
    //FIXME:  I think 'ignored' is irrelevant as defined here.  Can we remove this?
    var ignored = new Array();
    if (minCount != undefined) {
        ignored = minCount;
    }

    var $idElement = $($(element).attr("autocompleteIdElement"));
    var allowNew = $idElement.attr("allowNew");

    var nonempty = 0;
    // for each item in the request
    for ( var p in req) {
        total++;
        if ($.inArray(p, ignored) == -1 && req[p] != undefined && req[p] != '') {
            nonempty++;
        }
        if (p == "id") {
            nonempty++;
        }
    }
    //    console.log("req size:" + total + " nonEmpty:" + nonempty + " ignored:" + ignored);
    if (nonempty == 0) {
        return true;
    }

    if (allowNew != undefined && allowNew == "true" && ($idElement.val() == "" || $idElement.val() == -1)) {
        return true;
    }

    return false;
}

//if user tabs away from autocomplete field instead of selecting valid menu item,  register as new record
function _registerOnBlur(objectCache, elem) {
    var parentid = $(elem).attr("autocompleteparentelement");
    var $parentElem = $(parentid);
    var $hidden = $parentElem.find("input[type=hidden]").first();

    $parentElem.find(".ui-autocomplete-input").bind("blur", function() {
        var hiddenVal = $hidden.val();
        if((hiddenVal === "" || hiddenVal === "-1") && this.value !== "") {
            objectCache.register($parentElem.get());
        }
    });
}

function _applyGenericAutocomplete($elements, opts) {
    var options = $.extend({

        //callback function that returns list of extra items to include in dropdown: function(options, requestData)
        addCustomValuesToReturn: function(term) {
            return cache.search(term);
        }
    }, opts);

    var cache = _getCache(options);

    // if there's a change in the autocomplete, reset the ID to ""
    $elements.change(function() {
        var $element = $(this);
        // if the existing autocomplete value stored in the "autoVal" attribute does is not undefined and is not the same as the current
        // evaluate it for being significant (important when trying to figure out if a minimum set of fields have been filled in
        if (($element.attr("autoVal") != undefined && $element.attr("autoVal") != $element.val()) ||
                _evaluateAutocompleteRowAsEmpty(this, options.ignoreRequestOptionsWhenEvaluatingEmptyRow == undefined ? []
                        : options.ignoreRequestOptionsWhenEvaluatingEmptyRow)) {
            if ($element.attr("autocompleteIdElement")) {
                var $idElement = $($element.attr("autocompleteIdElement"));
                $idElement.val("");

            } else {
                //TODO:  confirm  $element.closest('.autocomplete-id-element') will work for all use cases.
            }
        }
        return true;
    });

    //set allowNew attribute for each element's corresponding 'id' element
    $elements.each(function() {
        if (options.showCreate) {
            var $idElement = $($(this).attr("autocompleteIdElement"));
            $idElement.attr("allowNew", "true");
        }
    });

    //register the autocomplete for each element
    var autoResult = $elements.autocomplete({
        source : function(request, response) {
            var $elem = $(this.element);

            //is another ajax request in flight?
            var oldResponseHolder = $elem.data('responseHolder');
            if (oldResponseHolder) {
                //cancel the previous search
                //                        console.log("cancelling previous search");
                oldResponseHolder.callback({});

                //swap out the no-op before the xhrhhtp.success callback calls it
                oldResponseHolder.callback = function() {
                    //                            console.log("an ajax success callback just called a now-defunct response callback");
                };
            }

            // add requestData that's passed from the options
            var requestData = {};
            if (options.requestData != undefined) {
                $.extend(requestData, options.requestData);
            }

            // add the sortField
            if (options.sortField != undefined) {
                requestData.sortField = options.sortField;
            }

            // hard-code map for term
            if (request.term != undefined) {
                requestData.term = request.term;
            }
            // more generic map for any form based
            // autocomplete elements
            $.extend(requestData, _buildRequestData(this.element));

            // final callback for using custom method
            if (options.enhanceRequestData != undefined) {
                options.enhanceRequestData(requestData, request);
            }

            //add a closure to ajax request that wraps the response callback. This way we can swap it out for a no-op if a new source() request
            //happens before the existing is complete.
            var responseHolder = {};
            responseHolder.callback = response;
            $elem.data('responseHolder', responseHolder);

            var ajaxRequest = {
                url : getBaseURI() + options.url,
                dataType : "jsonp",
                data : requestData,
                success : function(data) {
                    if (!$elem.is(':focus')) {
                        console.debug("input blurred before autocomplete results returned. returning no elements");
                        responseHolder.callback({});
                        return;
                    }

                    // if there's a custom dataMap function, use that, otherwise not
                    if (options.customDisplayMap == undefined) {
                        options.customDisplayMap = function(item) {
                            if (item.name != undefined && options.dataPath != 'person') {
                                // there is no need to escape this because we're rendering as plain text
                                item.label = item.name;
                            }
                            return item;
                        };
                    }

                    //tdar lookup returns an object that wraps the results - the property with the results is specified by options.dataPath
                    var dataItems = typeof options.dataPath === "function" ? options.dataPath(data) : data[options.dataPath];
                    var values = $.map(dataItems, options.customDisplayMap);

                    // enable custom data to be pushed onto values
                    if (options.addCustomValuesToReturn) {
                        var extraValues = options.addCustomValuesToReturn(request.term);
                        // could be push, need to test
                        if(extraValues) {
                            values = values.concat(extraValues);
                        }
                    }
                    console.log(options.dataPath + " autocomplete returned " + values.length);

                    if (options.showCreate) {
                        var createRow = _buildRequestData($elem);
                        createRow.value = request.term;
                        // allow for custom phrasing
                        if (options.showCreatePhrase) {
                            createRow.label = "(" + options.showCreatePhrase + ": " + request.term + ")";
                        }
                        createRow.id = -1;
                        createRow.isNewItem = true;
                        createRow.showCreate = true;
                        values.push(createRow);
                    }
                    responseHolder.callback(values);
                },

                complete : function() {
                    $elem.removeData('responseHolder');
                }
            };
            $.ajax(ajaxRequest);
        },
        minLength : options.minLength || 0,
        select : function(event, ui) {
            var $elem = $(event.target);
            _applyDataElements(this, ui.item);

            //cancel any pending searches once the user selects an item
            var responseHolder = $elem.data('responseHolder');
            if (responseHolder) {
                responseHolder.callback();
                responseHolder.callback = function() {
                };
            }

            //if user selects 'create new' option, add it to the new item cache and stop trying to find matches.
            if(ui.item.isNewItem) {
                var $parent = $($elem.attr("autocompleteparentelement"));
                cache.register($parent.get());
                $parent.find(".ui-autocomplete-input").autocomplete("disable");
            }
        },
        open : function() {
            $(this).removeClass("ui-corner-all").addClass("ui-corner-top");
            if (options.customRender != undefined) {
                $("ul.ui-autocomplete li a").each(function() {
                    var htmlString = $(this).html().replace(/&lt;/g, '<');
                    htmlString = htmlString.replace(/&gt;/g, '>');
                    $(this).html(htmlString);
                });
            }
            $("ul.ui-autocomplete").css("width", $(this).parent().width());
        },
        close : function() {
            $(this).removeClass("ui-corner-top").addClass("ui-corner-all");
        }
    });
    if (options.customRender != undefined) {
        autoResult.each(function(idx, elem) {
            // handle custom rendering of result
            $(elem).data("autocomplete")._renderItem = options.customRender;
        });
    }

    $elements.filter("[autocompleteparentelement]").each(function(){
        _registerOnBlur(cache, this);
    });

};

function _applyKeywordAutocomplete(selector, lookupType, extraData, newOption) {
    var options = {};
    options.url = "lookup/" + lookupType;
    options.enhanceRequestData = function(requestData) {
        $.extend(requestData, extraData);
    };

    options.dataPath = "items";
    options.sortField = 'LABEL';
    options.showCreate = newOption;
    options.showCreatePhrase = "Create a new keyword";
    options.minLength = 2;
    _applyGenericAutocomplete($(selector), options);
}

//todo: i'm guessing that the user control may end up using a different objectMapper & customRender
function _applyUserAutoComplete($elements) {
    _applyGenericAutocomplete($elements, {
        url: "lookup/person",
        dataPath: "people",
        retainInputValueOnSelect: true,
        showCreate: false,
        minLength: 3,
        customRender: _renderPerson,
        requestData:  {
            registered: true
        }
    });
}

function _applyPersonAutoComplete($elements, usersOnly, showCreate) {
    var options = {
        url: "lookup/person",
        dataPath: "people",
        retainInputValueOnSelect: true,
        showCreate: showCreate,
        minLength: 3,
        customRender: _renderPerson,
        requestData:  {
            registered: usersOnly
        },
        objectMapper: function(parentElem) {
            var obj = _objectFromAutocompleteParent(parentElem)
            obj.properName = obj.firstName + " " + obj.lastName;
            return obj;
        }
    };
    _applyGenericAutocomplete($elements, options);
    _getCache(options).search = ObjectCache.basicSearch;

}


function _applyCollectionAutocomplete($elements, options, extraData) {
    //FIXME: HACK: this is a bandaid.  need better way to not bind multiple autocompletes
    if($elements.data("autocompleteApplied")) return true;
    $elements.data("autocompleteApplied", true);
    var _options = {};
    if (typeof options === "object") {
        _options = options;
    }
    var defaults = {};
    options.enhanceRequestData = function(requestData) {
        $.extend(requestData, extraData);
    };

    defaults.url = "lookup/collection";
    defaults.dataPath = "collections";
    defaults.sortField = 'TITLE';
    defaults.showCreate = false;
    if (defaults.showCreate) {
        defaults.showCreatePhrase = "Create a new collection";
    }
    defaults.minLength = 2;
    _applyGenericAutocomplete($elements, $.extend({}, defaults, _options));
}

function _displayResourceAutocomplete(item) {
    var label = "";
    if (item.name) {
        label = item.name;
    }
    if (item.title) {
        label = item.title;
    }
    item.value = label + " (" + item.id + ") ";
    return item;
}

function _applyResourceAutocomplete($elements, type) {
    var options = {};
    options.url = "lookup/resource";
    options.dataPath = "resources";
    options.sortField = 'TITLE';
    options.enhanceRequestData = function(requestData) {
        if (requestData["subCategoryId"] != undefined && requestData["subCategoryId"] != '' && requestData["subCategoryId"] != -1) {
            requestData["sortCategoryId"] = requestData["subCategoryId"];
        }
        requestData.resourceTypes = type;
    };
    options.ignoreRequestOptionsWhenEvaluatingEmptyRow = [ "subCategoryId", "sortCategoryId" ];
    options.minLength = 0;
    options.customDisplayMap = _displayResourceAutocomplete;
    options.customRender = function(ul, item) {
        var description = "";
        //            console.log(item);
        if (item.description != undefined) {
            description = item.description;
        }
        var link = "";
        if (item.urlNamespace) {
            // link = "<b onClick=\"openWindow('"+ getBaseURI() +
            // item.urlNamespace + "/view/" + item.id +"\')\">view</b>";
        }
        //double-encode on custom render
        return $("<li></li>").data("item.autocomplete", item).append(
                "<a  title=\"" + TDAR.common.htmlDecode(description) + "\">" + TDAR.common.htmlDoubleEncode(item.value) + link + "</a>").appendTo(ul);
    };

    _applyGenericAutocomplete($elements, options);
    $elements.autocomplete("option", "delay", 600);
}

function _applyInstitutionAutocomplete($elements, newOption) {

    var options = {};
    options.url = "lookup/institution";
    options.dataPath = "institutions";
    options.sortField = 'CREATOR_NAME';
    options.enhanceRequestData = function(requestData) {
        requestData.institution = requestData.term;
    };
    options.showCreate = newOption;
    options.minLength = 2;
    options.showCreatePhrase = "Create new institution";
    _applyGenericAutocomplete($elements, options);
};

function _autocompleteShowAll() {
    $(this).siblings('input[type=text]').focus().autocomplete("search", "");
}

function _applyComboboxAutocomplete($elements, type) {
    "use strict";
    
    //register autocomplete text box
    //TODO: defer autocomplete registration if better perf needed,  but "show all" button must be registered at onload
    _applyResourceAutocomplete($elements, type);
    
    //register "show-all" click
    $elements.each(function() {
            var $controls = $(this).closest('.controls');
            var $textInput = $controls.find("input[type=text]");
            var $button = $controls.find("button.show-all");
            $button.click(function(){
                $textInput.focus().autocomplete("search", "");
            });
    });
}

/**
 * return an object from any autocomplete input elements inside the specified parentElem elment. This function
 * maps every .autocomplete-ui-input  into a property of the returned object.  The property name is based on the
 * value of the "autocompletename" attribute of the input element (or the value of the 'name' attribute, if no
 * autocompletename attribute specified.
 *
 * @param parentElem
 */
function _objectFromAutocompleteParent(parentElem) {
    var obj = {};
    $(parentElem).find(".ui-autocomplete-input").each(function(idx) {
        var key = $(this).attr("autocompletename") || this.name;
        obj[key] = this.value;
    });
    return obj;
}

return {
    applyPersonAutoComplete: _applyPersonAutoComplete,
    evaluateAutocompleteRowAsEmpty: _evaluateAutocompleteRowAsEmpty,
    applyKeywordAutocomplete: _applyKeywordAutocomplete,
    applyCollectionAutocomplete: _applyCollectionAutocomplete,
    applyResourceAutocomplete: _applyResourceAutocomplete,
    applyInstitutionAutocomplete: _applyInstitutionAutocomplete,
    applyComboboxAutocomplete: _applyComboboxAutocomplete,
    objectFromAutocompleteParent: _objectFromAutocompleteParent
    };
})();