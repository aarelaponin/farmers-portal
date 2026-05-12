<#-- Smart Farmer Search Form Element Template -->
<#-- Phase 7: Inline Quick Entry -->
<div class="form-cell fss-form-cell" ${elementMetaData!}>
    <label class="label">
        ${element.properties.label!}
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>
    <div class="form-cell-value">
        <#-- Hidden field for form submission (stores farmer ID) -->
        <input type="hidden" 
               id="${fieldId!}" 
               name="${elementParamName!}" 
               value="${value!?html}"
               class="fss-hidden-field">

        <#-- Display field with inline quick entry -->
        <div id="${elementId!}_display" class="fss-display-field">
            <#-- Inline quick entry (shown when no farmer selected) -->
            <div class="fss-inline-search" <#if value?? && value != "">style="display:none;"</#if>>
                <input type="text" 
                       class="fss-inline-input" 
                       placeholder="Enter ID, phone, or name..."
                       autocomplete="off">
                <span class="fss-inline-type-badge"></span>
                <div class="fss-inline-loading" style="display:none;">
                    <i class="fa fa-spinner fa-spin"></i>
                </div>
                <div class="fss-inline-error" style="display:none;">
                    <i class="fa fa-exclamation-circle"></i>
                    <span class="fss-inline-error-text"></span>
                </div>
            </div>
            
            <#-- Selected farmer display (shown when farmer selected) -->
            <div class="fss-selected-display" <#if !(value?? && value != "")>style="display:none;"</#if>>
                <span class="fss-farmer-icon"><i class="fa fa-user"></i></span>
                <span class="fss-farmer-info">
                    <#if value?? && value != "">
                        <span class="fss-farmer-name">Loading...</span>
                    </#if>
                </span>
            </div>
            
            <#-- Search button (opens full popup for advanced search) -->
            <button type="button" class="fss-search-btn" title="Advanced search with filters">
                <i class="fa fa-search"></i>
                <span>Search</span>
            </button>
            
            <#-- Clear button -->
            <button type="button" class="fss-clear-btn" <#if !(value?? && value != "")>style="display:none;"</#if> title="Clear selection">
                <i class="fa fa-times"></i>
            </button>
        </div>

        <#-- Search container (popup or inline based on displayMode) -->
        <div id="${elementId!}" 
             class="fss-search-container fss-mode-${displayMode!}" 
             style="display:none;"
             data-mode="${displayMode!}"
             data-api-endpoint="${apiEndpoint!}"
             data-api-id="${apiId!}"
             data-api-key="${apiKey!}">
            <#-- UI will be built by JavaScript -->
        </div>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Cache bust version - change to force reload -->
<#assign fssCacheVersion = "20260127_retry_fix">

<#-- Load CSS (only once per page) -->
<#if !request.getAttribute("fss_css_loaded")??>
    ${request.setAttribute("fss_css_loaded", true)!}
    <link rel="stylesheet" href="${resourceBase!}smart-search.css&v=${fssCacheVersion}" data-fss-css="true">
</#if>

