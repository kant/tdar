/**
 * common functions to the tDAR homepage
 */

const common = require("./tdar.common");

function _init() {
    $("#welcome-menu a").click(function() {
        $(".welcome-drop").toggle();
        return false;
    });

    // for the last 60 pixels of the searchboxes, make it submit the parent form
    $(".searchbox").click(function(e) {
        var $t = $(e.target);
        var ar = $(e.target).offset().left + $t.width() - 60;
        if (e.pageX > ar && $t.val() != '') {
            $t.parents("form").submit();
        }
    });
    $(document).click(function() {
        $('.welcome-drop').hide();
    });

    common.applyWatermarks($('form.searchheader, form[name=searchheader]'));
}

module.exports = {
    "init" : _init,
    "main": _init
};
