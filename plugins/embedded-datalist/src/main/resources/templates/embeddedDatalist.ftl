<#-- Embedded Datalist Form Element Template -->
<div class="form-cell embedded-datalist-form-cell" ${elementMetaData!}>
    <#if element.properties.label?? && element.properties.label != "">
        <label class="label">${element.properties.label!}</label>
    </#if>
    
    <div class="form-cell-value">
        <#-- Container for the embedded datalist -->
        <div id="embedded-datalist-${elementUniqueKey!}" 
             class="embedded-datalist-container"
             data-datalist-id="${datalistId!}"
             data-app-id="${appId!}"
             data-app-version="${appVersion!}"
             data-page-size="${pageSize!}"
             data-show-pagination="${showPagination?string('true', 'false')}"
             data-empty-message="${emptyMessage!?html}"
             style="min-height: ${height!};">
            <div class="embedded-datalist-loading">
                <i class="fas fa-spinner fa-spin"></i> Loading...
            </div>
        </div>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Include CSS once per page -->
<#if !(request.getAttribute("embeddedDatalistCssLoaded")??)>
    <#assign void = request.setAttribute("embeddedDatalistCssLoaded", true)>
    <style>
        /* Embedded Datalist Container Styles */
        .embedded-datalist-container {
            border: 1px solid #ddd;
            border-radius: 4px;
            overflow: hidden;
            background: #fff;
        }
        
        .embedded-datalist-loading {
            padding: 40px;
            text-align: center;
            color: #666;
            font-size: 14px;
        }
        
        .embedded-datalist-loading i {
            margin-right: 8px;
        }
        
        .embedded-datalist-error {
            padding: 20px;
            color: #c00;
            background: #fff0f0;
            border-radius: 4px;
            text-align: center;
        }
        
        .embedded-datalist-empty {
            padding: 30px;
            text-align: center;
            color: #999;
            font-style: italic;
        }
        
        /* Table Styles */
        .embedded-datalist-table-wrapper {
            overflow-x: auto;
            max-height: calc(${height!} - 50px);
            overflow-y: auto;
        }
        
        .embedded-datalist-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }
        
        .embedded-datalist-table th {
            background: #f5f5f5;
            border: 1px solid #ddd;
            padding: 10px 12px;
            text-align: left;
            font-weight: 600;
            position: sticky;
            top: 0;
            z-index: 10;
            white-space: nowrap;
        }
        
        .embedded-datalist-table td {
            border: 1px solid #ddd;
            padding: 8px 12px;
            vertical-align: top;
        }
        
        .embedded-datalist-table tbody tr:nth-child(even) {
            background: #fafafa;
        }
        
        .embedded-datalist-table tbody tr:hover {
            background: #f0f7ff;
        }
        
        /* Footer / Pagination Styles */
        .embedded-datalist-footer {
            padding: 10px 12px;
            background: #f9f9f9;
            border-top: 1px solid #ddd;
            font-size: 12px;
            color: #666;
        }
        
        .embedded-datalist-pagination {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
        }
        
        .embedded-datalist-pagination a {
            color: #337ab7;
            text-decoration: none;
            padding: 4px 8px;
            border: 1px solid #ddd;
            border-radius: 3px;
            margin: 0 2px;
        }
        
        .embedded-datalist-pagination a:hover {
            background: #f0f0f0;
            text-decoration: none;
        }
        
        .embedded-datalist-pagination .edl-page-current {
            font-weight: bold;
            padding: 4px 8px;
        }
        
        .embedded-datalist-pagination .edl-page-info {
            color: #888;
        }
        
        /* Form Cell Full Width Override */
        .embedded-datalist-form-cell {
            display: block !important;
            width: 100% !important;
        }

        .embedded-datalist-form-cell > .form-cell-value {
            display: block !important;
            width: 100% !important;
            max-width: 100% !important;
            margin-left: 0 !important;
        }

        /* Filter Controls Styles */
        .embedded-datalist-filters {
            padding: 10px 12px;
            background: #f9f9f9;
            border-bottom: 1px solid #ddd;
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            align-items: flex-end;
        }

        .edl-filter-field {
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .edl-filter-field label {
            font-size: 11px;
            color: #666;
            font-weight: 500;
        }

        .edl-filter-field input,
        .edl-filter-field select {
            padding: 6px 8px;
            border: 1px solid #ccc;
            border-radius: 3px;
            font-size: 13px;
            min-width: 120px;
        }

        .edl-filter-field input:focus,
        .edl-filter-field select:focus {
            outline: none;
            border-color: #337ab7;
        }

        .edl-filter-buttons {
            display: flex;
            gap: 6px;
        }

        .edl-filter-buttons button {
            padding: 6px 12px;
            border: 1px solid #ccc;
            border-radius: 3px;
            cursor: pointer;
            font-size: 13px;
        }

        .edl-filter-buttons .edl-btn-filter {
            background: #337ab7;
            color: #fff;
            border-color: #337ab7;
        }

        .edl-filter-buttons .edl-btn-filter:hover {
            background: #286090;
        }

        .edl-filter-buttons .edl-btn-clear {
            background: #fff;
        }

        .edl-filter-buttons .edl-btn-clear:hover {
            background: #f0f0f0;
        }

        /* Export Links Styles */
        .edl-export-links {
            margin-left: auto;
        }

        .edl-export-links a {
            color: #337ab7;
            text-decoration: none;
            margin-left: 10px;
            font-size: 12px;
        }

        .edl-export-links a:hover {
            text-decoration: underline;
        }

        .edl-export-links a i {
            margin-right: 4px;
        }

        /* Row Click Styles */
        .embedded-datalist-table.edl-clickable tbody tr {
            cursor: pointer;
        }

        .embedded-datalist-table.edl-clickable tbody tr:hover {
            background: #e8f4fc;
        }

        <#-- Custom CSS from configuration -->
        <#if customCss?? && customCss != "">
        ${customCss}
        </#if>
    </style>
</#if>

<#-- JavaScript for loading and rendering the datalist -->
<script>
(function() {
    // Unique instance ID to prevent conflicts with multiple elements
    var instanceId = '${elementUniqueKey!}';
    var containerId = 'embedded-datalist-' + instanceId;

    // Check if already fully loaded (has table or empty message rendered)
    var $existingContainer = document.getElementById(containerId);
    if ($existingContainer) {
        var content = $existingContainer.innerHTML;
        if (content.indexOf('embedded-datalist-table') !== -1 ||
            content.indexOf('embedded-datalist-empty') !== -1) {
            // Already rendered, skip initialization
            return;
        }
    }

    // Configuration from server
    var config = {
        datalistId: '${datalistId!}',
        appId: '${appId!}',
        appVersion: '${appVersion!}',
        pageSize: parseInt('${pageSize!}') || 10,
        showPagination: ${showPagination?string('true', 'false')},
        emptyMessage: '${emptyMessage!?js_string}',
        filterParams: ${filterParamsJson!},
        showExport: ${showExport?string('true', 'false')},
        showFilter: ${showFilter?string('true', 'false')},
        rowClickAction: '${rowClickAction!}',
        rowClickFormId: '${rowClickFormId!}',
        contextPath: '${contextPath!}'
    };

    // Debug: log configuration
    console.log('[EmbeddedDatalist] Config:', config);
    
    // State
    var currentPage = 1;
    var isLoading = false;
    var datalistFilters = []; // Filter definitions from datalist
    var activeFilterValues = {}; // Current filter values (fn_xxx)
    
    /**
     * Get current value of a form field by ID
     */
    function getFieldValue(fieldId) {
        if (!fieldId) return '';
        
        // Try various selector patterns for Joget form fields
        var selectors = [
            '[name="' + fieldId + '"]',
            '[name$="_' + fieldId + '"]',
            '#' + fieldId,
            '[id$="_' + fieldId + '"]'
        ];
        
        for (var i = 0; i < selectors.length; i++) {
            var $field = $(selectors[i]);
            if ($field.length > 0) {
                var val = $field.val();
                if (val !== null && val !== undefined && val !== '') {
                    return val;
                }
            }
        }
        
        return '';
    }
    
    /**
     * Build URL query parameters from filter configuration
     */
    function buildFilterQueryString() {
        var params = [];

        // Add form field filter params (from plugin configuration)
        if (config.filterParams && config.filterParams.length > 0) {
            for (var i = 0; i < config.filterParams.length; i++) {
                var fp = config.filterParams[i];
                var value = '';

                // Try to get current form field value
                if (fp.fieldId) {
                    value = getFieldValue(fp.fieldId);
                }

                // Fall back to server-provided current value
                if (!value && fp.currentValue) {
                    value = fp.currentValue;
                }

                // Finally fall back to default value
                if (!value && fp.defaultValue) {
                    value = fp.defaultValue;
                }

                if (value && fp.paramName) {
                    // Pass parameter directly for #requestParam.xxx# hash variables
                    params.push(encodeURIComponent(fp.paramName) + '=' + encodeURIComponent(value));
                }
            }
        }

        // Add active filter values (fn_xxx from filter controls)
        for (var key in activeFilterValues) {
            if (activeFilterValues.hasOwnProperty(key) && activeFilterValues[key]) {
                params.push(encodeURIComponent(key) + '=' + encodeURIComponent(activeFilterValues[key]));
            }
        }

        return params.join('&');
    }

    /**
     * Build export URL with current filters
     */
    function buildExportUrl(format) {
        var baseUrl = config.contextPath + '/web/json/data/list/' +
                      config.appId + '/' + config.datalistId;

        var filterQuery = buildFilterQueryString();
        var exportParam = '_export=' + format;

        return baseUrl + '?' + exportParam + (filterQuery ? '&' + filterQuery : '');
    }

    /**
     * Fetch datalist definition to get filter metadata
     */
    function fetchDatalistDefinition(callback) {
        // Datalist definition endpoint includes version
        var defUrl = config.contextPath + '/web/json/console/app/' +
                     config.appId + '/' + config.appVersion +
                     '/datalist/' + config.datalistId + '/json';

        $.ajax({
            url: defUrl,
            type: 'GET',
            dataType: 'json',
            success: function(response) {
                if (response && response.filters) {
                    datalistFilters = response.filters;
                }
                callback();
            },
            error: function() {
                // Silently fail - filters just won't be shown
                console.warn('[EmbeddedDatalist] Could not fetch datalist definition for filters');
                callback();
            }
        });
    }

    /**
     * Render filter controls based on datalist filter definitions
     */
    function renderFilterControls() {
        if (!config.showFilter || datalistFilters.length === 0) {
            return '';
        }

        var html = '<div class="embedded-datalist-filters">';

        for (var i = 0; i < datalistFilters.length; i++) {
            var filter = datalistFilters[i];
            var filterName = filter.name || filter.id;
            var filterLabel = filter.label || filterName;
            var filterType = filter.type || 'text';
            var fnKey = 'fn_' + filterName;
            var currentValue = activeFilterValues[fnKey] || '';

            html += '<div class="edl-filter-field">';
            html += '<label for="edl-filter-' + instanceId + '-' + filterName + '">' + escapeHtml(filterLabel) + '</label>';

            if (filter.options && filter.options.length > 0) {
                // Render as select dropdown
                html += '<select id="edl-filter-' + instanceId + '-' + filterName + '" data-filter="' + escapeHtml(filterName) + '">';
                html += '<option value="">-- All --</option>';
                for (var j = 0; j < filter.options.length; j++) {
                    var opt = filter.options[j];
                    var optValue = opt.value !== undefined ? opt.value : opt;
                    var optLabel = opt.label !== undefined ? opt.label : opt;
                    var selected = (currentValue === String(optValue)) ? ' selected' : '';
                    html += '<option value="' + escapeHtml(String(optValue)) + '"' + selected + '>' + escapeHtml(String(optLabel)) + '</option>';
                }
                html += '</select>';
            } else {
                // Render as text input
                html += '<input type="text" id="edl-filter-' + instanceId + '-' + filterName + '" data-filter="' + escapeHtml(filterName) + '" value="' + escapeHtml(currentValue) + '" />';
            }

            html += '</div>';
        }

        // Filter buttons
        html += '<div class="edl-filter-buttons">';
        html += '<button type="button" class="edl-btn-filter">Filter</button>';
        html += '<button type="button" class="edl-btn-clear">Clear</button>';
        html += '</div>';

        html += '</div>';
        return html;
    }

    /**
     * Collect filter values from filter controls
     */
    function collectFilterValues() {
        var $container = $('#' + containerId);
        activeFilterValues = {};

        $container.find('.embedded-datalist-filters [data-filter]').each(function() {
            var filterName = $(this).data('filter');
            var value = $(this).val();
            if (value) {
                activeFilterValues['fn_' + filterName] = value;
            }
        });
    }

    /**
     * Clear all filter values
     */
    function clearFilterValues() {
        var $container = $('#' + containerId);
        activeFilterValues = {};

        $container.find('.embedded-datalist-filters [data-filter]').each(function() {
            $(this).val('');
        });
    }

    /**
     * Bind filter control event handlers
     */
    function bindFilterHandlers() {
        var $container = $('#' + containerId);

        // Filter button click
        $container.on('click', '.edl-btn-filter', function(e) {
            e.preventDefault();
            collectFilterValues();
            loadDatalist(1);
        });

        // Clear button click
        $container.on('click', '.edl-btn-clear', function(e) {
            e.preventDefault();
            clearFilterValues();
            loadDatalist(1);
        });

        // Enter key in filter inputs
        $container.on('keypress', '.embedded-datalist-filters input', function(e) {
            if (e.which === 13) {
                e.preventDefault();
                collectFilterValues();
                loadDatalist(1);
            }
        });
    }

    /**
     * Load datalist data via AJAX
     */
    function loadDatalist(page) {
        if (isLoading) return;
        if (!config.datalistId) {
            showError('No datalist configured');
            return;
        }
        
        isLoading = true;
        page = page || 1;
        currentPage = page;
        
        var $container = $('#' + containerId);
        $container.html('<div class="embedded-datalist-loading"><i class="fas fa-spinner fa-spin"></i> Loading...</div>');
        
        // Build API URL: /web/json/data/list/{appId}/{listId}
        var apiUrl = config.contextPath + '/web/json/data/list/' +
                     config.appId + '/' + config.datalistId;

        // Add filter parameters
        var filterQuery = buildFilterQueryString();

        // Pagination: use 'start' and 'rows' parameters
        var startRow = (page - 1) * config.pageSize;
        var paginationParams = 'start=' + startRow + '&rows=' + config.pageSize;

        var queryString = filterQuery ? (filterQuery + '&' + paginationParams) : paginationParams;
        apiUrl += '?' + queryString;
        
        // Debug: log the URL being called
        console.log('[EmbeddedDatalist] Calling API URL:', apiUrl);

        $.ajax({
            url: apiUrl,
            type: 'GET',
            dataType: 'json',
            timeout: 30000, // 30 second timeout
            success: function(response) {
                isLoading = false;
                console.log('[EmbeddedDatalist] Response:', response);
                renderDatalist(response, page);
            },
            error: function(xhr, status, error) {
                isLoading = false;
                console.error('[EmbeddedDatalist] Error loading data:', {
                    status: status,
                    error: error,
                    responseText: xhr.responseText,
                    statusCode: xhr.status,
                    url: apiUrl
                });
                var errorMsg = status === 'timeout' ? 'Request timed out' : (error || status || 'Unknown error');
                showError('Error loading data: ' + errorMsg + ' (Status: ' + xhr.status + ')');
            }
        });
    }
    
    /**
     * Render the datalist as an HTML table
     */
    function renderDatalist(response, page) {
        var $container = $('#' + containerId);
        
        // Build HTML starting with filter controls
        var html = renderFilterControls();

        // Check for empty data
        if (!response || !response.data || response.data.length === 0) {
            html += '<div class="embedded-datalist-empty">' + config.emptyMessage + '</div>';
            $container.html(html);
            return;
        }

        html += '<div class="embedded-datalist-table-wrapper">';
        var tableClass = 'embedded-datalist-table';
        if (config.rowClickAction) {
            tableClass += ' edl-clickable';
        }
        html += '<table class="' + tableClass + '">';
        
        // Build table headers
        html += '<thead><tr>';
        var columns = [];
        
        if (response.columns && response.columns.length > 0) {
            // Use column definitions from response
            for (var i = 0; i < response.columns.length; i++) {
                var col = response.columns[i];
                if (!col.hidden) {
                    columns.push(col.name);
                    html += '<th>' + escapeHtml(col.label || col.name) + '</th>';
                }
            }
        } else {
            // Fallback: use keys from first data row
            var firstRow = response.data[0];
            for (var key in firstRow) {
                if (firstRow.hasOwnProperty(key) && key !== 'id' && key.indexOf('_') !== 0) {
                    columns.push(key);
                    html += '<th>' + escapeHtml(formatColumnHeader(key)) + '</th>';
                }
            }
        }
        html += '</tr></thead>';
        
        // Build table body
        html += '<tbody>';
        for (var i = 0; i < response.data.length; i++) {
            var row = response.data[i];
            html += '<tr data-id="' + escapeHtml(row.id || '') + '">';
            
            for (var j = 0; j < columns.length; j++) {
                var colName = columns[j];
                var cellValue = row[colName];
                html += '<td>' + formatCellValue(cellValue) + '</td>';
            }
            
            html += '</tr>';
        }
        html += '</tbody></table></div>';
        
        // Build footer with pagination
        var total = response.total || response.data.length;
        var totalPages = Math.ceil(total / config.pageSize);
        var startRow = (page - 1) * config.pageSize + 1;
        var endRow = Math.min(page * config.pageSize, total);
        
        html += '<div class="embedded-datalist-footer">';
        html += '<div class="embedded-datalist-pagination">';
        html += '<span class="edl-page-info">Showing ' + startRow + '-' + endRow + ' of ' + total + ' record(s)</span>';

        if (config.showPagination && totalPages > 1) {
            html += '<span class="edl-page-nav">';

            // Previous link
            if (page > 1) {
                html += '<a href="#" class="edl-page" data-page="' + (page - 1) + '">&laquo; Prev</a> ';
            }

            // Page numbers (show up to 5 pages)
            var startPage = Math.max(1, page - 2);
            var endPage = Math.min(totalPages, startPage + 4);
            startPage = Math.max(1, endPage - 4);

            for (var p = startPage; p <= endPage; p++) {
                if (p === page) {
                    html += '<span class="edl-page-current">' + p + '</span>';
                } else {
                    html += '<a href="#" class="edl-page" data-page="' + p + '">' + p + '</a>';
                }
            }

            // Next link
            if (page < totalPages) {
                html += ' <a href="#" class="edl-page" data-page="' + (page + 1) + '">Next &raquo;</a>';
            }

            html += '</span>';
        }

        // Export links
        if (config.showExport) {
            html += '<span class="edl-export-links">';
            html += '<a href="' + buildExportUrl('csv') + '" target="_blank"><i class="fas fa-file-csv"></i>CSV</a>';
            html += '<a href="' + buildExportUrl('xls') + '" target="_blank"><i class="fas fa-file-excel"></i>Excel</a>';
            html += '</span>';
        }

        html += '</div></div>';
        
        $container.html(html);
        
        // Bind pagination click handlers
        $container.find('.edl-page').on('click', function(e) {
            e.preventDefault();
            var targetPage = parseInt($(this).data('page'));
            if (targetPage && targetPage !== currentPage) {
                loadDatalist(targetPage);
            }
        });

        // Bind row click handlers
        if (config.rowClickAction && config.rowClickFormId) {
            $container.find('.embedded-datalist-table tbody tr').on('click', function(e) {
                var rowId = $(this).data('id');
                if (!rowId) return;

                if (config.rowClickAction === 'popup') {
                    // Open form in popup using JPopup
                    var popupUrl = config.contextPath + '/web/app/' +
                                   config.appId + '/' + config.appVersion +
                                   '/form/embed?_id=' + encodeURIComponent(rowId) +
                                   '&_formId=' + encodeURIComponent(config.rowClickFormId);
                    if (typeof JPopup !== 'undefined' && JPopup.show) {
                        JPopup.show(null, popupUrl, {}, '');
                    } else {
                        // Fallback to window.open if JPopup not available
                        window.open(popupUrl, '_blank', 'width=800,height=600');
                    }
                } else if (config.rowClickAction === 'redirect') {
                    // Redirect to form page
                    var redirectUrl = config.contextPath + '/web/userview/' +
                                      config.appId + '/' + config.appVersion +
                                      '/_/form/' + encodeURIComponent(config.rowClickFormId) +
                                      '?id=' + encodeURIComponent(rowId);
                    window.location.href = redirectUrl;
                }
            });
        }
    }
    
    /**
     * Show error message in container
     */
    function showError(message) {
        var $container = $('#' + containerId);
        $container.html('<div class="embedded-datalist-error">' + escapeHtml(message) + '</div>');
    }
    
    /**
     * Format column header from field name (e.g., 'field_name' -> 'Field Name')
     */
    function formatColumnHeader(key) {
        return key
            .replace(/^c_/, '')  // Remove Joget 'c_' prefix
            .replace(/_/g, ' ')
            .replace(/\b\w/g, function(l) { return l.toUpperCase(); });
    }
    
    /**
     * Format cell value for display
     */
    function formatCellValue(value) {
        if (value === null || value === undefined) {
            return '-';
        }
        if (typeof value === 'object') {
            return escapeHtml(JSON.stringify(value));
        }
        return escapeHtml(String(value));
    }
    
    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        if (!text) return '';
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    /**
     * Initialize the embedded datalist
     */
    var initAttempts = 0;
    var maxInitAttempts = 50; // 5 seconds max wait
    var initialized = false;

    function init() {
        if (initialized) return;

        var $container = $('#' + containerId);
        if ($container.length === 0) {
            // Container not yet in DOM, retry with limit
            initAttempts++;
            if (initAttempts < maxInitAttempts) {
                setTimeout(init, 100);
            } else {
                console.error('[EmbeddedDatalist] Container not found after max attempts:', containerId);
            }
            return;
        }

        // Check if container is visible (not in hidden tab)
        if (!$container.is(':visible')) {
            // Container exists but is hidden (likely in inactive tab)
            // Set up a listener for when it becomes visible
            console.log('[EmbeddedDatalist] Container hidden, waiting for visibility:', containerId);

            // Check periodically if container becomes visible
            var visibilityCheck = setInterval(function() {
                if ($container.is(':visible')) {
                    clearInterval(visibilityCheck);
                    console.log('[EmbeddedDatalist] Container now visible, initializing:', containerId);
                    doInit($container);
                }
            }, 200);

            // Also listen for tab clicks (Joget uses various tab implementations)
            $(document).on('click', '.nav-tabs a, .ui-tabs-nav a, .subform-tab-link', function() {
                setTimeout(function() {
                    if ($container.is(':visible') && !initialized) {
                        console.log('[EmbeddedDatalist] Tab activated, initializing:', containerId);
                        doInit($container);
                    }
                }, 100);
            });
            return;
        }

        doInit($container);
    }

    function doInit($container) {
        if (initialized) return;
        initialized = true;

        console.log('[EmbeddedDatalist] Initializing:', containerId);

        // If showFilter is enabled, fetch datalist definition first to get filter metadata
        if (config.showFilter) {
            fetchDatalistDefinition(function() {
                loadDatalist(1);
                bindFilterHandlers();
            });
        } else {
            // Initial load without filters
            loadDatalist(1);
        }

        // Setup refresh on field change
        <#if refreshOnChange?? && refreshOnChange != "">
        var refreshFields = '${refreshOnChange!}'.split(',');
        for (var i = 0; i < refreshFields.length; i++) {
            var fieldId = refreshFields[i].trim();
            if (fieldId) {
                // Bind to various field patterns
                $(document).on('change', '[name="' + fieldId + '"], [name$="_' + fieldId + '"]', function() {
                    collectFilterValues(); // Preserve current filter values
                    loadDatalist(1);
                });
            }
        }
        </#if>
    }

    // Initialize when DOM is ready
    if (document.readyState === 'complete') {
        setTimeout(init, 50); // Small delay to ensure DOM is stable
    } else {
        $(document).ready(function() {
            setTimeout(init, 50);
        });
    }
    
    // Expose reload function globally for external use
    window['embeddedDatalistReload_' + instanceId] = function(page) {
        loadDatalist(page || 1);
    };
    
})();
</script>
