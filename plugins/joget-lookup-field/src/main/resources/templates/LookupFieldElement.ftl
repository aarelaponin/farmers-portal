<#-- Lookup Field Form Element Template -->
<div class="form-cell lookup-field-cell" ${elementMetaData!}>
    <#if displayType! != "hidden">
    <label class="label">
        ${label!}
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>
    </#if>
    <div class="form-cell-value">
        <#if displayType! == "hidden">
            <#-- Hidden field -->
            <input type="hidden"
                   id="${fieldId!}"
                   name="${elementParamName!}"
                   value="${value!?html}"
                   class="lookup-field-input" />
        <#elseif displayType! == "readonly">
            <#-- Read-only display with hidden input -->
            <span id="${fieldId!}_display" class="lookup-field-display">${value!?html}</span>
            <input type="hidden"
                   id="${fieldId!}"
                   name="${elementParamName!}"
                   value="${value!?html}"
                   class="lookup-field-input" />
        <#else>
            <#-- Editable text field -->
            <input type="text"
                   id="${fieldId!}"
                   name="${elementParamName!}"
                   value="${value!?html}"
                   class="lookup-field-input" />
        </#if>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Inline CSS -->
<style>
.lookup-field-cell {
    margin-bottom: 10px;
}
.lookup-field-display {
    display: inline-block;
    padding: 4px 8px;
    background-color: #f5f5f5;
    border: 1px solid #ddd;
    border-radius: 3px;
    min-width: 100px;
    min-height: 1.2em;
}
.lookup-field-input[type="text"] {
    width: 100%;
    max-width: 400px;
    padding: 4px 8px;
}
.lookup-field-loading {
    color: #999;
    font-style: italic;
}
</style>

