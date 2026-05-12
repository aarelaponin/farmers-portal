console.log('=== GIS-CAPTURE.JS LOADED - BUILD 2026-01-28-v6 (GIS_DEBUG) ===');
/**
 * GIS Polygon Capture Component
 *
 * A Leaflet.js-based component for capturing polygon boundaries
 * with Walk Mode (GPS) and Draw Mode (desktop) support.
 *
 * @version 2.0 - Performance & Maintainability Refactor
 *
 * =============================================================================
 * MODULE ARCHITECTURE
 * =============================================================================
 *
 * This file is organized into logical sections for maintainability:
 *
 * 1. UTILITIES              (line ~30)    - throttle, debounce, escapeHtml
 * 2. TILE_PROVIDERS         (line ~115)   - Map tile configurations
 * 3. GISCaptureInstance     (line ~160)   - Main component class
 *    ├── CORE METHODS                     - Initialization, state management
 *    ├── UI METHODS                       - Building UI components
 *    ├── MAP METHODS                      - Leaflet map operations
 *    ├── ACCESSIBILITY                    - Screen reader, keyboard nav
 *    ├── DRAWING METHODS                  - Draw/Walk mode vertex capture
 *    ├── VALIDATION MODULE                - Area warnings, self-intersection
 *    ├── OVERLAP MODULE                   - API calls, overlap display
 *    ├── NEARBY PARCELS MODULE            - Read-only parcel display
 *    ├── AUTO-CENTER MODULE               - Field monitoring, geocoding
 *    └── CLEANUP                          - Event listeners, destroy
 * 4. PUBLIC API             (line ~4350)  - GISCapture.init()
 *
 * DEPENDENCIES:
 * - Leaflet.js 1.9.x       - Map rendering
 * - Turf.js 6.x            - Geometry calculations
 *
 * PERFORMANCE OPTIMIZATIONS (v2.0):
 * - Throttled vertex drag updates (60fps visual, debounced calculations)
 * - Debounced API calls for overlap/nearby parcels
 * - Event-driven auto-center with fallback polling
 * - Optimized self-intersection checks
 *
 * =============================================================================
 */
