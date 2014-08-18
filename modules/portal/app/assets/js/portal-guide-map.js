/* General functions */
function isNumeric(n) {
	return !isNaN(parseFloat(n)) && isFinite(n);
}
/* Default Params */
var	mapParams = {
		lat : 50.508174054149,
		lng : 14.152353697237,
		zoom : 13
	},
	RedIcon = L.Icon.Default.extend({options: { iconUrl: redIcon_URL} }),
	redIcon = new RedIcon(),
	$map = L.map('map').setView([mapParams.lat, mapParams.lng], mapParams.zoom),
	$items = {},
	$bounds = [],
	$markers = {};

/*
 *
 *	Merging Params function
 *
 */

 (window.onpopstate = function () {
	var match,
		pl     = /\+/g,  // Regex for replacing addition symbol with a space
		search = /([^&=]+)=?([^&]*)/g,
		decode = function (s) { return decodeURIComponent(s.replace(pl, " ")); },
		query  = window.location.search.substring(1);

	while (match = search.exec(templateParams))
	   if(isNumeric(match[2])) { mapParams[decode(match[1])] = parseFloat(decode(match[2])); }
	   else { mapParams[decode(match[1])] = decode(match[2]); }

	while (match = search.exec(query))
	   if(isNumeric(match[2])) { mapParams[decode(match[1])] = parseFloat(decode(match[2])); }
	   else { mapParams[decode(match[1])] = decode(match[2]); }
})();


/*
 *
 *	MAP FUNCTIONS
 *
 */

addMarker = function(data) {
	if(!$items[data.id]) {
		$items[data.id] = true;

	 			var markerLocation = new L.LatLng(parseFloat(data.latitude),parseFloat(data.longitude));

		var markerProperty = {
			title : data.name,
			alt : data.name
		};
		if(ORIGINAL === true) {
			markerProperty.icon = redIcon;
		}

		$items[data.id] = L.marker(markerLocation, markerProperty).addTo($map).bindPopup('<b>'+ data.name + '</b>', {
			minWidth : 300
		});
		return $items[data.id];
	}
	return false;
}

addMarkerList = function(data) {
	var i = 0;
	while(data[i]) {
		if(data[i].latitude !== "None" && data[i].longitude !== "None") {
			bindMarker(addMarker(data[i]), data[i].id, data[i].links)
		}
		++i;
	}
}

panToPopup = function(elem)  {
	var px = $map.project(elem.popup._latlng);
	px.y -= elem.popup._container.clientHeight/2
	$map.panTo($map.unproject(px),{animate: true});
}

bindMarker = function(marker, id, linkCount) {
	var linkCount = parseInt(linkCount);
	if(marker) {
		marker.on("popupopen", function(elem) {
			if(id in $markers) {
				var html = $markers[id];
				$(elem.popup._contentNode).html(html);
				panToPopup(elem);
			} else {
				if(linkCount > 0) {
					$.get(jsRoutes.controllers.portal.Portal.linkedDataInContext(id , VIRTUAL_UNIT).url, function(data) {
						var links = [];
						$.each(data, function(index, link) {
							links.push('<li><a href="'+ jsRoutes.controllers.portal.Guides.browseDocument(GUIDE_PATH, link.id).url +'">'+ link.name+'</a></li>')
						})
						var html = $(elem.popup._contentNode).html();
						if(links.length > 0) { var html = html + '<ul class="list-unstyled">' + links.join(" "); }
						if(links.length == 5) { var html = html + '<li><a href="'+ VIRTUAL_UNIT_ROUTE+ id + '"> ' + (linkCount - 5) + ' More...</a></li>'; }
						if(links.length > 0) { var html = html + '</ul>'; }
						$markers[id] = html;
						$(elem.popup._contentNode).html(html)
						panToPopup(elem);
					});
				} else {
					var html = $(elem.popup._contentNode).html();
					$markers[id] = html;
					panToPopup(elem);
				}
			}

		})
	}
}


/*
Initiate Map
*/
L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
	attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
}).addTo($map);


/*
Movement loading and Maps Triggers
*/
$map.on('moveend', function(e) {
	$.get(MAP_URL, { lat : $map.getCenter().lat, lng: $map.getCenter().lng }, 
		function (data) { 
			addMarkerList(data);
		}, "json")
});

$(".zoom-to").on("click", function(e) {
	e.preventDefault()
	var $elem = $(this),
		$lng = $elem.attr("data-longitude"),
		$lat = $elem.attr("data-latitude"),
		$id = $elem.attr("data-id"),
		$markerLocation = new L.LatLng(parseFloat($lat),parseFloat($lng));
	$map.panTo($markerLocation)
	$items[$id].openPopup()
})


/* Initiate */
$(document).ready(function() {
	console.log($originalMarkers)
	ORIGINAL = false;
	if("q" in mapParams && mapParams.q.length > 0 && mapParams.q != "undefined") {
		ORIGINAL = true;
	}
	addMarkerList($originalMarkers)
	$.each($originalMarkers, function(i, m) {
		$bounds.push([parseFloat(m.latitude), parseFloat(m.longitude)])
	});
	$map.fitBounds($bounds);
	ORIGINAL = false;
})