<#-- JavaScript -->
<script>
(function() {
    // Unique instance ID to prevent duplicate initialization
    var instanceId = '${elementId!?replace("-", "_")}';

    if (window['LookupFieldLoaded_' + instanceId]) return;
    window['LookupFieldLoaded_' + instanceId] = true;

    // Configuration from server
    var config = ${config!'{}'};
    var fieldId = '${fieldId!}';
    var elementParamName = '${elementParamName!}';

    // Debug logging
    var DEBUG = false;
    function log(msg) {
        if (DEBUG) console.log('[LookupField ' + fieldId + '] ' + msg);
    }

    log('Initializing with config: ' + JSON.stringify(config));

    // Global lookup cache: shared across all LookupField instances
    // Key: "formId/primaryKey" → Value: full record object
    window._lookupFieldCache = window._lookupFieldCache || {};

    // Global registry for chained lookup notifications
    // Key: sourceFieldId → Value: array of callback functions
    window._lookupFieldWatchers = window._lookupFieldWatchers || {};

    /**
     * Find the source field element using multiple selector strategies
     */
    function findSourceField() {
        var sourceFieldId = config.sourceFieldId;
        var selectors = [
            '[name="' + sourceFieldId + '"]',
            '[name$="' + sourceFieldId + '"]',
            '#' + sourceFieldId,
            '[id$="' + sourceFieldId + '"]'
        ];

        for (var i = 0; i < selectors.length; i++) {
            var elements = document.querySelectorAll(selectors[i]);
            if (elements.length > 0) {
                log('Found source field with selector: ' + selectors[i]);
                return elements[0];
            }
        }

        log('Source field not found: ' + sourceFieldId);
        return null;
    }

    /**
     * Update the display and hidden input with the looked-up value
     */
    function updateDisplay(value) {
        var changed = false;

        // Update hidden/input field by ID
        var inputField = document.getElementById(fieldId);
        if (inputField) {
            changed = (inputField.value !== value);
            inputField.value = value;
        }

        // Also try with element param name (Joget pattern)
        var inputFieldByName = document.querySelector('[name="' + elementParamName + '"]');
        if (inputFieldByName && inputFieldByName !== inputField) {
            inputFieldByName.value = value;
        }

        // Update display span if exists (for readonly mode)
        var displaySpan = document.getElementById(fieldId + '_display');
        if (displaySpan) {
            displaySpan.textContent = value;
            displaySpan.classList.remove('lookup-field-loading');
        }

        // Notify chained LookupFields watching this field
        if (changed) {
            // 1. Native DOM event on the input
            if (inputField) {
                inputField.dispatchEvent(new Event('change', { bubbles: true }));
            }
            // 2. Direct notification via global watcher registry (reliable for chained lookups)
            var watchers = window._lookupFieldWatchers[fieldId] || [];
            for (var w = 0; w < watchers.length; w++) {
                log('Notifying chained watcher for: ' + fieldId);
                watchers[w]();
            }
        }

        log('Updated to: ' + value);
    }

    /**
     * Show loading state
     */
    function showLoading() {
        var displaySpan = document.getElementById(fieldId + '_display');
        if (displaySpan) {
            displaySpan.textContent = '...';
            displaySpan.classList.add('lookup-field-loading');
        }
    }

    /**
     * Fetch the record and extract the configured column.
     *
     * Two modes:
     * - If lookupKeyColumn is set: query the list endpoint with column filter
     *   (when SelectBox value != Joget primary key, e.g. customerId from ConcatField)
     * - If lookupKeyColumn is empty: direct GET by primary key
     *   (when SelectBox value IS the Joget primary key, e.g. IdGeneratorField)
     */
    function fetchAndUpdate() {
        var sourceField = findSourceField();
        if (!sourceField) return;

        var selectedId = sourceField.value;

        if (!selectedId) {
            updateDisplay('');
            return;
        }

        var cacheKey = config.lookupFormId + '/' + selectedId;

        // Check cache first
        if (window._lookupFieldCache[cacheKey]) {
            var cached = window._lookupFieldCache[cacheKey];
            var value = cached[config.lookupColumn] || '';
            updateDisplay(value);
            log('Cache hit: ' + cacheKey + ' → ' + value);
            return;
        }

        showLoading();

        // Use the plugin's own web service endpoint (session-authenticated)
        // Table name is resolved server-side from the form definition — no need to pass it
        var wsUrl = '/jw/web/json/plugin/'
            + 'global.govstack.lookupfield.element.LookupFieldWebService/service'
            + '?action=lookup'
            + '&appId=' + encodeURIComponent(config.appId)
            + '&formId=' + encodeURIComponent(config.lookupFormId)
            + '&keyVal=' + encodeURIComponent(selectedId);

        if (config.lookupKeyColumn) {
            wsUrl += '&keyCol=' + encodeURIComponent(config.lookupKeyColumn);
        }

        log('Fetching via plugin WS: ' + wsUrl);

        $.getJSON(wsUrl, function(record) {
            if (record && Object.keys(record).length > 0) {
                window._lookupFieldCache[cacheKey] = record;
                var value = record[config.lookupColumn] || '';
                updateDisplay(value);
                log('Fetched: ' + cacheKey + ' → ' + value);
            } else {
                log('No record found for: ' + selectedId);
                updateDisplay('');
            }
        }).fail(function(jqXHR, textStatus, errorThrown) {
            log('Fetch failed: ' + textStatus + ' ' + errorThrown
                + ' (status=' + jqXHR.status + ', response=' + jqXHR.responseText + ')');
            updateDisplay('');
        });
    }

    /**
     * Attach event listener to the source field
     */
    function attachListener() {
        var sourceFieldId = config.sourceFieldId;

        // Register in the global watcher registry (for chained lookup notifications)
        if (!window._lookupFieldWatchers[sourceFieldId]) {
            window._lookupFieldWatchers[sourceFieldId] = [];
        }
        window._lookupFieldWatchers[sourceFieldId].push(fetchAndUpdate);
        log('Registered as watcher for: ' + sourceFieldId);

        // Also attach a DOM event listener for SelectBox/TextField sources
        var sourceField = findSourceField();
        if (!sourceField) {
            log('Source DOM element not found (will rely on watcher registry): ' + sourceFieldId);
            return;
        }

        var eventType = config.updateOn === 'blur' ? 'blur' : 'change';

        if (!sourceField.hasAttribute('data-lookup-listener-' + fieldId)) {
            sourceField.setAttribute('data-lookup-listener-' + fieldId, 'true');
            sourceField.addEventListener(eventType, function() {
                fetchAndUpdate();
            });
            log('Attached ' + eventType + ' listener on DOM element');
        }
    }

    /**
     * Initialize the component
     */
    function init() {
        log('Initializing...');

        // If we already have a value (edit mode), just display it
        var existingValue = '${value!?js_string}';
        if (existingValue) {
            updateDisplay(existingValue);
        }

        // Attach listener for future changes
        attachListener();

        // If no existing value, try to fetch based on current source selection
        if (!existingValue) {
            fetchAndUpdate();
        }

        log('Initialization complete');
    }

    // Initialize when DOM is ready
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
        setTimeout(init, 100);
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(init, 100);
        });
    }

    // Also re-initialize after a delay (for dynamic form loading)
    setTimeout(init, 500);

    // Store reference globally for debugging
    window['lookupField_' + fieldId] = {
        fetch: fetchAndUpdate,
        config: config
    };

})();
</script>