var GISCapture = (function() {
    'use strict';

    // =============================================
    // UTILITY FUNCTIONS
    // =============================================

    /**
     * Throttle function - limits execution rate to at most once per wait period.
     * Use for high-frequency events where you want regular updates (e.g., mousemove).
     *
     * @param {Function} func - Function to throttle
     * @param {number} wait - Minimum time between executions in ms
     * @param {Object} options - { leading: true, trailing: true }
     * @returns {Function} Throttled function with .cancel() method
     */
    function throttle(func, wait, options) {
        var context, args, result;
        var timeout = null;
        var previous = 0;
        if (!options) options = {};

        var later = function() {
            previous = options.leading === false ? 0 : Date.now();
            timeout = null;
            result = func.apply(context, args);
            if (!timeout) context = args = null;
        };

        var throttled = function() {
            var now = Date.now();
            if (!previous && options.leading === false) previous = now;
            var remaining = wait - (now - previous);
            context = this;
            args = arguments;
            if (remaining <= 0 || remaining > wait) {
                if (timeout) {
                    clearTimeout(timeout);
                    timeout = null;
                }
                previous = now;
                result = func.apply(context, args);
                if (!timeout) context = args = null;
            } else if (!timeout && options.trailing !== false) {
                timeout = setTimeout(later, remaining);
            }
            return result;
        };

        throttled.cancel = function() {
            clearTimeout(timeout);
            previous = 0;
            timeout = context = args = null;
        };

        return throttled;
    }

    /**
     * Debounce function - delays execution until after wait period of inactivity.
     * Use for events where you only care about the final value (e.g., input, resize).
     *
     * @param {Function} func - Function to debounce
     * @param {number} wait - Time to wait after last call in ms
     * @param {boolean} immediate - Execute on leading edge instead of trailing
     * @returns {Function} Debounced function with .cancel() method
     */
    function debounce(func, wait, immediate) {
        var timeout, result;

        var debounced = function() {
            var context = this, args = arguments;
            var later = function() {
                timeout = null;
                if (!immediate) result = func.apply(context, args);
            };
            var callNow = immediate && !timeout;
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
            if (callNow) result = func.apply(context, args);
            return result;
        };

        debounced.cancel = function() {
            clearTimeout(timeout);
            timeout = null;
        };

        return debounced;
    }

    /**
     * Escape HTML to prevent XSS (utility function, moved to module level)
     * @param {string} text - Text to escape
     * @returns {string} Escaped HTML
     */
    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // =============================================
    // TILE PROVIDERS CONFIGURATION
    // =============================================

    // Tile layer configurations
    var TILE_PROVIDERS = {
        OSM: {
            url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
            attribution: '&copy; <a href="https://openstreetmap.org">OpenStreetMap</a> contributors',
            maxZoom: 19
        },
        SATELLITE_ESRI: {
            url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
            attribution: '&copy; <a href="https://www.esri.com">Esri</a>',
            maxZoom: 19
        },
        HYBRID: {
            url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
            attribution: '&copy; <a href="https://www.esri.com">Esri</a>',
            maxZoom: 19,
            labels: true
        },
        TERRAIN: {
            url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
            attribution: '&copy; <a href="https://opentopomap.org">OpenTopoMap</a>',
            maxZoom: 17
        }
    };

    /**
     * Initialize GIS Capture component
     * 
     * @param {string} containerId - DOM element ID for the container
     * @param {Object} options - Configuration options
     */
    function init(containerId, options) {
        var container = document.getElementById(containerId);
        if (!container) {
            console.error('[GISCapture] Container not found: ' + containerId);
            return null;
        }

        var instance = new GISCaptureInstance(container, options);
        return instance;
    }

    /**
     * GIS Capture Instance
     */
    function GISCaptureInstance(container, options) {
        this.container = container;
        this.options = Object.assign({
            apiBase: '/jw/api/gis/gis',
            apiId: '',
            apiKey: '',
            hiddenFieldId: '',
            recordId: '',  // Current record ID for edit mode (excluded from overlap check)
            outputFields: {},
            captureMode: 'BOTH',
            defaultMode: 'AUTO',
            defaultLatitude: -29.5,
            defaultLongitude: 28.5,
            defaultZoom: 10,
            tileProvider: 'OSM',
            showSatelliteOption: true,
            mapHeight: 400,
            validation: {
                minAreaHectares: 0.01,
                maxAreaHectares: 1000,
                minVertices: 3,
                maxVertices: 100,
                allowSelfIntersection: false
            },
            gps: {
                highAccuracy: true,
                minAccuracy: 10,
                autoCloseDistance: 15
            },
            style: {
                fillColor: '#3388ff',
                fillOpacity: 0.2,
                strokeColor: '#3388ff',
                strokeWidth: 3
            },
            overlap: null,
            // Nearby parcels configuration (READ-ONLY display)
            nearbyParcels: null,
            // Auto-center configuration
            autoCenter: null,
            // Simplification configuration
            simplification: {
                enabled: false,
                tolerance: 2
            },
            onGeometryChange: null,
            onValidationError: null,
            onError: null
        }, options);

        // State
        this.state = {
            mode: null,         // 'DRAW' or 'WALK'
            phase: 'SELECT',    // 'SELECT', 'DRAWING', 'PREVIEW', 'SAVED'
            vertices: [],
            polygon: null,
            metrics: null,
            gpsWatchId: null,
            currentPosition: null,
            gpsAccuracy: null,
            gpsAccuracyValues: [],       // Track accuracy for each vertex in Walk Mode
            selectedVertexIndex: null,  // For vertex deletion
            intersectionPoints: [],      // Self-intersection points
            intersectingEdges: [],       // Edges that self-intersect
            // Overlap checking state
            overlaps: [],                // Array of overlapping records
            overlapChecked: false,       // Whether overlap check has been performed
            overlapConfirmed: false,     // Whether user confirmed saving with overlaps
            overlapCheckPending: false,  // Whether overlap check is in progress
            // Nearby parcels state (READ-ONLY display)
            nearbyParcelsLoaded: false,
            nearbyParcelsLoading: false,
            nearbyParcelsVisible: false,
            nearbyParcelsData: [],       // Cached parcel data
            // Auto-center state
            autoCenterAttempted: false,
            autoCenterLastValues: null,   // { district, village, lat, lon }
            autoCenterInProgress: false,
            // Initial geometry for self-overlap detection
            initialGeometry: null,        // Stores the originally loaded GeoJSON for edit mode
            initialAreaHectares: null,    // Stores the initial area for self-overlap detection
            // Network status
            isOnline: navigator.onLine !== false
        };

        // Map references
        this.map = null;
        this.tileLayer = null;
        this.drawingLayer = null;
        this.vertexMarkers = [];
        this.midpointMarkers = [];
        this.intersectionMarkers = [];
        this.intersectingEdgeLines = [];
        this.positionMarker = null;
        this.ghostLine = null;
        this.closeHintTooltip = null;
        this.warningPanel = null;
        // Overlap display references
        this.overlapLayer = null;          // Layer group for existing polygons
        this.overlapHighlightLayer = null; // Layer for overlap intersection areas
        this.overlapPanel = null;          // Overlap warning panel element

        // Nearby parcels display references (READ-ONLY)
        this.nearbyParcelsLayer = null;    // Layer group for nearby parcels
        this.nearbyParcelsToggle = null;   // Toggle button element
        this.nearbyParcelsBadge = null;    // Count badge element
        this.nearbyParcelsLegend = null;   // Legend element

        // Auto-center references
        this.autoCenterInterval = null;    // Polling interval for field changes
        this.autoCenterStatusEl = null;    // Status indicator element

        // Network status references
        this.networkStatusEl = null;       // Network status indicator element

        // Accessibility
        this.liveRegion = null;        // ARIA live region for announcements
        this.searchInput = null;       // Location search input
        this.searchResults = null;     // Search results dropdown

        // Event listener cleanup tracking (for memory leak prevention)
        this._eventListeners = [];     // Array of { element, event, handler }
        this._abortControllers = new Map(); // Map of requestId -> AbortController
        this._requestIdCounter = 0;    // Counter for generating unique request IDs
        this._destroyed = false;       // Flag to prevent operations after destroy

        // Initialize
        this._init();
    }

    // =========================================================================
    // GISCaptureInstance PROTOTYPE METHODS
    // =========================================================================
    GISCaptureInstance.prototype = {

        // =============================================
        // CORE METHODS - Initialization & State
        // =============================================

        /**
         * Initialize the component
         */
        _init: function() {
            var self = this;

            // Run self-test to verify intersection algorithm
            this._testSelfIntersection();

            // Debug: log recordId received from server
            console.log('[GIS] RecordId from server: "' + (this.options.recordId || '') + '"');

            // Fallback: extract recordId from URL if not provided by server
            // This handles cases where the Java-side extraction failed due to malformed URLs
            if (!this.options.recordId) {
                console.log('[GIS] RecordId empty, attempting URL extraction...');
                console.log('[GIS] URL search string: ' + window.location.search);
                // Allow 8-16 hex chars in last segment to handle standard (12) and non-standard UUIDs
                var urlMatch = window.location.search.match(/[?&]id=([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{8,16})/i);
                if (urlMatch) {
                    this.options.recordId = urlMatch[1];
                    console.log('[GIS] RecordId extracted from URL: ' + this.options.recordId);
                } else {
                    console.log('[GIS] No UUID found in URL');
                }
            }

            // Build HTML structure
            this._buildUI();

            // Load existing value if any
            this._loadExistingValue();

            // If value exists, go to preview state
            if (this.state.vertices.length > 0) {
                this.state.phase = 'PREVIEW';
                this._renderPreviewState();
            } else {
                // Show empty state first
                this._showEmptyState();
            }

            // Initialize auto-center if enabled
            if (this._isAutoCenterEnabled()) {
                this._initAutoCenter();
            }

            // Store instance reference for debug access
            window._gisInstance = this;
        },

        /**
         * Build UI structure
         */
        _buildUI: function() {
            this.container.innerHTML = '';
            this.container.classList.add('gis-container');

            // Create wrapper
            this.wrapper = document.createElement('div');
            this.wrapper.className = 'gis-map-wrapper';
            this.wrapper.style.height = this.options.mapHeight + 'px';
            this.container.appendChild(this.wrapper);

            // Create map container
            this.mapContainer = document.createElement('div');
            this.mapContainer.className = 'gis-map';
            this.mapContainer.style.height = '100%';
            this.wrapper.appendChild(this.mapContainer);

            // Create info panel
            this.infoPanel = document.createElement('div');
            this.infoPanel.className = 'gis-info-panel';
            this.infoPanel.style.display = 'none';
            this.container.appendChild(this.infoPanel);

            // Create validation panel
            this.validationPanel = document.createElement('div');
            this.validationPanel.className = 'gis-validation-panel';
            this.validationPanel.style.display = 'none';
            this.container.appendChild(this.validationPanel);

            // Create warning panel (for real-time warnings during drawing)
            this.warningPanel = document.createElement('div');
            this.warningPanel.className = 'gis-warning-panel';
            this.warningPanel.style.display = 'none';
            this.container.appendChild(this.warningPanel);

            // Create overlap panel (for overlap warnings)
            this.overlapPanel = document.createElement('div');
            this.overlapPanel.className = 'gis-overlap-panel';
            this.overlapPanel.style.display = 'none';
            this.container.appendChild(this.overlapPanel);

            // Initialize map
            this._initMap();

            // Initialize accessibility features
            this._initAccessibility();

            // Initialize network status indicator
            this._initNetworkStatus();
        },

        /**
         * Initialize Leaflet map
         */
        _initMap: function() {
            var self = this;

            // Create map
            this.map = L.map(this.mapContainer, {
                center: [this.options.defaultLatitude, this.options.defaultLongitude],
                zoom: this.options.defaultZoom,
                zoomControl: true
            });

            // Add tile layer
            this._setTileLayer(this.options.tileProvider);

            // Create drawing layer
            this.drawingLayer = L.layerGroup().addTo(this.map);

            // Create overlap layers (below drawing layer)
            this.overlapLayer = L.layerGroup().addTo(this.map);
            this.overlapHighlightLayer = L.layerGroup().addTo(this.map);

            // Initialize nearby parcels layer (READ-ONLY, below drawing layer)
            if (this._isNearbyParcelsEnabled()) {
                this._initNearbyParcelsLayer();
                this._setupNearbyParcelsReload();
            }

            // Add layer control if enabled
            if (this.options.showSatelliteOption) {
                this._addLayerControl();
            }

            // Map click handler for Draw Mode
            this.map.on('click', function(e) {
                if (self.state.mode === 'DRAW' && self.state.phase === 'DRAWING') {
                    self._addVertex(e.latlng.lat, e.latlng.lng);
                }
            });

            // Tile-render race fix (May 2026): when the map container is
            // initialised inside a hidden or zero-sized parent (e.g. inactive
            // wizard tab, collapsed panel, recently-rendered MetaScreenElement
            // form-cell), Leaflet computes 0×0 dimensions at init and never
            // requests tiles. Symptom: grey background on first load, normal
            // tiles only after browser refresh. Standard Leaflet remedy is
            // invalidateSize() once the container has real dimensions.
            //
            // We do two passes:
            //   1. setTimeout 250 ms — covers the common case where the
            //      container becomes sized within one animation frame after
            //      init (Joget's wizard step transitions, App Composer save,
            //      tab activation).
            //   2. ResizeObserver — covers the long-tail case where the
            //      container resizes later (tab switch much later, window
            //      resize, CSS transition completing). Idempotent: calling
            //      invalidateSize on an already-correct map is a no-op.
            setTimeout(function() {
                if (self.map) self.map.invalidateSize();
            }, 250);
            if (typeof ResizeObserver !== 'undefined' && this.mapContainer) {
                try {
                    var ro = new ResizeObserver(function() {
                        if (self.map) self.map.invalidateSize();
                    });
                    ro.observe(this.mapContainer);
                } catch (e) {
                    // ResizeObserver missing or container detached — fall back
                    // to the setTimeout path above.
                }
            }
        },

        /**
         * Set tile layer
         */
        _setTileLayer: function(provider) {
            if (this.tileLayer) {
                this.map.removeLayer(this.tileLayer);
            }

            var config = TILE_PROVIDERS[provider] || TILE_PROVIDERS.OSM;
            this.tileLayer = L.tileLayer(config.url, {
                attribution: config.attribution,
                maxZoom: config.maxZoom
            }).addTo(this.map);
        },

        /**
         * Add layer control
         */
        _addLayerControl: function() {
            var self = this;

            var control = document.createElement('div');
            control.className = 'gis-layer-control';

            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'gis-layer-btn';
            btn.innerHTML = '🗺️ Layers';
            btn.setAttribute('aria-label', 'Change map layer');
            btn.setAttribute('aria-haspopup', 'true');
            btn.setAttribute('aria-expanded', 'false');

            var dropdown = document.createElement('div');
            dropdown.className = 'gis-layer-dropdown';

            Object.keys(TILE_PROVIDERS).forEach(function(key) {
                var option = document.createElement('div');
                option.className = 'gis-layer-option';
                if (key === self.options.tileProvider) {
                    option.classList.add('selected');
                }
                option.textContent = key.replace('_', ' ');
                option.onclick = function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    self._setTileLayer(key);
                    dropdown.querySelectorAll('.gis-layer-option').forEach(function(opt) {
                        opt.classList.remove('selected');
                    });
                    option.classList.add('selected');
                    dropdown.classList.remove('open');
                };
                dropdown.appendChild(option);
            });

            btn.onclick = function(e) {
                e.preventDefault();
                e.stopPropagation();
                var isOpen = dropdown.classList.toggle('open');
                btn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
            };

            document.addEventListener('click', function() {
                dropdown.classList.remove('open');
                btn.setAttribute('aria-expanded', 'false');
            });

            control.appendChild(btn);
            control.appendChild(dropdown);
            this.mapContainer.appendChild(control);
        },

        // =============================================
        // ACCESSIBILITY METHODS
        // =============================================

        /**
         * Initialize accessibility features
         */
        _initAccessibility: function() {
            var self = this;

            // Create ARIA live region for screen reader announcements
            this._createLiveRegion();

            // Create location search control
            this._createLocationSearch();

            // Set up keyboard navigation for the map container
            this._setupKeyboardNavigation();

            // Add ARIA attributes to container
            this.container.setAttribute('role', 'application');
            this.container.setAttribute('aria-label', 'GIS polygon capture tool');

            // Make map container focusable for keyboard navigation
            this.mapContainer.setAttribute('tabindex', '0');
            this.mapContainer.setAttribute('role', 'img');
            this.mapContainer.setAttribute('aria-label', 'Interactive map for capturing polygon boundaries. Use arrow keys to pan, plus and minus to zoom.');
        },

        /**
         * Initialize network status indicator
         */
        _initNetworkStatus: function() {
            var self = this;

            this.networkStatusEl = document.createElement('div');
            this.networkStatusEl.className = 'gis-network-status ' + (this.state.isOnline ? 'online' : 'offline');
            this.networkStatusEl.setAttribute('role', 'status');
            this.networkStatusEl.setAttribute('aria-live', 'polite');
            this.networkStatusEl.innerHTML =
                '<span class="gis-network-indicator"></span>' +
                '<span class="gis-network-label">' + (this.state.isOnline ? 'Online' : 'Offline') + '</span>';

            this.wrapper.appendChild(this.networkStatusEl);

            var onOnline = function() {
                self.state.isOnline = true;
                self._updateNetworkStatus();
                self._showToast('Connection restored', 'success');
            };

            var onOffline = function() {
                self.state.isOnline = false;
                self._updateNetworkStatus();
                self._showToast('No internet connection', 'warning');
            };

            window.addEventListener('online', onOnline);
            window.addEventListener('offline', onOffline);

            this._eventListeners.push({ element: window, event: 'online', handler: onOnline });
            this._eventListeners.push({ element: window, event: 'offline', handler: onOffline });
        },

        /**
         * Update network status display
         */
        _updateNetworkStatus: function() {
            if (!this.networkStatusEl) return;

            this.networkStatusEl.className = 'gis-network-status ' + (this.state.isOnline ? 'online' : 'offline');
            this.networkStatusEl.querySelector('.gis-network-label').textContent = this.state.isOnline ? 'Online' : 'Offline';
        },

        /**
         * Create ARIA live region for screen reader announcements
         */
        _createLiveRegion: function() {
            this.liveRegion = document.createElement('div');
            this.liveRegion.className = 'gis-live-region';
            this.liveRegion.setAttribute('role', 'status');
            this.liveRegion.setAttribute('aria-live', 'polite');
            this.liveRegion.setAttribute('aria-atomic', 'true');
            // Visually hidden but accessible to screen readers
            this.liveRegion.style.cssText = 'position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;';
            this.container.appendChild(this.liveRegion);
        },

        /**
         * Announce message to screen readers
         */
        _announce: function(message) {
            if (!this.liveRegion) return;

            // Clear and set new message (forces re-announcement)
            this.liveRegion.textContent = '';
            setTimeout(function() {
                this.liveRegion.textContent = message;
            }.bind(this), 50);
        },

        /**
         * Create location search control
         */
        _createLocationSearch: function() {
            var self = this;

            // Create toggle button for search
            var searchToggle = document.createElement('button');
            searchToggle.type = 'button';
            searchToggle.className = 'gis-search-toggle';
            searchToggle.innerHTML = '&#128269;'; // magnifying glass
            searchToggle.setAttribute('aria-label', 'Toggle location search');
            searchToggle.setAttribute('aria-expanded', 'false');
            searchToggle.title = 'Search location';
            this.mapContainer.appendChild(searchToggle);

            var searchContainer = document.createElement('div');
            searchContainer.className = 'gis-search-container';
            searchContainer.style.display = 'none'; // Hidden by default
            searchContainer.setAttribute('role', 'search');
            searchContainer.setAttribute('aria-label', 'Location search');
            this.searchContainer = searchContainer;

            // Toggle button click handler
            searchToggle.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                var isVisible = searchContainer.style.display !== 'none';
                searchContainer.style.display = isVisible ? 'none' : 'flex';
                searchToggle.classList.toggle('active', !isVisible);
                searchToggle.setAttribute('aria-expanded', !isVisible);
                if (!isVisible) {
                    self.searchInput.focus();
                }
            });

            // Search input
            this.searchInput = document.createElement('input');
            this.searchInput.type = 'text';
            this.searchInput.className = 'gis-search-input';
            this.searchInput.placeholder = 'Search location...';
            this.searchInput.setAttribute('aria-label', 'Search for a location');
            this.searchInput.setAttribute('aria-autocomplete', 'list');
            this.searchInput.setAttribute('aria-expanded', 'false');
            this.searchInput.setAttribute('autocomplete', 'off');

            // Search button
            var searchBtn = document.createElement('button');
            searchBtn.type = 'button';
            searchBtn.className = 'gis-search-btn';
            searchBtn.innerHTML = '&#128269;'; // magnifying glass
            searchBtn.setAttribute('aria-label', 'Search');

            // Results dropdown
            this.searchResults = document.createElement('div');
            this.searchResults.className = 'gis-search-results';
            this.searchResults.setAttribute('role', 'listbox');
            this.searchResults.setAttribute('aria-label', 'Search results');
            this.searchResults.style.display = 'none';

            // Event handlers
            var searchTimeout = null;

            this.searchInput.addEventListener('input', function() {
                clearTimeout(searchTimeout);
                var query = self.searchInput.value.trim();

                if (query.length < 3) {
                    self._hideSearchResults();
                    return;
                }

                // Debounce search
                searchTimeout = setTimeout(function() {
                    self._performSearch(query);
                }, 300);
            });

            this.searchInput.addEventListener('keydown', function(e) {
                self._handleSearchKeydown(e);
            });

            searchBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                var query = self.searchInput.value.trim();
                if (query.length >= 3) {
                    self._performSearch(query);
                }
            });

            // Close results when clicking outside
            document.addEventListener('click', function(e) {
                if (!searchContainer.contains(e.target)) {
                    self._hideSearchResults();
                }
            });

            searchContainer.appendChild(this.searchInput);
            searchContainer.appendChild(searchBtn);
            searchContainer.appendChild(this.searchResults);
            this.mapContainer.appendChild(searchContainer);
        },

        /**
         * Perform location search via API
         */
        _performSearch: function(query) {
            var self = this;

            // Build API URL
            var apiUrl = this.options.apiBase + '/geocode?query=' + encodeURIComponent(query) + '&limit=5';

            // Validate API URL for security
            var validatedUrl = this._validateApiUrl(apiUrl);
            if (!validatedUrl) {
                console.error('[GIS] Invalid API URL, skipping search');
                this._showSearchError();
                return;
            }

            // Build headers (session-based auth)
            var headers = this._buildApiHeaders();

            // Show loading state
            this._showSearchLoading();

            fetch(validatedUrl, {
                method: 'GET',
                headers: headers
            })
            .then(function(response) {
                if (!response.ok) {
                    throw new Error('Search failed');
                }
                return response.json();
            })
            .then(function(response) {
                // Parse Joget API response
                var data = response;
                if (response.message && typeof response.message === 'string') {
                    try {
                        data = JSON.parse(response.message);
                    } catch (e) {
                        // Fall back to original response
                    }
                }

                var results = data.results || data.data || data || [];
                self._displaySearchResults(results);
            })
            .catch(function(error) {
                console.warn('[GISCapture] Search error:', error);
                self._showSearchError();
            });
        },

        /**
         * Show search loading state
         */
        _showSearchLoading: function() {
            this.searchResults.innerHTML = '<div class="gis-search-loading">Searching...</div>';
            this.searchResults.style.display = 'block';
            this.searchInput.setAttribute('aria-expanded', 'true');
        },

        /**
         * Show search error
         */
        _showSearchError: function() {
            this.searchResults.innerHTML = '<div class="gis-search-error">Search failed. Try again.</div>';
            this.searchResults.style.display = 'block';
        },

        /**
         * Display search results
         */
        _displaySearchResults: function(results) {
            var self = this;

            if (!results || results.length === 0) {
                this.searchResults.innerHTML = '<div class="gis-search-no-results">No locations found</div>';
                this.searchResults.style.display = 'block';
                this._announce('No locations found');
                return;
            }

            this.searchResults.innerHTML = '';

            results.forEach(function(result, index) {
                var item = document.createElement('div');
                item.className = 'gis-search-result-item';
                item.setAttribute('role', 'option');
                item.setAttribute('tabindex', '-1');
                item.setAttribute('data-index', index);
                item.setAttribute('data-lat', result.lat || result.latitude);
                item.setAttribute('data-lng', result.lon || result.lng || result.longitude);

                var name = result.display_name || result.displayName || result.name || 'Unknown location';
                item.textContent = name;

                item.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    self._selectSearchResult(result);
                });

                item.addEventListener('keydown', function(e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        self._selectSearchResult(result);
                    }
                });

                self.searchResults.appendChild(item);
            });

            this.searchResults.style.display = 'block';
            this.searchInput.setAttribute('aria-expanded', 'true');
            this._announce(results.length + ' location' + (results.length > 1 ? 's' : '') + ' found');
        },

        /**
         * Select a search result and navigate map
         */
        _selectSearchResult: function(result) {
            var lat = parseFloat(result.lat || result.latitude);
            var lng = parseFloat(result.lon || result.lng || result.longitude);

            if (isNaN(lat) || isNaN(lng)) {
                this._showToast('Invalid location coordinates', 'error');
                return;
            }

            // Navigate map to location
            this.map.setView([lat, lng], 16);

            // Clear search
            this.searchInput.value = '';
            this._hideSearchResults();

            // Announce navigation
            var name = result.display_name || result.displayName || result.name || 'Selected location';
            this._announce('Map moved to ' + name);
            this._showToast('Navigated to location', 'success');
        },

        /**
         * Hide search results
         */
        _hideSearchResults: function() {
            if (this.searchResults) {
                this.searchResults.style.display = 'none';
                this.searchResults.innerHTML = '';
            }
            if (this.searchInput) {
                this.searchInput.setAttribute('aria-expanded', 'false');
            }
        },

        /**
         * Handle keyboard navigation in search results
         */
        _handleSearchKeydown: function(e) {
            var items = this.searchResults.querySelectorAll('.gis-search-result-item');
            if (items.length === 0) return;

            var currentIndex = -1;
            var focused = this.searchResults.querySelector('.gis-search-result-item:focus');
            if (focused) {
                currentIndex = parseInt(focused.getAttribute('data-index'), 10);
            }

            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    if (currentIndex < items.length - 1) {
                        items[currentIndex + 1].focus();
                    } else if (currentIndex === -1 && items.length > 0) {
                        items[0].focus();
                    }
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    if (currentIndex > 0) {
                        items[currentIndex - 1].focus();
                    } else if (currentIndex === 0) {
                        this.searchInput.focus();
                    }
                    break;
                case 'Escape':
                    e.preventDefault();
                    this._hideSearchResults();
                    this.searchInput.focus();
                    break;
                case 'Enter':
                    if (focused) {
                        e.preventDefault();
                        focused.click();
                    }
                    break;
            }
        },

        /**
         * Set up keyboard navigation for map and controls
         */
        _setupKeyboardNavigation: function() {
            var self = this;

            // Global keyboard handler for the component
            this._boundGlobalKeyHandler = function(e) {
                // Only handle if focus is within our container or on map
                if (!self.container.contains(document.activeElement) &&
                    document.activeElement !== self.mapContainer) {
                    return;
                }

                self._handleGlobalKeydown(e);
            };

            document.addEventListener('keydown', this._boundGlobalKeyHandler);

            // Map-specific keyboard handler when map is focused
            this.mapContainer.addEventListener('keydown', function(e) {
                self._handleMapKeydown(e);
            });
        },

        /**
         * Handle global keyboard events
         */
        _handleGlobalKeydown: function(e) {
            // Escape key - cancel current action
            if (e.key === 'Escape') {
                if (this.state.phase === 'DRAWING') {
                    // If drawing and has vertices, complete the polygon if possible
                    if (this.state.vertices.length >= 3) {
                        this._completePolygon();
                        this._announce('Drawing completed');
                    } else {
                        this._announce('Need at least 3 corners to complete');
                    }
                }
                // Deselect any selected vertex
                this._deselectVertex();
                // Hide search results
                this._hideSearchResults();
            }
        },

        /**
         * Handle map-specific keyboard events (when map container is focused)
         */
        _handleMapKeydown: function(e) {
            var panAmount = 100; // pixels to pan
            var handled = false;

            switch (e.key) {
                case 'ArrowUp':
                    this.map.panBy([0, -panAmount]);
                    this._announce('Panned map north');
                    handled = true;
                    break;
                case 'ArrowDown':
                    this.map.panBy([0, panAmount]);
                    this._announce('Panned map south');
                    handled = true;
                    break;
                case 'ArrowLeft':
                    this.map.panBy([-panAmount, 0]);
                    this._announce('Panned map west');
                    handled = true;
                    break;
                case 'ArrowRight':
                    this.map.panBy([panAmount, 0]);
                    this._announce('Panned map east');
                    handled = true;
                    break;
                case '+':
                case '=':
                    this.map.zoomIn();
                    this._announce('Zoomed in to level ' + this.map.getZoom());
                    handled = true;
                    break;
                case '-':
                case '_':
                    this.map.zoomOut();
                    this._announce('Zoomed out to level ' + this.map.getZoom());
                    handled = true;
                    break;
                case 'Enter':
                case ' ':
                    // Add vertex at map center during drawing mode
                    if (this.state.phase === 'DRAWING' && this.state.mode === 'DRAW') {
                        e.preventDefault();
                        var center = this.map.getCenter();
                        this._addVertex(center.lat, center.lng);
                        handled = true;
                    }
                    break;
            }

            if (handled) {
                e.preventDefault();
            }
        },

        // =============================================
        // END ACCESSIBILITY METHODS
        // =============================================

        // =============================================
        // STATE MANAGEMENT & DRAWING METHODS
        // Mode selection, Draw Mode, Walk Mode, vertex management
        // =============================================

        /**
         * Show empty state UI
         */
        _showEmptyState: function() {
            var self = this;
            this.state.phase = 'EMPTY';

            // Remove any existing overlays
            var existingOverlay = this.wrapper.querySelector('.gis-empty-state');
            if (existingOverlay) existingOverlay.remove();

            // Create empty state overlay
            var overlay = document.createElement('div');
            overlay.className = 'gis-empty-state';
            overlay.innerHTML =
                '<div class="gis-empty-content">' +
                    '<div class="gis-empty-icon" aria-hidden="true">&#128506;</div>' +
                    '<div class="gis-empty-text">No boundary captured yet</div>' +
                    '<button type="button" class="gis-capture-btn" aria-label="Start capturing polygon boundary">&#128205; Capture Boundary</button>' +
                '</div>';

            overlay.querySelector('.gis-capture-btn').onclick = function(e) {
                e.preventDefault();
                e.stopPropagation();
                overlay.remove();
                self._determineInitialState();
            };

            this.wrapper.appendChild(overlay);
        },

        /**
         * Determine initial state based on options
         */
        _determineInitialState: function() {
            var captureMode = this.options.captureMode;
            var defaultMode = this.options.defaultMode;

            if (captureMode === 'VIEW_ONLY') {
                this.state.mode = null;
                this.state.phase = 'VIEW';
                return;
            }

            if (captureMode === 'WALK') {
                this._startWalkMode();
            } else if (captureMode === 'DRAW') {
                this._startDrawMode();
            } else if (captureMode === 'BOTH') {
                if (defaultMode === 'AUTO') {
                    // Detect device type
                    var isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
                    if (isMobile) {
                        this._showModeSelection();
                    } else {
                        this._startDrawMode();
                    }
                } else if (defaultMode === 'WALK') {
                    this._startWalkMode();
                } else {
                    this._startDrawMode();
                }
            }
        },

        /**
         * Show mode selection UI
         */
        _showModeSelection: function() {
            var self = this;
            this.state.phase = 'SELECT';

            // Create selection overlay
            var overlay = document.createElement('div');
            overlay.className = 'gis-mode-selection';
            overlay.innerHTML = '<h3>How would you like to capture this boundary?</h3>' +
                '<div class="gis-mode-buttons">' +
                    '<div class="gis-mode-btn" data-mode="WALK">' +
                        '<span class="gis-mode-icon">🚶</span>' +
                        '<span class="gis-mode-title">Walk the Boundary</span>' +
                        '<span class="gis-mode-subtitle">Best when at the location</span>' +
                    '</div>' +
                    '<div class="gis-mode-btn" data-mode="DRAW">' +
                        '<span class="gis-mode-icon">🖱️</span>' +
                        '<span class="gis-mode-title">Draw on Map</span>' +
                        '<span class="gis-mode-subtitle">Best from office</span>' +
                    '</div>' +
                '</div>';

            overlay.querySelectorAll('.gis-mode-btn').forEach(function(btn) {
                btn.onclick = function() {
                    var mode = btn.getAttribute('data-mode');
                    overlay.remove();
                    if (mode === 'WALK') {
                        self._startWalkMode();
                    } else {
                        self._startDrawMode();
                    }
                };
            });

            this.container.appendChild(overlay);
        },

        /**
         * Start Draw Mode
         */
        _startDrawMode: function() {
            var self = this;
            this.state.mode = 'DRAW';
            this.state.phase = 'DRAWING';
            this.state.vertices = [];

            // Add step progress indicator
            this._addStepProgress(1);

            // Add toolbar
            this._addDrawToolbar();

            // Update cursor
            this.mapContainer.style.cursor = 'crosshair';

            // Set up ghost line tracking
            this._boundMouseMoveHandler = function(e) {
                self._updateGhostLine(e.latlng);
                self._checkCloseHint(e.latlng);
            };
            this.map.on('mousemove', this._boundMouseMoveHandler);

            // Set up double-click to finish
            this._boundDblClickHandler = function(e) {
                L.DomEvent.preventDefault(e);
                L.DomEvent.stopPropagation(e);
                if (self.state.vertices.length >= 3) {
                    self._completePolygon();
                }
            };
            this.map.on('dblclick', this._boundDblClickHandler);

            // Disable map double-click zoom during drawing
            this.map.doubleClickZoom.disable();

            // Set up keyboard handler for Delete key
            this._boundKeyDownHandler = function(e) {
                self._handleKeyDown(e);
            };
            document.addEventListener('keydown', this._boundKeyDownHandler);

            this._showToast('Click on the map to add corners', 'info');
        },

        /**
         * Handle keyboard events (Delete key for vertex deletion, Ctrl+Z for undo)
         */
        _handleKeyDown: function(e) {
            if (e.key === 'Delete' || e.key === 'Backspace') {
                if (this.state.selectedVertexIndex !== null &&
                    (this.state.phase === 'DRAWING' || this.state.phase === 'PREVIEW')) {
                    e.preventDefault();
                    this._deleteSelectedVertex();
                }
            } else if (e.key === 'Escape') {
                this._deselectVertex();
            } else if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
                // Ctrl+Z or Cmd+Z for undo
                if (this.state.phase === 'DRAWING' && this.state.vertices.length > 0) {
                    e.preventDefault();
                    this._undoLastVertex();
                    this._announce('Undo: removed last corner');
                }
            }
        },

        /**
         * Delete the currently selected vertex
         */
        _deleteSelectedVertex: function() {
            var index = this.state.selectedVertexIndex;
            if (index === null || this.state.vertices.length <= 3) {
                if (this.state.vertices.length <= 3) {
                    this._showToast('Need at least 3 corners', 'warning');
                }
                return;
            }

            // Remove vertex
            this.state.vertices.splice(index, 1);

            // Deselect
            this._deselectVertex();

            // Rebuild all markers
            this._rebuildAllMarkers();

            // Update polygon and metrics
            this._updatePolygon();
            this._calculateMetrics();
            this._updateInfoPanel();
            this._checkSelfIntersection();
            this._checkAreaWarnings();

            // Update hidden field
            this._saveToHiddenField();

            // Re-check overlaps after vertex deletion in preview mode
            if (this.state.phase === 'PREVIEW') {
                this._checkOverlaps();
            }

            this._showToast('Corner removed', 'info');
        },

        /**
         * Select a vertex for editing
         */
        _selectVertex: function(index) {
            // Deselect previous
            this._deselectVertex();

            this.state.selectedVertexIndex = index;
            var marker = this.vertexMarkers[index];
            if (marker) {
                var el = marker.getElement();
                if (el) {
                    el.classList.add('selected');
                }
            }
        },

        /**
         * Deselect current vertex
         */
        _deselectVertex: function() {
            if (this.state.selectedVertexIndex !== null) {
                var marker = this.vertexMarkers[this.state.selectedVertexIndex];
                if (marker) {
                    var el = marker.getElement();
                    if (el) {
                        el.classList.remove('selected');
                    }
                }
            }
            this.state.selectedVertexIndex = null;
        },

        /**
         * Add Draw Mode toolbar
         */
        _addDrawToolbar: function() {
            var self = this;

            if (this.toolbar) {
                this.toolbar.remove();
            }

            this.toolbar = document.createElement('div');
            this.toolbar.className = 'gis-toolbar';

            // Undo button
            var undoBtn = document.createElement('button');
            undoBtn.type = 'button';
            undoBtn.className = 'gis-toolbar-btn';
            undoBtn.innerHTML = '↩️ Undo';
            undoBtn.setAttribute('aria-label', 'Undo last corner');
            undoBtn.onclick = function(e) { e.preventDefault(); e.stopPropagation(); self._undoLastVertex(); };
            this.toolbar.appendChild(undoBtn);

            // Clear button
            var clearBtn = document.createElement('button');
            clearBtn.type = 'button';
            clearBtn.className = 'gis-toolbar-btn danger';
            clearBtn.innerHTML = '🗑️ Clear';
            clearBtn.setAttribute('aria-label', 'Clear all corners');
            clearBtn.onclick = function(e) { e.preventDefault(); e.stopPropagation(); self._clearDrawing(); };
            this.toolbar.appendChild(clearBtn);

            // Spacer
            var spacer = document.createElement('div');
            spacer.className = 'gis-toolbar-spacer';
            this.toolbar.appendChild(spacer);

            // Complete button
            var completeBtn = document.createElement('button');
            completeBtn.type = 'button';
            completeBtn.className = 'gis-toolbar-btn primary';
            completeBtn.innerHTML = '✓ Complete';
            completeBtn.setAttribute('aria-label', 'Complete polygon drawing');
            completeBtn.onclick = function(e) { e.preventDefault(); e.stopPropagation(); self._completePolygon(); };
            this.toolbar.appendChild(completeBtn);

            this.wrapper.insertBefore(this.toolbar, this.mapContainer);
        },

        /**
         * Add step progress indicator
         */
        _addStepProgress: function(step) {
            if (this.stepProgress) {
                this.stepProgress.remove();
            }

            this.stepProgress = document.createElement('div');
            this.stepProgress.className = 'gis-step-progress';

            var step1Active = step >= 1;
            var step2Active = step >= 2;
            var step1Complete = step > 1;

            this.stepProgress.innerHTML =
                '<div class="gis-step-header">STEP ' + step + ' of 2: ' +
                    (step === 1 ? 'Draw the boundary' : 'Preview') + '</div>' +
                '<div class="gis-step-bar">' +
                    '<div class="gis-step-fill" style="width: ' + (step === 1 ? '50' : '100') + '%"></div>' +
                '</div>' +
                '<div class="gis-step-labels">' +
                    '<span class="gis-step-label ' + (step1Active ? 'active' : '') + (step1Complete ? ' complete' : '') + '">Draw Boundary</span>' +
                    '<span class="gis-step-label ' + (step2Active ? 'active' : '') + '">Preview</span>' +
                '</div>';

            this.container.insertBefore(this.stepProgress, this.wrapper);
        },

        /**
         * Update ghost line from last vertex to cursor
         */
        _updateGhostLine: function(latlng) {
            if (this.state.phase !== 'DRAWING' || this.state.vertices.length === 0) {
                if (this.ghostLine) {
                    this.drawingLayer.removeLayer(this.ghostLine);
                    this.ghostLine = null;
                }
                return;
            }

            var lastVertex = this.state.vertices[this.state.vertices.length - 1];
            var coords = [
                [lastVertex.lat, lastVertex.lng],
                [latlng.lat, latlng.lng]
            ];

            if (this.ghostLine) {
                this.ghostLine.setLatLngs(coords);
            } else {
                this.ghostLine = L.polyline(coords, {
                    color: this.options.style.strokeColor,
                    weight: 2,
                    dashArray: '5, 10',
                    opacity: 0.5
                }).addTo(this.drawingLayer);
            }
        },

        /**
         * Check if cursor is near first vertex and show close hint
         */
        _checkCloseHint: function(latlng) {
            if (this.state.vertices.length < 3 || !this.vertexMarkers[0]) {
                this._hideCloseHint();
                return;
            }

            var firstVertex = this.state.vertices[0];
            var distance = this._distanceBetween(
                firstVertex.lat, firstVertex.lng,
                latlng.lat, latlng.lng
            );

            // Show hint if within 20 meters (adjustable)
            var closeThreshold = 20;
            if (distance <= closeThreshold) {
                this._showCloseHint();
            } else {
                this._hideCloseHint();
            }
        },

        /**
         * Show close polygon hint on first vertex
         */
        _showCloseHint: function() {
            if (this._closeHintShown) return;
            this._closeHintShown = true;

            var firstMarker = this.vertexMarkers[0];
            if (firstMarker) {
                // Enlarge first marker
                firstMarker.setRadius(10);
                firstMarker.setStyle({ weight: 4 });

                // Add tooltip
                if (!this.closeHintTooltip) {
                    this.closeHintTooltip = L.tooltip({
                        permanent: true,
                        direction: 'top',
                        className: 'gis-close-hint-tooltip',
                        offset: [0, -10]
                    })
                    .setContent('Click to close')
                    .setLatLng([this.state.vertices[0].lat, this.state.vertices[0].lng]);

                    this.closeHintTooltip.addTo(this.map);
                }
            }
        },

        /**
         * Hide close polygon hint
         */
        _hideCloseHint: function() {
            if (!this._closeHintShown) return;
            this._closeHintShown = false;

            var firstMarker = this.vertexMarkers[0];
            if (firstMarker) {
                // Restore first marker size
                firstMarker.setRadius(6);
                firstMarker.setStyle({ weight: 3 });
            }

            // Remove tooltip
            if (this.closeHintTooltip) {
                this.map.removeLayer(this.closeHintTooltip);
                this.closeHintTooltip = null;
            }
        },

        /**
         * Start Walk Mode
         */
        _startWalkMode: function() {
            var self = this;
            this.state.mode = 'WALK';
            this.state.phase = 'DRAWING';
            this.state.vertices = [];
            this.state.gpsAccuracyValues = [];  // Reset GPS accuracy tracking

            // Add GPS panel
            this._addGPSPanel();

            // Add Mark Corner button
            this._addMarkCornerButton();

            // Start GPS tracking
            this._startGPSTracking();
        },

        /**
         * Add GPS status panel
         */
        _addGPSPanel: function() {
            if (this.gpsPanel) {
                this.gpsPanel.remove();
            }

            this.gpsPanel = document.createElement('div');
            this.gpsPanel.className = 'gis-gps-panel';
            this.gpsPanel.setAttribute('role', 'status');
            this.gpsPanel.setAttribute('aria-label', 'GPS accuracy status');
            this.gpsPanel.innerHTML =
                '<div class="gis-gps-header">' +
                    '<span class="gis-gps-icon">&#128225;</span>' +
                    '<span class="gis-gps-title">GPS ACCURACY</span>' +
                '</div>' +
                '<div class="gis-gps-bar-container">' +
                    '<div class="gis-gps-bar">' +
                        '<div class="gis-gps-bar-fill"></div>' +
                    '</div>' +
                    '<span class="gis-gps-accuracy-value">Searching...</span>' +
                '</div>' +
                '<div class="gis-gps-status-text">' +
                    '<span class="gis-gps-indicator searching"></span>' +
                    '<span class="gis-gps-status-label">Acquiring GPS signal...</span>' +
                '</div>';

            this.wrapper.appendChild(this.gpsPanel);
        },

        /**
         * Add Mark Corner button
         */
        _addMarkCornerButton: function() {
            var self = this;

            if (this.markBtn) {
                this.markBtn.remove();
            }

            this.markBtn = document.createElement('button');
            this.markBtn.type = 'button';
            this.markBtn.className = 'gis-mark-corner-btn';
            this.markBtn.innerHTML = '📍 Mark Corner';
            this.markBtn.setAttribute('aria-label', 'Mark current GPS position as polygon corner');
            this.markBtn.disabled = true;
            this.markBtn.onclick = function(e) {
                e.preventDefault();
                e.stopPropagation();
                if (self.state.currentPosition) {
                    // Check if GPS accuracy meets threshold before allowing marking
                    var minAccuracy = self.options.gps.minAccuracy || 10;
                    if (self.state.gpsAccuracy !== null && self.state.gpsAccuracy > minAccuracy) {
                        self._showToast(
                            'GPS accuracy (' + Math.round(self.state.gpsAccuracy) + 'm) exceeds limit (' + minAccuracy + 'm). Wait for better signal.',
                            'warning'
                        );
                        self._announce('Cannot mark corner. GPS accuracy ' + Math.round(self.state.gpsAccuracy) + 'm exceeds ' + minAccuracy + 'm threshold.');
                        return; // Block marking
                    }

                    // Track GPS accuracy for this vertex
                    if (self.state.gpsAccuracy !== null) {
                        self.state.gpsAccuracyValues.push(self.state.gpsAccuracy);
                    }
                    self._addVertex(
                        self.state.currentPosition.lat,
                        self.state.currentPosition.lng
                    );
                }
            };

            this.wrapper.appendChild(this.markBtn);
        },

        /**
         * Start GPS tracking
         */
        _startGPSTracking: function() {
            var self = this;

            if (!navigator.geolocation) {
                this._showToast('GPS not available on this device', 'error');
                return;
            }

            var gpsOptions = {
                enableHighAccuracy: this.options.gps.highAccuracy,
                timeout: 10000,
                maximumAge: 0
            };

            this.state.gpsWatchId = navigator.geolocation.watchPosition(
                function(position) {
                    self._onGPSPosition(position);
                },
                function(error) {
                    self._onGPSError(error);
                },
                gpsOptions
            );
        },

        /**
         * Handle GPS position update
         */
        _onGPSPosition: function(position) {
            var lat = position.coords.latitude;
            var lng = position.coords.longitude;
            var accuracy = position.coords.accuracy;

            this.state.currentPosition = { lat: lat, lng: lng };
            this.state.gpsAccuracy = accuracy;

            // Update GPS panel
            this._updateGPSPanel(accuracy);

            // Update position marker
            this._updatePositionMarker(lat, lng, accuracy);

            // Enable mark button
            if (this.markBtn) {
                this.markBtn.disabled = false;
            }

            // Center map on first fix
            if (!this.map._gpsFixed) {
                this.map.setView([lat, lng], 17);
                this.map._gpsFixed = true;
            }
        },

        /**
         * Update GPS panel display
         */
        _updateGPSPanel: function(accuracy) {
            if (!this.gpsPanel) return;

            var barFill = this.gpsPanel.querySelector('.gis-gps-bar-fill');
            var accuracyValue = this.gpsPanel.querySelector('.gis-gps-accuracy-value');
            var indicator = this.gpsPanel.querySelector('.gis-gps-indicator');
            var statusLabel = this.gpsPanel.querySelector('.gis-gps-status-label');

            // Determine accuracy level and bar percentage
            var level, barPercent, statusText;

            if (accuracy <= 3) {
                level = 'excellent';
                barPercent = 100;
                statusText = 'Excellent - Ready to mark';
            } else if (accuracy <= 5) {
                level = 'good';
                barPercent = 80;
                statusText = 'Good - Ready to mark';
            } else if (accuracy <= 10) {
                level = 'fair';
                barPercent = 60;
                statusText = 'Fair - Wait if possible';
            } else if (accuracy <= 20) {
                level = 'poor';
                barPercent = 40;
                statusText = 'Poor - Move to open area';
            } else {
                level = 'very-poor';
                barPercent = 20;
                statusText = 'Very poor - Check GPS settings';
            }

            // Update bar fill
            if (barFill) {
                barFill.style.width = barPercent + '%';
                barFill.className = 'gis-gps-bar-fill ' + level;
            }

            // Update accuracy value
            if (accuracyValue) {
                accuracyValue.textContent = '±' + Math.round(accuracy) + 'm';
                accuracyValue.className = 'gis-gps-accuracy-value ' + level;
            }

            // Update indicator
            if (indicator) {
                indicator.className = 'gis-gps-indicator ' + level;
            }

            // Update status label
            if (statusLabel) {
                statusLabel.textContent = statusText;
                statusLabel.className = 'gis-gps-status-label ' + level;
            }
        },

        /**
         * Update position marker on map
         */
        _updatePositionMarker: function(lat, lng, accuracy) {
            if (this.positionMarker) {
                this.positionMarker.setLatLng([lat, lng]);
                if (this.accuracyCircle) {
                    this.accuracyCircle.setLatLng([lat, lng]);
                    this.accuracyCircle.setRadius(accuracy);
                }
            } else {
                // Create marker
                this.positionMarker = L.circleMarker([lat, lng], {
                    radius: 8,
                    fillColor: '#4285f4',
                    fillOpacity: 1,
                    color: 'white',
                    weight: 2
                }).addTo(this.map);

                // Create accuracy circle
                this.accuracyCircle = L.circle([lat, lng], {
                    radius: accuracy,
                    fillColor: '#4285f4',
                    fillOpacity: 0.1,
                    color: '#4285f4',
                    weight: 1
                }).addTo(this.map);
            }
        },

        /**
         * Handle GPS error
         */
        _onGPSError: function(error) {
            console.error('[GISCapture] GPS error:', error);
            this._showToast('GPS error: ' + error.message, 'error');
        },

        /**
         * Add a vertex to the polygon
         */
        _addVertex: function(lat, lng) {
            // Check if at vertex limit
            var maxVertices = this.options.validation.maxVertices || 100;
            if (this.state.vertices.length >= maxVertices) {
                this._showToast('Maximum corners reached (' + maxVertices + ')', 'error');
                this._announce('Cannot add more corners. Maximum ' + maxVertices + ' reached.');
                return;
            }

            var vertex = { lat: lat, lng: lng };
            this.state.vertices.push(vertex);

            // Add marker
            this._addVertexMarker(vertex, this.state.vertices.length - 1);

            // Update polygon
            this._updatePolygon();

            // Calculate metrics
            this._calculateMetrics();

            // Show info panel
            this._updateInfoPanel();

            // Check if close to start (Walk Mode)
            if (this.state.mode === 'WALK' && this.state.vertices.length >= 3) {
                this._checkAutoClose();
            }

            // Check for real-time warnings
            this._checkSelfIntersection();
            this._checkAreaWarnings();
            this._checkVertexLimitWarning();

            // Haptic feedback on mobile
            if (navigator.vibrate) {
                navigator.vibrate(50);
            }

            this._showToast('Corner ' + this.state.vertices.length + ' marked', 'success');

            // Screen reader announcement with metrics
            var announcement = 'Corner ' + this.state.vertices.length + ' marked';
            if (this.state.metrics && this.state.vertices.length >= 3) {
                announcement += '. Area ' + this.state.metrics.areaHectares.toFixed(2) + ' hectares';
            }
            this._announce(announcement);
        },

        /**
         * Add vertex marker to map
         */
        _addVertexMarker: function(vertex, index) {
            var self = this;
            var isFirst = index === 0;
            var vertexNumber = index + 1;

            // Create numbered marker using DivIcon
            var markerIcon = L.divIcon({
                className: 'gis-numbered-vertex' + (isFirst ? ' first' : ''),
                html: '<span>' + vertexNumber + '</span>',
                iconSize: [24, 24],
                iconAnchor: [12, 12]
            });

            var marker = L.marker([vertex.lat, vertex.lng], {
                icon: markerIcon,
                draggable: false
            }).addTo(this.drawingLayer);

            // Store reference for close hint functionality (create a pseudo circleMarker interface)
            marker._isNumberedMarker = true;
            marker._originalIcon = markerIcon;
            marker.setRadius = function(r) {
                // Update icon size based on radius
                var size = r * 4;
                var newIcon = L.divIcon({
                    className: 'gis-numbered-vertex' + (isFirst ? ' first' : '') + (r > 6 ? ' enlarged' : ''),
                    html: '<span>' + vertexNumber + '</span>',
                    iconSize: [size, size],
                    iconAnchor: [size/2, size/2]
                });
                marker.setIcon(newIcon);
            };
            marker.setStyle = function() {}; // No-op for compatibility

            // Make draggable in edit mode
            marker.on('mousedown', function(e) {
                if (self.state.phase === 'DRAWING' || self.state.phase === 'PREVIEW') {
                    self._startVertexDrag(index, e);
                }
            });

            // Click on first vertex to close polygon, or select vertex for deletion
            marker.on('click', function(e) {
                L.DomEvent.stopPropagation(e);

                // If drawing and this is the first vertex with 3+ vertices, close polygon
                if (isFirst && self.state.vertices.length >= 3 && self.state.phase === 'DRAWING') {
                    self._completePolygon();
                    return;
                }

                // In preview/edit mode, select vertex for deletion
                if (self.state.phase === 'PREVIEW' || self.state.phase === 'DRAWING') {
                    if (self.state.selectedVertexIndex === index) {
                        // Already selected - deselect
                        self._deselectVertex();
                    } else {
                        // Select this vertex
                        self._selectVertex(index);
                        self._showToast('Press Delete to remove corner ' + (index + 1), 'info');
                    }
                }
            });

            this.vertexMarkers.push(marker);
        },

        /**
         * Rebuild all vertex markers (after deletion or insertion)
         */
        _rebuildAllMarkers: function() {
            var self = this;

            // Clear existing vertex markers
            this.vertexMarkers.forEach(function(m) {
                self.drawingLayer.removeLayer(m);
            });
            this.vertexMarkers = [];

            // Clear midpoint markers
            this._clearMidpointMarkers();

            // Rebuild vertex markers
            this.state.vertices.forEach(function(v, i) {
                self._addVertexMarker(v, i);
            });

            // Add midpoint markers if we have a complete polygon
            if (this.state.vertices.length >= 3 && this.state.phase === 'PREVIEW') {
                this._addMidpointMarkers();
            }
        },

        /**
         * Add midpoint handles on polygon edges
         */
        _addMidpointMarkers: function() {
            var self = this;
            this._clearMidpointMarkers();

            if (this.state.vertices.length < 3) return;

            var vertices = this.state.vertices;
            for (var i = 0; i < vertices.length; i++) {
                var v1 = vertices[i];
                var v2 = vertices[(i + 1) % vertices.length];

                // Calculate midpoint
                var midLat = (v1.lat + v2.lat) / 2;
                var midLng = (v1.lng + v2.lng) / 2;

                // Create midpoint marker
                var midpointIcon = L.divIcon({
                    className: 'gis-midpoint-handle',
                    html: '<div class="gis-midpoint-diamond"></div>',
                    iconSize: [12, 12],
                    iconAnchor: [6, 6]
                });

                var marker = L.marker([midLat, midLng], {
                    icon: midpointIcon,
                    draggable: true
                }).addTo(this.drawingLayer);

                // Store the edge index
                marker._edgeIndex = i;

                // Drag handler - insert new vertex
                marker.on('dragend', function(e) {
                    var edgeIndex = e.target._edgeIndex;
                    var newPos = e.target.getLatLng();

                    // Insert new vertex after edgeIndex
                    self.state.vertices.splice(edgeIndex + 1, 0, {
                        lat: newPos.lat,
                        lng: newPos.lng
                    });

                    // Rebuild everything
                    self._rebuildAllMarkers();
                    self._updatePolygon();
                    self._calculateMetrics();
                    self._updateInfoPanel();
                    self._checkSelfIntersection();
                    self._checkAreaWarnings();
                    self._saveToHiddenField();

                    // Re-check overlaps after adding new vertex in preview mode
                    if (self.state.phase === 'PREVIEW') {
                        self._checkOverlaps();
                    }

                    self._showToast('Corner ' + (edgeIndex + 2) + ' added', 'success');
                });

                // Visual feedback on hover
                marker.on('mouseover', function() {
                    this.getElement().classList.add('hover');
                });
                marker.on('mouseout', function() {
                    this.getElement().classList.remove('hover');
                });

                this.midpointMarkers.push(marker);
            }
        },

        /**
         * Clear midpoint markers
         */
        _clearMidpointMarkers: function() {
            var self = this;
            this.midpointMarkers.forEach(function(m) {
                self.drawingLayer.removeLayer(m);
            });
            this.midpointMarkers = [];
        },

        // =============================================
        // VALIDATION MODULE
        // Self-intersection detection, area warnings, vertex limits
        // Dependencies: Turf.js for geometry analysis
        // =============================================

        /**
         * Check for self-intersection using turf.kinks() with sweepline-intersections fallback
         * Uses multiple algorithms to ensure reliable detection during real-time editing
         */
        _checkSelfIntersection: function() {
            this._clearIntersectionHighlights();

            // Need at least 3 vertices to form a polygon
            if (this.state.vertices.length < 3) {
                this._hideWarning('intersection');
                return false;
            }

            try {
                // Build closed ring coordinates for turf.polygon
                var coords = this.state.vertices.map(function(v) {
                    return [v.lng, v.lat];
                });
                coords.push(coords[0]); // Close the ring

                var polygon = turf.polygon([coords]);
                var intersections = [];

                // Primary method: turf.kinks()
                var kinks = turf.kinks(polygon);
                if (kinks.features.length > 0) {
                    console.log('[GIS] Self-intersection detected via turf.kinks: ' + kinks.features.length + ' kink(s)');
                    intersections = kinks.features.map(function(f) {
                        return {
                            lng: f.geometry.coordinates[0],
                            lat: f.geometry.coordinates[1]
                        };
                    });
                }

                // Fallback method: sweepline-intersections (if turf.kinks found nothing)
                if (intersections.length === 0 && typeof sweeplineIntersections !== 'undefined') {
                    try {
                        var sweeplineResult = sweeplineIntersections(polygon.geometry);
                        if (sweeplineResult && sweeplineResult.length > 0) {
                            console.log('[GIS] Self-intersection detected via sweepline: ' + sweeplineResult.length + ' intersection(s)');
                            intersections = sweeplineResult.map(function(pt) {
                                return {
                                    lng: pt[0],
                                    lat: pt[1]
                                };
                            });
                        }
                    } catch (sweepErr) {
                        console.warn('[GIS] Sweepline fallback failed:', sweepErr);
                    }
                }

                if (intersections.length > 0) {
                    console.log('[GIS] Total intersections found: ' + intersections.length);

                    // Store intersection points
                    this.state.intersectionPoints = intersections;

                    // Highlight intersection points
                    this._highlightIntersectionPoints(intersections);

                    // Show warning
                    this._showWarning('intersection', 'Boundary lines are crossing. Adjust the corners to fix this.');
                    return true;
                } else {
                    console.log('[GIS] Self-intersection check: polygon is valid');
                    this._hideWarning('intersection');
                    return false;
                }
            } catch (e) {
                console.warn('[GISCapture] Self-intersection check failed:', e);
                this._hideWarning('intersection');
                return false;
            }
        },

        /**
         * Check if two line segments intersect and return intersection point
         * Uses cross-product based parametric line intersection algorithm
         */
        _lineSegmentIntersection: function(x1, y1, x2, y2, x3, y3, x4, y4) {
            // Cross-product based line segment intersection
            // Segment 1: (x1,y1) to (x2,y2)
            // Segment 2: (x3,y3) to (x4,y4)
            var denom = (x2 - x1) * (y4 - y3) - (y2 - y1) * (x4 - x3);
            if (Math.abs(denom) < 1e-10) return null; // Parallel lines

            var t = ((x3 - x1) * (y4 - y3) - (y3 - y1) * (x4 - x3)) / denom;
            var u = ((x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)) / denom;

            console.log('[GIS DEBUG] Intersection check: t=' + t.toFixed(4) + ', u=' + u.toFixed(4));

            // Check if intersection is within both line segments (exclusive of endpoints)
            // Both t and u must be in (0,1) for intersection within segments
            if (t > 0.0001 && t < 0.9999 && u > 0.0001 && u < 0.9999) {
                console.log('[GIS DEBUG] INTERSECTION FOUND at t=' + t.toFixed(4) + ', u=' + u.toFixed(4));
                return {
                    lng: x1 + t * (x2 - x1),
                    lat: y1 + t * (y2 - y1)
                };
            }
            return null;
        },

        /**
         * Self-test for intersection algorithms - runs on init to verify correctness
         * Tests both turf.kinks() and sweepline-intersections fallback
         */
        _testSelfIntersection: function() {
            var allPassed = true;

            try {
                // Test polygons
                var bowtie = turf.polygon([[[0, 0], [1, 1], [1, 0], [0, 1], [0, 0]]]);
                var square = turf.polygon([[[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]]);
                var figure8 = turf.polygon([[[0, 0], [2, 2], [2, 0], [0, 2], [0, 0]]]);
                var triangle = turf.polygon([[[0, 0], [1, 0], [0.5, 1], [0, 0]]]);

                // Test turf.kinks()
                var kinks1 = turf.kinks(bowtie);
                var kinks2 = turf.kinks(square);
                var kinks3 = turf.kinks(figure8);
                var kinks4 = turf.kinks(triangle);

                var p1 = kinks1.features.length === 1;
                var p2 = kinks2.features.length === 0;
                var p3 = kinks3.features.length === 1;
                var p4 = kinks4.features.length === 0;

                console.log('[GIS] turf.kinks() tests:');
                console.log('[GIS]   Test 1 (bowtie - 1 kink): ' + (p1 ? 'PASS' : 'FAIL') + ' (found: ' + kinks1.features.length + ')');
                console.log('[GIS]   Test 2 (square - 0 kinks): ' + (p2 ? 'PASS' : 'FAIL') + ' (found: ' + kinks2.features.length + ')');
                console.log('[GIS]   Test 3 (figure-8 - 1 kink): ' + (p3 ? 'PASS' : 'FAIL') + ' (found: ' + kinks3.features.length + ')');
                console.log('[GIS]   Test 4 (triangle - 0 kinks): ' + (p4 ? 'PASS' : 'FAIL') + ' (found: ' + kinks4.features.length + ')');

                var turfPassed = p1 && p2 && p3 && p4;
                console.log('[GIS]   turf.kinks result: ' + (turfPassed ? 'ALL PASSED' : 'SOME FAILED'));

                // Test sweepline-intersections if available
                if (typeof sweeplineIntersections !== 'undefined') {
                    var sw1 = sweeplineIntersections(bowtie.geometry);
                    var sw2 = sweeplineIntersections(square.geometry);
                    var sw3 = sweeplineIntersections(figure8.geometry);
                    var sw4 = sweeplineIntersections(triangle.geometry);

                    var sp1 = sw1.length === 1;
                    var sp2 = sw2.length === 0;
                    var sp3 = sw3.length === 1;
                    var sp4 = sw4.length === 0;

                    console.log('[GIS] sweepline-intersections tests:');
                    console.log('[GIS]   Test 1 (bowtie - 1 intersection): ' + (sp1 ? 'PASS' : 'FAIL') + ' (found: ' + sw1.length + ')');
                    console.log('[GIS]   Test 2 (square - 0 intersections): ' + (sp2 ? 'PASS' : 'FAIL') + ' (found: ' + sw2.length + ')');
                    console.log('[GIS]   Test 3 (figure-8 - 1 intersection): ' + (sp3 ? 'PASS' : 'FAIL') + ' (found: ' + sw3.length + ')');
                    console.log('[GIS]   Test 4 (triangle - 0 intersections): ' + (sp4 ? 'PASS' : 'FAIL') + ' (found: ' + sw4.length + ')');

                    var sweeplinePassed = sp1 && sp2 && sp3 && sp4;
                    console.log('[GIS]   sweepline result: ' + (sweeplinePassed ? 'ALL PASSED' : 'SOME FAILED'));
                    allPassed = turfPassed && sweeplinePassed;
                } else {
                    console.log('[GIS] sweepline-intersections not loaded, skipping fallback tests');
                    allPassed = turfPassed;
                }

                console.log('[GIS] Self-intersection tests: ' + (allPassed ? 'ALL PASSED' : 'SOME FAILED'));
            } catch (e) {
                console.error('[GIS] Self-intersection test error:', e);
                allPassed = false;
            }

            return allPassed;
        },

        /**
         * Highlight intersection points on the map
         */
        _highlightIntersectionPoints: function(intersections) {
            var self = this;
            intersections.forEach(function(pt) {
                var marker = L.circleMarker([pt.lat, pt.lng], {
                    radius: 8,
                    color: '#ff0000',
                    fillColor: '#ff0000',
                    fillOpacity: 0.8,
                    weight: 2
                }).addTo(self.drawingLayer);
                self.intersectionMarkers.push(marker);
            });
        },

        /**
         * Debounced self-intersection check - delays O(n²) calculation
         * Use this during rapid changes (e.g., vertex dragging)
         */
        _checkSelfIntersectionDebounced: function() {
            var self = this;
            // Create debounced function on first call and cache it
            if (!this._debouncedSelfIntersectionFn) {
                this._debouncedSelfIntersectionFn = debounce(function() {
                    self._checkSelfIntersection();
                }, 150);
            }
            this._debouncedSelfIntersectionFn();
        },

        /**
         * Highlight edges that intersect
         */
        _highlightIntersectingEdges: function(intersectionFeatures) {
            var self = this;
            var vertices = this.state.vertices;

            // Find which edges contain the intersection points
            var edgesToHighlight = new Set();

            intersectionFeatures.forEach(function(feature) {
                var pt = turf.point(feature.geometry.coordinates);

                // Check each edge
                for (var i = 0; i < vertices.length; i++) {
                    var v1 = vertices[i];
                    var v2 = vertices[(i + 1) % vertices.length];
                    var line = turf.lineString([[v1.lng, v1.lat], [v2.lng, v2.lat]]);
                    var distance = turf.pointToLineDistance(pt, line, { units: 'meters' });

                    if (distance < 1) { // Within 1 meter of the edge
                        edgesToHighlight.add(i);
                    }
                }
            });

            // Draw red lines for intersecting edges
            edgesToHighlight.forEach(function(i) {
                var v1 = vertices[i];
                var v2 = vertices[(i + 1) % vertices.length];

                var line = L.polyline([
                    [v1.lat, v1.lng],
                    [v2.lat, v2.lng]
                ], {
                    color: '#dc3545',
                    weight: 5,
                    opacity: 0.8
                }).addTo(self.drawingLayer);

                self.intersectingEdgeLines.push(line);
            });

            // Add markers at intersection points
            intersectionFeatures.forEach(function(feature) {
                var marker = L.circleMarker([
                    feature.geometry.coordinates[1],
                    feature.geometry.coordinates[0]
                ], {
                    radius: 8,
                    fillColor: '#dc3545',
                    fillOpacity: 1,
                    color: 'white',
                    weight: 2
                }).addTo(self.drawingLayer);

                // Add warning icon tooltip
                marker.bindTooltip('Lines crossing', {
                    permanent: false,
                    direction: 'top',
                    className: 'gis-intersection-tooltip'
                });

                self.intersectionMarkers.push(marker);
            });
        },

        /**
         * Clear intersection highlights
         */
        _clearIntersectionHighlights: function() {
            var self = this;

            this.intersectingEdgeLines.forEach(function(line) {
                self.drawingLayer.removeLayer(line);
            });
            this.intersectingEdgeLines = [];

            this.intersectionMarkers.forEach(function(marker) {
                self.drawingLayer.removeLayer(marker);
            });
            this.intersectionMarkers = [];

            this.state.intersectionPoints = [];
        },

        /**
         * Check area warnings and display them
         */
        _checkAreaWarnings: function() {
            var metrics = this.state.metrics;
            var validation = this.options.validation;

            if (!metrics) {
                this._hideWarning('area');
                return;
            }

            var warnings = [];

            // Check minimum area
            if (validation.minAreaHectares && metrics.areaHectares < validation.minAreaHectares) {
                warnings.push('Area (' + metrics.areaHectares.toFixed(4) + ' ha) is very small (minimum: ' + validation.minAreaHectares + ' ha)');
            }

            // Check maximum area
            if (validation.maxAreaHectares && metrics.areaHectares > validation.maxAreaHectares) {
                warnings.push('Area (' + metrics.areaHectares.toFixed(2) + ' ha) exceeds maximum (' + validation.maxAreaHectares + ' ha)');
            }

            if (warnings.length > 0) {
                this._showWarning('area', warnings.join('. '));
            } else {
                this._hideWarning('area');
            }
        },

        /**
         * Check vertex limit warning (at 90% of max)
         */
        _checkVertexLimitWarning: function() {
            var validation = this.options.validation;
            var vertexCount = this.state.vertices.length;
            var maxVertices = validation.maxVertices || 100;

            // Calculate 90% threshold
            var warningThreshold = Math.floor(maxVertices * 0.9);

            if (vertexCount >= maxVertices) {
                // At or over limit - show error
                this._showWarning('vertexLimit', 'Maximum corners reached (' + vertexCount + '/' + maxVertices + '). Cannot add more.');
            } else if (vertexCount >= warningThreshold) {
                // Approaching limit - show warning
                this._showWarning('vertexLimit', 'Corners: ' + vertexCount + '/' + maxVertices + ' - approaching limit. Consider simplifying.');
            } else {
                this._hideWarning('vertexLimit');
            }
        },

        /**
         * Show a warning message
         */
        _showWarning: function(type, message) {
            if (!this.warningPanel) return;

            // Check if this warning type already exists
            var existingWarning = this.warningPanel.querySelector('[data-warning-type="' + type + '"]');
            if (existingWarning) {
                existingWarning.querySelector('.gis-warning-text').textContent = message;
                return;
            }

            // Build warning element using DOM methods to prevent XSS
            var warningDiv = document.createElement('div');
            warningDiv.className = 'gis-warning-item ' + this._escapeHtml(type);
            warningDiv.setAttribute('data-warning-type', type);

            var iconSpan = document.createElement('span');
            iconSpan.className = 'gis-warning-icon';
            iconSpan.textContent = '⚠';

            var textSpan = document.createElement('span');
            textSpan.className = 'gis-warning-text';
            textSpan.textContent = message; // textContent is XSS-safe

            warningDiv.appendChild(iconSpan);
            warningDiv.appendChild(textSpan);

            this.warningPanel.appendChild(warningDiv);
            this.warningPanel.style.display = 'block';
        },

        /**
         * Hide a specific warning type
         */
        _hideWarning: function(type) {
            if (!this.warningPanel) return;

            var existingWarning = this.warningPanel.querySelector('[data-warning-type="' + type + '"]');
            if (existingWarning) {
                existingWarning.remove();
            }

            // Hide panel if no warnings left
            if (this.warningPanel.children.length === 0) {
                this.warningPanel.style.display = 'none';
            }
        },

        /**
         * Clear all warnings
         */
        _clearAllWarnings: function() {
            if (this.warningPanel) {
                this.warningPanel.innerHTML = '';
                this.warningPanel.style.display = 'none';
            }
        },

        /**
         * Update polygon on map
         */
        _updatePolygon: function() {
            if (this.state.polygon) {
                this.drawingLayer.removeLayer(this.state.polygon);
            }

            if (this.state.vertices.length < 2) return;

            var coords = this.state.vertices.map(function(v) {
                return [v.lat, v.lng];
            });

            // Close the polygon
            if (this.state.vertices.length >= 3) {
                coords.push([this.state.vertices[0].lat, this.state.vertices[0].lng]);
            }

            this.state.polygon = L.polygon(coords, {
                fillColor: this.options.style.fillColor,
                fillOpacity: this.options.style.fillOpacity,
                color: this.options.style.strokeColor,
                weight: this.options.style.strokeWidth
            }).addTo(this.drawingLayer);
        },

        /**
         * Calculate metrics using Turf.js
         */
        _calculateMetrics: function() {
            if (this.state.vertices.length < 3) {
                this.state.metrics = null;
                return;
            }

            try {
                var coords = this.state.vertices.map(function(v) {
                    return [v.lng, v.lat]; // GeoJSON is [lng, lat]
                });
                coords.push(coords[0]); // Close the ring

                var polygon = turf.polygon([coords]);
                var area = turf.area(polygon);
                var areaHectares = area / 10000;
                var perimeter = turf.length(turf.polygonToLine(polygon), { units: 'meters' });
                var centroid = turf.centroid(polygon);

                this.state.metrics = {
                    areaSquareMeters: area,
                    areaHectares: areaHectares,
                    perimeterMeters: perimeter,
                    centroid: {
                        lat: centroid.geometry.coordinates[1],
                        lng: centroid.geometry.coordinates[0]
                    },
                    vertexCount: this.state.vertices.length
                };

                // Callback
                if (this.options.onGeometryChange) {
                    this.options.onGeometryChange(this._toGeoJSON(), this.state.metrics);
                }

            } catch (e) {
                console.error('[GISCapture] Metrics calculation error:', e);
            }
        },

        /**
         * Update info panel
         */
        _updateInfoPanel: function() {
            if (!this.state.metrics) {
                this.infoPanel.style.display = 'none';
                return;
            }

            this.infoPanel.style.display = 'flex';
            var html =
                '<div class="gis-info-item">' +
                    '<span class="gis-info-label">Area</span>' +
                    '<span class="gis-info-value highlight">' +
                        this.state.metrics.areaHectares.toFixed(2) + ' ha</span>' +
                '</div>' +
                '<div class="gis-info-item">' +
                    '<span class="gis-info-label">Perimeter</span>' +
                    '<span class="gis-info-value">' +
                        this.state.metrics.perimeterMeters.toFixed(0) + ' m</span>' +
                '</div>' +
                '<div class="gis-info-item">' +
                    '<span class="gis-info-label">Corners</span>' +
                    '<span class="gis-info-value">' +
                        this.state.metrics.vertexCount + '</span>' +
                '</div>';

            // Show GPS average for Walk Mode
            if (this.state.mode === 'WALK' && this.state.gpsAccuracyValues.length > 0) {
                var avgAccuracy = this._calculateGPSAverage();
                html += '<div class="gis-info-item">' +
                    '<span class="gis-info-label">GPS Avg</span>' +
                    '<span class="gis-info-value">±' + avgAccuracy + 'm</span>' +
                '</div>';
            }

            this.infoPanel.innerHTML = html;
        },

        /**
         * Calculate average GPS accuracy from tracked values
         */
        _calculateGPSAverage: function() {
            if (this.state.gpsAccuracyValues.length === 0) return 0;

            var sum = this.state.gpsAccuracyValues.reduce(function(a, b) {
                return a + b;
            }, 0);
            return Math.round(sum / this.state.gpsAccuracyValues.length);
        },

        /**
         * Check if should auto-close polygon (Walk Mode)
         */
        _checkAutoClose: function() {
            if (!this.state.currentPosition || this.state.vertices.length < 3) return;

            var start = this.state.vertices[0];
            var distance = this._distanceBetween(
                start.lat, start.lng,
                this.state.currentPosition.lat, this.state.currentPosition.lng
            );

            if (distance <= this.options.gps.autoCloseDistance) {
                this._showAutoClosePrompt();
            }
        },

        /**
         * Show auto-close prompt
         */
        _showAutoClosePrompt: function() {
            var self = this;

            if (this._autoCloseShown) return;
            this._autoCloseShown = true;

            var toast = document.createElement('div');
            toast.className = 'gis-toast warning';
            toast.innerHTML = 'Close to start point. <button>Close Polygon</button>';
            toast.querySelector('button').onclick = function() {
                toast.remove();
                self._completePolygon();
            };

            var container = document.createElement('div');
            container.className = 'gis-toast-container';
            container.appendChild(toast);
            this.wrapper.appendChild(container);

            setTimeout(function() {
                container.remove();
                self._autoCloseShown = false;
            }, 5000);
        },

        /**
         * Complete polygon drawing
         */
        _completePolygon: function() {
            if (this.state.vertices.length < 3) {
                this._showToast('Need at least 3 corners', 'error');
                return;
            }

            // Stop GPS tracking
            if (this.state.gpsWatchId) {
                navigator.geolocation.clearWatch(this.state.gpsWatchId);
            }

            // Clean up Draw Mode specific handlers
            if (this._boundMouseMoveHandler) {
                this.map.off('mousemove', this._boundMouseMoveHandler);
                this._boundMouseMoveHandler = null;
            }
            if (this._boundDblClickHandler) {
                this.map.off('dblclick', this._boundDblClickHandler);
                this._boundDblClickHandler = null;
            }

            // Re-enable double-click zoom
            this.map.doubleClickZoom.enable();

            // Note: Keep keyboard handler active for Delete key in preview mode

            // Remove ghost line
            if (this.ghostLine) {
                this.drawingLayer.removeLayer(this.ghostLine);
                this.ghostLine = null;
            }

            // Hide close hint
            this._hideCloseHint();

            this.state.phase = 'PREVIEW';

            // Update step progress to step 2
            this._addStepProgress(2);

            this._renderPreviewState();

            // Validate
            this._validate();

            // Save to hidden field
            this._saveToHiddenField();

            // Check for overlaps with existing records
            this._checkOverlaps();
        },

        /**
         * Render preview state
         */
        _renderPreviewState: function() {
            var self = this;

            // Remove mode-specific UI
            if (this.markBtn) this.markBtn.remove();
            if (this.gpsPanel) this.gpsPanel.remove();

            // Reset cursor
            this.mapContainer.style.cursor = '';

            // Update toolbar for preview
            this._addPreviewToolbar();

            // Set up keyboard handler for Delete key if not already set
            if (!this._boundKeyDownHandler) {
                this._boundKeyDownHandler = function(e) {
                    self._handleKeyDown(e);
                };
                document.addEventListener('keydown', this._boundKeyDownHandler);
            }

            // Add midpoint markers for editing
            this._addMidpointMarkers();

            // Check for warnings
            this._checkSelfIntersection();
            this._checkAreaWarnings();

            // Fit map to polygon
            if (this.state.polygon) {
                // May 2026 fix — when an existing polygon is loaded into a
                // freshly-rendered map, fitBounds runs synchronously against
                // the container's pixel dimensions to compute zoom + tile
                // pyramid + tile DOM transforms. Inside a wizard step that's
                // just become visible, the container is often still 0×0 at
                // this moment: tiles get fetched (visible in
                // `.leaflet-tile-loaded.length`) but their CSS transforms
                // place them outside the eventual viewport, leaving the
                // polygon fill painting over a grey background.
                //
                // The fix is twofold: invalidateSize NOW (so any later
                // setView sees real dimensions), AND defer the fitBounds via
                // setTimeout so it runs after browser layout has had a chance
                // to give the container its real size. 250 ms is a forgiving
                // upper bound for wizard-step transition time.
                var self = this;
                this.map.invalidateSize();
                setTimeout(function() {
                    if (self.state && self.state.polygon && self.map) {
                        self.map.invalidateSize();
                        self.map.fitBounds(
                            self.state.polygon.getBounds(),
                            { padding: [50, 50] });
                    }
                }, 250);
            }
        },

        /**
         * Add preview toolbar
         */
        _addPreviewToolbar: function() {
            var self = this;

            if (this.toolbar) {
                this.toolbar.remove();
            }

            this.toolbar = document.createElement('div');
            this.toolbar.className = 'gis-toolbar';

            // Edit button
            var editBtn = document.createElement('button');
            editBtn.type = 'button';
            editBtn.className = 'gis-toolbar-btn';
            editBtn.innerHTML = '✏️ Edit';
            editBtn.setAttribute('aria-label', 'Edit polygon boundary');
            editBtn.onclick = function(e) { e.preventDefault(); e.stopPropagation(); self._enterEditMode(); };
            this.toolbar.appendChild(editBtn);

            // Redraw button
            var redrawBtn = document.createElement('button');
            redrawBtn.type = 'button';
            redrawBtn.className = 'gis-toolbar-btn danger';
            redrawBtn.innerHTML = '🔄 Redraw';
            redrawBtn.setAttribute('aria-label', 'Clear and redraw polygon');
            redrawBtn.onclick = function(e) { e.preventDefault(); e.stopPropagation(); self._redraw(); };
            this.toolbar.appendChild(redrawBtn);

            this.wrapper.insertBefore(this.toolbar, this.mapContainer);
        },

        /**
         * Enter edit mode
         */
        _enterEditMode: function() {
            var self = this;
            this.state.mode = 'DRAW';
            this.state.phase = 'DRAWING';
            this.mapContainer.style.cursor = 'crosshair';

            // Clear midpoint markers (will show during drawing as ghost line instead)
            this._clearMidpointMarkers();

            // Clear overlap display (will recheck when editing is complete)
            this._clearOverlapDisplay();
            this._hideOverlapPanel();
            this.state.overlapChecked = false;

            // Update step progress back to step 1
            this._addStepProgress(1);

            this._addDrawToolbar();

            // Re-enable ghost line tracking
            this._boundMouseMoveHandler = function(e) {
                self._updateGhostLine(e.latlng);
                self._checkCloseHint(e.latlng);
            };
            this.map.on('mousemove', this._boundMouseMoveHandler);

            // Re-enable double-click to finish
            this._boundDblClickHandler = function(e) {
                L.DomEvent.preventDefault(e);
                L.DomEvent.stopPropagation(e);
                if (self.state.vertices.length >= 3) {
                    self._completePolygon();
                }
            };
            this.map.on('dblclick', this._boundDblClickHandler);

            // Set up keyboard handler for Delete key if not already set
            if (!this._boundKeyDownHandler) {
                this._boundKeyDownHandler = function(e) {
                    self._handleKeyDown(e);
                };
                document.addEventListener('keydown', this._boundKeyDownHandler);
            }

            // Disable map double-click zoom during drawing
            this.map.doubleClickZoom.disable();
        },

        /**
         * Redraw polygon
         */
        _redraw: function() {
            // Clean up handlers
            if (this._boundMouseMoveHandler) {
                this.map.off('mousemove', this._boundMouseMoveHandler);
                this._boundMouseMoveHandler = null;
            }
            if (this._boundDblClickHandler) {
                this.map.off('dblclick', this._boundDblClickHandler);
                this._boundDblClickHandler = null;
            }
            this.map.doubleClickZoom.enable();

            // Remove step progress
            if (this.stepProgress) {
                this.stepProgress.remove();
                this.stepProgress = null;
            }

            this._clearDrawing();
            this._showEmptyState();
        },

        /**
         * Validate polygon
         */
        _validate: function() {
            var errors = [];
            var warnings = [];
            var validation = this.options.validation;
            var metrics = this.state.metrics;

            if (!metrics) return;

            // Check area
            if (metrics.areaHectares < validation.minAreaHectares) {
                warnings.push('Area (' + metrics.areaHectares.toFixed(4) + ' ha) is very small');
            }
            if (metrics.areaHectares > validation.maxAreaHectares) {
                warnings.push('Area (' + metrics.areaHectares.toFixed(2) + ' ha) exceeds maximum');
            }

            // Check vertices
            if (metrics.vertexCount < validation.minVertices) {
                errors.push('Need at least ' + validation.minVertices + ' corners');
            }
            if (metrics.vertexCount > validation.maxVertices) {
                warnings.push('Too many corners (' + metrics.vertexCount + '). Consider simplifying.');
            }

            // Check self-intersection using turf.kinks with sweepline fallback
            if (!validation.allowSelfIntersection) {
                try {
                    var geojson = this._toGeoJSON();
                    var hasIntersection = false;

                    // Primary: turf.kinks()
                    var kinks = turf.kinks(geojson);
                    if (kinks.features.length > 0) {
                        hasIntersection = true;
                    }

                    // Fallback: sweepline-intersections
                    if (!hasIntersection && typeof sweeplineIntersections !== 'undefined') {
                        try {
                            var sweepResult = sweeplineIntersections(geojson.geometry);
                            if (sweepResult && sweepResult.length > 0) {
                                hasIntersection = true;
                            }
                        } catch (sweepErr) {
                            console.warn('[GISCapture] Sweepline validation fallback failed:', sweepErr);
                        }
                    }

                    if (hasIntersection) {
                        errors.push('Boundary lines are crossing');
                    }
                } catch (e) {
                    console.warn('[GISCapture] Self-intersection check failed:', e);
                }
            }

            // Display validation results
            this._showValidation(errors, warnings);

            if (errors.length > 0 && this.options.onValidationError) {
                this.options.onValidationError(errors);
            }
        },

        /**
         * Show validation results
         */
        _showValidation: function(errors, warnings) {
            if (errors.length === 0 && warnings.length === 0) {
                this.validationPanel.style.display = 'block';
                this.validationPanel.innerHTML = 
                    '<div class="gis-validation-success">' +
                        '<span>✓</span> Boundary captured successfully' +
                    '</div>';
                return;
            }

            var html = '';
            errors.forEach(function(err) {
                html += '<div class="gis-validation-error"><span>✗</span> ' + err + '</div>';
            });
            warnings.forEach(function(warn) {
                html += '<div class="gis-validation-warning"><span>⚠</span> ' + warn + '</div>';
            });

            this.validationPanel.style.display = 'block';
            this.validationPanel.innerHTML = html;
        },

        // =============================================
        // END VALIDATION MODULE
        // =============================================

        // =============================================
        // OVERLAP CHECKING MODULE
        // API calls to check overlap with existing records
        // Dependencies: API endpoint, fetch with abort controller
        // =============================================

        /**
         * Check for overlapping polygons if enabled
         */
        _checkOverlaps: function() {
            var self = this;
            var overlapConfig = this.options.overlap;

            // Skip if overlap checking is not enabled
            if (!overlapConfig || !overlapConfig.enabled) {
                return;
            }

            // Skip if no geometry
            var geojson = this._toGeoJSON();
            if (!geojson) {
                return;
            }

            // Clear previous overlaps
            this._clearOverlapDisplay();
            this.state.overlaps = [];
            this.state.overlapChecked = false;
            this.state.overlapConfirmed = false;

            // Show loading state
            this.state.overlapCheckPending = true;
            this._showOverlapLoading();

            // Build API request - endpoint is /gis/gis/checkOverlap per OpenAPI spec
            var apiUrl = this.options.apiBase + '/checkOverlap';

            // Parse displayFields into returnFields array
            var returnFields = [];
            if (overlapConfig.displayFields) {
                returnFields = overlapConfig.displayFields.split(',').map(function(f) {
                    return f.trim();
                }).filter(function(f) {
                    return f.length > 0;
                });
            }

            // Build request body per server API spec
            var targetConfig = {
                formId: overlapConfig.formId || '',
                geometryFieldId: overlapConfig.geometryField || 'c_geometry',
                filterCondition: overlapConfig.filterCondition || null
            };

            // Exclude current record from overlap check when editing (Phase 4)
            if (self.options.recordId) {
                targetConfig.excludeRecordId = self.options.recordId;
                console.log('[GIS] Overlap check - excluding recordId: ' + self.options.recordId);
            } else {
                console.log('[GIS] Overlap check - NO recordId to exclude (will match all records)');
            }

            var requestBody = {
                geometry: geojson,
                target: targetConfig,
                options: {
                    returnFields: returnFields,
                    minOverlapPercent: 1.0,
                    maxResults: 10,
                    includeOverlapGeometry: true
                }
            };

            // Validate API URL for security
            var validatedUrl = this._validateApiUrl(apiUrl);
            if (!validatedUrl) {
                console.error('[GIS] Invalid API URL, skipping overlap check');
                self.state.overlapCheckPending = false;
                self._hideOverlapPanel();
                return;
            }

            // Cancel any previous overlap check request (race condition prevention)
            if (this._overlapRequestId) {
                this._abortRequest(this._overlapRequestId);
            }

            // Create new request ID and abort controller
            var requestId = this._generateRequestId();
            this._overlapRequestId = requestId;
            var abortController = this._createAbortController(requestId);

            // Build headers (session-based auth, no explicit credentials)
            var headers = this._buildApiHeaders();

            // Set up timeout (30 seconds)
            var timeoutId = setTimeout(function() {
                self._abortRequest(requestId);
                console.warn('[GIS] Overlap check timed out after 30 seconds');
            }, 30000);

            // Make API call with abort signal
            fetch(validatedUrl, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(requestBody),
                signal: abortController.signal
            })
            .then(function(response) {
                clearTimeout(timeoutId);
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status + ': ' + response.statusText);
                }
                return response.json();
            })
            .then(function(response) {
                // Ignore response if this is a stale request
                if (requestId !== self._overlapRequestId) {
                    console.log('[GIS] Ignoring stale overlap check response');
                    return;
                }

                self._cleanupRequest(requestId);
                self.state.overlapCheckPending = false;
                self.state.overlapChecked = true;

                // Joget API wraps response in { code, message: "JSON string" } format
                // Need to parse the message field if it's a string
                var parsedResponse = response;
                if (response.message && typeof response.message === 'string') {
                    try {
                        parsedResponse = JSON.parse(response.message);
                    } catch (e) {
                        // Fall back to original response
                    }
                }

                // Parse server response format: { success, data: { hasOverlaps, overlaps: [...] } }
                var data = parsedResponse.data || parsedResponse;
                var hasOverlaps = data.hasOverlaps || (data.overlaps && data.overlaps.length > 0);

                if (hasOverlaps && data.overlaps && data.overlaps.length > 0) {

                    // Transform server response to internal format
                    var overlaps = data.overlaps.map(function(item) {
                        return {
                            id: item.recordId,
                            geometry: item.overlapGeometry || null,
                            displayValues: item.recordData || {},
                            overlapArea: item.overlapAreaHectares,
                            overlapPercentage: item.overlapPercentOfInput
                        };
                    });

                    // Client-side self-overlap filter (Phase 4 enhancement)
                    // When editing an existing record and the polygon was SHRUNK, the new polygon
                    // will be entirely inside the original (stored in DB), causing 100% overlap.
                    // We detect this by checking: edit mode + high overlap% + polygon was shrunk
                    var isEditMode = self.options.recordId || self.state.initialGeometry;
                    var initialArea = self.state.initialAreaHectares;
                    var currentArea = self.state.metrics ? self.state.metrics.areaHectares : null;

                    console.log('[GIS] Self-overlap filter check:');
                    console.log('[GIS]   isEditMode=' + !!isEditMode + ' (recordId=' + (self.options.recordId || 'none') + ')');
                    console.log('[GIS]   initialArea=' + (initialArea ? initialArea.toFixed(4) + ' ha' : 'not set'));
                    console.log('[GIS]   currentArea=' + (currentArea ? currentArea.toFixed(4) + ' ha' : 'not set'));
                    if (initialArea && currentArea) {
                        console.log('[GIS]   Polygon state: ' +
                            (currentArea > initialArea ? 'EXPANDED' :
                             currentArea < initialArea ? 'SHRUNK' : 'SAME SIZE'));
                    }

                    if (isEditMode && currentArea) {
                        var originalOverlapCount = overlaps.length;

                        overlaps = overlaps.filter(function(overlap) {
                            console.log('[GIS] Checking overlap: id=' + overlap.id +
                                ', percentage=' + overlap.overlapPercentage.toFixed(1) +
                                '%, overlapArea=' + overlap.overlapArea.toFixed(4) + ' ha');

                            // Strategy 1: If polygon was SHRUNK and overlap is ~100%
                            // The new smaller polygon fits entirely inside the original
                            if (overlap.overlapPercentage >= 95.0 && initialArea && initialArea > currentArea) {
                                console.log('[GIS] Polygon was shrunk (initial=' + initialArea.toFixed(4) +
                                    ' > current=' + currentArea.toFixed(4) + ') and overlap is ' +
                                    overlap.overlapPercentage.toFixed(1) + '% - filtering as self-overlap');
                                return false;
                            }

                            // Strategy 2: If overlap area ≈ current area (same polygon, not shrunk)
                            if (overlap.overlapPercentage >= 99.0) {
                                var areaDiff = Math.abs(overlap.overlapArea - currentArea);
                                var areaThreshold = currentArea * 0.02; // 2% tolerance
                                if (areaDiff <= areaThreshold) {
                                    console.log('[GIS] Overlap area ≈ current area - filtering as self-overlap');
                                    return false;
                                }
                            }

                            // Strategy 3: Polygon was EXPANDED - use spatial containment check
                            // If current polygon CONTAINS the initial geometry, it's the same parcel being expanded
                            if (isEditMode && self.state.initialGeometry && currentArea > initialArea) {
                                try {
                                    var currentGeojson = self._toGeoJSON();
                                    var initialGeojson = self.state.initialGeometry;

                                    // Check if current polygon fully contains the original
                                    if (turf.booleanContains(currentGeojson, initialGeojson)) {
                                        // The overlap area should be approximately the initial area
                                        var areaDiffFromInitial = Math.abs(overlap.overlapArea - initialArea);
                                        var initialThreshold = initialArea * 0.10; // 10% tolerance
                                        if (areaDiffFromInitial <= initialThreshold) {
                                            console.log('[GIS] Self-overlap filter: expanded polygon contains original geometry, ' +
                                                'overlap area (' + overlap.overlapArea.toFixed(4) + ' ha) ≈ initial area (' +
                                                initialArea.toFixed(4) + ' ha), filtering out');
                                            return false;
                                        }
                                    }
                                } catch (e) {
                                    console.warn('[GIS] Spatial containment check failed:', e);
                                    // Fallback to simple area comparison
                                    var areaDiffFromInitial = Math.abs(overlap.overlapArea - initialArea);
                                    var initialThreshold = initialArea * 0.05;
                                    if (areaDiffFromInitial <= initialThreshold) {
                                        console.log('[GIS] Self-overlap filter (fallback): polygon expanded, ' +
                                            'overlap area ≈ initial area, filtering out');
                                        return false;
                                    }
                                }
                            }

                            // Strategy 4: SHIFTED polygon - compare overlap geometry to initial geometry
                            // This handles cases where polygon was moved/reshaped (some parts expanded, some shrunk)
                            // If the overlap geometry is essentially the same as our initial geometry, filter it
                            if (isEditMode && self.state.initialGeometry && overlap.geometry) {
                                try {
                                    var initialGeojson = self.state.initialGeometry;
                                    var overlapGeojson = overlap.geometry;

                                    // Compute intersection of overlap geometry with initial geometry
                                    var intersection = turf.intersect(
                                        turf.feature(overlapGeojson),
                                        turf.feature(initialGeojson)
                                    );

                                    if (intersection) {
                                        var intersectionArea = turf.area(intersection) / 10000; // m² to ha
                                        var overlapGeoArea = turf.area(overlapGeojson) / 10000;

                                        // If intersection covers most of both the overlap and initial geometries,
                                        // the overlap IS our initial geometry (or very close to it)
                                        var intersectionOfOverlap = intersectionArea / overlapGeoArea;
                                        var intersectionOfInitial = intersectionArea / initialArea;

                                        console.log('[GIS] Strategy 4 check: intersection=' + intersectionArea.toFixed(4) +
                                            ' ha, overlapGeo=' + overlapGeoArea.toFixed(4) + ' ha, initial=' + initialArea.toFixed(4) +
                                            ' ha, coverage: ' + (intersectionOfOverlap * 100).toFixed(1) + '% of overlap, ' +
                                            (intersectionOfInitial * 100).toFixed(1) + '% of initial');

                                        // Filter if EITHER condition is met:
                                        // 1. Both coverages >= 85% (geometries are essentially the same)
                                        // 2. Overlap is 95%+ contained in initial (overlap IS a subset of our parcel)
                                        // Condition 2 is key: if the overlap geometry is entirely inside our initial,
                                        // it's definitely our own parcel being detected, not a different parcel
                                        if (intersectionOfOverlap >= 0.85 && intersectionOfInitial >= 0.85) {
                                            console.log('[GIS] Self-overlap filter: overlap geometry matches initial geometry ' +
                                                '(>85% mutual coverage), filtering out');
                                            return false;
                                        }
                                        if (intersectionOfOverlap >= 0.95) {
                                            console.log('[GIS] Self-overlap filter: overlap geometry is 95%+ contained within ' +
                                                'initial geometry (overlap IS our parcel), filtering out');
                                            return false;
                                        }
                                    }
                                } catch (e) {
                                    console.warn('[GIS] Strategy 4 (shifted polygon) check failed:', e);
                                }
                            }

                            // Strategy 5: Fallback - high overlap with initial area similarity
                            // If overlap area is very close to initial area (within 15%), likely self-overlap
                            if (isEditMode && initialArea) {
                                var areaDiffFromInitial = Math.abs(overlap.overlapArea - initialArea);
                                var initialThreshold = initialArea * 0.15; // 15% tolerance
                                if (areaDiffFromInitial <= initialThreshold) {
                                    console.log('[GIS] Self-overlap filter: overlap area (' + overlap.overlapArea.toFixed(4) +
                                        ' ha) ≈ initial area (' + initialArea.toFixed(4) + ' ha) within 15%, filtering out');
                                    return false;
                                }
                            }

                            return true; // Keep this overlap
                        });

                        if (overlaps.length < originalOverlapCount) {
                            console.log('[GIS] Self-overlap filter removed ' +
                                (originalOverlapCount - overlaps.length) + ' overlap(s)');
                        }
                    } else {
                        console.log('[GIS] Self-overlap filter skipped: ' +
                            (!isEditMode ? 'not in edit mode' : 'no current metrics'));
                    }

                    // Check if we still have overlaps after filtering
                    if (overlaps.length > 0) {
                        self.state.overlaps = overlaps;
                        self._displayOverlaps(overlaps);
                        self._showOverlapWarning(overlaps);
                    } else {
                        self._hideOverlapPanel();
                    }
                } else {
                    self._hideOverlapPanel();
                }
            })
            .catch(function(error) {
                clearTimeout(timeoutId);
                self._cleanupRequest(requestId);
                self.state.overlapCheckPending = false;
                self._hideOverlapPanel();

                // Handle different error types
                if (error.name === 'AbortError') {
                    // Request was cancelled (timeout or newer request) - don't show error
                    console.log('[GIS] Overlap check cancelled');
                    return;
                }

                // Show user-visible error for actual failures
                console.error('[GIS] Overlap check failed:', error.message);
                self._showToast('Could not check for overlaps. Proceeding without check.', 'warning');

                if (self.options.onError) {
                    self.options.onError('Overlap check failed: ' + error.message);
                }
            });
        },

        /**
         * Display overlapping areas on the map
         * Server returns overlapGeometry (the intersection polygon) directly
         */
        _displayOverlaps: function(overlaps) {
            var self = this;

            // Clear previous display
            this._clearOverlapDisplay();

            overlaps.forEach(function(record) {
                try {
                    // The server returns overlapGeometry which is the intersection area
                    var overlapGeojson = record.geometry;
                    if (!overlapGeojson) {
                        return;
                    }

                    // Parse geometry if it's a string
                    if (typeof overlapGeojson === 'string') {
                        overlapGeojson = JSON.parse(overlapGeojson);
                    }

                    // Handle both Polygon and the geometry wrapper
                    var coordinates = overlapGeojson.coordinates;
                    if (overlapGeojson.type === 'Feature') {
                        coordinates = overlapGeojson.geometry.coordinates;
                    }

                    if (!coordinates || !coordinates[0]) {
                        return;
                    }

                    // Get coordinates for Leaflet - convert [lng, lat] to [lat, lng]
                    var coords = coordinates[0].map(function(c) {
                        return [c[1], c[0]];
                    });

                    // Draw overlap area in red/orange
                    var overlapLayer = L.polygon(coords, {
                        fillColor: '#dc3545',
                        fillOpacity: 0.4,
                        color: '#dc3545',
                        weight: 3
                    }).addTo(self.overlapHighlightLayer);

                    // Add popup with record info
                    var popupContent = self._buildOverlapPopupContent(record);
                    overlapLayer.bindPopup(popupContent);

                } catch (e) {
                    // Skip invalid geometries silently
                }
            });
        },

        /**
         * Build popup content for overlapping record
         */
        _buildOverlapPopupContent: function(record) {
            var self = this;
            var html = '<div class="gis-overlap-popup">';
            html += '<strong>Existing Record</strong><br>';

            // Display configured fields (escape values to prevent XSS)
            if (record.displayValues) {
                Object.keys(record.displayValues).forEach(function(key) {
                    var escapedKey = self._escapeHtml(String(key));
                    var escapedValue = self._escapeHtml(String(record.displayValues[key] || ''));
                    html += '<span>' + escapedKey + ': ' + escapedValue + '</span><br>';
                });
            }

            // Show overlap info (numeric values are safe)
            if (record.overlapArea !== undefined) {
                var area = Number(record.overlapArea);
                html += '<br><span class="gis-overlap-info">Overlap: ' +
                    (isNaN(area) ? '0' : area.toFixed(4)) + ' ha';
                if (record.overlapPercentage !== undefined) {
                    var pct = Number(record.overlapPercentage);
                    html += ' (' + (isNaN(pct) ? '0' : pct.toFixed(1)) + '%)';
                }
                html += '</span>';
            }

            html += '</div>';
            return html;
        },

        /**
         * Show overlap loading state
         */
        _showOverlapLoading: function() {
            if (!this.overlapPanel) return;

            this.overlapPanel.style.display = 'block';
            this.overlapPanel.innerHTML =
                '<div class="gis-overlap-loading">' +
                    '<div class="gis-loading-spinner"></div>' +
                    '<span>Checking for overlapping boundaries...</span>' +
                '</div>';
        },

        /**
         * Show overlap warning panel
         */
        _showOverlapWarning: function(overlaps) {
            var self = this;
            if (!this.overlapPanel) return;

            // Calculate total overlap info
            var totalOverlapArea = 0;
            overlaps.forEach(function(record) {
                if (record.overlapArea) {
                    totalOverlapArea += record.overlapArea;
                }
            });

            // Build record list HTML (escape all user-provided values to prevent XSS)
            var recordsHtml = '';
            overlaps.forEach(function(record, index) {
                var displayText = '';
                if (record.displayValues) {
                    var values = Object.values(record.displayValues).map(function(v) {
                        return self._escapeHtml(String(v || ''));
                    });
                    displayText = values.join(' - ');
                } else if (record.id) {
                    displayText = 'Record ' + self._escapeHtml(String(record.id));
                } else {
                    displayText = 'Record ' + (index + 1);
                }

                var overlapInfo = '';
                if (record.overlapArea !== undefined) {
                    var area = Number(record.overlapArea);
                    overlapInfo = (isNaN(area) ? '0' : area.toFixed(4)) + ' ha';
                    if (record.overlapPercentage !== undefined) {
                        var pct = Number(record.overlapPercentage);
                        overlapInfo += ' (' + (isNaN(pct) ? '0' : pct.toFixed(1)) + '%)';
                    }
                }

                recordsHtml += '<li class="gis-overlap-item">' +
                    '<span class="gis-overlap-record-name">' + displayText + '</span>' +
                    (overlapInfo ? '<span class="gis-overlap-record-info">' + overlapInfo + '</span>' : '') +
                    '</li>';
            });

            // Build panel HTML
            this.overlapPanel.style.display = 'block';
            this.overlapPanel.innerHTML =
                '<div class="gis-overlap-header">' +
                    '<span class="gis-overlap-icon">&#9888;</span>' +
                    '<span class="gis-overlap-title">Overlap Detected</span>' +
                '</div>' +
                '<div class="gis-overlap-content">' +
                    '<p>This boundary overlaps with ' + overlaps.length + ' existing record' +
                        (overlaps.length > 1 ? 's' : '') + ':</p>' +
                    '<ul class="gis-overlap-list">' + recordsHtml + '</ul>' +
                    (totalOverlapArea > 0 ?
                        '<p class="gis-overlap-total">Total overlap area: <strong>' +
                            totalOverlapArea.toFixed(4) + ' ha</strong></p>' : '') +
                '</div>' +
                '<div class="gis-overlap-actions">' +
                    '<button type="button" class="gis-btn gis-btn-secondary gis-overlap-adjust-btn">' +
                        'Adjust Boundary' +
                    '</button>' +
                    '<button type="button" class="gis-btn gis-btn-warning gis-overlap-save-btn">' +
                        'Save Anyway' +
                    '</button>' +
                '</div>';

            // Bind button handlers
            var adjustBtn = this.overlapPanel.querySelector('.gis-overlap-adjust-btn');
            var saveBtn = this.overlapPanel.querySelector('.gis-overlap-save-btn');

            if (adjustBtn) {
                adjustBtn.onclick = function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    self._handleOverlapAdjust();
                };
            }

            if (saveBtn) {
                saveBtn.onclick = function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    self._handleOverlapSaveAnyway();
                };
            }
        },

        /**
         * Handle "Adjust Boundary" button click
         */
        _handleOverlapAdjust: function() {
            // Enter edit mode so user can adjust the boundary
            this._enterEditMode();
            this._hideOverlapPanel();
            this._showToast('Adjust the boundary to avoid overlaps', 'info');
        },

        /**
         * Handle "Save Anyway" button click
         */
        _handleOverlapSaveAnyway: function() {
            this.state.overlapConfirmed = true;
            this._hideOverlapPanel();
            this._clearOverlapDisplay();
            this._showToast('Saving boundary with overlaps', 'warning');

            // Save to hidden field
            this._saveToHiddenField();
        },

        /**
         * Hide overlap panel
         */
        _hideOverlapPanel: function() {
            if (this.overlapPanel) {
                this.overlapPanel.style.display = 'none';
                this.overlapPanel.innerHTML = '';
            }
        },

        /**
         * Clear overlap display from map
         */
        _clearOverlapDisplay: function() {
            if (this.overlapLayer) {
                this.overlapLayer.clearLayers();
            }
            if (this.overlapHighlightLayer) {
                this.overlapHighlightLayer.clearLayers();
            }
        },

        /**
         * Reset overlap state
         */
        _resetOverlapState: function() {
            this.state.overlaps = [];
            this.state.overlapChecked = false;
            this.state.overlapConfirmed = false;
            this.state.overlapCheckPending = false;
            this._clearOverlapDisplay();
            this._hideOverlapPanel();
        },

        /**
         * Debounced overlap check - prevents excessive API calls during editing
         * Waits 500ms after last geometry change before making API call
         */
        _checkOverlapsDebounced: function() {
            var self = this;
            // Create debounced function on first call and cache it
            if (!this._debouncedOverlapCheckFn) {
                this._debouncedOverlapCheckFn = debounce(function() {
                    self._checkOverlaps();
                }, 500);
            }
            this._debouncedOverlapCheckFn();
        },

        // =============================================
        // END OVERLAP CHECKING MODULE
        // =============================================

        // =============================================
        // NEARBY PARCELS MODULE (READ-ONLY)
        // Loads and displays existing parcels in viewport
        // Dependencies: API endpoint, Leaflet LayerGroup
        // Performance: Throttled to 2s between API calls
        // =============================================

        /**
         * Check if nearby parcels feature is enabled
         */
        _isNearbyParcelsEnabled: function() {
            var config = this.options.nearbyParcels;
            return config && config.enabled && config.enabled !== 'DISABLED';
        },

        /**
         * Initialize nearby parcels layer.
         * CRITICAL: This layer is READ-ONLY. All interactions are disabled
         * to prevent any modification of existing parcels.
         */
        _initNearbyParcelsLayer: function() {
            var self = this;
            var config = this.options.nearbyParcels;

            // Create layer group for nearby parcels (added BELOW drawing layer)
            this.nearbyParcelsLayer = L.layerGroup();
            this.nearbyParcelsLayer.addTo(this.map);

            // Note: Layer order is determined by add order to map
            // Drawing layer is added after nearby parcels, so it's already on top

            // Add legend
            this._addNearbyParcelsLegend();

            // Load based on mode
            if (config.enabled === 'ON_LOAD') {
                // Load immediately
                this._loadNearbyParcels();
            } else if (config.enabled === 'ON_DEMAND') {
                // Add toggle button
                this._addNearbyParcelsToggle();
            }
        },

        /**
         * Set up map move handler to reload nearby parcels on pan/zoom.
         * Uses throttling with 2s minimum interval to reduce network usage on slow connections.
         */
        _setupNearbyParcelsReload: function() {
            var self = this;

            // Throttled reload function - at most once every 2 seconds
            // This reduces API calls significantly during rapid pan/zoom
            var throttledReload = throttle(function() {
                if (!self.state.nearbyParcelsVisible) {
                    return;
                }
                self._loadNearbyParcels();
            }, 2000, { leading: false, trailing: true });

            // Store reference for cleanup
            this._nearbyParcelsThrottledReload = throttledReload;

            this.map.on('moveend', throttledReload);
        },

        /**
         * Add toggle button for nearby parcels (ON_DEMAND mode).
         */
        _addNearbyParcelsToggle: function() {
            var self = this;

            var control = document.createElement('div');
            control.className = 'gis-nearby-parcels-control';

            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'gis-nearby-parcels-btn';
            btn.innerHTML = '<span>Show Nearby</span>';
            btn.setAttribute('aria-label', 'Show or hide nearby registered parcels');
            btn.setAttribute('aria-pressed', 'false');

            // Count badge (initially hidden)
            var badge = document.createElement('span');
            badge.className = 'gis-nearby-parcels-badge';
            badge.style.display = 'none';

            btn.onclick = function(e) {
                e.preventDefault();
                e.stopPropagation();

                var isActive = btn.classList.toggle('active');
                btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');

                if (isActive) {
                    btn.querySelector('span').textContent = 'Hide Nearby';
                    self.state.nearbyParcelsVisible = true;
                    self._loadNearbyParcels();
                } else {
                    btn.querySelector('span').textContent = 'Show Nearby';
                    self.state.nearbyParcelsVisible = false;
                    self._clearNearbyParcels();
                }
            };

            control.appendChild(btn);
            control.appendChild(badge);
            this.mapContainer.appendChild(control);

            this.nearbyParcelsToggle = btn;
            this.nearbyParcelsBadge = badge;
        },

        /**
         * Load nearby parcels from server.
         */
        _loadNearbyParcels: function() {
            var self = this;
            var config = this.options.nearbyParcels;

            // Skip if not enabled or already loading
            if (!this._isNearbyParcelsEnabled()) {
                return;
            }

            if (this.state.nearbyParcelsLoading) {
                return;
            }

            // For ON_LOAD mode, mark as visible
            if (config.enabled === 'ON_LOAD') {
                this.state.nearbyParcelsVisible = true;
            }

            // Get current map bounds
            var bounds = this.map.getBounds();
            var boundsStr = [
                bounds.getWest().toFixed(6),
                bounds.getSouth().toFixed(6),
                bounds.getEast().toFixed(6),
                bounds.getNorth().toFixed(6)
            ].join(',');

            // Build API URL
            var apiUrl = this.options.apiBase + '/nearbyParcels?' +
                'formId=' + encodeURIComponent(config.formId || '') +
                '&geometryFieldId=' + encodeURIComponent(config.geometryFieldId || 'c_geometry') +
                '&bounds=' + encodeURIComponent(boundsStr) +
                '&maxResults=' + (config.maxResults || 100);

            // Exclude current record if editing
            if (this.options.recordId) {
                apiUrl += '&excludeRecordId=' + encodeURIComponent(this.options.recordId);
            }

            // Add filter condition
            if (config.filterCondition) {
                apiUrl += '&filterCondition=' + encodeURIComponent(config.filterCondition);
            }

            // Add return fields
            var displayFields = config.displayFields;
            if (displayFields) {
                var fieldsStr = typeof displayFields === 'string' ? displayFields : displayFields.join(',');
                if (fieldsStr) {
                    apiUrl += '&returnFields=' + encodeURIComponent(fieldsStr);
                }
            }

            // Validate API URL for security
            var validatedUrl = this._validateApiUrl(apiUrl);
            if (!validatedUrl) {
                console.error('[GIS] Invalid API URL, skipping nearby parcels fetch');
                return;
            }

            // Cancel any previous nearby parcels request (race condition prevention)
            if (this._nearbyParcelsRequestId) {
                this._abortRequest(this._nearbyParcelsRequestId);
            }

            // Create new request ID and abort controller
            var requestId = this._generateRequestId();
            this._nearbyParcelsRequestId = requestId;
            var abortController = this._createAbortController(requestId);

            // Build headers (session-based auth)
            var headers = this._buildApiHeaders();

            // Set up timeout (30 seconds)
            var timeoutId = setTimeout(function() {
                self._abortRequest(requestId);
                console.warn('[GIS] Nearby parcels fetch timed out after 30 seconds');
            }, 30000);

            this.state.nearbyParcelsLoading = true;
            this._showNearbyParcelsLoading();

            fetch(validatedUrl, {
                method: 'GET',
                headers: headers,
                signal: abortController.signal
            })
            .then(function(response) {
                clearTimeout(timeoutId);
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.json();
            })
            .then(function(response) {
                // Ignore response if this is a stale request
                if (requestId !== self._nearbyParcelsRequestId) {
                    console.log('[GIS] Ignoring stale nearby parcels response');
                    return;
                }

                self._cleanupRequest(requestId);
                self.state.nearbyParcelsLoading = false;
                self._hideNearbyParcelsLoading();

                // Parse Joget API response wrapper
                var data = response;
                if (response.message && typeof response.message === 'string') {
                    try {
                        data = JSON.parse(response.message);
                    } catch (e) {
                        // Fall back to original
                    }
                }

                var parcelsData = data.data || data;
                self.state.nearbyParcelsData = parcelsData.parcels || [];
                self.state.nearbyParcelsLoaded = true;

                self._displayNearbyParcels(self.state.nearbyParcelsData);

                // Update count badge
                var truncated = parcelsData.truncated || false;
                self._updateNearbyParcelsCount(self.state.nearbyParcelsData.length, truncated);
            })
            .catch(function(error) {
                clearTimeout(timeoutId);
                self._cleanupRequest(requestId);
                self.state.nearbyParcelsLoading = false;
                self._hideNearbyParcelsLoading();

                // Ignore abort errors (cancelled or timed out)
                if (error.name === 'AbortError') {
                    console.log('[GIS] Nearby parcels fetch cancelled');
                    return;
                }

                console.warn('[GISCapture] Failed to load nearby parcels:', error);
            });
        },

        /**
         * Display nearby parcels on the map.
         *
         * CRITICAL SECURITY: All polygons are displayed as READ-ONLY.
         * - No drag handlers
         * - No edit controls
         * - No modification events
         * - Click only shows info popup (no editing)
         */
        _displayNearbyParcels: function(parcels) {
            var self = this;
            var config = this.options.nearbyParcels;
            var style = config.style || {};

            // Clear previous parcels
            if (this.nearbyParcelsLayer) {
                this.nearbyParcelsLayer.clearLayers();
            }

            if (!parcels || parcels.length === 0) {
                return;
            }

            parcels.forEach(function(parcel) {
                try {
                    var geojson = parcel.geometry;
                    if (typeof geojson === 'string') {
                        geojson = JSON.parse(geojson);
                    }

                    // Get coordinates
                    var coordinates = geojson.coordinates;
                    if (geojson.type === 'Feature') {
                        coordinates = geojson.geometry.coordinates;
                    }

                    if (!coordinates || !coordinates[0]) {
                        return;
                    }

                    // Convert [lng, lat] to [lat, lng] for Leaflet
                    var latlngs = coordinates[0].map(function(c) {
                        return [c[1], c[0]];
                    });

                    // Create polygon with READ-ONLY styling
                    var polygon = L.polygon(latlngs, {
                        fillColor: style.fillColor || '#808080',
                        fillOpacity: style.fillOpacity || 0.15,
                        color: style.strokeColor || '#666666',
                        weight: style.strokeWidth || 1,
                        dashArray: style.strokeDashArray || '3, 3',
                        // CRITICAL: Disable editing capability
                        interactive: true,      // Allow click for popup
                        bubblingMouseEvents: false
                    });

                    // Build popup content (READ-ONLY info display)
                    var popupContent = self._buildNearbyParcelPopup(parcel);
                    polygon.bindPopup(popupContent, {
                        closeButton: true,
                        autoClose: true,
                        maxWidth: 300
                    });

                    // Hover effect (visual feedback only)
                    polygon.on('mouseover', function() {
                        this.setStyle({
                            fillOpacity: (style.fillOpacity || 0.15) + 0.1,
                            weight: (style.strokeWidth || 1) + 1
                        });
                    });

                    polygon.on('mouseout', function() {
                        this.setStyle({
                            fillOpacity: style.fillOpacity || 0.15,
                            weight: style.strokeWidth || 1
                        });
                    });

                    // Add to layer
                    self.nearbyParcelsLayer.addLayer(polygon);

                } catch (e) {
                    console.warn('[GISCapture] Invalid nearby parcel geometry:', e);
                }
            });

            // Note: Drawing layer stays on top due to map add order
        },

        /**
         * Build popup content for nearby parcel.
         * Shows READ-ONLY information - no edit links or buttons.
         */
        _buildNearbyParcelPopup: function(parcel) {
            var self = this;
            var html = '<div class="gis-nearby-parcel-popup">';
            html += '<div class="gis-nearby-parcel-header">';
            html += '<span class="gis-nearby-parcel-icon">&#x1F4CD;</span>';
            html += '<span class="gis-nearby-parcel-title">Registered Parcel</span>';
            html += '</div>';

            // Display area
            if (parcel.areaHectares !== undefined) {
                html += '<div class="gis-nearby-parcel-info">';
                html += '<span class="gis-info-label">Area:</span> ';
                html += '<span class="gis-info-value">' + parcel.areaHectares.toFixed(2) + ' ha</span>';
                html += '</div>';
            }

            // Display configured fields
            if (parcel.recordData) {
                Object.keys(parcel.recordData).forEach(function(key) {
                    var value = parcel.recordData[key];
                    if (value) {
                        // Format field name (convert snake_case to Title Case)
                        var label = key.replace(/_/g, ' ').replace(/\b\w/g, function(c) {
                            return c.toUpperCase();
                        });
                        html += '<div class="gis-nearby-parcel-info">';
                        html += '<span class="gis-info-label">' + self._escapeHtml(label) + ':</span> ';
                        html += '<span class="gis-info-value">' + self._escapeHtml(value) + '</span>';
                        html += '</div>';
                    }
                });
            }

            // Footer note - no edit capability
            html += '<div class="gis-nearby-parcel-footer">';
            html += '<small>This parcel is already registered</small>';
            html += '</div>';

            html += '</div>';
            return html;
        },

        /**
         * Escape HTML to prevent XSS
         * Delegates to module-level utility function
         */
        _escapeHtml: function(text) {
            return escapeHtml(text);
        },

        /**
         * Validate API URL for security.
         * Allows relative paths (starting with /) or HTTPS URLs.
         * Returns the URL if valid, or null if invalid.
         */
        _validateApiUrl: function(url) {
            if (!url) return null;

            // Allow relative paths (most common and secure)
            if (url.startsWith('/')) {
                return url;
            }

            // Allow HTTPS URLs
            if (url.startsWith('https://')) {
                return url;
            }

            // Warn about HTTP URLs but allow them for development
            if (url.startsWith('http://')) {
                console.warn('[GIS] Warning: API URL uses insecure HTTP protocol. Consider using HTTPS.');
                // In production, you might want to return null here to block HTTP
                return url;
            }

            console.error('[GIS] Invalid API URL format. Must be relative path or HTTPS URL.');
            return null;
        },

        /**
         * Build request headers for API calls.
         * Includes API credentials if provided.
         */
        _buildApiHeaders: function() {
            var headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            };
            // Add API credentials if provided
            if (this.options.apiId && this.options.apiKey) {
                headers['api_id'] = this.options.apiId;
                headers['api_key'] = this.options.apiKey;
            }
            return headers;
        },

        /**
         * Add map legend showing parcel types.
         */
        _addNearbyParcelsLegend: function() {
            var legend = document.createElement('div');
            legend.className = 'gis-legend';

            legend.innerHTML =
                '<div class="gis-legend-title">Legend</div>' +
                '<div class="gis-legend-item">' +
                    '<div class="gis-legend-swatch nearby"></div>' +
                    '<span class="gis-legend-label">Registered parcels</span>' +
                '</div>' +
                '<div class="gis-legend-item">' +
                    '<div class="gis-legend-swatch current"></div>' +
                    '<span class="gis-legend-label">Current parcel</span>' +
                '</div>' +
                '<div class="gis-legend-item">' +
                    '<div class="gis-legend-swatch overlap"></div>' +
                    '<span class="gis-legend-label">Overlap area</span>' +
                '</div>';

            this.mapContainer.appendChild(legend);
            this.nearbyParcelsLegend = legend;
        },

        /**
         * Show loading indicator for nearby parcels.
         */
        _showNearbyParcelsLoading: function() {
            if (this.nearbyParcelsToggle) {
                this.nearbyParcelsToggle.classList.add('loading');
            }
        },

        /**
         * Hide loading indicator.
         */
        _hideNearbyParcelsLoading: function() {
            if (this.nearbyParcelsToggle) {
                this.nearbyParcelsToggle.classList.remove('loading');
            }
        },

        /**
         * Update count badge.
         */
        _updateNearbyParcelsCount: function(count, truncated) {
            if (this.nearbyParcelsBadge) {
                if (count > 0) {
                    this.nearbyParcelsBadge.textContent = truncated ? count + '+' : count;
                    this.nearbyParcelsBadge.style.display = 'inline-block';
                    if (truncated) {
                        this.nearbyParcelsBadge.classList.add('warning');
                    } else {
                        this.nearbyParcelsBadge.classList.remove('warning');
                    }
                } else {
                    this.nearbyParcelsBadge.style.display = 'none';
                }
            }
        },

        /**
         * Clear nearby parcels from map.
         */
        _clearNearbyParcels: function() {
            if (this.nearbyParcelsLayer) {
                this.nearbyParcelsLayer.clearLayers();
            }
            this.state.nearbyParcelsData = [];
            this._updateNearbyParcelsCount(0, false);
        },

        // =============================================
        // END NEARBY PARCELS MODULE
        // =============================================

        /**
         * Save to hidden field
         */
        _saveToHiddenField: function() {
            var hiddenField = document.getElementById(this.options.hiddenFieldId);
            if (hiddenField) {
                var geojson = this._toGeoJSON();
                // Apply simplification if enabled
                if (this.options.simplification && this.options.simplification.enabled) {
                    geojson = this._simplifyPolygon(geojson);
                }
                hiddenField.value = JSON.stringify(geojson);
            }

            // Update output fields
            var outputFields = this.options.outputFields;
            var metrics = this.state.metrics;

            if (metrics) {
                if (outputFields.areaFieldId) {
                    var areaField = document.querySelector('[name="' + outputFields.areaFieldId + '"]');
                    if (areaField) areaField.value = metrics.areaHectares.toFixed(4);
                }
                if (outputFields.perimeterFieldId) {
                    var perimField = document.querySelector('[name="' + outputFields.perimeterFieldId + '"]');
                    if (perimField) perimField.value = metrics.perimeterMeters.toFixed(2);
                }
                if (outputFields.centroidFieldId) {
                    var centField = document.querySelector('[name="' + outputFields.centroidFieldId + '"]');
                    if (centField) {
                        centField.value = JSON.stringify({
                            type: 'Point',
                            coordinates: [metrics.centroid.lng, metrics.centroid.lat]
                        });
                    }
                }
                if (outputFields.vertexCountFieldId) {
                    var countField = document.querySelector('[name="' + outputFields.vertexCountFieldId + '"]');
                    if (countField) countField.value = metrics.vertexCount;
                }
            }
        },

        /**
         * Load existing value from hidden field
         */
        _loadExistingValue: function() {
            console.log('[GIS] _loadExistingValue called, hiddenFieldId=' + this.options.hiddenFieldId);
            var hiddenField = document.getElementById(this.options.hiddenFieldId);
            console.log('[GIS] Hidden field found=' + !!hiddenField + ', value length=' + (hiddenField ? (hiddenField.value || '').length : 0));

            if (!hiddenField || !hiddenField.value) {
                console.log('[GIS] No hidden field or empty value - skipping initial geometry load');
                return;
            }

            console.log('[GIS] Hidden field value (first 200 chars): ' + hiddenField.value.substring(0, 200));

            try {
                var geojson = JSON.parse(hiddenField.value);
                var coords = null;

                // Store initial geometry for self-overlap detection in edit mode
                // Normalize to Polygon type for consistent comparison
                if (geojson.type === 'Polygon') {
                    this.state.initialGeometry = geojson;
                    coords = geojson.coordinates[0];
                } else if (geojson.type === 'Feature' && geojson.geometry.type === 'Polygon') {
                    this.state.initialGeometry = geojson.geometry;
                    coords = geojson.geometry.coordinates[0];
                }

                if (this.state.initialGeometry) {
                    // Calculate and store initial area using Turf.js
                    try {
                        var initialArea = turf.area(this.state.initialGeometry) / 10000; // m² to hectares
                        this.state.initialAreaHectares = initialArea;
                        console.log('[GIS] Stored initial geometry for self-overlap detection, area=' + initialArea.toFixed(4) + ' ha');
                    } catch (e) {
                        console.warn('[GIS] Could not calculate initial area:', e);
                    }
                }

                if (coords && coords.length > 0) {
                    // Remove closing point if present
                    var vertices = coords.slice(0, -1);
                    var self = this;
                    vertices.forEach(function(coord) {
                        self.state.vertices.push({ lat: coord[1], lng: coord[0] });
                    });

                    // Rebuild display
                    this.state.vertices.forEach(function(v, i) {
                        self._addVertexMarker(v, i);
                    });
                    this._updatePolygon();
                    this._calculateMetrics();
                    this._updateInfoPanel();
                }
            } catch (e) {
                console.warn('[GISCapture] Failed to load existing value:', e);
            }
        },

        /**
         * Convert to GeoJSON
         */
        _toGeoJSON: function() {
            if (this.state.vertices.length < 3) return null;

            var coords = this.state.vertices.map(function(v) {
                return [v.lng, v.lat]; // GeoJSON is [lng, lat]
            });
            coords.push(coords[0]); // Close the ring

            return {
                type: 'Polygon',
                coordinates: [coords]
            };
        },

        /**
         * Simplify polygon using Turf.js
         * Reduces vertex count while preserving shape and accuracy.
         *
         * @param {Object} geojson - GeoJSON Polygon to simplify
         * @returns {Object} Simplified GeoJSON or original if simplification disabled/fails
         */
        _simplifyPolygon: function(geojson) {
            if (!geojson || !this.options.simplification || !this.options.simplification.enabled) {
                return geojson;
            }

            try {
                var tolerance = this.options.simplification.tolerance || 2;
                // Convert meters to approximate degrees (1 degree ≈ 111km at equator)
                var toleranceDegrees = tolerance / 111000;

                var simplified = turf.simplify(geojson, {
                    tolerance: toleranceDegrees,
                    highQuality: true
                });

                // SAFEGUARD: Check simplified polygon doesn't have self-intersections
                var kinks = turf.kinks(simplified);
                if (kinks.features.length > 0) {
                    console.warn('[GIS] Simplification introduced self-intersection, using original');
                    return geojson;
                }

                var originalVerts = geojson.coordinates[0].length - 1;
                var simplifiedVerts = simplified.coordinates[0].length - 1;
                console.log('[GIS] Simplified: ' + originalVerts + ' -> ' + simplifiedVerts + ' vertices');

                return simplified;
            } catch (e) {
                console.warn('[GIS] Simplification failed:', e);
                return geojson;
            }
        },

        /**
         * Undo last vertex
         */
        _undoLastVertex: function() {
            if (this.state.vertices.length === 0) return;

            this.state.vertices.pop();

            var marker = this.vertexMarkers.pop();
            if (marker) {
                this.drawingLayer.removeLayer(marker);
            }

            this._updatePolygon();
            this._calculateMetrics();
            this._updateInfoPanel();

            this._showToast('Corner removed', 'info');
        },

        /**
         * Clear all drawing
         */
        _clearDrawing: function() {
            this.state.vertices = [];
            this.state.metrics = null;
            this.state.selectedVertexIndex = null;

            // Clear markers
            this.vertexMarkers.forEach(function(m) {
                this.drawingLayer.removeLayer(m);
            }, this);
            this.vertexMarkers = [];

            // Clear midpoint markers
            this._clearMidpointMarkers();

            // Clear intersection highlights
            this._clearIntersectionHighlights();

            // Clear overlap state and display
            this._resetOverlapState();

            // Clear polygon
            if (this.state.polygon) {
                this.drawingLayer.removeLayer(this.state.polygon);
                this.state.polygon = null;
            }

            // Clear ghost line
            if (this.ghostLine) {
                this.drawingLayer.removeLayer(this.ghostLine);
                this.ghostLine = null;
            }

            // Hide close hint
            this._hideCloseHint();

            // Clear hidden field
            var hiddenField = document.getElementById(this.options.hiddenFieldId);
            if (hiddenField) {
                hiddenField.value = '';
            }

            this._updateInfoPanel();
            this.validationPanel.style.display = 'none';
            this._clearAllWarnings();

            this._showToast('Drawing cleared', 'info');
        },

        /**
         * Start vertex drag - PERFORMANCE OPTIMIZED
         * Uses throttling for visual updates (60fps) and debouncing for calculations
         */
        _startVertexDrag: function(index, e) {
            var self = this;
            var map = this.map;
            var marker = this.vertexMarkers[index];

            map.dragging.disable();

            // Track last position for the throttled handler
            var lastLatlng = null;

            // Throttled visual update - runs at most every 16ms (60fps)
            var throttledVisualUpdate = throttle(function() {
                if (!lastLatlng) return;
                marker.setLatLng(lastLatlng);
                self.state.vertices[index] = { lat: lastLatlng.lat, lng: lastLatlng.lng };
                self._updatePolygon();
            }, 16);

            // Debounced metrics calculation - runs 100ms after last change
            var debouncedMetrics = debounce(function() {
                self._calculateMetrics();
            }, 100);

            function onMove(ev) {
                lastLatlng = ev.latlng;
                throttledVisualUpdate();
                debouncedMetrics();
            }

            function onUp() {
                map.off('mousemove', onMove);
                map.off('mouseup', onUp);
                map.dragging.enable();

                // Cancel any pending throttled/debounced calls
                throttledVisualUpdate.cancel();
                debouncedMetrics.cancel();

                // Final update with actual position
                if (lastLatlng) {
                    marker.setLatLng(lastLatlng);
                    self.state.vertices[index] = { lat: lastLatlng.lat, lng: lastLatlng.lng };
                    self._updatePolygon();
                    self._calculateMetrics();
                }

                self._updateInfoPanel();
                self._checkSelfIntersection();  // Immediate on drag end
                self._checkAreaWarnings();
                self._saveToHiddenField();

                // Re-check overlaps after vertex modification in preview mode
                if (self.state.phase === 'PREVIEW') {
                    self._checkOverlaps();  // Immediate on drag end
                }
            }

            map.on('mousemove', onMove);
            map.on('mouseup', onUp);
        },

        /**
         * Calculate distance between two points (Haversine)
         */
        _distanceBetween: function(lat1, lng1, lat2, lng2) {
            var R = 6371000; // Earth's radius in meters
            var dLat = this._toRad(lat2 - lat1);
            var dLng = this._toRad(lng2 - lng1);
            var a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                    Math.cos(this._toRad(lat1)) * Math.cos(this._toRad(lat2)) *
                    Math.sin(dLng/2) * Math.sin(dLng/2);
            var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
            return R * c;
        },

        _toRad: function(deg) {
            return deg * Math.PI / 180;
        },

        /**
         * Show toast notification
         */
        _showToast: function(message, type) {
            var existing = this.wrapper.querySelector('.gis-toast-container');
            if (existing) existing.remove();

            var container = document.createElement('div');
            container.className = 'gis-toast-container';

            var toast = document.createElement('div');
            toast.className = 'gis-toast ' + (type || 'info');
            toast.textContent = message;

            container.appendChild(toast);
            this.wrapper.appendChild(container);

            setTimeout(function() {
                container.remove();
            }, 3000);
        },

        /**
         * Get current geometry as GeoJSON
         */
        getGeometry: function() {
            return this._toGeoJSON();
        },

        /**
         * Get metrics
         */
        getMetrics: function() {
            return this.state.metrics;
        },

        // =============================================
        // AUTO-CENTER MODULE
        // Monitors form fields and centers map accordingly
        // Dependencies: Nominatim geocoding API
        // Performance: Event-driven with 3s fallback polling
        // =============================================

        /**
         * Check if auto-center is enabled
         */
        _isAutoCenterEnabled: function() {
            return this.options.autoCenter && this.options.autoCenter.enabled === true;
        },

        /**
         * Initialize auto-center feature
         */
        _initAutoCenter: function() {
            var self = this;
            var config = this.options.autoCenter;

            if (!config) return;

            // Delay initial auto-center attempt to ensure fields are populated
            setTimeout(function() {
                self._attemptAutoCenter();
            }, 300);

            // Set up field change monitoring if enabled
            if (config.retryOnFieldChange) {
                this._setupAutoCenterFieldMonitoring();
            }
        },

        /**
         * Set up event-driven + fallback polling to monitor field changes.
         * PERFORMANCE OPTIMIZED: Uses event listeners primarily, with 3s fallback polling
         * This reduces CPU usage by ~66% compared to 1s constant polling
         */
        _setupAutoCenterFieldMonitoring: function() {
            var self = this;
            var config = this.options.autoCenter;

            // Debounced auto-center check (300ms after last field change)
            var debouncedCheck = debounce(function() {
                if (self.state.autoCenterInProgress) return;
                self._checkAutoCenterFieldChange();
            }, 300);

            // Attach event listeners to monitored fields
            var fieldIds = [
                config.districtFieldId,
                config.villageFieldId,
                config.latFieldId,
                config.lonFieldId
            ].filter(Boolean);

            fieldIds.forEach(function(fieldId) {
                // Try multiple selectors to find the field
                var selectors = [
                    '[name="' + fieldId + '"]',
                    '[name="' + fieldId + '_name"]',
                    '#' + fieldId,
                    'input[id$="_' + fieldId + '"]',
                    'select[id$="_' + fieldId + '"]'
                ];

                selectors.forEach(function(selector) {
                    var el = document.querySelector(selector);
                    if (el) {
                        // Use 'change' for selects, 'input' for text fields
                        var eventType = el.tagName === 'SELECT' ? 'change' : 'input';
                        self._addEventListener(el, eventType, debouncedCheck);
                        self._addEventListener(el, 'change', debouncedCheck);
                    }
                });
            });

            // Fallback: Poll every 3 seconds for fields that may be updated programmatically
            // (e.g., by other Joget plugins or AJAX calls)
            this.autoCenterInterval = setInterval(function() {
                if (self.state.autoCenterInProgress) return;
                self._checkAutoCenterFieldChange();
            }, 3000);
        },

        /**
         * Check if auto-center field values have changed and trigger re-center
         */
        _checkAutoCenterFieldChange: function() {
            var currentValues = this._getAutoCenterFieldValues();
            var lastValues = this.state.autoCenterLastValues;

            // Check if values have changed
            if (lastValues && (
                currentValues.district !== lastValues.district ||
                currentValues.village !== lastValues.village ||
                currentValues.lat !== lastValues.lat ||
                currentValues.lon !== lastValues.lon
            )) {
                // Values changed, attempt re-center
                this._attemptAutoCenter();
            }
        },

        /**
         * Get current values from auto-center fields
         */
        _getAutoCenterFieldValues: function() {
            var config = this.options.autoCenter;
            var values = {
                district: '',
                village: '',
                lat: '',
                lon: ''
            };

            if (!config) return values;

            // Helper to get field value from Joget form
            var getFieldValue = function(fieldId) {
                if (!fieldId) return '';

                // Try multiple selectors (Joget forms can have different field structures)
                var selectors = [
                    '[name="' + fieldId + '"]',
                    '[name="' + fieldId + '_name"]',          // For select lists with display names
                    '#' + fieldId,
                    'input[id$="_' + fieldId + '"]',
                    'select[id$="_' + fieldId + '"]',
                    'textarea[id$="_' + fieldId + '"]'
                ];

                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        // Handle select elements
                        if (el.tagName === 'SELECT') {
                            var selected = el.options[el.selectedIndex];
                            return selected ? (selected.text || selected.value || '') : '';
                        }
                        return el.value || '';
                    }
                }
                return '';
            };

            values.district = getFieldValue(config.districtFieldId);
            values.village = getFieldValue(config.villageFieldId);
            values.lat = getFieldValue(config.latFieldId);
            values.lon = getFieldValue(config.lonFieldId);

            return values;
        },

        /**
         * Attempt auto-center using fallback chain
         */
        _attemptAutoCenter: function() {
            var self = this;
            var config = this.options.autoCenter;

            if (!config || this.state.autoCenterInProgress) return;

            var values = this._getAutoCenterFieldValues();
            this.state.autoCenterLastValues = values;

            // Fallback 1: Check for pre-computed coordinates
            var lat = parseFloat(values.lat);
            var lon = parseFloat(values.lon);

            if (!isNaN(lat) && !isNaN(lon) && lat !== 0 && lon !== 0) {
                this._performAutoCenter(lat, lon, 'Using pre-computed coordinates');
                return;
            }

            // Fallback 2: Geocode district/village
            if (values.district || values.village) {
                this._geocodeForAutoCenter(values.village, values.district);
                return;
            }

            // Fallback 3: Use default coordinates (only on first attempt)
            if (!this.state.autoCenterAttempted) {
                this.state.autoCenterAttempted = true;
                // Don't center to defaults - let the plugin's default lat/lon handle initial view
            }
        },

        /**
         * Geocode location for auto-center
         */
        _geocodeForAutoCenter: function(village, district) {
            var self = this;
            var config = this.options.autoCenter;

            // Build query
            var parts = [];
            if (village) parts.push(village);
            if (district) parts.push(district);
            if (config.countrySuffix) parts.push(config.countrySuffix);
            var query = parts.join(', ');

            if (!query) {
                this._autoCenterToDefaults();
                return;
            }

            this.state.autoCenterInProgress = true;
            this._showAutoCenterStatus('Locating ' + (village || district) + '...');

            // Use Nominatim geocoding (same as existing search feature)
            var url = 'https://nominatim.openstreetmap.org/search?format=json&q=' + encodeURIComponent(query) + '&limit=1';

            fetch(url, {
                headers: {
                    'Accept': 'application/json'
                }
            })
            .then(function(response) {
                if (!response.ok) throw new Error('Geocode request failed');
                return response.json();
            })
            .then(function(results) {
                self.state.autoCenterInProgress = false;
                self._hideAutoCenterStatus();

                if (results && results.length > 0) {
                    var lat = parseFloat(results[0].lat);
                    var lon = parseFloat(results[0].lon);
                    self._performAutoCenter(lat, lon, 'Map centered on ' + (village || district));
                    self.state.autoCenterAttempted = true;
                } else {
                    self._autoCenterToDefaults();
                }
            })
            .catch(function(error) {
                console.warn('[GISCapture] Auto-center geocode failed:', error);
                self.state.autoCenterInProgress = false;
                self._hideAutoCenterStatus();
                self._autoCenterToDefaults();
            });
        },

        /**
         * Perform the actual map centering
         */
        _performAutoCenter: function(lat, lon, message) {
            var config = this.options.autoCenter;
            var zoom = config && config.zoom ? config.zoom : 14;

            // May 2026 fix — auto-centre fires immediately after district
            // resolves, often BEFORE the container has been laid out. Calling
            // setView on a 0×0 map computes the tile pyramid against zero
            // dimensions → tile layer never requests any tiles → grey map
            // even though the polygon overlay renders fine. invalidateSize
            // first so the dimensions are correct when setView triggers tile
            // computation.
            this.map.invalidateSize();
            this.map.setView([lat, lon], zoom);

            if (message) {
                this._showToast(message, 'success');
                this._announce(message);
            }
        },

        /**
         * Fall back to default coordinates
         */
        _autoCenterToDefaults: function() {
            if (!this.state.autoCenterAttempted) {
                this.state.autoCenterAttempted = true;
                // Show warning toast only if we tried to geocode something
                this._showToast('Location not found, using default view', 'warning');
                this._announce('Location not found, using default map view');
            }
            // Let the default lat/lon from plugin config handle the view
        },

        /**
         * Show auto-center status indicator
         */
        _showAutoCenterStatus: function(message) {
            if (this.autoCenterStatusEl) {
                this.autoCenterStatusEl.textContent = message;
                return;
            }

            var status = document.createElement('div');
            status.className = 'gis-autocenter-status';
            status.innerHTML = '<span class="gis-autocenter-spinner"></span><span class="gis-autocenter-text">' + message + '</span>';
            this.wrapper.appendChild(status);
            this.autoCenterStatusEl = status;
        },

        /**
         * Hide auto-center status indicator
         */
        _hideAutoCenterStatus: function() {
            if (this.autoCenterStatusEl) {
                this.autoCenterStatusEl.remove();
                this.autoCenterStatusEl = null;
            }
        },

        // =============================================
        // END AUTO-CENTER MODULE
        // =============================================

        // =============================================
        // LIFECYCLE & CLEANUP
        // Event listener tracking, request cancellation, destroy
        // Critical for memory leak prevention in SPAs
        // =============================================

        /**
         * Add an event listener and track it for cleanup.
         */
        _addEventListener: function(element, event, handler, options) {
            element.addEventListener(event, handler, options);
            this._eventListeners.push({ element: element, event: event, handler: handler, options: options });
        },

        /**
         * Remove all tracked event listeners.
         */
        _removeAllEventListeners: function() {
            this._eventListeners.forEach(function(item) {
                try {
                    item.element.removeEventListener(item.event, item.handler, item.options);
                } catch (e) {
                    // Element may no longer exist
                }
            });
            this._eventListeners = [];
        },

        /**
         * Generate a unique request ID for tracking async operations.
         */
        _generateRequestId: function() {
            return 'req_' + (++this._requestIdCounter) + '_' + Date.now();
        },

        /**
         * Create an AbortController for a request and track it.
         */
        _createAbortController: function(requestId) {
            var controller = new AbortController();
            this._abortControllers.set(requestId, controller);
            return controller;
        },

        /**
         * Abort a specific request by ID.
         */
        _abortRequest: function(requestId) {
            var controller = this._abortControllers.get(requestId);
            if (controller) {
                controller.abort();
                this._abortControllers.delete(requestId);
            }
        },

        /**
         * Abort all pending requests.
         */
        _abortAllRequests: function() {
            var self = this;
            this._abortControllers.forEach(function(controller, requestId) {
                try {
                    controller.abort();
                } catch (e) {
                    // Ignore errors during abort
                }
            });
            this._abortControllers.clear();
        },

        /**
         * Clean up request tracking after completion.
         */
        _cleanupRequest: function(requestId) {
            this._abortControllers.delete(requestId);
        },

        /**
         * Check if the component has been destroyed.
         */
        _isDestroyed: function() {
            return this._destroyed;
        },

        /**
         * Destroy instance and clean up all resources.
         */
        destroy: function() {
            if (this._destroyed) return;
            this._destroyed = true;

            // Abort all pending API requests
            this._abortAllRequests();

            // Clean up GPS watch
            if (this.state.gpsWatchId) {
                navigator.geolocation.clearWatch(this.state.gpsWatchId);
                this.state.gpsWatchId = null;
            }

            // Clean up auto-center polling
            if (this.autoCenterInterval) {
                clearInterval(this.autoCenterInterval);
                this.autoCenterInterval = null;
            }
            if (this.autoCenterStatusEl) {
                this.autoCenterStatusEl.remove();
                this.autoCenterStatusEl = null;
            }

            // Cancel any pending throttled/debounced functions
            if (this._nearbyParcelsThrottledReload) {
                this._nearbyParcelsThrottledReload.cancel();
            }
            if (this._debouncedSelfIntersectionFn) {
                this._debouncedSelfIntersectionFn.cancel();
            }
            if (this._debouncedOverlapCheckFn) {
                this._debouncedOverlapCheckFn.cancel();
            }

            // Clean up tracked event listeners
            this._removeAllEventListeners();

            // Clean up legacy bound event handlers
            if (this._boundMouseMoveHandler && this.map) {
                this.map.off('mousemove', this._boundMouseMoveHandler);
            }
            if (this._boundDblClickHandler && this.map) {
                this.map.off('dblclick', this._boundDblClickHandler);
            }
            if (this._boundKeyDownHandler) {
                document.removeEventListener('keydown', this._boundKeyDownHandler);
            }
            if (this._boundGlobalKeyHandler) {
                document.removeEventListener('keydown', this._boundGlobalKeyHandler);
            }
            if (this.closeHintTooltip && this.map) {
                this.map.removeLayer(this.closeHintTooltip);
            }

            // Clear midpoint and intersection markers
            if (typeof this._clearMidpointMarkers === 'function') {
                this._clearMidpointMarkers();
            }
            if (typeof this._clearIntersectionHighlights === 'function') {
                this._clearIntersectionHighlights();
            }

            // Clear overlap display
            if (typeof this._clearOverlapDisplay === 'function') {
                this._clearOverlapDisplay();
            }

            // Clear nearby parcels display
            if (typeof this._clearNearbyParcels === 'function') {
                this._clearNearbyParcels();
            }
            if (this.nearbyParcelsLayer && this.map) {
                this.map.removeLayer(this.nearbyParcelsLayer);
            }

            // Destroy Leaflet map
            if (this.map) {
                this.map.remove();
                this.map = null;
            }

            // Clear container
            if (this.container) {
                this.container.innerHTML = '';
            }

            console.log('[GIS] Component destroyed');
        }
    };

    // Public API
    return {
        init: init
    };

})();

