/**
 * Smart Farmer Search - JavaScript
 * Phase 7: Polish, Error Handling & Testing
 * 
 * Features:
 * - Search input with auto-detection (ID/phone/name)
 * - District filter dropdown
 * - API integration with proper headers
 * - Results rendering with farmer cards
 * - Score color coding
 * - Popup/inline mode support
 * - Loading and error states
 * - Farmer selection with form field update
 * - [Phase 4] Confidence engine integration
 * - [Phase 4] Confidence bar UI
 * - [Phase 4] Criteria validation
 * - [Phase 5] Criteria builder with add/remove
 * - [Phase 5] Village autocomplete (district-dependent)
 * - [Phase 5] Community Council autocomplete
 * - [Phase 5] Cooperative autocomplete
 * - [Phase 5] Partial ID/Phone inputs
 * - [Phase 6] Recent farmers panel (localStorage)
 * - [Phase 6] Offline detection and status indicator
 * - [Phase 6] Statistics caching for offline use
 * - [Phase 6] Graceful degradation when offline
 * - [Phase 7] Comprehensive error handling with retry
 * - [Phase 7] Keyboard navigation & focus management
 * - [Phase 7] Mobile responsiveness improvements
 * - [Phase 7] Accessibility enhancements (ARIA)
 */

(function(global) {
    'use strict';

    // Version for debugging
    var VERSION = '8.1-SNAPSHOT-phase10';

    // Phase 7: Error codes and messages
    var ERROR_CODES = {
        ERR_NETWORK: {
            code: 'ERR_NETWORK',
            message: 'Unable to connect. Please check your internet connection.',
            canRetry: true
        },
        ERR_TIMEOUT: {
            code: 'ERR_TIMEOUT',
            message: 'Request timed out. Please try again.',
            canRetry: true
        },
        ERR_AUTH: {
            code: 'ERR_AUTH',
            message: 'Authentication failed. Please contact administrator.',
            canRetry: false
        },
        ERR_NO_RESULTS: {
            code: 'ERR_NO_RESULTS',
            message: 'No farmers found matching your criteria.',
            canRetry: false
        },
        ERR_TOO_MANY: {
            code: 'ERR_TOO_MANY',
            message: 'Too many results. Please add more criteria to narrow down.',
            canRetry: false
        },
        ERR_INVALID_CRITERIA: {
            code: 'ERR_INVALID_CRITERIA',
            message: 'Please check your search criteria.',
            canRetry: false
        },
        ERR_SERVER: {
            code: 'ERR_SERVER',
            message: 'Server error. Please try again later.',
            canRetry: true
        },
        ERR_UNKNOWN: {
            code: 'ERR_UNKNOWN',
            message: 'An unexpected error occurred.',
            canRetry: true
        }
    };

    // Phase 7: Retry configuration
    var RETRY_CONFIG = {
        maxRetries: 3,
        baseDelay: 1000,  // 1 second
        maxDelay: 10000   // 10 seconds
    };

    // Phase 8: Score display thresholds (Issue #7)
    var SCORE_THRESHOLDS = {
        high: 90,
        medium: 70,
        low: 50
    };

    // Phase 8: Search limits (Issues #8, #9)
    var SEARCH_RESULT_LIMIT = 20;
    var MAX_RESULTS_WARNING = 100;

    // ==========================================================================
    // Phase 9: FSS_DEFAULTS - Consolidated default values (Phase 3 fixes)
    // ==========================================================================
    var FSS_DEFAULTS = {
        // Timing (Issue #13, #15, #16)
        apiTimeout: 30000,           // 30 seconds for main API calls
        statisticsTimeout: 10000,    // 10 seconds for statistics API
        errorAutoHideDelay: 3000,    // 3 seconds for inline error auto-hide
        inlineLookupDebounce: 500,   // 500ms debounce for inline lookup
        autocompleteDebounce: 300,   // 300ms debounce for autocomplete

        // Length requirements (Issue #11, #12)
        autocompleteMinChars: 2,     // Minimum chars to trigger autocomplete
        nameMinLength: 2,            // Minimum chars for name search

        // Display limits (Issue #17)
        autocompleteMaxItems: 10     // Max items in autocomplete dropdown
    };

    console.log('[FarmerSmartSearch] Loading v' + VERSION);

    // TODO Issue #20: Districts are currently hardcoded for Lesotho.
    // Future enhancement: Load districts dynamically via API endpoint (e.g., /jw/api/fss/fss/districts)
    // to make the plugin reusable for other countries. The API endpoint should query distinct
    // district values from the v_farmer_search view or a separate configuration table.
    var DISTRICTS = [
        { code: 'BER', name: 'Berea' },
        { code: 'BB', name: 'Butha-Buthe' },
        { code: 'LEI', name: 'Leribe' },
        { code: 'MAF', name: 'Mafeteng' },
        { code: 'MAS', name: 'Maseru' },
        { code: 'MHK', name: "Mohale's Hoek" },
        { code: 'MOK', name: 'Mokhotlong' },
        { code: 'QAC', name: "Qacha's Nek" },
        { code: 'QUT', name: 'Quthing' },
        { code: 'TT', name: 'Thaba-Tseka' }
    ];

    /**
     * Get criteria types with config-aware minLength values (Issue #5)
     * @param {Object} config - The search config object
     * @returns {Array} Criteria type definitions
     */
    function getCriteriaTypes(config) {
        var idMinLen = (config && config.nationalIdMinLength) || 4;
        var phoneMinLen = (config && config.phoneMinLength) || 8;

        return [
            { type: 'village', label: 'Village', icon: 'fa-map-marker-alt', requiresDistrict: true, inputType: 'autocomplete' },
            { type: 'community_council', label: 'Community Council', icon: 'fa-building', requiresDistrict: false, inputType: 'autocomplete' },
            { type: 'cooperative', label: 'Cooperative', icon: 'fa-users', requiresDistrict: false, inputType: 'autocomplete' },
            { type: 'partial_id', label: 'Partial ID (' + idMinLen + '+ digits)', icon: 'fa-id-card', requiresDistrict: false, inputType: 'text', minLength: idMinLen, pattern: '[0-9]+' },
            { type: 'partial_phone', label: 'Partial Phone (' + phoneMinLen + '+ digits)', icon: 'fa-phone', requiresDistrict: false, inputType: 'text', minLength: phoneMinLen, pattern: '[0-9]+' }
        ];
    }

    /**
     * FarmerSmartSearch - Main class for farmer search functionality
     */
    var FarmerSmartSearch = {
        version: VERSION,
        instances: {},

        /**
         * Initialize a new search instance
         * @param {string} containerId - ID of the container element
         * @param {object} options - Configuration options
         * @returns {object} Search instance
         */
        init: function(containerId, options) {
            console.log('[FarmerSmartSearch] Initializing for container: ' + containerId);
            console.log('[FarmerSmartSearch] Options:', options);

            var container = document.getElementById(containerId);
            if (!container) {
                console.error('[FarmerSmartSearch] Container not found: ' + containerId);
                throw new Error('Container not found: ' + containerId);
            }

            console.log('[FarmerSmartSearch] Container found:', container);

            // Default options
            var config = {
                apiEndpoint: '/jw/api/fss',
                apiId: '',
                apiKey: '',
                hiddenFieldId: '',
                storeValue: 'nationalId',  // Which farmer property to store: nationalId, id, or phone
                displayMode: 'popup',
                displayColumns: ['nationalId', 'firstName', 'lastName', 'district', 'village'],
                initialValue: '',
                // Input detection patterns (configurable)
                nationalIdPattern: '^\\d{9,13}$',
                nationalIdMinLength: 4,
                phonePattern: '^\\+?\\d{8,}$',
                phoneMinLength: 8,
                // Auto-select configuration
                autoSelectSingleResult: true,
                autoSelectMinScore: 90,
                showAutoSelectNotification: true,
                // Callbacks
                onSelect: function(farmer) {},
                onClear: function() {},
                onError: function(error) {}
            };

            // Merge with provided options
            for (var key in options) {
                if (options.hasOwnProperty(key)) {
                    config[key] = options[key];
                }
            }

            // Create instance
            var instance = new SearchInstance(containerId, container, config);
            this.instances[containerId] = instance;

            return instance;
        },

        /**
         * Get an existing instance
         * @param {string} containerId - ID of the container element
         * @returns {object|null} Search instance or null
         */
        getInstance: function(containerId) {
            return this.instances[containerId] || null;
        }
    };

    /**
     * SearchInstance - Individual search component instance
     */
    function SearchInstance(id, container, config) {
        var self = this;
        
        this.id = id;
        this.container = container;
        this.config = config;

        // Phase 8: Initialize config-aware criteria types (Issue #5)
        this.criteriaTypes = getCriteriaTypes(config);

        // State
        this.state = {
            isOpen: config.displayMode === 'inline',
            isLoading: false,
            searchText: '',
            districtCode: '',
            results: [],
            selectedFarmer: null,
            error: null,
            confidence: 0,
            statistics: null,
            statisticsLoaded: false,
            // Phase 5: Additional criteria
            additionalCriteria: [], // Array of { type: string, value: string, id: number }
            criteriaIdCounter: 0,
            criteriaMenuOpen: false,
            activeAutocomplete: null, // Currently active autocomplete dropdown
            autocompleteOptions: [], // Current autocomplete options
            autocompleteLoading: false,
            autocompleteActiveIndex: -1,
            // Phase 6: Offline support
            isOnline: navigator.onLine,
            recentFarmers: [],
            usingCachedStatistics: false,
            // Phase 7: Error handling & keyboard navigation
            lastError: null,
            retryCount: 0,
            lastSearchRequest: null,
            selectedResultIndex: -1,
            focusableElements: [],
            previousActiveElement: null
        };

        // Element references
        this.elements = {};
        this.displayField = null;
        this.displayFieldText = null;
        this.searchBtn = null;
        this.clearBtn = null;

        // Confidence engine
        this.confidenceEngine = null;

        // Phase 6: Recent farmers manager
        this.recentFarmersManager = null;

        // Debounce timers
        this.autocompleteTimer = null;

        // Find parent elements using multiple strategies
        this.findParentElements();

        // Initialize UI
        this.render();
        this.bindEvents();

        // Phase 6: Initialize offline support
        this.initOfflineSupport();

        // Initialize confidence engine (now with caching)
        this.initConfidenceEngine();

        // Load initial value if present
        if (config.initialValue) {
            this.loadFarmerById(config.initialValue);
        }
        
        console.log('[FarmerSmartSearch] Instance created successfully for: ' + id);
    }

    // ==========================================================================
    // PHASE 6: OFFLINE SUPPORT METHODS
    // ==========================================================================

    /**
     * Initialize offline support features
     */
    SearchInstance.prototype.initOfflineSupport = function() {
        var self = this;

        // Initialize RecentFarmers manager if available and enabled (Issue #1, #2)
        if (typeof RecentFarmers !== 'undefined' && this.config.showRecentFarmers !== false) {
            var maxRecent = this.config.maxRecentFarmers || 5;
            this.recentFarmersManager = new RecentFarmers(maxRecent);
            this.state.recentFarmers = this.recentFarmersManager.getAll();
            console.log('[FarmerSmartSearch] RecentFarmers initialized, max:', maxRecent, 'count:', this.state.recentFarmers.length);
        } else {
            console.log('[FarmerSmartSearch] RecentFarmers disabled or not available, showRecentFarmers:', this.config.showRecentFarmers);
        }

        // Set up online/offline event listeners
        window.addEventListener('online', function() {
            console.log('[FarmerSmartSearch] Online event received');
            self.handleOnlineStatusChange(true);
        });

        window.addEventListener('offline', function() {
            console.log('[FarmerSmartSearch] Offline event received');
            self.handleOnlineStatusChange(false);
        });

        // Initial status
        this.state.isOnline = navigator.onLine;
        console.log('[FarmerSmartSearch] Initial online status:', this.state.isOnline);
    };

    /**
     * Handle online status changes
     */
    SearchInstance.prototype.handleOnlineStatusChange = function(isOnline) {
        this.state.isOnline = isOnline;
        
        // Update UI
        this.updateConnectionStatusIndicator();
        
        if (isOnline) {
            // Hide offline warning
            this.hideOfflineWarning();
            
            // Enable search
            this.updateConfidence();
            
            // Refresh statistics if stale
            if (this.confidenceEngine && this.confidenceEngine.isCacheStale()) {
                console.log('[FarmerSmartSearch] Refreshing stale statistics');
                this.refreshStatistics();
            }
        } else {
            // Show offline warning
            this.showOfflineWarning();
            
            // Disable search button
            if (this.elements.doSearchBtn) {
                this.elements.doSearchBtn.disabled = true;
            }
        }
    };

    /**
     * Update connection status indicator in dialog header
     */
    SearchInstance.prototype.updateConnectionStatusIndicator = function() {
        var indicator = this.container.querySelector('.fss-connection-status');
        if (!indicator) return;

        if (this.state.isOnline) {
            indicator.className = 'fss-connection-status fss-status-online';
            indicator.innerHTML = '<i class="fa fa-circle"></i> Online';
            indicator.title = 'Connected';
        } else {
            indicator.className = 'fss-connection-status fss-status-offline';
            indicator.innerHTML = '<i class="fa fa-circle"></i> Offline';
            indicator.title = 'No internet connection';
        }
    };

    /**
     * Show offline warning banner
     */
    SearchInstance.prototype.showOfflineWarning = function() {
        var warning = this.container.querySelector('.fss-offline-warning');
        if (warning) {
            warning.style.display = 'flex';
        }

        // Disable search-related elements
        if (this.elements.searchInput) {
            this.elements.searchInput.disabled = true;
        }
        if (this.elements.districtSelect) {
            this.elements.districtSelect.disabled = true;
        }
        if (this.elements.addCriteriaBtn) {
            this.elements.addCriteriaBtn.disabled = true;
        }
    };

    /**
     * Hide offline warning banner
     */
    SearchInstance.prototype.hideOfflineWarning = function() {
        var warning = this.container.querySelector('.fss-offline-warning');
        if (warning) {
            warning.style.display = 'none';
        }

        // Re-enable search elements
        if (this.elements.searchInput) {
            this.elements.searchInput.disabled = false;
        }
        if (this.elements.districtSelect) {
            this.elements.districtSelect.disabled = false;
        }
        if (this.elements.addCriteriaBtn) {
            this.elements.addCriteriaBtn.disabled = false;
        }
    };

    /**
     * Render the recent farmers panel
     */
    SearchInstance.prototype.renderRecentFarmersPanel = function() {
        var panel = this.elements.recentFarmersPanel;
        if (!panel) return;

        // Update state from storage
        if (this.recentFarmersManager) {
            this.state.recentFarmers = this.recentFarmersManager.getAll();
        }

        var farmers = this.state.recentFarmers;

        if (!farmers || farmers.length === 0) {
            panel.innerHTML = '<div class="fss-recent-empty">No recent farmers</div>';
            panel.style.display = 'none';
            return;
        }

        var html = '';
        html += '<div class="fss-recent-header">';
        html += '  <span class="fss-recent-title"><i class="fa fa-history"></i> Recent Farmers</span>';
        html += '  <button type="button" class="fss-clear-recent-btn" title="Clear recent farmers">Clear</button>';
        html += '</div>';
        html += '<div class="fss-recent-list">';

        for (var i = 0; i < farmers.length; i++) {
            var f = farmers[i];
            var location = f.districtName || f.districtCode || '';
            if (f.village) {
                location = location ? location + ' > ' + f.village : f.village;
            }

            html += '<div class="fss-recent-farmer-item" data-id="' + this.escapeHtml(f.id) + '">';
            html += '  <div class="fss-recent-farmer-info">';
            html += '    <span class="fss-recent-farmer-icon"><i class="fa fa-user"></i></span>';
            html += '    <div class="fss-recent-farmer-details">';
            html += '      <span class="fss-recent-farmer-name">' + this.escapeHtml(f.firstName + ' ' + f.lastName) + '</span>';
            html += '      <span class="fss-recent-farmer-location">' + this.escapeHtml(location) + '</span>';
            html += '    </div>';
            html += '  </div>';
            html += '  <button type="button" class="fss-recent-farmer-select">Select</button>';
            html += '</div>';
        }

        html += '</div>';

        panel.innerHTML = html;
        panel.style.display = 'block';

        // Bind events
        this.bindRecentFarmersEvents();
    };

    /**
     * Bind events for recent farmers panel
     */
    SearchInstance.prototype.bindRecentFarmersEvents = function() {
        var self = this;
        var panel = this.elements.recentFarmersPanel;
        if (!panel) return;

        // Clear button
        var clearBtn = panel.querySelector('.fss-clear-recent-btn');
        if (clearBtn) {
            clearBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                self.clearRecentFarmers();
            });
        }

        // Select buttons
        var selectBtns = panel.querySelectorAll('.fss-recent-farmer-select');
        selectBtns.forEach(function(btn) {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                var item = btn.closest('.fss-recent-farmer-item');
                var farmerId = item.getAttribute('data-id');
                self.selectRecentFarmer(farmerId);
            });
        });

        // Entire row click
        var items = panel.querySelectorAll('.fss-recent-farmer-item');
        items.forEach(function(item) {
            item.addEventListener('click', function(e) {
                if (!e.target.classList.contains('fss-recent-farmer-select')) {
                    var farmerId = item.getAttribute('data-id');
                    self.selectRecentFarmer(farmerId);
                }
            });
        });
    };

    /**
     * Select a farmer from the recent list
     */
    SearchInstance.prototype.selectRecentFarmer = function(farmerId) {
        var farmer = this.state.recentFarmers.find(function(f) {
            return f.id === farmerId;
        });

        if (farmer) {
            console.log('[FarmerSmartSearch] Selecting recent farmer:', farmer);
            this.selectFarmer(farmer);
        }
    };

    /**
     * Clear all recent farmers
     */
    SearchInstance.prototype.clearRecentFarmers = function() {
        if (this.recentFarmersManager) {
            this.recentFarmersManager.clear();
            this.state.recentFarmers = [];
            this.renderRecentFarmersPanel();
            console.log('[FarmerSmartSearch] Recent farmers cleared');
        }
    };

    /**
     * Add a farmer to recent list
     */
    SearchInstance.prototype.addToRecentFarmers = function(farmer) {
        console.log('[FarmerSmartSearch] addToRecentFarmers called');
        console.log('[FarmerSmartSearch] - farmer object:', JSON.stringify(farmer));
        console.log('[FarmerSmartSearch] - recentFarmersManager exists:', !!this.recentFarmersManager);
        
        if (!this.recentFarmersManager) {
            console.warn('[FarmerSmartSearch] recentFarmersManager not initialized!');
            return;
        }
        
        var result = this.recentFarmersManager.add(farmer);
        console.log('[FarmerSmartSearch] - add result:', result);
        
        this.state.recentFarmers = this.recentFarmersManager.getAll();
        console.log('[FarmerSmartSearch] - total recent farmers:', this.state.recentFarmers.length);
        
        // Re-render if panel is visible
        if (this.elements.recentFarmersPanel) {
            this.renderRecentFarmersPanel();
        }
    };

    /**
     * Refresh statistics from API
     */
    SearchInstance.prototype.refreshStatistics = function() {
        var self = this;
        
        if (!this.confidenceEngine) return;

        this.confidenceEngine.loadStatistics(
            this.config.apiEndpoint,
            this.config.apiId,
            this.config.apiKey,
            this.state.isOnline,
            function(error, stats, fromCache) {
                if (error) {
                    console.warn('[FarmerSmartSearch] Failed to refresh statistics:', error);
                } else {
                    console.log('[FarmerSmartSearch] Statistics refreshed, fromCache:', fromCache);
                    self.state.statistics = stats;
                    self.state.statisticsLoaded = true;
                    self.state.usingCachedStatistics = fromCache;
                    self.updateCachedDataIndicator();
                    self.updateConfidence();
                }
            }
        );
    };

    /**
     * Update the "using cached data" indicator
     */
    SearchInstance.prototype.updateCachedDataIndicator = function() {
        var indicator = this.container.querySelector('.fss-cached-data-indicator');
        if (!indicator) return;

        if (this.state.usingCachedStatistics && this.confidenceEngine) {
            var age = this.confidenceEngine.getCacheAge();
            indicator.innerHTML = '<i class="fa fa-database"></i> Using cached data (' + age + ')';
            indicator.style.display = 'inline-block';
        } else {
            indicator.style.display = 'none';
        }
    };

    // ==========================================================================
    // PHASE 7: ERROR HANDLING METHODS
    // ==========================================================================

    /**
     * Handle and display errors with appropriate user-friendly messages
     * @param {string} errorCode - Error code from ERROR_CODES
     * @param {*} originalError - Original error object/message for logging
     * @param {Object} context - Additional context (e.g., which operation failed)
     */
    SearchInstance.prototype.handleError = function(errorCode, originalError, context) {
        var self = this;
        var errorInfo = ERROR_CODES[errorCode] || ERROR_CODES.ERR_UNKNOWN;
        
        console.error('[FarmerSmartSearch] Error:', errorCode, originalError, context);
        
        // Store error state
        this.state.lastError = {
            code: errorInfo.code,
            message: errorInfo.message,
            originalError: originalError,
            context: context,
            timestamp: Date.now()
        };
        
        // Build error HTML
        var html = '<div class="fss-error fss-error-' + errorInfo.code.toLowerCase().replace(/_/g, '-') + '">';
        html += '  <div class="fss-error-content">';
        html += '    <i class="fa fa-exclamation-triangle"></i>';
        html += '    <div class="fss-error-text">';
        html += '      <span class="fss-error-message">' + this.escapeHtml(errorInfo.message) + '</span>';
        
        // Add suggestions based on error type
        var suggestions = this.getErrorSuggestions(errorCode);
        if (suggestions.length > 0) {
            html += '      <ul class="fss-error-suggestions">';
            for (var i = 0; i < suggestions.length; i++) {
                html += '        <li>' + this.escapeHtml(suggestions[i]) + '</li>';
            }
            html += '      </ul>';
        }
        
        html += '    </div>';
        html += '  </div>';
        
        // Add action buttons
        html += '  <div class="fss-error-actions">';
        
        if (errorInfo.canRetry && this.state.retryCount < RETRY_CONFIG.maxRetries) {
            html += '    <button type="button" class="fss-retry-btn" tabindex="0">';
            html += '      <i class="fa fa-redo"></i> Retry';
            html += '    </button>';
        }
        
        html += '    <button type="button" class="fss-dismiss-error-btn" tabindex="0">';
        html += '      <i class="fa fa-times"></i> Dismiss';
        html += '    </button>';
        html += '  </div>';
        html += '</div>';
        
        // Show error panel
        this.elements.emptyState.style.display = 'none';
        this.elements.resultsPanel.style.display = 'none';
        this.elements.loadingPanel.style.display = 'none';
        this.elements.errorPanel.innerHTML = html;
        this.elements.errorPanel.style.display = 'block';
        
        // Bind error action events
        this.bindErrorActions();
        
        // Call error callback
        if (typeof this.config.onError === 'function') {
            this.config.onError(this.state.lastError);
        }
    };

    /**
     * Get helpful suggestions based on error type
     * @param {string} errorCode - Error code
     * @returns {Array} Array of suggestion strings
     */
    SearchInstance.prototype.getErrorSuggestions = function(errorCode) {
        var suggestions = [];
        
        switch (errorCode) {
            case 'ERR_NETWORK':
                suggestions.push('Check your internet connection');
                suggestions.push('Try again in a few moments');
                break;
            case 'ERR_TIMEOUT':
                suggestions.push('The server is taking too long to respond');
                suggestions.push('Try searching with more specific criteria');
                break;
            case 'ERR_NO_RESULTS':
                suggestions.push('Check the spelling of the name');
                suggestions.push('Try a different village or district');
                suggestions.push('Search by National ID or Phone instead');
                break;
            case 'ERR_TOO_MANY':
                suggestions.push('Add a village to narrow results');
                suggestions.push('Include partial ID or phone number');
                suggestions.push('Select a specific community council');
                break;
            case 'ERR_AUTH':
                suggestions.push('Your session may have expired');
                suggestions.push('Contact your system administrator');
                break;
            case 'ERR_SERVER':
                suggestions.push('The server encountered an error');
                suggestions.push('Please try again later');
                break;
        }
        
        return suggestions;
    };

    /**
     * Bind event handlers for error action buttons
     */
    SearchInstance.prototype.bindErrorActions = function() {
        var self = this;
        
        // Retry button
        var retryBtn = this.elements.errorPanel.querySelector('.fss-retry-btn');
        if (retryBtn) {
            retryBtn.addEventListener('click', function(e) {
                e.preventDefault();
                self.retryLastSearch();
            });
        }
        
        // Dismiss button
        var dismissBtn = this.elements.errorPanel.querySelector('.fss-dismiss-error-btn');
        if (dismissBtn) {
            dismissBtn.addEventListener('click', function(e) {
                e.preventDefault();
                self.dismissError();
            });
        }
    };

    /**
     * Retry the last failed search with exponential backoff
     */
    SearchInstance.prototype.retryLastSearch = function() {
        var self = this;
        
        if (!this.state.lastSearchRequest) {
            console.warn('[FarmerSmartSearch] No previous search to retry');
            return;
        }
        
        if (this.state.retryCount >= RETRY_CONFIG.maxRetries) {
            console.warn('[FarmerSmartSearch] Max retries reached');
            this.handleError('ERR_SERVER', 'Max retries exceeded', { retryCount: this.state.retryCount });
            return;
        }
        
        // Calculate delay with exponential backoff
        var delay = Math.min(
            RETRY_CONFIG.baseDelay * Math.pow(2, this.state.retryCount),
            RETRY_CONFIG.maxDelay
        );
        
        this.state.retryCount++;
        console.log('[FarmerSmartSearch] Retrying search (attempt ' + this.state.retryCount + ') after ' + delay + 'ms');
        
        // Show retry loading state
        this.showRetryLoading(delay);
        
        setTimeout(function() {
            self.executeSearch();
        }, delay);
    };

    /**
     * Show loading state during retry with countdown
     * @param {number} delay - Delay in milliseconds
     */
    SearchInstance.prototype.showRetryLoading = function(delay) {
        var self = this;
        var seconds = Math.ceil(delay / 1000);
        
        this.elements.errorPanel.style.display = 'none';
        this.elements.loadingPanel.innerHTML = '<div class="fss-retry-loading">' +
            '<i class="fa fa-spinner fa-spin"></i>' +
            '<span>Retrying in <span class="fss-retry-countdown">' + seconds + '</span>s...</span>' +
            '<span class="fss-retry-attempt">Attempt ' + this.state.retryCount + ' of ' + RETRY_CONFIG.maxRetries + '</span>' +
            '</div>';
        this.elements.loadingPanel.style.display = 'flex';
        
        // Update countdown
        var countdownEl = this.elements.loadingPanel.querySelector('.fss-retry-countdown');
        var interval = setInterval(function() {
            seconds--;
            if (seconds > 0 && countdownEl) {
                countdownEl.textContent = seconds;
            } else {
                clearInterval(interval);
            }
        }, 1000);
    };

    /**
     * Dismiss the current error and return to initial state
     */
    SearchInstance.prototype.dismissError = function() {
        this.state.lastError = null;
        this.state.retryCount = 0;
        this.elements.errorPanel.style.display = 'none';
        this.elements.emptyState.style.display = 'block';
        
        // Focus search input
        if (this.elements.searchInput) {
            this.elements.searchInput.focus();
        }
    };

    /**
     * Map HTTP status codes and error types to error codes
     * @param {number} status - HTTP status code
     * @param {string} errorType - Error type string (e.g., 'timeout', 'network')
     * @returns {string} Error code
     */
    SearchInstance.prototype.mapErrorCode = function(status, errorType) {
        if (errorType === 'timeout') return 'ERR_TIMEOUT';
        if (errorType === 'network' || errorType === 'offline') return 'ERR_NETWORK';
        
        if (status === 401 || status === 403) return 'ERR_AUTH';
        if (status === 404) return 'ERR_NO_RESULTS';
        if (status >= 500) return 'ERR_SERVER';
        
        return 'ERR_UNKNOWN';
    };

    // ==========================================================================
    // PHASE 7: KEYBOARD NAVIGATION & FOCUS MANAGEMENT
    // ==========================================================================

    /**
     * Initialize keyboard navigation for the dialog
     */
    SearchInstance.prototype.initKeyboardNavigation = function() {
        var self = this;
        
        // Add keydown listener to dialog
        if (this.elements.dialog) {
            this.elements.dialog.addEventListener('keydown', function(e) {
                self.handleDialogKeydown(e);
            });
        }
        
        // Add keydown listener to results panel for arrow navigation
        if (this.elements.resultsPanel) {
            this.elements.resultsPanel.addEventListener('keydown', function(e) {
                self.handleResultsKeydown(e);
            });
        }
    };

    /**
     * Handle keydown events within the dialog
     * @param {KeyboardEvent} e - Keyboard event
     */
    SearchInstance.prototype.handleDialogKeydown = function(e) {
        // Handle Tab for focus trapping in popup mode
        if (e.key === 'Tab' && this.config.displayMode === 'popup' && this.state.isOpen) {
            this.handleTabKey(e);
        }
        
        // Handle Escape
        if (e.key === 'Escape') {
            this.handleEscapeKey(e);
        }
        
        // Handle Enter on search input
        if (e.key === 'Enter' && e.target === this.elements.searchInput) {
            if (!this.elements.doSearchBtn.disabled) {
                e.preventDefault();
                this.executeSearch();
            }
        }
    };

    /**
     * Handle Tab key for focus trapping in modal
     * @param {KeyboardEvent} e - Keyboard event
     */
    SearchInstance.prototype.handleTabKey = function(e) {
        var focusableSelectors = [
            'button:not([disabled])',
            'input:not([disabled])',
            'select:not([disabled])',
            'textarea:not([disabled])',
            '[tabindex]:not([tabindex="-1"])',
            'a[href]'
        ].join(', ');
        
        var focusableElements = this.elements.dialog.querySelectorAll(focusableSelectors);
        var firstFocusable = focusableElements[0];
        var lastFocusable = focusableElements[focusableElements.length - 1];
        
        if (!firstFocusable || !lastFocusable) return;
        
        if (e.shiftKey) {
            // Shift + Tab: move backwards
            if (document.activeElement === firstFocusable) {
                e.preventDefault();
                lastFocusable.focus();
            }
        } else {
            // Tab: move forwards
            if (document.activeElement === lastFocusable) {
                e.preventDefault();
                firstFocusable.focus();
            }
        }
    };

    /**
     * Handle Escape key to close dialogs/dropdowns
     * @param {KeyboardEvent} e - Keyboard event
     */
    SearchInstance.prototype.handleEscapeKey = function(e) {
        // Priority: close dropdowns first, then dialogs
        if (this.state.criteriaMenuOpen) {
            e.preventDefault();
            this.closeCriteriaMenu();
            return;
        }
        
        if (this.state.activeAutocomplete) {
            e.preventDefault();
            this.closeAutocomplete();
            return;
        }
        
        // Close popup dialog
        if (this.config.displayMode === 'popup' && this.state.isOpen) {
            e.preventDefault();
            this.hide();
        }
    };

    /**
     * Handle keydown events in results panel for arrow navigation
     * @param {KeyboardEvent} e - Keyboard event
     */
    SearchInstance.prototype.handleResultsKeydown = function(e) {
        var cards = this.elements.resultsPanel.querySelectorAll('.fss-farmer-card');
        if (cards.length === 0) return;
        
        var currentIndex = this.state.selectedResultIndex;
        
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            currentIndex = Math.min(currentIndex + 1, cards.length - 1);
            this.highlightResult(currentIndex);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            currentIndex = Math.max(currentIndex - 1, 0);
            this.highlightResult(currentIndex);
        } else if (e.key === 'Enter' && currentIndex >= 0) {
            e.preventDefault();
            this.selectFarmerByIndex(currentIndex);
        } else if (e.key === 'Home') {
            e.preventDefault();
            this.highlightResult(0);
        } else if (e.key === 'End') {
            e.preventDefault();
            this.highlightResult(cards.length - 1);
        }
    };

    /**
     * Highlight a result card by index
     * @param {number} index - Index of the result to highlight
     */
    SearchInstance.prototype.highlightResult = function(index) {
        var cards = this.elements.resultsPanel.querySelectorAll('.fss-farmer-card');
        
        // Remove highlight from all cards
        cards.forEach(function(card) {
            card.classList.remove('fss-highlighted');
            card.removeAttribute('aria-selected');
        });
        
        // Highlight selected card
        if (index >= 0 && index < cards.length) {
            var selectedCard = cards[index];
            selectedCard.classList.add('fss-highlighted');
            selectedCard.setAttribute('aria-selected', 'true');
            
            // Scroll into view if needed
            selectedCard.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            
            // Focus the card for screen readers
            selectedCard.focus();
            
            this.state.selectedResultIndex = index;
        }
    };

    /**
     * Select a farmer by result index
     * @param {number} index - Index of the result to select
     */
    SearchInstance.prototype.selectFarmerByIndex = function(index) {
        if (index >= 0 && index < this.state.results.length) {
            var farmer = this.state.results[index];
            this.selectFarmer(farmer);
        }
    };

    /**
     * Store previously focused element and set focus to dialog
     */
    SearchInstance.prototype.saveFocusAndTrap = function() {
        // Save currently focused element
        this.state.previousActiveElement = document.activeElement;
        
        // Focus the search input
        if (this.elements.searchInput && this.state.isOnline) {
            setTimeout(function() {
                this.elements.searchInput.focus();
            }.bind(this), 100);
        } else if (this.elements.recentFarmersPanel) {
            // If offline, focus recent farmers panel
            var firstFocusable = this.elements.recentFarmersPanel.querySelector('button');
            if (firstFocusable) {
                setTimeout(function() {
                    firstFocusable.focus();
                }, 100);
            }
        }
    };

    /**
     * Restore focus to previously focused element
     */
    SearchInstance.prototype.restoreFocus = function() {
        if (this.state.previousActiveElement && typeof this.state.previousActiveElement.focus === 'function') {
            this.state.previousActiveElement.focus();
        }
        this.state.previousActiveElement = null;
    };

    /**
     * Add ARIA attributes for accessibility
     */
    SearchInstance.prototype.addAriaAttributes = function() {
        // Dialog role and label
        if (this.elements.dialog) {
            this.elements.dialog.setAttribute('role', 'dialog');
            this.elements.dialog.setAttribute('aria-labelledby', this.id + '_title');
            this.elements.dialog.setAttribute('aria-modal', this.config.displayMode === 'popup' ? 'true' : 'false');
        }
        
        // Title ID
        var title = this.container.querySelector('.fss-title');
        if (title) {
            title.id = this.id + '_title';
        }
        
        // Search input
        if (this.elements.searchInput) {
            this.elements.searchInput.setAttribute('aria-label', 'Search for farmer by ID, phone, or name');
            this.elements.searchInput.setAttribute('aria-describedby', this.id + '_confidence');
        }
        
        // Confidence area
        if (this.elements.confidenceArea) {
            this.elements.confidenceArea.id = this.id + '_confidence';
            this.elements.confidenceArea.setAttribute('aria-live', 'polite');
        }
        
        // Results panel
        if (this.elements.resultsPanel) {
            this.elements.resultsPanel.setAttribute('role', 'listbox');
            this.elements.resultsPanel.setAttribute('aria-label', 'Search results');
        }
        
        // Close button
        if (this.elements.closeBtn) {
            this.elements.closeBtn.setAttribute('aria-label', 'Close search dialog');
        }
    };

    /**
     * Update ARIA attributes for farmer cards after rendering
     */
    SearchInstance.prototype.updateResultsAria = function() {
        var cards = this.elements.resultsPanel.querySelectorAll('.fss-farmer-card');
        cards.forEach(function(card, index) {
            card.setAttribute('role', 'option');
            card.setAttribute('tabindex', '0');
            card.setAttribute('aria-selected', 'false');
            
            // Add descriptive label
            var name = card.querySelector('.fss-farmer-name');
            var location = card.querySelector('.fss-farmer-location');
            if (name && location) {
                card.setAttribute('aria-label', name.textContent + ', ' + location.textContent);
            }
        });
        
        // Reset selection index
        this.state.selectedResultIndex = -1;
    };

    // ==========================================================================
    // CONFIDENCE ENGINE INITIALIZATION (Updated for Phase 6)
    // ==========================================================================

    /**
     * Initialize the confidence engine and load statistics
     */
    SearchInstance.prototype.initConfidenceEngine = function() {
        var self = this;

        // Check if ConfidenceEngine is available
        if (typeof ConfidenceEngine === 'undefined') {
            console.warn('[FarmerSmartSearch] ConfidenceEngine not loaded, confidence features disabled');
            return;
        }

        // Create confidence engine with configurable parameters
        this.confidenceEngine = new ConfidenceEngine({
            nationalIdMinLength: this.config.nationalIdMinLength || 4,
            phoneMinLength: this.config.phoneMinLength || 8,
            nameMinLength: FSS_DEFAULTS.nameMinLength,
            statisticsTimeout: FSS_DEFAULTS.statisticsTimeout
        });

        // Load statistics (with cache support)
        console.log('[FarmerSmartSearch] Loading statistics...');
        this.confidenceEngine.loadStatistics(
            this.config.apiEndpoint,
            this.config.apiId,
            this.config.apiKey,
            this.state.isOnline,
            function(error, stats, fromCache) {
                if (error) {
                    console.warn('[FarmerSmartSearch] Failed to load statistics:', error);
                } else {
                    console.log('[FarmerSmartSearch] Statistics loaded, fromCache:', fromCache);
                    self.state.statistics = stats;
                    self.state.statisticsLoaded = true;
                    self.state.usingCachedStatistics = fromCache;
                    self.updateCachedDataIndicator();
                    // Update confidence display with current criteria
                    self.updateConfidence();
                }
            }
        );
    };

    /**
     * Find parent elements (display field, buttons) using multiple strategies
     * Phase 7: Added inline search elements
     */
    SearchInstance.prototype.findParentElements = function() {
        var container = this.container;
        
        console.log('[FarmerSmartSearch] Finding parent elements...');
        
        // Strategy 1: Look for .fss-form-cell ancestor
        var formCell = container.closest('.fss-form-cell');
        console.log('[FarmerSmartSearch] formCell via closest:', formCell);
        
        if (formCell) {
            this.displayField = formCell.querySelector('.fss-display-field');
            this.displayFieldText = formCell.querySelector('.fss-selected-display');
            this.searchBtn = formCell.querySelector('.fss-search-btn');
            this.clearBtn = formCell.querySelector('.fss-clear-btn');
            // Phase 7: Inline search elements
            this.inlineSearch = formCell.querySelector('.fss-inline-search');
            this.inlineInput = formCell.querySelector('.fss-inline-input');
            this.inlineTypeBadge = formCell.querySelector('.fss-inline-type-badge');
            this.inlineLoading = formCell.querySelector('.fss-inline-loading');
            this.inlineError = formCell.querySelector('.fss-inline-error');
        }
        
        // Strategy 2: Look for sibling .fss-display-field
        if (!this.displayField) {
            var parent = container.parentElement;
            console.log('[FarmerSmartSearch] Parent element:', parent);
            if (parent) {
                this.displayField = parent.querySelector('.fss-display-field');
                if (this.displayField) {
                    this.displayFieldText = this.displayField.querySelector('.fss-selected-display');
                    this.searchBtn = this.displayField.querySelector('.fss-search-btn');
                    this.clearBtn = this.displayField.querySelector('.fss-clear-btn');
                    // Phase 7: Inline search elements
                    this.inlineSearch = this.displayField.querySelector('.fss-inline-search');
                    this.inlineInput = this.displayField.querySelector('.fss-inline-input');
                    this.inlineTypeBadge = this.displayField.querySelector('.fss-inline-type-badge');
                    this.inlineLoading = this.displayField.querySelector('.fss-inline-loading');
                    this.inlineError = this.displayField.querySelector('.fss-inline-error');
                }
            }
        }
        
        // Strategy 3: Use the ID pattern (elementId + _display)
        if (!this.displayField) {
            var displayId = this.id + '_display';
            this.displayField = document.getElementById(displayId);
            console.log('[FarmerSmartSearch] displayField via ID (' + displayId + '):', this.displayField);
            if (this.displayField) {
                this.displayFieldText = this.displayField.querySelector('.fss-selected-display');
                this.searchBtn = this.displayField.querySelector('.fss-search-btn');
                this.clearBtn = this.displayField.querySelector('.fss-clear-btn');
                // Phase 7: Inline search elements
                this.inlineSearch = this.displayField.querySelector('.fss-inline-search');
                this.inlineInput = this.displayField.querySelector('.fss-inline-input');
                this.inlineTypeBadge = this.displayField.querySelector('.fss-inline-type-badge');
                this.inlineLoading = this.displayField.querySelector('.fss-inline-loading');
                this.inlineError = this.displayField.querySelector('.fss-inline-error');
            }
        }
        
        console.log('[FarmerSmartSearch] Found elements:', {
            displayField: this.displayField,
            displayFieldText: this.displayFieldText,
            searchBtn: this.searchBtn,
            clearBtn: this.clearBtn,
            inlineSearch: this.inlineSearch,
            inlineInput: this.inlineInput
        });
    };

    /**
     * Render the search UI
     */
    SearchInstance.prototype.render = function() {
        console.log('[FarmerSmartSearch] Rendering UI for: ' + this.id);

        var isPopup = this.config.displayMode === 'popup';
        var html = '';

        if (isPopup) {
            html += '<div class="fss-modal-overlay"></div>';
            html += '<div class="fss-modal">';
        }

        html += '<div class="fss-dialog">';
        
        // Header (Phase 6: Added connection status)
        html += '<div class="fss-header">';
        html += '  <span class="fss-title">Find Farmer</span>';
        html += '  <div class="fss-header-right">';
        html += '    <span class="fss-connection-status fss-status-online"><i class="fa fa-circle"></i> Online</span>';
        if (isPopup) {
            html += '    <button type="button" class="fss-close-btn" title="Close"><i class="fa fa-times"></i></button>';
        }
        html += '  </div>';
        html += '</div>';

        // Phase 6: Offline warning banner
        html += '<div class="fss-offline-warning" style="display:none;">';
        html += '  <i class="fa fa-exclamation-triangle"></i>';
        html += '  <span>You are offline. Search is not available. Select from recent farmers or wait for connection.</span>';
        html += '</div>';

        // Phase 6: Recent farmers panel (conditionally rendered based on config)
        if (this.config.showRecentFarmers !== false) {
            html += '<div class="fss-recent-farmers-panel"></div>';
        }

        // Divider
        html += '<div class="fss-section-divider">';
        html += '  <span class="fss-divider-line"></span>';
        html += '  <span class="fss-divider-text">OR SEARCH</span>';
        html += '  <span class="fss-divider-line"></span>';
        html += '</div>';

        // Search area
        html += '<div class="fss-search-area">';
        html += '  <div class="fss-search-row">';
        html += '    <div class="fss-search-input-wrapper">';
        html += '      <input type="text" class="fss-search-input" placeholder="Enter ID, phone, or name...">';
        html += '      <span class="fss-input-type-badge"></span>';
        html += '    </div>';
        html += '    <button type="button" class="fss-do-search-btn" disabled>Search</button>';
        html += '  </div>';
        html += '  <div class="fss-filter-row">';
        html += '    <label>District:</label>';
        html += '    <select class="fss-district-select">';
        html += '      <option value="">All districts</option>';
        for (var i = 0; i < DISTRICTS.length; i++) {
            var d = DISTRICTS[i];
            html += '      <option value="' + d.code + '">' + d.name + '</option>';
        }
        html += '    </select>';
        html += '  </div>';
        
        // Phase 5: Criteria builder panel
        html += '  <div class="fss-criteria-builder">';
        html += '    <div class="fss-criteria-builder-header">';
        html += '      <span class="fss-criteria-builder-title">Additional Criteria</span>';
        html += '    </div>';
        html += '    <div class="fss-criteria-rows"></div>';
        html += '    <div class="fss-add-criteria-wrapper">';
        html += '      <button type="button" class="fss-add-criteria-btn"><i class="fa fa-plus"></i> Add criteria</button>';
        html += '      <div class="fss-criteria-menu" style="display:none;"></div>';
        html += '    </div>';
        html += '  </div>';
        
        // Confidence bar area (Phase 4, updated Phase 6)
        html += '  <div class="fss-confidence-area">';
        html += '    <div class="fss-confidence-track">';
        html += '      <div class="fss-confidence-bar"></div>';
        html += '    </div>';
        html += '    <div class="fss-confidence-info">';
        html += '      <span class="fss-confidence-percent">0%</span>';
        html += '      <span class="fss-confidence-text">Enter search criteria</span>';
        html += '    </div>';
        html += '    <div class="fss-cached-data-indicator" style="display:none;"></div>';
        html += '    <div class="fss-validation-message"></div>';
        html += '  </div>';
        
        html += '</div>';

        // Results area
        html += '<div class="fss-results-area">';
        html += '  <div class="fss-results-panel" style="display:none;"></div>';
        html += '  <div class="fss-loading-panel" style="display:none;"><i class="fa fa-spinner fa-spin"></i> Searching...</div>';
        html += '  <div class="fss-error-panel" style="display:none;"></div>';
        html += '  <div class="fss-empty-state">Enter search criteria and click Search</div>';
        html += '</div>';

        html += '</div>'; // end fss-dialog

        if (isPopup) {
            html += '</div>'; // end fss-modal
        }

        this.container.innerHTML = html;

        // Cache element references
        this.elements = {
            overlay: this.container.querySelector('.fss-modal-overlay'),
            modal: this.container.querySelector('.fss-modal'),
            dialog: this.container.querySelector('.fss-dialog'),
            closeBtn: this.container.querySelector('.fss-close-btn'),
            searchInput: this.container.querySelector('.fss-search-input'),
            inputTypeBadge: this.container.querySelector('.fss-input-type-badge'),
            doSearchBtn: this.container.querySelector('.fss-do-search-btn'),
            districtSelect: this.container.querySelector('.fss-district-select'),
            resultsPanel: this.container.querySelector('.fss-results-panel'),
            loadingPanel: this.container.querySelector('.fss-loading-panel'),
            errorPanel: this.container.querySelector('.fss-error-panel'),
            emptyState: this.container.querySelector('.fss-empty-state'),
            // Phase 4: Confidence elements
            confidenceArea: this.container.querySelector('.fss-confidence-area'),
            confidenceTrack: this.container.querySelector('.fss-confidence-track'),
            confidenceBar: this.container.querySelector('.fss-confidence-bar'),
            confidencePercent: this.container.querySelector('.fss-confidence-percent'),
            confidenceText: this.container.querySelector('.fss-confidence-text'),
            validationMessage: this.container.querySelector('.fss-validation-message'),
            // Phase 5: Criteria builder elements
            criteriaBuilder: this.container.querySelector('.fss-criteria-builder'),
            criteriaRows: this.container.querySelector('.fss-criteria-rows'),
            addCriteriaBtn: this.container.querySelector('.fss-add-criteria-btn'),
            criteriaMenu: this.container.querySelector('.fss-criteria-menu'),
            // Phase 6: Offline support elements
            connectionStatus: this.container.querySelector('.fss-connection-status'),
            offlineWarning: this.container.querySelector('.fss-offline-warning'),
            recentFarmersPanel: this.container.querySelector('.fss-recent-farmers-panel'),
            cachedDataIndicator: this.container.querySelector('.fss-cached-data-indicator')
        };

        console.log('[FarmerSmartSearch] Dialog elements:', this.elements);

        // For inline mode, show immediately
        if (this.config.displayMode === 'inline') {
            this.container.style.display = 'block';
        }
    };

    /**
     * Bind event handlers
     */
    SearchInstance.prototype.bindEvents = function() {
        var self = this;

        console.log('[FarmerSmartSearch] Binding events...');

        // Search button in display field (opens popup)
        if (this.searchBtn) {
            console.log('[FarmerSmartSearch] Binding click to searchBtn');
            this.searchBtn.addEventListener('click', function(e) {
                console.log('[FarmerSmartSearch] Search button clicked!');
                e.preventDefault();
                e.stopPropagation();
                self.show();
            });
            
            // Also try jQuery if available (Joget uses jQuery)
            if (typeof jQuery !== 'undefined') {
                jQuery(this.searchBtn).off('click.fss').on('click.fss', function(e) {
                    console.log('[FarmerSmartSearch] Search button clicked (jQuery)!');
                    e.preventDefault();
                    e.stopPropagation();
                    self.show();
                    return false;
                });
            }
        } else {
            console.warn('[FarmerSmartSearch] searchBtn not found - cannot bind click event');
        }

        // Clear button in display field
        if (this.clearBtn) {
            this.clearBtn.addEventListener('click', function(e) {
                console.log('[FarmerSmartSearch] Clear button clicked!');
                e.preventDefault();
                e.stopPropagation();
                self.clearSelection();
            });
        }

        // Close button (popup mode)
        if (this.elements.closeBtn) {
            this.elements.closeBtn.addEventListener('click', function(e) {
                e.preventDefault();
                self.hide();
            });
        }

        // Overlay click to close (popup mode)
        if (this.elements.overlay) {
            this.elements.overlay.addEventListener('click', function(e) {
                e.preventDefault();
                self.hide();
            });
        }

        // Search input - detect type as user types
        if (this.elements.searchInput) {
            this.elements.searchInput.addEventListener('input', function(e) {
                self.state.searchText = e.target.value;
                self.updateInputTypeBadge();
                self.updateConfidence(); // Phase 4: Update confidence on input change
            });

            // Search input - Enter key
            this.elements.searchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    if (!self.elements.doSearchBtn.disabled) {
                        self.executeSearch();
                    }
                }
            });
        }

        // District select
        if (this.elements.districtSelect) {
            this.elements.districtSelect.addEventListener('change', function(e) {
                self.state.districtCode = e.target.value;
                self.updateConfidence(); // Phase 4: Update confidence on district change
                // Phase 5: Update village criteria if present (reset value)
                self.resetVillageCriteria();
            });
        }

        // Search button (in dialog)
        if (this.elements.doSearchBtn) {
            this.elements.doSearchBtn.addEventListener('click', function(e) {
                e.preventDefault();
                self.executeSearch();
            });
        }

        // Phase 5: Add criteria button
        if (this.elements.addCriteriaBtn) {
            this.elements.addCriteriaBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                self.toggleCriteriaMenu();
            });
        }

        // Close criteria menu when clicking outside
        document.addEventListener('click', function(e) {
            if (self.state.criteriaMenuOpen) {
                if (!e.target.closest('.fss-add-criteria-wrapper')) {
                    self.closeCriteriaMenu();
                }
            }
            // Also close any autocomplete dropdowns
            if (self.state.activeAutocomplete) {
                if (!e.target.closest('.fss-criteria-input-wrapper')) {
                    self.closeAutocomplete();
                }
            }
        });

        // Escape key to close popup
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                if (self.state.criteriaMenuOpen) {
                    self.closeCriteriaMenu();
                } else if (self.state.activeAutocomplete) {
                    self.closeAutocomplete();
                } else if (self.state.isOpen && self.config.displayMode === 'popup') {
                    self.hide();
                }
            }
        });
        
        // Phase 7: Initialize keyboard navigation and accessibility
        this.initKeyboardNavigation();
        this.addAriaAttributes();
        
        // Phase 7: Bind inline quick entry events
        this.bindInlineEvents();
        
        console.log('[FarmerSmartSearch] Events bound successfully');
    };

    // ==========================================================================
    // PHASE 7: INLINE QUICK ENTRY METHODS
    // ==========================================================================

    /**
     * Bind events for inline quick entry
     */
    SearchInstance.prototype.bindInlineEvents = function() {
        var self = this;
        
        if (!this.inlineInput) {
            console.log('[FarmerSmartSearch] No inline input found, skipping inline events');
            return;
        }
        
        console.log('[FarmerSmartSearch] Binding inline events');
        
        // Debounce timer for inline lookup
        this.inlineLookupTimer = null;
        
        // Input event - detect type and trigger lookup for ID/phone
        this.inlineInput.addEventListener('input', function(e) {
            self.handleInlineInput(e.target.value);
        });
        
        // Enter key - trigger search or open popup for names
        this.inlineInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                self.handleInlineEnter();
            }
        });
        
        // Focus - clear any previous errors
        this.inlineInput.addEventListener('focus', function(e) {
            self.clearInlineError();
        });
    };

    /**
     * Handle inline input changes
     * @param {string} value - Current input value
     */
    SearchInstance.prototype.handleInlineInput = function(value) {
        var self = this;
        
        // Update type badge
        this.updateInlineTypeBadge(value);
        
        // Clear previous timer
        if (this.inlineLookupTimer) {
            clearTimeout(this.inlineLookupTimer);
        }
        
        // Clear any previous error
        this.clearInlineError();
        
        var inputType = this.detectInputType(value);
        
        // For ID or phone with enough digits, trigger quick lookup after debounce
        if (inputType === 'national_id' || inputType === 'phone') {
            var digitsOnly = value.replace(/[^\d]/g, '');
            var minLength = inputType === 'national_id' 
                ? (this.config.nationalIdMinLength || 4)
                : (this.config.phoneMinLength || 8);
            
            // Only lookup if we have enough digits for a reasonable match
            // Use the configured minLength, not hardcoded 8
            if (digitsOnly.length >= minLength) {
                this.inlineLookupTimer = setTimeout(function() {
                    self.executeInlineLookup(value, inputType);
                }, FSS_DEFAULTS.inlineLookupDebounce);
            }
        }
    };

    /**
     * Update the inline type badge
     * @param {string} value - Current input value
     */
    SearchInstance.prototype.updateInlineTypeBadge = function(value) {
        if (!this.inlineTypeBadge) return;
        
        var type = this.detectInputType(value);
        
        if (!type) {
            this.inlineTypeBadge.textContent = '';
            this.inlineTypeBadge.className = 'fss-inline-type-badge';
            return;
        }
        
        var labels = {
            'national_id': 'ID',
            'phone': 'Phone',
            'name': 'Name'
        };
        
        this.inlineTypeBadge.textContent = labels[type];
        this.inlineTypeBadge.className = 'fss-inline-type-badge fss-type-' + type.replace('_', '-');
    };

    /**
     * Handle Enter key in inline input
     */
    SearchInstance.prototype.handleInlineEnter = function() {
        var value = this.inlineInput.value.trim();
        if (!value) return;
        
        var inputType = this.detectInputType(value);
        
        if (inputType === 'national_id' || inputType === 'phone') {
            // For ID/phone, do immediate lookup
            this.executeInlineLookup(value, inputType);
        } else if (inputType === 'name') {
            // For names, open the popup with pre-filled search
            this.openPopupWithSearch(value);
        }
    };

    /**
     * Execute inline quick lookup for ID/phone
     * Uses dedicated GET endpoints for simpler, more reliable lookup
     * @param {string} value - Search value
     * @param {string} inputType - 'national_id' or 'phone'
     */
    SearchInstance.prototype.executeInlineLookup = function(value, inputType) {
        var self = this;
        
        if (!this.state.isOnline) {
            this.showInlineError('Offline - cannot search');
            return;
        }
        
        // Show loading
        this.showInlineLoading(true);
        
        // Clean the value
        var cleanValue = inputType === 'national_id' 
            ? value.replace(/[-\s]/g, '') 
            : value.replace(/[\s\-\(\)]/g, '');
        
        console.log('[FarmerSmartSearch] Inline lookup:', inputType, cleanValue);
        
        // Use dedicated GET endpoints for simpler lookup
        var endpoint = inputType === 'national_id' 
            ? '/search/byNationalId/' + encodeURIComponent(cleanValue)
            : '/search/byPhone/' + encodeURIComponent(cleanValue);
        
        this.apiCall('GET', endpoint, null, function(response) {
            self.showInlineLoading(false);
            
            console.log('[FarmerSmartSearch] Inline lookup response:', response);
            
            if (response.success && response.farmers && response.farmers.length > 0) {
                var farmer = response.farmers[0];
                
                // Check if it's a good match (score >= 90)
                if (farmer.relevanceScore >= self.config.autoSelectMinScore) {
                    console.log('[FarmerSmartSearch] Inline lookup found match:', farmer);
                    self.selectFarmer(farmer);
                } else {
                    // Low confidence - open popup for confirmation
                    console.log('[FarmerSmartSearch] Inline lookup low confidence (' + farmer.relevanceScore + '), opening popup');
                    self.openPopupWithSearch(value);
                }
            } else if (response.farmers && response.farmers.length === 0) {
                self.showInlineError('No farmer found');
            } else {
                // Error or unexpected response - open popup
                self.openPopupWithSearch(value);
            }
        }, function(error, status) {
            self.showInlineLoading(false);
            console.error('[FarmerSmartSearch] Inline lookup error:', error, 'status:', status);
            
            if (status === 404) {
                self.showInlineError('No farmer found');
            } else {
                self.showInlineError('Search failed');
            }
        });
    };

    /**
     * Open the popup dialog with pre-filled search
     * @param {string} searchValue - Value to pre-fill in search
     */
    SearchInstance.prototype.openPopupWithSearch = function(searchValue) {
        // Open the popup
        this.show();
        
        // Pre-fill the search input
        if (this.elements.searchInput && searchValue) {
            this.elements.searchInput.value = searchValue;
            this.state.searchText = searchValue;
            this.updateInputTypeBadge();
            this.updateConfidence();
        }
    };

    /**
     * Show/hide inline loading indicator
     * @param {boolean} show - Whether to show loading
     */
    SearchInstance.prototype.showInlineLoading = function(show) {
        if (this.inlineLoading) {
            this.inlineLoading.style.display = show ? 'flex' : 'none';
        }
        if (this.inlineInput) {
            this.inlineInput.disabled = show;
        }
    };

    /**
     * Show inline error message
     * @param {string} message - Error message
     */
    SearchInstance.prototype.showInlineError = function(message) {
        if (this.inlineError) {
            var errorText = this.inlineError.querySelector('.fss-inline-error-text');
            if (errorText) {
                errorText.textContent = message;
            }
            this.inlineError.style.display = 'flex';
            
            // Auto-hide after configured delay
            var self = this;
            setTimeout(function() {
                self.clearInlineError();
            }, FSS_DEFAULTS.errorAutoHideDelay);
        }
    };

    /**
     * Clear inline error message
     */
    SearchInstance.prototype.clearInlineError = function() {
        if (this.inlineError) {
            this.inlineError.style.display = 'none';
        }
    };

    /**
     * Show/hide inline search vs selected farmer display
     * @param {boolean} showSelected - Whether to show selected farmer (true) or inline search (false)
     */
    SearchInstance.prototype.toggleInlineDisplay = function(showSelected) {
        if (this.inlineSearch) {
            this.inlineSearch.style.display = showSelected ? 'none' : 'flex';
        }
        if (this.displayFieldText) {
            this.displayFieldText.style.display = showSelected ? 'flex' : 'none';
        }
        
        // Clear inline input when hiding
        if (!showSelected && this.inlineInput) {
            this.inlineInput.value = '';
            this.updateInlineTypeBadge('');
        }
    };

    // ==========================================================================
    // PHASE 5: CRITERIA BUILDER METHODS
    // ==========================================================================

    /**
     * Toggle the criteria type menu
     */
    SearchInstance.prototype.toggleCriteriaMenu = function() {
        if (this.state.criteriaMenuOpen) {
            this.closeCriteriaMenu();
        } else {
            this.openCriteriaMenu();
        }
    };

    /**
     * Open the criteria type menu
     */
    SearchInstance.prototype.openCriteriaMenu = function() {
        var self = this;
        var menu = this.elements.criteriaMenu;
        
        // Build menu items
        var html = '';
        for (var i = 0; i < this.criteriaTypes.length; i++) {
            var ct = this.criteriaTypes[i];
            
            // Check if already added (only allow one of each type)
            var alreadyAdded = this.state.additionalCriteria.some(function(c) { 
                return c.type === ct.type; 
            });
            
            // Check if village requires district
            var disabled = false;
            var disabledReason = '';
            if (ct.requiresDistrict && !this.state.districtCode) {
                disabled = true;
                disabledReason = ' (requires district)';
            }
            if (alreadyAdded) {
                disabled = true;
                disabledReason = ' (already added)';
            }
            
            html += '<div class="fss-criteria-menu-item' + (disabled ? ' fss-disabled' : '') + '" ';
            html += 'data-type="' + ct.type + '" data-disabled="' + disabled + '">';
            html += '  <i class="fa ' + ct.icon + '"></i>';
            html += '  <span>' + ct.label + disabledReason + '</span>';
            html += '</div>';
        }
        
        menu.innerHTML = html;
        menu.style.display = 'block';
        this.state.criteriaMenuOpen = true;
        
        // Bind click events to menu items
        var items = menu.querySelectorAll('.fss-criteria-menu-item');
        items.forEach(function(item) {
            item.addEventListener('click', function(e) {
                var type = this.getAttribute('data-type');
                var disabled = this.getAttribute('data-disabled') === 'true';
                if (!disabled) {
                    self.addCriteria(type);
                    self.closeCriteriaMenu();
                }
            });
        });
    };

    /**
     * Close the criteria type menu
     */
    SearchInstance.prototype.closeCriteriaMenu = function() {
        if (this.elements.criteriaMenu) {
            this.elements.criteriaMenu.style.display = 'none';
        }
        this.state.criteriaMenuOpen = false;
    };

    /**
     * Add a new criteria row
     */
    SearchInstance.prototype.addCriteria = function(type) {
        var self = this;
        var criteriaType = this.criteriaTypes.find(function(ct) { return ct.type === type; });
        
        if (!criteriaType) {
            console.error('[FarmerSmartSearch] Unknown criteria type:', type);
            return;
        }
        
        // Check if village requires district
        if (criteriaType.requiresDistrict && !this.state.districtCode) {
            console.warn('[FarmerSmartSearch] Cannot add village without district');
            return;
        }
        
        // Generate unique ID
        var criteriaId = ++this.state.criteriaIdCounter;
        
        // Add to state
        var newCriteria = {
            id: criteriaId,
            type: type,
            value: ''
        };
        this.state.additionalCriteria.push(newCriteria);
        
        // Render the row
        this.renderCriteriaRow(newCriteria, criteriaType);
        
        // Update confidence
        this.updateConfidence();
        
        console.log('[FarmerSmartSearch] Added criteria:', type);
    };

    /**
     * Render a single criteria row
     */
    SearchInstance.prototype.renderCriteriaRow = function(criteria, criteriaType) {
        var self = this;
        var row = document.createElement('div');
        row.className = 'fss-criteria-row';
        row.setAttribute('data-id', criteria.id);
        row.setAttribute('data-type', criteria.type);
        
        var html = '';
        html += '<span class="fss-criteria-label">' + criteriaType.label + ':</span>';
        html += '<div class="fss-criteria-input-wrapper">';
        
        if (criteriaType.inputType === 'autocomplete') {
            html += '<input type="text" class="fss-criteria-input" placeholder="Type to search..."';
            html += ' data-criteria-id="' + criteria.id + '" autocomplete="off">';
        } else {
            // Issue #18: Use dynamic minLength from criteria type config
            var minLen = criteriaType.minLength || 4;
            html += '<input type="text" class="fss-criteria-input" placeholder="Enter at least ' + minLen + ' digits..."';
            html += ' data-criteria-id="' + criteria.id + '" pattern="' + (criteriaType.pattern || '') + '">';
            html += '<div class="fss-partial-input-hint">Minimum ' + minLen + ' digits required</div>';
        }
        
        html += '</div>';
        html += '<button type="button" class="fss-remove-criteria" title="Remove"><i class="fa fa-times"></i></button>';
        
        row.innerHTML = html;
        
        // Add to DOM
        this.elements.criteriaRows.appendChild(row);
        
        // Get input element
        var input = row.querySelector('.fss-criteria-input');
        
        // Focus the input
        setTimeout(function() { input.focus(); }, 50);
        
        // Bind events
        if (criteriaType.inputType === 'autocomplete') {
            // Autocomplete input handling
            input.addEventListener('input', function(e) {
                self.handleAutocompleteInput(criteria.id, criteria.type, e.target.value);
            });
            
            input.addEventListener('keydown', function(e) {
                self.handleAutocompleteKeydown(e, criteria.id);
            });
            
            input.addEventListener('focus', function(e) {
                // If there's a value, trigger autocomplete
                if (e.target.value.length >= FSS_DEFAULTS.autocompleteMinChars) {
                    self.handleAutocompleteInput(criteria.id, criteria.type, e.target.value);
                }
            });
        } else {
            // Text input (partial ID/phone)
            input.addEventListener('input', function(e) {
                self.handlePartialInput(criteria.id, criteria.type, e.target.value);
            });
        }
        
        // Remove button
        var removeBtn = row.querySelector('.fss-remove-criteria');
        removeBtn.addEventListener('click', function(e) {
            e.preventDefault();
            self.removeCriteria(criteria.id);
        });
    };

    /**
     * Remove a criteria row
     */
    SearchInstance.prototype.removeCriteria = function(criteriaId) {
        var self = this;
        
        // Find the row
        var row = this.elements.criteriaRows.querySelector('[data-id="' + criteriaId + '"]');
        if (!row) return;
        
        // Add removal animation class
        row.classList.add('fss-removing');
        
        // Remove from DOM after animation
        setTimeout(function() {
            row.remove();
        }, 200);
        
        // Remove from state
        this.state.additionalCriteria = this.state.additionalCriteria.filter(function(c) {
            return c.id !== criteriaId;
        });
        
        // Update confidence
        this.updateConfidence();
        
        console.log('[FarmerSmartSearch] Removed criteria:', criteriaId);
    };

    /**
     * Reset village criteria when district changes
     */
    SearchInstance.prototype.resetVillageCriteria = function() {
        var villageCriteria = this.state.additionalCriteria.find(function(c) { 
            return c.type === 'village'; 
        });
        
        if (villageCriteria) {
            villageCriteria.value = '';
            var row = this.elements.criteriaRows.querySelector('[data-type="village"]');
            if (row) {
                var input = row.querySelector('.fss-criteria-input');
                if (input) {
                    input.value = '';
                }
            }
        }
    };

    /**
     * Handle autocomplete input (debounced)
     */
    SearchInstance.prototype.handleAutocompleteInput = function(criteriaId, type, value) {
        var self = this;
        
        // Update state
        var criteria = this.state.additionalCriteria.find(function(c) { return c.id === criteriaId; });
        if (criteria) {
            criteria.value = value;
        }
        
        // Update confidence immediately
        this.updateConfidence();
        
        // Debounce autocomplete API call
        if (this.autocompleteTimer) {
            clearTimeout(this.autocompleteTimer);
        }
        
        // Minimum characters for autocomplete
        if (value.length < FSS_DEFAULTS.autocompleteMinChars) {
            this.closeAutocomplete();
            return;
        }

        this.autocompleteTimer = setTimeout(function() {
            self.fetchAutocompleteOptions(criteriaId, type, value);
        }, FSS_DEFAULTS.autocompleteDebounce);
    };

    /**
     * Handle partial input (ID/Phone)
     */
    SearchInstance.prototype.handlePartialInput = function(criteriaId, type, value) {
        // Only allow digits
        var digitsOnly = value.replace(/[^\d]/g, '');
        
        // Update input value
        var row = this.elements.criteriaRows.querySelector('[data-id="' + criteriaId + '"]');
        if (row) {
            var input = row.querySelector('.fss-criteria-input');
            if (input && input.value !== digitsOnly) {
                input.value = digitsOnly;
            }
        }
        
        // Update state
        var criteria = this.state.additionalCriteria.find(function(c) { return c.id === criteriaId; });
        if (criteria) {
            criteria.value = digitsOnly;
        }
        
        // Validate minimum length (uses config-aware criteriaTypes)
        var criteriaType = this.criteriaTypes.find(function(ct) { return ct.type === type; });
        var minLength = criteriaType ? criteriaType.minLength : (this.config.nationalIdMinLength || 4);
        
        if (row) {
            var input = row.querySelector('.fss-criteria-input');
            var hint = row.querySelector('.fss-partial-input-hint');
            
            if (digitsOnly.length > 0 && digitsOnly.length < minLength) {
                input.classList.add('fss-has-error');
                if (hint) hint.style.color = '#dc2626';
            } else {
                input.classList.remove('fss-has-error');
                if (hint) hint.style.color = '';
            }
        }
        
        // Update confidence
        this.updateConfidence();
    };

    /**
     * Fetch autocomplete options from API
     */
    SearchInstance.prototype.fetchAutocompleteOptions = function(criteriaId, type, query) {
        var self = this;

        // Phase 6: Check if offline
        if (!this.state.isOnline) {
            console.log('[FarmerSmartSearch] Offline - cannot fetch autocomplete');
            this.closeAutocomplete();
            return;
        }
        
        var endpoint = '';
        var params = [];
        
        switch (type) {
            case 'village':
                endpoint = '/villages';
                if (this.state.districtCode) {
                    params.push('district=' + encodeURIComponent(this.state.districtCode));
                }
                params.push('q=' + encodeURIComponent(query));
                break;
            case 'community_council':
                endpoint = '/community-councils';
                if (this.state.districtCode) {
                    params.push('district=' + encodeURIComponent(this.state.districtCode));
                }
                break;
            case 'cooperative':
                endpoint = '/cooperatives';
                if (this.state.districtCode) {
                    params.push('district=' + encodeURIComponent(this.state.districtCode));
                }
                params.push('q=' + encodeURIComponent(query));
                break;
            default:
                console.warn('[FarmerSmartSearch] Unknown autocomplete type:', type);
                return;
        }
        
        var url = endpoint + (params.length > 0 ? '?' + params.join('&') : '');
        
        // Show loading state
        this.state.autocompleteLoading = true;
        this.state.activeAutocomplete = criteriaId;
        this.showAutocompleteDropdown(criteriaId, [], true);
        
        // Make API call
        this.apiCall('GET', url, null, function(response) {
            self.state.autocompleteLoading = false;
            
            if (response.success) {
                var options = [];
                if (response.villages) options = response.villages;
                if (response.councils) options = response.councils;
                if (response.cooperatives) options = response.cooperatives;
                
                // Filter by query for community councils (no query param)
                if (type === 'community_council' && query) {
                    var lowerQuery = query.toLowerCase();
                    options = options.filter(function(opt) {
                        return opt.name.toLowerCase().indexOf(lowerQuery) !== -1;
                    });
                }
                
                self.state.autocompleteOptions = options;
                self.showAutocompleteDropdown(criteriaId, options, false);
            } else {
                self.closeAutocomplete();
            }
        }, function(error) {
            console.error('[FarmerSmartSearch] Autocomplete error:', error);
            self.state.autocompleteLoading = false;
            self.closeAutocomplete();
        });
    };

    /**
     * Show autocomplete dropdown
     */
    SearchInstance.prototype.showAutocompleteDropdown = function(criteriaId, options, isLoading) {
        var self = this;
        var row = this.elements.criteriaRows.querySelector('[data-id="' + criteriaId + '"]');
        if (!row) return;
        
        var wrapper = row.querySelector('.fss-criteria-input-wrapper');
        
        // Remove existing dropdown
        var existing = wrapper.querySelector('.fss-autocomplete-dropdown');
        if (existing) {
            existing.remove();
        }
        
        // Create dropdown
        var dropdown = document.createElement('div');
        dropdown.className = 'fss-autocomplete-dropdown';
        
        var html = '';
        if (isLoading) {
            html = '<div class="fss-autocomplete-loading"><i class="fa fa-spinner fa-spin"></i> Loading...</div>';
        } else if (options.length === 0) {
            html = '<div class="fss-autocomplete-empty">No results found</div>';
        } else {
            for (var i = 0; i < Math.min(options.length, FSS_DEFAULTS.autocompleteMaxItems); i++) {
                var opt = options[i];
                html += '<div class="fss-autocomplete-item' + (i === this.state.autocompleteActiveIndex ? ' fss-active' : '') + '" data-index="' + i + '">';
                html += '  <span class="fss-autocomplete-item-name">' + this.escapeHtml(opt.name) + '</span>';
                html += '  <span class="fss-autocomplete-item-count">(' + opt.count + ')</span>';
                html += '</div>';
            }
        }
        
        dropdown.innerHTML = html;
        wrapper.appendChild(dropdown);
        
        // Bind click events
        var items = dropdown.querySelectorAll('.fss-autocomplete-item');
        items.forEach(function(item) {
            item.addEventListener('click', function(e) {
                var index = parseInt(this.getAttribute('data-index'), 10);
                self.selectAutocompleteOption(criteriaId, index);
            });
        });
        
        this.state.activeAutocomplete = criteriaId;
        this.state.autocompleteActiveIndex = -1;
    };

    /**
     * Handle autocomplete keyboard navigation
     */
    SearchInstance.prototype.handleAutocompleteKeydown = function(e, criteriaId) {
        if (!this.state.activeAutocomplete || this.state.activeAutocomplete !== criteriaId) {
            return;
        }
        
        var options = this.state.autocompleteOptions;
        if (!options || options.length === 0) return;
        
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            this.state.autocompleteActiveIndex = Math.min(
                this.state.autocompleteActiveIndex + 1, 
                Math.min(options.length - 1, 9)
            );
            this.updateAutocompleteActive(criteriaId);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            this.state.autocompleteActiveIndex = Math.max(this.state.autocompleteActiveIndex - 1, 0);
            this.updateAutocompleteActive(criteriaId);
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (this.state.autocompleteActiveIndex >= 0) {
                this.selectAutocompleteOption(criteriaId, this.state.autocompleteActiveIndex);
            }
        }
    };

    /**
     * Update active autocomplete item
     */
    SearchInstance.prototype.updateAutocompleteActive = function(criteriaId) {
        var row = this.elements.criteriaRows.querySelector('[data-id="' + criteriaId + '"]');
        if (!row) return;
        
        var items = row.querySelectorAll('.fss-autocomplete-item');
        items.forEach(function(item, i) {
            if (i === this.state.autocompleteActiveIndex) {
                item.classList.add('fss-active');
            } else {
                item.classList.remove('fss-active');
            }
        }.bind(this));
    };

    /**
     * Select an autocomplete option
     */
    SearchInstance.prototype.selectAutocompleteOption = function(criteriaId, index) {
        var options = this.state.autocompleteOptions;
        if (!options || index < 0 || index >= options.length) return;
        
        var selected = options[index];
        
        // Update state
        var criteria = this.state.additionalCriteria.find(function(c) { return c.id === criteriaId; });
        if (criteria) {
            criteria.value = selected.name;
        }
        
        // Update input
        var row = this.elements.criteriaRows.querySelector('[data-id="' + criteriaId + '"]');
        if (row) {
            var input = row.querySelector('.fss-criteria-input');
            if (input) {
                input.value = selected.name;
            }
        }
        
        // Close dropdown
        this.closeAutocomplete();
        
        // Update confidence
        this.updateConfidence();
        
        console.log('[FarmerSmartSearch] Selected autocomplete:', selected.name);
    };

    /**
     * Close autocomplete dropdown
     */
    SearchInstance.prototype.closeAutocomplete = function() {
        if (this.state.activeAutocomplete) {
            var row = this.elements.criteriaRows.querySelector('[data-id="' + this.state.activeAutocomplete + '"]');
            if (row) {
                var dropdown = row.querySelector('.fss-autocomplete-dropdown');
                if (dropdown) {
                    dropdown.remove();
                }
            }
        }
        this.state.activeAutocomplete = null;
        this.state.autocompleteOptions = [];
        this.state.autocompleteActiveIndex = -1;
    };

    // ==========================================================================
    // EXISTING METHODS (updated for Phase 6)
    // ==========================================================================

    /**
     * Detect input type (national_id, phone, or name)
     * Uses configurable patterns from plugin settings
     */
    SearchInstance.prototype.detectInputType = function(value) {
        if (!value) return null;

        value = value.trim();

        // Get configurable patterns from config
        var nationalIdMinLength = this.config.nationalIdMinLength || 4;
        var phoneMinLength = this.config.phoneMinLength || 8;
        var nationalIdPatternStr = this.config.nationalIdPattern || '^\\d{9,13}$';
        var phonePatternStr = this.config.phonePattern || '^\\+?\\d{8,}$';

        // Create regex from pattern strings
        var nationalIdRegex, phoneRegex;
        try {
            nationalIdRegex = new RegExp(nationalIdPatternStr);
        } catch (e) {
            console.warn('[FarmerSmartSearch] Invalid nationalIdPattern, using default');
            nationalIdRegex = /^\d{9,13}$/;
        }
        try {
            phoneRegex = new RegExp(phonePatternStr);
        } catch (e) {
            console.warn('[FarmerSmartSearch] Invalid phonePattern, using default');
            phoneRegex = /^\+?\d{8,}$/;
        }

        // Clean value for pattern matching
        var digitsOnly = value.replace(/[-\s]/g, '');
        var phoneClean = value.replace(/[\s\-\(\)]/g, '');
        var isNumeric = /^\d+$/.test(digitsOnly);

        // First, check if it matches the exact national ID pattern
        if (nationalIdRegex.test(digitsOnly)) {
            return 'national_id';
        }

        // Check if it matches phone pattern
        if (phoneRegex.test(phoneClean)) {
            return 'phone';
        }

        // If numeric and meets minimum length, classify based on length
        if (isNumeric && digitsOnly.length >= nationalIdMinLength) {
            // If also meets phone min length, need to disambiguate
            if (digitsOnly.length >= phoneMinLength) {
                // If starts with + or common country codes, treat as phone
                if (/^\+/.test(value) || /^(266|27)/.test(digitsOnly)) {
                    return 'phone';
                }
            }
            // Default to national_id for numeric input meeting min length
            return 'national_id';
        }

        // If numeric but too short for ID, check if could be phone with prefix
        if (/^\+/.test(value) && phoneClean.length >= phoneMinLength) {
            return 'phone';
        }

        // Otherwise it's a name search (minimum chars from FSS_DEFAULTS)
        if (value.length >= FSS_DEFAULTS.nameMinLength) {
            return 'name';
        }

        return null;
    };

    /**
     * Update the input type badge
     */
    SearchInstance.prototype.updateInputTypeBadge = function() {
        var badge = this.elements.inputTypeBadge;
        if (!badge) return;
        
        var type = this.detectInputType(this.state.searchText);

        if (!type) {
            badge.textContent = '';
            badge.className = 'fss-input-type-badge';
            return;
        }

        var labels = {
            'national_id': 'ID',
            'phone': 'Phone',
            'name': 'Name'
        };

        badge.textContent = labels[type];
        badge.className = 'fss-input-type-badge fss-type-' + type.replace('_', '-');
    };

    /**
     * Build search criteria object from current state
     * Updated for Phase 5 to include additional criteria
     */
    SearchInstance.prototype.buildCriteria = function() {
        var self = this;
        var inputType = this.detectInputType(this.state.searchText);
        var criteria = {};

        if (inputType === 'national_id') {
            criteria.nationalId = this.state.searchText.replace(/[-\s]/g, '');
        } else if (inputType === 'phone') {
            criteria.phone = this.state.searchText.replace(/[\s\-\(\)]/g, '');
        } else if (inputType === 'name') {
            criteria.name = this.state.searchText;
        }

        if (this.state.districtCode) {
            criteria.districtCode = this.state.districtCode;
            // Also send district name for flexible matching
            var districtCode = this.state.districtCode;
            var districtInfo = DISTRICTS.find(function(d) { return d.code === districtCode; });
            if (districtInfo) {
                criteria.districtName = districtInfo.name;
            }
        }

        // Phase 5: Add additional criteria
        for (var i = 0; i < this.state.additionalCriteria.length; i++) {
            var ac = this.state.additionalCriteria[i];
            if (ac.value && ac.value.trim()) {
                switch (ac.type) {
                    case 'village':
                        criteria.village = ac.value.trim();
                        break;
                    case 'community_council':
                        criteria.communityCouncil = ac.value.trim();
                        break;
                    case 'cooperative':
                        criteria.cooperative = ac.value.trim();
                        break;
                    case 'partial_id':
                        if (ac.value.length >= 4) {
                            criteria.partialId = ac.value;
                        }
                        break;
                    case 'partial_phone':
                        if (ac.value.length >= 4) {
                            criteria.partialPhone = ac.value;
                        }
                        break;
                }
            }
        }

        return criteria;
    };

    /**
     * Update confidence display and search button state (Phase 4, updated Phase 6)
     */
    SearchInstance.prototype.updateConfidence = function() {
        // Phase 6: If offline, disable search
        if (!this.state.isOnline) {
            if (this.elements.doSearchBtn) {
                this.elements.doSearchBtn.disabled = true;
            }
            return;
        }

        // Build current criteria
        var criteria = this.buildCriteria();

        // Calculate confidence
        var confidence = 0;
        var validation = { valid: false, type: 'rejected', message: 'Enter search criteria', canSearch: false };

        if (this.confidenceEngine) {
            confidence = this.confidenceEngine.calculate(criteria);
            validation = this.confidenceEngine.validateCriteria(criteria);
        } else {
            // Fallback without confidence engine
            var inputType = this.detectInputType(this.state.searchText);
            if (inputType === 'national_id' || inputType === 'phone') {
                confidence = 100;
                validation = { valid: true, type: 'exact_match', message: '✓ Ready to search', canSearch: true };
            } else if (inputType === 'name') {
                if (criteria.village) {
                    confidence = 85;
                    validation = { valid: true, type: 'acceptable', message: '✓ Ready to search', canSearch: true };
                } else if (this.state.districtCode) {
                    confidence = 50;
                    validation = { valid: true, type: 'warning', message: '⚠ Results may be broad', canSearch: true };
                } else {
                    confidence = 20;
                    validation = { valid: false, type: 'rejected', message: '⚠ Please add district', canSearch: false };
                }
            }
        }

        this.state.confidence = confidence;

        // Update UI
        this.updateConfidenceDisplay(confidence, validation);
        this.updateSearchButtonState(validation.canSearch);
    };

    /**
     * Update confidence bar and text display
     */
    SearchInstance.prototype.updateConfidenceDisplay = function(confidence, validation) {
        if (!this.elements.confidenceBar || !this.elements.confidencePercent || !this.elements.confidenceText) {
            return;
        }

        // Update bar width
        this.elements.confidenceBar.style.width = confidence + '%';

        // Update bar color based on level
        var level = 'low';
        if (confidence >= 70) level = 'high';
        else if (confidence >= 40) level = 'medium';

        // Remove old level classes and add new
        this.elements.confidenceBar.className = 'fss-confidence-bar fss-confidence-' + level;
        this.elements.confidenceArea.className = 'fss-confidence-area fss-confidence-' + level;

        // Update percent text
        this.elements.confidencePercent.textContent = confidence + '%';

        // Update confidence text
        this.elements.confidenceText.textContent = this.confidenceEngine 
            ? this.confidenceEngine.getConfidenceText(confidence)
            : (confidence >= 70 ? 'Ready to search' : confidence >= 40 ? 'Add more criteria for better results' : 'Please add criteria');

        // Update validation message
        if (this.elements.validationMessage) {
            if (validation.type === 'warning' || validation.type === 'rejected') {
                this.elements.validationMessage.textContent = validation.message;
                this.elements.validationMessage.style.display = 'block';
                this.elements.validationMessage.className = 'fss-validation-message fss-validation-' + validation.type;
            } else if (validation.type === 'exact_match') {
                this.elements.validationMessage.textContent = validation.message;
                this.elements.validationMessage.style.display = 'block';
                this.elements.validationMessage.className = 'fss-validation-message fss-validation-success';
            } else {
                this.elements.validationMessage.textContent = validation.message;
                this.elements.validationMessage.style.display = 'block';
                this.elements.validationMessage.className = 'fss-validation-message fss-validation-success';
            }
        }
    };

    /**
     * Update search button enabled state
     */
    SearchInstance.prototype.updateSearchButtonState = function(canSearch) {
        if (!this.elements.doSearchBtn) return;
        // Phase 6: Also check online status
        this.elements.doSearchBtn.disabled = !canSearch || !this.state.isOnline;
    };

    /**
     * Show the search dialog
     */
    SearchInstance.prototype.show = function() {
        console.log('[FarmerSmartSearch] show() called');
        
        // Phase 7: Save current focus for restoration
        this.saveFocusAndTrap();
        
        this.state.isOpen = true;
        this.container.style.display = 'block';

        if (this.config.displayMode === 'popup') {
            document.body.style.overflow = 'hidden';
        }

        // Phase 6: Update connection status and render recent farmers (if enabled)
        this.updateConnectionStatusIndicator();
        if (this.config.showRecentFarmers !== false) {
            this.renderRecentFarmersPanel();
        }
        
        // Phase 6: Show/hide offline warning
        if (!this.state.isOnline) {
            this.showOfflineWarning();
        } else {
            this.hideOfflineWarning();
        }

        // Update confidence display when opening
        this.updateConfidence();
        
        // Phase 7: Reset error state when opening
        this.state.retryCount = 0;
        this.state.lastError = null;
    };

    /**
     * Hide the search dialog
     */
    SearchInstance.prototype.hide = function() {
        console.log('[FarmerSmartSearch] hide() called');
        
        if (this.config.displayMode !== 'popup') return;

        this.state.isOpen = false;
        this.container.style.display = 'none';
        document.body.style.overflow = '';
        
        // Close any open menus/dropdowns
        this.closeCriteriaMenu();
        this.closeAutocomplete();
        
        // Phase 7: Restore focus to previously focused element
        this.restoreFocus();
    };

    /**
     * Execute search
     */
    SearchInstance.prototype.executeSearch = function() {
        var self = this;

        // Phase 6: Check if online
        if (!this.state.isOnline) {
            this.handleError('ERR_NETWORK', 'Offline', { operation: 'search' });
            return;
        }

        var criteria = this.buildCriteria();
        var inputType = this.detectInputType(this.state.searchText);

        console.log('[FarmerSmartSearch] executeSearch() - criteria:', criteria, 'inputType:', inputType);

        // Build request data (Issue #9: uses SEARCH_RESULT_LIMIT constant)
        var requestData = {
            criteria: criteria,
            limit: SEARCH_RESULT_LIMIT
        };

        // Phase 7: Store request for retry
        this.state.lastSearchRequest = requestData;

        console.log('[FarmerSmartSearch] Search request:', requestData);

        // Show loading
        this.showLoading();

        // Make API call with Phase 7 enhanced error handling
        this.apiCall('POST', '/search', requestData, function(response) {
            console.log('[FarmerSmartSearch] Search response:', response);
            self.hideLoading();

            if (response.success) {
                // Phase 7: Reset retry count on success
                self.state.retryCount = 0;
                self.state.lastError = null;
                
                self.state.results = response.farmers || [];
                
                // Check for too many results (Issue #8: uses MAX_RESULTS_WARNING constant)
                if (response.totalCount > MAX_RESULTS_WARNING) {
                    self.handleError('ERR_TOO_MANY', 'Too many results: ' + response.totalCount, { count: response.totalCount });
                    return;
                }
                
                // Check for no results
                if (!response.farmers || response.farmers.length === 0) {
                    self.handleError('ERR_NO_RESULTS', 'No results found', { criteria: criteria });
                    return;
                }
                
                self.renderResults(response);
            } else {
                // Map API error to error code
                var errorCode = self.mapApiErrorCode(response.error);
                self.handleError(errorCode, response.error, { response: response });
            }
        }, function(error, status, errorType) {
            console.error('[FarmerSmartSearch] Search error:', error, status, errorType);
            self.hideLoading();
            
            // Phase 7: Map error to appropriate code
            var errorCode = self.mapErrorCode(status, errorType);
            self.handleError(errorCode, error, { status: status, errorType: errorType });
        });
    };

    /**
     * Map API error message to error code
     * @param {string} errorMessage - Error message from API
     * @returns {string} Error code
     */
    SearchInstance.prototype.mapApiErrorCode = function(errorMessage) {
        if (!errorMessage) return 'ERR_UNKNOWN';
        
        var msg = errorMessage.toLowerCase();
        
        if (msg.indexOf('not found') !== -1 || msg.indexOf('no results') !== -1) {
            return 'ERR_NO_RESULTS';
        }
        if (msg.indexOf('too many') !== -1 || msg.indexOf('limit exceeded') !== -1) {
            return 'ERR_TOO_MANY';
        }
        if (msg.indexOf('invalid') !== -1 || msg.indexOf('criteria') !== -1) {
            return 'ERR_INVALID_CRITERIA';
        }
        if (msg.indexOf('auth') !== -1 || msg.indexOf('permission') !== -1 || msg.indexOf('unauthorized') !== -1) {
            return 'ERR_AUTH';
        }
        
        return 'ERR_SERVER';
    };

    /**
     * Make API call with timeout and enhanced error handling
     * @param {string} method - HTTP method
     * @param {string} endpoint - API endpoint path
     * @param {Object} data - Request data
     * @param {Function} onSuccess - Success callback(response)
     * @param {Function} onError - Error callback(message, status, errorType)
     */
    SearchInstance.prototype.apiCall = function(method, endpoint, data, onSuccess, onError) {
        var self = this;
        var xhr = new XMLHttpRequest();
        var url = this.config.apiEndpoint + endpoint;
        var timedOut = false;

        console.log('[FarmerSmartSearch] API call:', method, url);

        xhr.open(method, url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');

        // API authentication headers
        if (this.config.apiId) {
            xhr.setRequestHeader('api_id', this.config.apiId);
        }
        if (this.config.apiKey) {
            xhr.setRequestHeader('api_key', this.config.apiKey);
        }

        // Phase 7: Set timeout (uses FSS_DEFAULTS)
        xhr.timeout = FSS_DEFAULTS.apiTimeout;

        xhr.onload = function() {
            if (timedOut) return;
            
            console.log('[FarmerSmartSearch] API response status:', xhr.status);
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    var response = JSON.parse(xhr.responseText);
                    onSuccess(response);
                } catch (e) {
                    console.error('[FarmerSmartSearch] JSON parse error:', e);
                    onError('Invalid response format', xhr.status, 'parse');
                }
            } else {
                try {
                    var errResp = JSON.parse(xhr.responseText);
                    onError(errResp.error || 'Request failed: ' + xhr.status, xhr.status, 'http');
                } catch (e) {
                    onError('Request failed: ' + xhr.status, xhr.status, 'http');
                }
            }
        };

        xhr.onerror = function() {
            if (timedOut) return;
            console.error('[FarmerSmartSearch] Network error');
            onError('Network error - please check your connection', 0, 'network');
        };

        // Phase 7: Timeout handler
        xhr.ontimeout = function() {
            timedOut = true;
            console.error('[FarmerSmartSearch] Request timeout');
            onError('Request timed out', 0, 'timeout');
        };

        xhr.send(data ? JSON.stringify(data) : null);
    };

    /**
     * Show auto-select notification
     */
    SearchInstance.prototype.showAutoSelectNotification = function(farmer) {
        if (!this.config.showAutoSelectNotification) return;

        // Create notification element
        var notification = document.createElement('div');
        notification.className = 'fss-auto-select-notification';
        notification.innerHTML = '<i class="fa fa-check-circle"></i> <span>Farmer found and selected: <strong>' +
            this.escapeHtml(farmer.firstName + ' ' + farmer.lastName) + '</strong></span>';

        // Add to container or body
        var container = this.container.querySelector('.fss-dialog') || this.container;
        container.appendChild(notification);

        // Trigger animation
        setTimeout(function() {
            notification.classList.add('fss-notification-show');
        }, 10);

        // Remove after delay
        setTimeout(function() {
            notification.classList.add('fss-notification-hide');
            setTimeout(function() {
                notification.remove();
            }, 300);
        }, 2000);
    };

    /**
     * Check if auto-select should be triggered
     */
    SearchInstance.prototype.shouldAutoSelect = function(response) {
        console.log('[FarmerSmartSearch] Checking auto-select conditions...');
        console.log('[FarmerSmartSearch] - autoSelectSingleResult config:', this.config.autoSelectSingleResult);
        console.log('[FarmerSmartSearch] - autoSelectMinScore config:', this.config.autoSelectMinScore);
        console.log('[FarmerSmartSearch] - farmers count:', response.farmers ? response.farmers.length : 0);

        // Must be enabled
        if (!this.config.autoSelectSingleResult) {
            console.log('[FarmerSmartSearch] Auto-select disabled in config');
            return false;
        }

        // Must have exactly one result
        if (!response.farmers || response.farmers.length !== 1) {
            console.log('[FarmerSmartSearch] Auto-select skipped: not exactly 1 result');
            return false;
        }

        // Must meet minimum score
        var farmer = response.farmers[0];
        var score = farmer.relevanceScore || 0;
        var minScore = this.config.autoSelectMinScore || 90;

        console.log('[FarmerSmartSearch] - farmer score:', score, '/ minScore:', minScore);

        if (score < minScore) {
            console.log('[FarmerSmartSearch] Auto-select skipped: score ' + score + ' < ' + minScore);
            return false;
        }

        console.log('[FarmerSmartSearch] Auto-select conditions met!');
        return true;
    };

    /**
     * Render search results
     */
    SearchInstance.prototype.renderResults = function(response) {
        var self = this;
        var panel = this.elements.resultsPanel;

        // Hide other panels
        this.elements.emptyState.style.display = 'none';
        this.elements.errorPanel.style.display = 'none';
        panel.style.display = 'block';

        if (!response.farmers || response.farmers.length === 0) {
            panel.innerHTML = this.buildNoResultsHtml(response);
            return;
        }

        // Check for auto-select
        if (this.shouldAutoSelect(response)) {
            var farmer = response.farmers[0];
            console.log('[FarmerSmartSearch] Auto-selecting single result:', farmer);

            // Show notification before selecting (so user sees it before dialog closes)
            this.showAutoSelectNotification(farmer);

            // Delay selection slightly so user can see notification
            var selectDelay = this.config.showAutoSelectNotification ? 500 : 0;
            setTimeout(function() {
                self.selectFarmer(farmer);
            }, selectDelay);

            return;
        }

        var html = '<div class="fss-results-header">';
        html += '  <span class="fss-results-count">' + response.totalCount + ' farmer(s) found</span>';
        html += '  <span class="fss-search-time">' + response.searchTime + 'ms</span>';
        html += '</div>';

        html += '<div class="fss-results-list">';
        for (var i = 0; i < response.farmers.length; i++) {
            html += this.buildFarmerCardHtml(response.farmers[i]);
        }
        html += '</div>';

        panel.innerHTML = html;

        // Bind click events to farmer cards
        var cards = panel.querySelectorAll('.fss-farmer-card');
        cards.forEach(function(card) {
            card.addEventListener('click', function() {
                var farmerId = this.getAttribute('data-id');
                var farmer = self.state.results.find(function(f) { return f.id === farmerId; });
                if (farmer) {
                    self.selectFarmer(farmer);
                }
            });
        });
        
        // Phase 7: Update ARIA attributes for accessibility
        this.updateResultsAria();
    };

    /**
     * Build no results HTML
     */
    SearchInstance.prototype.buildNoResultsHtml = function(response) {
        var html = '<div class="fss-no-results">';
        html += '  <i class="fa fa-search"></i>';
        html += '  <p>No farmers found matching your criteria</p>';

        if (response.suggestions && response.suggestions.length > 0) {
            html += '  <div class="fss-suggestions">';
            html += '    <p><strong>Suggestions:</strong></p>';
            html += '    <ul>';
            for (var i = 0; i < response.suggestions.length; i++) {
                html += '      <li>' + this.escapeHtml(response.suggestions[i]) + '</li>';
            }
            html += '    </ul>';
            html += '  </div>';
        }

        html += '</div>';
        return html;
    };

    /**
     * Build farmer card HTML
     */
    SearchInstance.prototype.buildFarmerCardHtml = function(farmer) {
        var score = farmer.relevanceScore || 0;
        var scoreClass = this.getScoreClass(score);
        var genderIcon = farmer.gender === 'Female' ? 'fa-female' : 'fa-male';
        var genderClass = farmer.gender === 'Female' ? 'fss-gender-female' : 'fss-gender-male';

        var html = '<div class="fss-farmer-card" data-id="' + farmer.id + '">';
        
        // Left section - name and icon
        html += '  <div class="fss-farmer-main">';
        html += '    <span class="fss-farmer-icon ' + genderClass + '"><i class="fa ' + genderIcon + '"></i></span>';
        html += '    <div class="fss-farmer-info">';
        html += '      <span class="fss-farmer-name">' + this.escapeHtml(farmer.firstName || '') + ' ' + this.escapeHtml(farmer.lastName || '') + '</span>';
        html += '      <span class="fss-farmer-location">' + this.escapeHtml(farmer.districtName || farmer.districtCode || '') + ' &gt; ' + this.escapeHtml(farmer.village || '') + '</span>';
        html += '    </div>';
        html += '  </div>';

        // Right section - ID and score
        html += '  <div class="fss-farmer-meta">';
        html += '    <span class="fss-farmer-id">ID: ' + this.escapeHtml(farmer.nationalIdMasked || farmer.nationalId || '—') + '</span>';
        html += '    <span class="fss-farmer-score ' + scoreClass + '">' + score + '%</span>';
        html += '  </div>';

        html += '</div>';
        return html;
    };

    /**
     * Get CSS class for score (Issue #7: uses SCORE_THRESHOLDS constant)
     */
    SearchInstance.prototype.getScoreClass = function(score) {
        if (score >= SCORE_THRESHOLDS.high) return 'fss-score-high';
        if (score >= SCORE_THRESHOLDS.medium) return 'fss-score-medium';
        if (score >= SCORE_THRESHOLDS.low) return 'fss-score-low';
        return 'fss-score-verylow';
    };

    /**
     * Show loading state
     */
    SearchInstance.prototype.showLoading = function() {
        this.state.isLoading = true;
        this.elements.emptyState.style.display = 'none';
        this.elements.resultsPanel.style.display = 'none';
        this.elements.errorPanel.style.display = 'none';
        this.elements.loadingPanel.style.display = 'flex';
        this.elements.doSearchBtn.disabled = true;
    };

    /**
     * Hide loading state
     */
    SearchInstance.prototype.hideLoading = function() {
        this.state.isLoading = false;
        this.elements.loadingPanel.style.display = 'none';
        this.updateConfidence(); // Restore search button state based on criteria
    };

    /**
     * Show error message
     */
    SearchInstance.prototype.showError = function(message) {
        this.state.error = message;
        this.elements.emptyState.style.display = 'none';
        this.elements.resultsPanel.style.display = 'none';
        this.elements.loadingPanel.style.display = 'none';

        var html = '<div class="fss-error">';
        html += '  <i class="fa fa-exclamation-triangle"></i>';
        html += '  <span>' + this.escapeHtml(message) + '</span>';
        html += '</div>';

        this.elements.errorPanel.innerHTML = html;
        this.elements.errorPanel.style.display = 'block';
    };

    /**
     * Select a farmer
     */
    SearchInstance.prototype.selectFarmer = function(farmer) {
        console.log('[FarmerSmartSearch] selectFarmer called');
        console.log('[FarmerSmartSearch] - farmer object:', JSON.stringify(farmer));
        console.log('[FarmerSmartSearch] - storeValue config:', this.config.storeValue);
        console.log('[FarmerSmartSearch] - hiddenFieldId config:', this.config.hiddenFieldId);

        this.state.selectedFarmer = farmer;

        // Determine which value to store based on config
        var storeValue = this.config.storeValue || 'nationalId';
        var valueToStore = farmer[storeValue] || farmer.nationalId || farmer.id;
        console.log('[FarmerSmartSearch] - valueToStore:', valueToStore);

        // Update hidden field
        if (this.config.hiddenFieldId) {
            var hiddenField = document.getElementById(this.config.hiddenFieldId);
            console.log('[FarmerSmartSearch] - hiddenField element found:', !!hiddenField);

            if (hiddenField) {
                var oldValue = hiddenField.value;
                hiddenField.value = valueToStore;
                console.log('[FarmerSmartSearch] - Updated value: "' + oldValue + '" -> "' + valueToStore + '"');
                console.log('[FarmerSmartSearch] - Hidden field name attr:', hiddenField.name);

                // Trigger change events for Joget form handling
                var nativeEvent = new Event('change', { bubbles: true });
                hiddenField.dispatchEvent(nativeEvent);

                // jQuery event if available
                if (typeof jQuery !== 'undefined') {
                    jQuery(hiddenField).trigger('change');
                }
                console.log('[FarmerSmartSearch] - Change events dispatched');
            } else {
                console.error('[FarmerSmartSearch] Hidden field NOT FOUND with id:', this.config.hiddenFieldId);
            }
        } else {
            console.warn('[FarmerSmartSearch] No hiddenFieldId configured!');
        }

        // Update display field
        this.updateDisplayField(farmer);

        // Phase 6: Add to recent farmers
        this.addToRecentFarmers(farmer);

        // Callback
        if (typeof this.config.onSelect === 'function') {
            this.config.onSelect(farmer);
        }

        // Hide popup
        if (this.config.displayMode === 'popup') {
            this.hide();
        }
    };

    /**
     * Clear farmer selection
     */
    SearchInstance.prototype.clearSelection = function() {
        console.log('[FarmerSmartSearch] clearSelection');
        
        this.state.selectedFarmer = null;

        // Update hidden field
        if (this.config.hiddenFieldId) {
            var hiddenField = document.getElementById(this.config.hiddenFieldId);
            if (hiddenField) {
                hiddenField.value = '';

                var nativeEvent = new Event('change', { bubbles: true });
                hiddenField.dispatchEvent(nativeEvent);

                if (typeof jQuery !== 'undefined') {
                    jQuery(hiddenField).trigger('change');
                }
            }
        }

        // Phase 7: Toggle to show inline input instead of selected display
        this.toggleInlineDisplay(false);

        // Update display field (fallback if no inline input)
        if (this.displayFieldText && !this.inlineSearch) {
            this.displayFieldText.innerHTML = '<span class="fss-placeholder-text">No farmer selected</span>';
        }

        if (this.clearBtn) {
            this.clearBtn.style.display = 'none';
        }

        // Callback
        if (typeof this.config.onClear === 'function') {
            this.config.onClear();
        }
    };

    /**
     * Update the display field with selected farmer info
     */
    SearchInstance.prototype.updateDisplayField = function(farmer) {
        // Phase 7: Toggle to show selected display instead of inline input
        this.toggleInlineDisplay(true);
        
        if (!this.displayFieldText) return;

        var displayText = farmer.firstName + ' ' + farmer.lastName;
        var displayId = farmer.nationalIdMasked || farmer.nationalId;
        if (displayId) {
            displayText += ' (ID: ' + displayId + ')';
        }

        this.displayFieldText.innerHTML = '<span class="fss-farmer-icon">👤</span><span class="fss-farmer-name">' + this.escapeHtml(displayText) + '</span>';

        if (this.clearBtn) {
            this.clearBtn.style.display = 'inline-flex';
        }
    };

    /**
     * Load farmer by ID (for initial value)
     * This handles loading existing farmer data when the form has an initial value.
     * 
     * Strategy:
     * 1. Check recent farmers list first (works offline)
     * 2. Try search by nationalId (more reliable than index lookup)
     * 3. Fall back to index lookup
     * 4. Show ID as fallback if all fail
     */
    SearchInstance.prototype.loadFarmerById = function(farmerId) {
        var self = this;

        console.log('[FarmerSmartSearch] loadFarmerById:', farmerId);

        // Helper function to show fallback display
        function showFallbackDisplay() {
            console.log('[FarmerSmartSearch] Showing fallback display for:', farmerId);
            if (self.displayFieldText) {
                self.displayFieldText.innerHTML = '<span class="fss-farmer-name">ID: ' + self.escapeHtml(farmerId) + '</span>';
            }
            // Show clear button since there is a value
            if (self.clearBtn) {
                self.clearBtn.style.display = 'inline-flex';
            }
        }

        // Helper function to display found farmer
        function displayFarmer(farmer) {
            console.log('[FarmerSmartSearch] Displaying farmer:', farmer);
            self.state.selectedFarmer = farmer;
            self.updateDisplayField(farmer);
        }

        // Phase 6: Try to find in recent farmers first (works both online and offline)
        if (this.recentFarmersManager) {
            // Check by ID first
            var recentFarmer = this.recentFarmersManager.get(farmerId);
            
            // Also try to find by nationalId match
            if (!recentFarmer) {
                var allRecent = this.recentFarmersManager.getAll();
                for (var i = 0; i < allRecent.length; i++) {
                    // Check for exact nationalId match (ignoring masking)
                    var storedId = allRecent[i].nationalId || '';
                    if (storedId === farmerId || 
                        storedId.replace(/\*/g, '').indexOf(farmerId.replace(/\*/g, '')) !== -1 ||
                        farmerId.replace(/\*/g, '').indexOf(storedId.replace(/\*/g, '')) !== -1) {
                        recentFarmer = allRecent[i];
                        break;
                    }
                }
            }
            
            if (recentFarmer) {
                console.log('[FarmerSmartSearch] Found farmer in recent list:', recentFarmer);
                displayFarmer(recentFarmer);
                return;
            }
        }

        // If offline and not in recent, show fallback
        if (!this.state.isOnline) {
            console.log('[FarmerSmartSearch] Offline and farmer not in recent list');
            showFallbackDisplay();
            return;
        }

        // Try search by nationalId first (more reliable than index lookup)
        // The stored value is typically the nationalId
        var searchCriteria = { nationalId: farmerId };
        var searchRequest = { criteria: searchCriteria, limit: 1 };

        console.log('[FarmerSmartSearch] Searching by nationalId:', farmerId);
        this.apiCall('POST', '/search', searchRequest, function(response) {
            console.log('[FarmerSmartSearch] Search response:', response);
            if (response.success && response.farmers && response.farmers.length > 0) {
                // Found farmer via search
                displayFarmer(response.farmers[0]);
            } else {
                // Search didn't find anything, try index lookup as fallback
                console.log('[FarmerSmartSearch] Search found nothing, trying lookup...');
                self.apiCall('GET', '/lookup/' + encodeURIComponent(farmerId), null, function(lookupResponse) {
                    console.log('[FarmerSmartSearch] Lookup response:', lookupResponse);
                    if (lookupResponse.success && lookupResponse.farmer) {
                        displayFarmer(lookupResponse.farmer);
                    } else {
                        // Both search and lookup failed
                        console.warn('[FarmerSmartSearch] Both search and lookup failed');
                        showFallbackDisplay();
                    }
                }, function(error) {
                    console.warn('[FarmerSmartSearch] Lookup failed:', error);
                    showFallbackDisplay();
                });
            }
        }, function(error) {
            console.warn('[FarmerSmartSearch] Search failed:', error);
            // Try lookup as fallback
            self.apiCall('GET', '/lookup/' + encodeURIComponent(farmerId), null, function(lookupResponse) {
                if (lookupResponse.success && lookupResponse.farmer) {
                    displayFarmer(lookupResponse.farmer);
                } else {
                    showFallbackDisplay();
                }
            }, function() {
                showFallbackDisplay();
            });
        });
    };

    /**
     * Escape HTML for safe display
     */
    SearchInstance.prototype.escapeHtml = function(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    };

    /**
     * Get currently selected farmer
     */
    SearchInstance.prototype.getSelectedFarmer = function() {
        return this.state.selectedFarmer;
    };

    /**
     * Get current state
     */
    SearchInstance.prototype.getState = function() {
        return this.state;
    };

    /**
     * Get confidence score
     */
    SearchInstance.prototype.getConfidence = function() {
        return this.state.confidence;
    };

    /**
     * Check if statistics are loaded
     */
    SearchInstance.prototype.hasStatistics = function() {
        return this.state.statisticsLoaded;
    };

    /**
     * Get additional criteria (Phase 5)
     */
    SearchInstance.prototype.getAdditionalCriteria = function() {
        return this.state.additionalCriteria;
    };

    /**
     * Clear all additional criteria (Phase 5)
     */
    SearchInstance.prototype.clearAdditionalCriteria = function() {
        var self = this;
        this.state.additionalCriteria.forEach(function(c) {
            self.removeCriteria(c.id);
        });
    };

    /**
     * Check if online (Phase 6)
     */
    SearchInstance.prototype.isOnline = function() {
        return this.state.isOnline;
    };

    /**
     * Get recent farmers (Phase 6)
     */
    SearchInstance.prototype.getRecentFarmers = function() {
        return this.state.recentFarmers;
    };

    // Expose to global scope
    global.FarmerSmartSearch = FarmerSmartSearch;

    console.log('[FarmerSmartSearch] Loaded successfully');

})(typeof window !== 'undefined' ? window : this);
