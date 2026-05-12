<#-- GIS Polygon Capture Form Element Template -->

<#-- Resource hints for CDN preconnection - improves load time on slow networks -->
<link rel="preconnect" href="https://unpkg.com" crossorigin>
<link rel="preconnect" href="https://cdnjs.cloudflare.com" crossorigin>
<link rel="dns-prefetch" href="https://unpkg.com">
<link rel="dns-prefetch" href="https://cdnjs.cloudflare.com">

<div class="form-cell gis-form-cell-fullwidth" style="display: block !important; width: 100% !important; float: none !important; clear: both !important;" ${elementMetaData!}>
    <label class="label" style="display: block !important; width: 100% !important; max-width: 100% !important; float: none !important; margin-bottom: 8px !important; text-align: left !important;">
        ${element.properties.label!}
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>
    <div class="form-cell-value gis-form-cell-value-fullwidth" style="display: block !important; width: 100% !important; max-width: 100% !important; float: none !important; margin-left: 0 !important; padding-left: 0 !important;">
        <#-- Hidden field for form submission (stores GeoJSON) -->
        <textarea id="${fieldId!}" name="${elementParamName!}" class="gis-hidden-textarea" style="display:none;">${value!?html}</textarea>

        <#-- GIS Map container - height is set by JS on inner wrapper, container grows to fit panels -->
        <div id="${elementId!}" class="gis-map-container"></div>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Cache bust version - use Java value if provided, otherwise fallback -->
<#if gisCacheVersion?? && gisCacheVersion?has_content>
<#else>
<#assign gisCacheVersion = "20260128_v6">
</#if>

<#-- Load Leaflet CSS -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" 
      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" 
      crossorigin="" data-gis-css="true">
<link rel="stylesheet" href="${resourceBase!}gis-capture.css&v=${gisCacheVersion}" data-gis-css="true">

