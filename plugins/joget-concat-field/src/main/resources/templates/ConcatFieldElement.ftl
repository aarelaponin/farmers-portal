<#-- Concatenation Field Form Element Template -->
<div class="form-cell concat-field-cell" ${elementMetaData!}>
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
                   class="concat-field-input" />
        <#elseif displayType! == "readonly">
            <#-- Read-only display with hidden input -->
            <span id="${fieldId!}_display" class="concat-field-display">${value!?html}</span>
            <input type="hidden" 
                   id="${fieldId!}" 
                   name="${elementParamName!}" 
                   value="${value!?html}"
                   class="concat-field-input" />
        <#else>
            <#-- Editable text field -->
            <input type="text" 
                   id="${fieldId!}" 
                   name="${elementParamName!}" 
                   value="${value!?html}"
                   class="concat-field-input"
                   <#if displayType! == "readonly">readonly</#if> />
        </#if>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Cache bust version -->
<#assign concatCacheVersion = "20260107_v1">

<#-- Load CSS (minimal, inline for simplicity) -->
<style>
.concat-field-cell {
    margin-bottom: 10px;
}
.concat-field-display {
    display: inline-block;
    padding: 4px 8px;
    background-color: #f5f5f5;
    border: 1px solid #ddd;
    border-radius: 3px;
    min-width: 100px;
    font-family: monospace;
}
.concat-field-input[type="text"] {
    width: 100%;
    max-width: 400px;
    padding: 4px 8px;
    font-family: monospace;
}
</style>

<#-- Load JS and initialize -->
<script>
(function() {
    // Unique instance ID
    var instanceId = '${elementId!?replace("-", "_")}';
    
    // Prevent duplicate initialization
    if (window['ConcatFieldLoaded_' + instanceId]) return;
    window['ConcatFieldLoaded_' + instanceId] = true;
    
    // Configuration from server
    var config = ${config!'{}'};
    var fieldId = '${fieldId!}';
    var elementParamName = '${elementParamName!}';
    
    // Debug logging
    var DEBUG = false;
    function log(msg) {
        if (DEBUG) console.log('[ConcatField ' + fieldId + '] ' + msg);
    }
    
    log('Initializing with config: ' + JSON.stringify(config));
    
    /**
     * Get the value of a source field
     */
    function getFieldValue(sourceFieldId) {
        // Try various selectors (Joget uses different patterns)
        var selectors = [
            '[name="' + sourceFieldId + '"]',
            '[name$="' + sourceFieldId + '"]',
            '#' + sourceFieldId,
            '[id$="' + sourceFieldId + '"]'
        ];
        
        for (var i = 0; i < selectors.length; i++) {
            var elements = document.querySelectorAll(selectors[i]);
            if (elements.length > 0) {
                var el = elements[0];
                
                // Handle radio buttons and checkboxes
                if (el.type === 'radio' || el.type === 'checkbox') {
                    var checked = document.querySelector(selectors[i] + ':checked');
                    return checked ? checked.value : '';
                }
                
                // Handle select boxes
                if (el.tagName === 'SELECT') {
                    return el.value;
                }
                
                // Handle text inputs, textareas, hidden fields
                return el.value || '';
            }
        }
        
        log('Field not found: ' + sourceFieldId);
        return '';
    }
    
    /**
     * Apply transformation to a value
     */
    function applyTransform(value, transform) {
        if (!value || !transform) return value || '';
        
        switch (transform) {
            case 'uppercase':
                return value.toUpperCase();
            case 'lowercase':
                return value.toLowerCase();
            case 'capitalize':
                return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
            case 'trim':
                return value.trim();
            default:
                return value;
        }
    }
    
    /**
     * Compute the concatenated value
     */
    function computeValue() {
        var sourceFields = config.sourceFields || [];
        var separator = config.separator || '_';
        var formatPattern = config.formatPattern || '';
        var prefix = config.prefix || '';
        var suffix = config.suffix || '';
        var skipEmpty = config.skipEmpty !== false;
        
        var values = [];
        
        // Collect values from source fields
        for (var i = 0; i < sourceFields.length; i++) {
            var sf = sourceFields[i];
            var fieldValue = getFieldValue(sf.fieldId);
            fieldValue = applyTransform(fieldValue, sf.transform);
            values.push(fieldValue);
        }
        
        var result = '';
        
        if (formatPattern) {
            // Use format pattern with placeholders
            result = formatPattern;
            for (var i = 0; i < values.length; i++) {
                result = result.split('{' + i + '}').join(values[i]);
            }
        } else {
            // Simple concatenation with separator
            var filteredValues = skipEmpty ? values.filter(function(v) { return v !== ''; }) : values;
            result = filteredValues.join(separator);
        }
        
        // Add prefix and suffix if result is not empty
        if (result) {
            result = prefix + result + suffix;
        }
        
        log('Computed value: ' + result);
        return result;
    }
    
    /**
     * Update the concatenated field
     */
    function updateField() {
        var value = computeValue();
        
        // Update hidden/input field
        var inputField = document.getElementById(fieldId);
        if (inputField) {
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
        }
        
        log('Field updated to: ' + value);
    }
    
    /**
     * Attach event listeners to source fields
     */
    function attachListeners() {
        var sourceFields = config.sourceFields || [];
        var eventType = config.updateOn === 'blur' ? 'blur' : 'change';
        
        // Don't attach listeners if update is only on submit
        if (config.updateOn === 'submit') {
            log('Update on submit only, no listeners attached');
            return;
        }
        
        for (var i = 0; i < sourceFields.length; i++) {
            var sf = sourceFields[i];
            var selectors = [
                '[name="' + sf.fieldId + '"]',
                '[name$="' + sf.fieldId + '"]',
                '#' + sf.fieldId,
                '[id$="' + sf.fieldId + '"]'
            ];
            
            for (var j = 0; j < selectors.length; j++) {
                var elements = document.querySelectorAll(selectors[j]);
                for (var k = 0; k < elements.length; k++) {
                    var el = elements[k];
                    if (!el.hasAttribute('data-concat-listener-' + fieldId)) {
                        el.setAttribute('data-concat-listener-' + fieldId, 'true');
                        el.addEventListener(eventType, updateField);
                        
                        // Also listen to input event for real-time updates on text fields
                        if (eventType === 'change' && (el.type === 'text' || el.tagName === 'TEXTAREA')) {
                            el.addEventListener('input', updateField);
                        }
                        
                        log('Attached ' + eventType + ' listener to: ' + sf.fieldId);
                    }
                }
            }
        }
    }
    
    /**
     * Initialize the component
     */
    function init() {
        log('Initializing...');
        
        // Compute initial value
        updateField();
        
        // Attach event listeners
        attachListeners();
        
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
    window['concatField_' + fieldId] = {
        update: updateField,
        compute: computeValue,
        config: config
    };
    
})();
</script>