<#-- Load JS and initialize -->
<script>
(function() {
    // Unique instance ID for this element
    var instanceId = '${elementId!}';
    var fieldId = '${fieldId!}';

    // Prevent duplicate initialization
    if (window['FSSInit_' + instanceId]) {
        console.log('[FSS] Already initialized: ' + instanceId);
        return;
    }
    window['FSSInit_' + instanceId] = true;

    // Debug mode
    var DEBUG = true;
    function log(msg, data) {
        if (DEBUG) {
            if (data !== undefined) {
                console.log('[FSS ' + instanceId + '] ' + msg, data);
            } else {
                console.log('[FSS ' + instanceId + '] ' + msg);
            }
        }
    }

    log('FTL script starting...');
    log('instanceId: ' + instanceId);
    log('fieldId: ' + fieldId);

    /**
     * Load a script by URL, waiting for a specific global to be defined
     */
    function loadScript(src, globalName, callback) {
        var fileParam = src.match(/[?&]file=([^&]+)/);
        var filename = fileParam ? fileParam[1] : src;

        log('loadScript called for: ' + filename + ', waiting for: ' + globalName);

        // Check if already loaded
        if (typeof window[globalName] !== 'undefined') {
            log(globalName + ' already available, skipping load');
            callback();
            return;
        }

        // Check if script tag already exists
        var existing = document.querySelector('script[data-fss-src="' + src + '"]');
        if (existing) {
            log('Script tag already exists: ' + filename + ', waiting for ' + globalName);
            var checkInterval = setInterval(function() {
                if (typeof window[globalName] !== 'undefined') {
                    log(globalName + ' now available');
                    clearInterval(checkInterval);
                    callback();
                }
            }, 50);
            setTimeout(function() {
                clearInterval(checkInterval);
                if (typeof window[globalName] === 'undefined') {
                    console.error('[FSS] Timeout waiting for ' + globalName);
                    callback();
                }
            }, 5000);
            return;
        }

        log('Loading script: ' + filename);
        var script = document.createElement('script');
        script.src = src;
        script.setAttribute('data-fss-src', src);

        script.onload = function() {
            log('Script loaded: ' + filename + ', waiting for ' + globalName);
            var checkInterval = setInterval(function() {
                if (typeof window[globalName] !== 'undefined') {
                    log(globalName + ' now available after load');
                    clearInterval(checkInterval);
                    callback();
                }
            }, 20);
            setTimeout(function() {
                clearInterval(checkInterval);
                if (typeof window[globalName] === 'undefined') {
                    console.error('[FSS] Timeout waiting for ' + globalName + ' after load');
                }
                callback();
            }, 3000);
        };

        script.onerror = function(e) {
            console.error('[FSS] Failed to load script: ' + src, e);
            callback();
        };

        document.head.appendChild(script);
    }

    /**
     * Initialize the search component
     */
    var initRetryCount = 0;
    var MAX_INIT_RETRIES = 50; // Max 5 seconds (50 * 100ms)

    function initSearch() {
        log('initSearch called (attempt ' + (initRetryCount + 1) + ')');

        var container = document.getElementById(instanceId);
        if (!container) {
            initRetryCount++;
            if (initRetryCount >= MAX_INIT_RETRIES) {
                console.error('[FSS ' + instanceId + '] Container not found after ' + MAX_INIT_RETRIES + ' retries. Giving up.');
                return;
            }
            log('Container not found, retrying in 100ms...');
            setTimeout(initSearch, 100);
            return;
        }
        log('Container found:', container);

        if (typeof FarmerSmartSearch === 'undefined') {
            initRetryCount++;
            if (initRetryCount >= MAX_INIT_RETRIES) {
                console.error('[FSS ' + instanceId + '] FarmerSmartSearch not loaded after ' + MAX_INIT_RETRIES + ' retries. Giving up.');
                return;
            }
            log('FarmerSmartSearch not yet available, retrying in 100ms...');
            setTimeout(initSearch, 100);
            return;
        }

        log('Dependencies status:');
        log('  - ConfidenceEngine: ' + (typeof ConfidenceEngine !== 'undefined' ? 'OK' : 'MISSING'));
        log('  - RecentFarmers: ' + (typeof RecentFarmers !== 'undefined' ? 'OK' : 'MISSING'));
        log('  - FarmerSmartSearch: ' + (typeof FarmerSmartSearch !== 'undefined' ? 'OK v' + FarmerSmartSearch.version : 'MISSING'));

        try {
            var config = {
                apiEndpoint: '${apiEndpoint!}',
                apiId: '${apiId!}',
                apiKey: '${apiKey!}',
                hiddenFieldId: '${fieldId!}',
                storeValue: '${storeValue!"nationalId"}',
                displayMode: '${displayMode!}',
                displayColumns: '${displayColumns!}'.split(',').map(function(s) { return s.trim(); }),
                initialValue: '${value!?js_string}',
                // Input detection patterns (Issue #3, #4: now passed from config)
                nationalIdPattern: '${nationalIdPattern?js_string}',
                nationalIdMinLength: ${nationalIdMinLength!4},
                phonePattern: '${phonePattern?js_string}',
                phoneMinLength: ${phoneMinLength!8},
                // Recent farmers configuration (Issue #1, #2)
                showRecentFarmers: ${showRecentFarmers?c},
                maxRecentFarmers: ${maxRecentFarmers!5},
                // Auto-select configuration
                autoSelectSingleResult: ${autoSelectSingleResult?c},
                autoSelectMinScore: ${autoSelectMinScore!90},
                showAutoSelectNotification: ${showAutoSelectNotification?c},
                // Callbacks
                onSelect: function(farmer) {
                    log('Farmer selected:', farmer);
                },
                onClear: function() {
                    log('Selection cleared');
                },
                onError: function(error) {
                    console.error('[FSS] Error:', error);
                }
            };

            log('Config:', config);

            var search = FarmerSmartSearch.init(instanceId, config);

            // Store reference globally
            window['fssSearch_' + fieldId] = search;
            log('Search component initialized successfully');

            // Backup click handler for search button
            var searchBtn = document.querySelector('#' + instanceId + '_display .fss-search-btn');
            if (searchBtn && search) {
                log('Adding backup click handler to searchBtn');
                searchBtn.onclick = function(e) {
                    log('Backup onclick triggered');
                    e.preventDefault();
                    e.stopPropagation();
                    search.show();
                    return false;
                };
            }

        } catch (e) {
            console.error('[FSS] Init error:', e);
            container.innerHTML = '<div class="fss-init-error">Error initializing search: ' + e.message + '</div>';
        }
    }

    // Define resource base and cache version
    var resourceBase = '${resourceBase!}';
    var cacheVersion = '${fssCacheVersion}';
    log('Resource base: ' + resourceBase);
    log('Cache version: ' + cacheVersion);

    // Build script URLs
    var confidenceEngineUrl = resourceBase + 'confidence-engine.js&v=' + cacheVersion;
    var recentFarmersUrl = resourceBase + 'recent-farmers.js&v=' + cacheVersion;
    var mainScriptUrl = resourceBase + 'smart-search.js&v=' + cacheVersion;
    
    // Load scripts in order
    log('Starting script loading sequence...');
    
    loadScript(confidenceEngineUrl, 'ConfidenceEngine', function() {
        log('ConfidenceEngine ready, typeof = ' + typeof ConfidenceEngine);
        
        loadScript(recentFarmersUrl, 'RecentFarmers', function() {
            log('RecentFarmers ready, typeof = ' + typeof RecentFarmers);
            
            loadScript(mainScriptUrl, 'FarmerSmartSearch', function() {
                log('FarmerSmartSearch ready, typeof = ' + typeof FarmerSmartSearch);

                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    log('DOM ready, initializing...');
                    setTimeout(initSearch, 10);
                } else {
                    log('Waiting for DOMContentLoaded...');
                    window.addEventListener('DOMContentLoaded', function() {
                        log('DOMContentLoaded fired');
                        initSearch();
                    });
                }
            });
        });
    });

})();
</script>
