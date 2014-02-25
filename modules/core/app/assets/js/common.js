
// Global var for select 2 options...
window.select2Opts = {
  placeholder: "Select an option...",
  allowClear: true,
  dropdownAutoWidth: true,
  dropdownCssClass: "facet-select-dropdown",
  minimumInputLength: 0
};

jQuery(function($) {

  var FB_REDIRECT_HASH = "#_=_";

  /**
   * Description viewport code. This fixes a viewport to a list
   * of item descriptions so only the selected one is present
   * at any time.
   */

    // HACK! If there's a description viewport, disable jumping
    // to the element on page load... this is soooo horrible.

  /**
   * Determine if the fragment refers to a description element.
   */
  function isDescriptionRef(descId) {
    // NB: The _=_ is what Facebook adds to Oauth login redirects
    return descId
        && descId != FB_REDIRECT_HASH
        && $(descId).hasClass("description-holder");
  }

  setTimeout(function() {
    if (isDescriptionRef(location.hash)) {
      window.scrollTo(0, 0);
    }
  }, 0);

  $(".description-switch").click(function(e) {
    switchDescription(this.href);
  })

  function switchDescription(descId) {
    $(".description-viewport").each(function(i, elem) {

      var $vp = $(elem);
      var $descs = $vp.find(".description-holder");

      // If the hash isn't set, default to the first element
      if (!descId) {
        descId = "#" + $descs.first().attr("id");
      }

      var $theitem = $(descId, $vp);

      $theitem.show();

      $descs.not($theitem).hide();

      // Set the active class on the current description
      $(".description-switch[href='" + descId + "']").parent().addClass("active")
      $(".description-switch[href!='" + descId + "']").parent().removeClass("active")
    });

  }

  function collapseDescriptions() {
    if (isDescriptionRef(location.hash)) {
      switchDescription(location.hash);
    } else {
      switchDescription();
    }
  }

  History.Adapter.bind(window, 'hashchange', collapseDescriptions);

  // Trigger a change on initial load...
  collapseDescriptions();


  // Re-check select2s whenever there's an Ajax event that could
  // load a widget (e.g. the profile form)
  $(document).ajaxComplete(function () {
    //$(".select2").select2(select2Opts);
  });

  $(".select2").select2(select2Opts).change(function (e) {
    if ($(e.target).hasClass("autosubmit")) {
      $(e.target).closest("form").submit();
    }
  });

  $(document).on("change", ".facet-toggle", function (e) {
    $(e.target).closest("form").submit();
  });
});