<#-- Load JS and initialize -->
<script>
(function() {
    // Unique instance ID for this element
    var instanceId = '${elementId!?replace("-", "_")}';

    // Prevent duplicate initialization
    if (window['GISCaptureLoaded_' + instanceId]) return;
    window['GISCaptureLoaded_' + instanceId] = true;

    // Debug logging
    var DEBUG = true;
    function log(msg) {
        if (DEBUG) console.log('[GIS ' + instanceId + '] ' + msg);
    }

    /**
     * Load a script by URL with optional fallback
     */
    function loadScript(src, callback, fallbackSrc) {
        var filename = src;
        var fileParam = src.match(/[?&]file=([^&]+)/);
        if (fileParam) {
            filename = fileParam[1];
        }

        log('Loading script: ' + filename);

        // Check if Leaflet is already loaded
        if (filename === 'leaflet.js' && typeof L !== 'undefined') {
            log('Leaflet already loaded');
            callback();
            return;
        }

        // Check if Turf is already loaded
        if (src.indexOf('turf') !== -1 && typeof turf !== 'undefined') {
            log('Turf.js already loaded');
            callback();
            return;
        }

        var existing = document.querySelector('script[data-gis-src="' + src + '"]');
        if (existing) {
            log('Script already loaded: ' + filename);
            callback();
            return;
        }

        var script = document.createElement('script');
        script.src = src;
        script.setAttribute('data-gis-file', filename);
        script.setAttribute('data-gis-src', src);

        script.onload = function() {
            log('Loaded: ' + filename);
            // Execute callback immediately - no artificial delay needed
            // The browser's onload event already ensures the script is ready
            callback();
        };

        script.onerror = function(e) {
            console.error('[GIS] Failed to load script: ' + src, e);
            // Try fallback CDN if available
            if (fallbackSrc) {
                log('Trying fallback CDN: ' + fallbackSrc);
                loadScript(fallbackSrc, callback);
            } else {
                callback();
            }
        };

        document.head.appendChild(script);
    }

    /**
     * Initialize the map component
     */
    var initMapRetryCount = 0;
    var MAX_INIT_RETRIES = 50; // Max 5 seconds of retries (50 * 100ms)

    function initMap() {
        log('initMap called');

        var container = document.getElementById('${elementId!}');
        if (!container) {
            initMapRetryCount++;
            if (initMapRetryCount > MAX_INIT_RETRIES) {
                console.error('[GIS] Container not found after ' + MAX_INIT_RETRIES + ' retries, giving up.');
                return;
            }
            log('Container not found, retrying in 100ms... (attempt ' + initMapRetryCount + ')');
            setTimeout(initMap, 100);
            return;
        }

        // Verify Leaflet is loaded
        if (typeof L === 'undefined') {
            console.error('[GIS] Leaflet is not loaded!');
            container.innerHTML = '<div style="color:red;padding:10px;">Error: Leaflet failed to load</div>';
            return;
        }

        // Verify GISCapture is loaded
        if (typeof GISCapture === 'undefined') {
            console.error('[GIS] GISCapture is not loaded!');
            container.innerHTML = '<div style="color:red;padding:10px;">Error: GISCapture failed to load</div>';
            return;
        }

        log('Initializing GISCapture...');

        try {
            var config = ${config!'{}'};

            // Validate API base URL for security (must be relative path or HTTPS)
            var apiBase = '${apiBase!}';
            if (apiBase && !apiBase.startsWith('/') && !apiBase.startsWith('https://')) {
                console.warn('[GIS] API base URL should use HTTPS or be a relative path');
            }

            var capture = GISCapture.init('${elementId!}', {
                apiBase: apiBase,
                apiId: '${apiId!}',
                apiKey: '${apiKey!}',
                hiddenFieldId: '${fieldId!}',
                recordId: '${recordId!}',
                outputFields: {
                    areaFieldId: '${areaFieldId!}',
                    perimeterFieldId: '${perimeterFieldId!}',
                    centroidFieldId: '${centroidFieldId!}',
                    vertexCountFieldId: '${vertexCountFieldId!}'
                },
                captureMode: config.captureMode || 'BOTH',
                defaultMode: config.defaultMode || 'AUTO',
                defaultLatitude: config.defaultLatitude || -29.5,
                defaultLongitude: config.defaultLongitude || 28.5,
                defaultZoom: config.defaultZoom || 10,
                tileProvider: config.tileProvider || 'OSM',
                showSatelliteOption: config.showSatelliteOption !== false,
                mapHeight: config.mapHeight || 400,
                validation: config.validation || {},
                gps: config.gps || {},
                style: config.style || {},
                overlap: config.overlap || null,
                nearbyParcels: config.nearbyParcels || null,
                autoCenter: config.autoCenter || null,
                onGeometryChange: function(geojson, metrics) {
                    log('Geometry changed: ' + (metrics ? metrics.areaHectares + ' ha' : 'cleared'));
                },
                onValidationError: function(errors) {
                    log('Validation errors: ' + errors.join(', '));
                },
                onError: function(error) {
                    console.error('[GIS] Error:', error);
                }
            });

            // Store reference globally
            window['gisCapture_${fieldId!}'] = capture;
            log('GIS Capture initialized successfully');

        } catch (e) {
            console.error('[GIS] Init error:', e);
            container.innerHTML = '<div style="color:red;padding:10px;">Error initializing map: ' + e.message + '</div>';
        }
    }

    // Define resource base and cache version
    var resourceBase = '${resourceBase!}';
    var cacheVersion = '${gisCacheVersion}';
    log('Resource base: ' + resourceBase + ', version: ' + cacheVersion);

    // Load scripts in sequence: Leaflet -> Turf -> Sweepline -> GIS Capture -> Init
    // Each has a fallback CDN in case the primary CDN is unavailable
    loadScript(
        'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
        function() {
            log('Leaflet loaded, typeof L = ' + typeof L);

            // Verify Leaflet loaded successfully
            if (typeof L === 'undefined') {
                console.error('[GIS] Leaflet failed to load from all CDNs');
                return;
            }

            loadScript(
                'https://unpkg.com/@turf/turf@6/turf.min.js',
                function() {
                    log('Turf.js loaded, typeof turf = ' + typeof turf);

                    // Verify Turf loaded successfully
                    if (typeof turf === 'undefined') {
                        console.error('[GIS] Turf.js failed to load from all CDNs');
                        return;
                    }

                    // Load sweepline-intersections for robust self-intersection detection fallback
                    // Using jsDelivr CDN (more reliable than unpkg for MIME types)
                    loadScript(
                        'https://cdn.jsdelivr.net/npm/sweepline-intersections@2.0.1/dist/sweeplineIntersections.min.js',
                        function() {
                            log('Sweepline loaded, typeof sweeplineIntersections = ' + typeof sweeplineIntersections);
                            // Note: sweepline is optional - continue even if it fails to load

                            loadScript(resourceBase + 'gis-capture.js&v=' + cacheVersion, function() {
                                log('GIS Capture loaded, typeof GISCapture = ' + typeof GISCapture);

                                if (document.readyState === 'complete') {
                                    initMap();
                                } else {
                                    window.addEventListener('load', initMap);
                                }
                            });
                        }
                    );
                },
                // Fallback CDN for Turf.js
                'https://cdnjs.cloudflare.com/ajax/libs/Turf.js/6.5.0/turf.min.js'
            );
        },
        // Fallback CDN for Leaflet
        'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js'
    );

})();
</script>

<style>
/* Full width layout for GIS element */
.gis-form-cell-fullwidth {
    display: block !important;
    width: 100% !important;
    float: none !important;
    clear: both !important;
}

.gis-form-cell-fullwidth > .label,
.gis-form-cell-fullwidth > label.label,
.gis-form-cell-fullwidth > label {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    float: none !important;
    margin-bottom: 8px !important;
    text-align: left !important;
}

.gis-form-cell-fullwidth > .form-cell-value {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    float: none !important;
    margin-left: 0 !important;
    padding-left: 0 !important;
}

.gis-hidden-textarea {
    display: none !important;
}

.gis-map-container {
    margin-top: 5px;
    border: 1px solid #ccc;
    border-radius: 4px;
    overflow: hidden;
}
</style>