// =============================================
// DEBUG INTERFACE
// =============================================
/**
 * GIS_DEBUG - Debug interface for testing self-intersection detection
 *
 * Usage in browser console:
 *   GIS_DEBUG.createBowtie()  - Creates a guaranteed self-intersecting polygon
 *   GIS_DEBUG.showVertices()  - Displays current vertex coordinates
 */
window.GIS_DEBUG = {
    /**
     * Create a guaranteed self-intersecting bowtie polygon for testing.
     * The polygon is created around the current map center with edges that
     * mathematically MUST cross (t=0.5, u=0.5).
     */
    createBowtie: function() {
        var instance = window._gisInstance;
        if (!instance) {
            console.error('[GIS DEBUG] No GIS instance found');
            return;
        }

        if (!instance.map) {
            console.error('[GIS DEBUG] Map not initialized');
            return;
        }

        var center = instance.map.getCenter();

        // Create guaranteed bowtie around center
        // V0 → V1: diagonal SE (top-left to bottom-right)
        // V2 → V3: diagonal SW (top-right to bottom-left)
        // These edges MUST cross at the center
        instance.state.vertices = [
            { lng: center.lng - 0.01, lat: center.lat + 0.01 },  // V0: top-left
            { lng: center.lng + 0.01, lat: center.lat - 0.01 },  // V1: bottom-right
            { lng: center.lng + 0.01, lat: center.lat + 0.01 },  // V2: top-right
            { lng: center.lng - 0.01, lat: center.lat - 0.01 }   // V3: bottom-left
        ];

        // Update polygon display
        if (typeof instance._updatePolygon === 'function') {
            instance._updatePolygon();
        }

        // Update vertex markers
        if (typeof instance._updateVertexMarkers === 'function') {
            instance._updateVertexMarkers();
        }

        console.log('[GIS DEBUG] Created bowtie at center: ' + center.lng.toFixed(6) + ', ' + center.lat.toFixed(6));
        console.log('[GIS DEBUG] Edges: E0(V0→V1) and E2(V2→V3) MUST cross at center');

        // Verify with turf.kinks
        var coords = instance.state.vertices.map(function(v) {
            return [v.lng, v.lat];
        });
        coords.push(coords[0]); // Close the ring

        try {
            var polygon = turf.polygon([coords]);
            var kinks = turf.kinks(polygon);
            console.log('[GIS DEBUG] turf.kinks() found ' + kinks.features.length + ' intersection(s)');
            if (kinks.features.length > 0) {
                kinks.features.forEach(function(f, i) {
                    console.log('[GIS DEBUG] Kink ' + i + ' at: ' + f.geometry.coordinates[0].toFixed(6) + ', ' + f.geometry.coordinates[1].toFixed(6));
                });
            }
        } catch (e) {
            console.error('[GIS DEBUG] turf.kinks() error:', e);
        }

        // Trigger self-intersection check
        if (typeof instance._checkSelfIntersection === 'function') {
            instance._checkSelfIntersection();
        }
    },

    /**
     * Display current vertex coordinates in the console.
     */
    showVertices: function() {
        var instance = window._gisInstance;
        if (!instance) {
            console.error('[GIS DEBUG] No GIS instance found');
            return;
        }

        if (!instance.state || !instance.state.vertices) {
            console.log('[GIS DEBUG] No vertices defined');
            return;
        }

        console.log('[GIS DEBUG] Current vertices (' + instance.state.vertices.length + '):');
        instance.state.vertices.forEach(function(v, i) {
            console.log('  V' + i + ': (' + v.lng.toFixed(6) + ', ' + v.lat.toFixed(6) + ')');
        });
    },

    /**
     * Get reference to the GIS instance for advanced debugging.
     */
    getInstance: function() {
        return window._gisInstance;
    }
};
