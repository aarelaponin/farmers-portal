<#-- Rule Editor Form Element Template -->
<#-- Use inline styles directly to override Joget's 30%/70% label/value layout -->
<div class="form-cell jre-form-cell-fullwidth" style="display: block !important; width: 100% !important; float: none !important; clear: both !important;" ${elementMetaData!}>
    <label class="label" style="display: block !important; width: 100% !important; max-width: 100% !important; float: none !important; margin-bottom: 8px !important; text-align: left !important;">
        ${element.properties.label!}
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>
    <div class="form-cell-value jre-form-cell-value-fullwidth" style="display: block !important; width: 100% !important; max-width: 100% !important; float: none !important; margin-left: 0 !important; padding-left: 0 !important;">
        <#-- Hidden field for form submission -->
        <textarea id="${fieldId!}" name="${elementParamName!}" class="jre-hidden-textarea" style="display:none;">${value!?html}</textarea>

        <#-- Rule Editor container -->
        <div id="${elementId!}" class="jre-editor-container"></div>
    </div>
    <div class="form-clear"></div>
</div>

<#-- Cache bust version - change to force reload -->
<#assign jreCacheVersion = "20251230_v7">

<#-- Load CSS (only once) -->
<link rel="stylesheet" href="${resourceBase!}codemirror.min.css&v=${jreCacheVersion}" data-jre-css="true">
<link rel="stylesheet" href="${resourceBase!}jre-editor.css&v=${jreCacheVersion}" data-jre-css="true">

<#-- Load JS and initialize (only once) -->
<script>
(function() {
    // Unique instance ID for this element
    var instanceId = '${elementId!?replace("-", "_")}';

    // Prevent duplicate initialization
    if (window['JREEditorLoaded_' + instanceId]) return;
    window['JREEditorLoaded_' + instanceId] = true;

    // Debug logging
    var DEBUG = true;
    function log(msg) {
        if (DEBUG) console.log('[JRE ' + instanceId + '] ' + msg);
    }

    /**
     * Load a script by URL, extracting the actual filename from query params
     */
    function loadScript(src, callback) {
        // Extract actual filename from URL like "...service?file=jre-editor.js&v=xxx"
        var filename = src;
        var fileParam = src.match(/[?&]file=([^&]+)/);
        if (fileParam) {
            filename = fileParam[1];
        }

        log('Loading script: ' + filename + ' from ' + src);

        // Check if already loaded WITH THE SAME VERSION
        var existing = document.querySelector('script[data-jre-src="' + src + '"]');
        if (existing) {
            log('Script already loaded (same version): ' + filename);
            callback();
            return;
        }

        // For different versions, we still load - don't check globals here
        // This ensures cache busting works properly

        var script = document.createElement('script');
        script.src = src;
        script.setAttribute('data-jre-file', filename);
        script.setAttribute('data-jre-src', src);

        script.onload = function() {
            log('Loaded: ' + filename);
            // Small delay to ensure script execution
            setTimeout(callback, 50);
        };

        script.onerror = function(e) {
            console.error('[JRE] Failed to load script: ' + src, e);
            // Still call callback to not block completely
            callback();
        };

        document.head.appendChild(script);
    }

    /**
     * Initialize the editor once all scripts are loaded
     */
    function initEditor() {
        log('initEditor called');

        // Check container exists
        var container = document.getElementById('${elementId!}');
        if (!container) {
            log('Container not found, retrying in 100ms...');
            setTimeout(initEditor, 100);
            return;
        }

        // Verify dependencies
        if (typeof CodeMirror === 'undefined') {
            console.error('[JRE] CodeMirror is not loaded!');
            container.innerHTML = '<div style="color:red;padding:10px;">Error: CodeMirror failed to load</div>';
            return;
        }

        if (typeof JREEditor === 'undefined') {
            console.error('[JRE] JREEditor is not loaded!');
            container.innerHTML = '<div style="color:red;padding:10px;">Error: JREEditor failed to load</div>';
            return;
        }

        log('Initializing JREEditor...');

        try {
            var editor = JREEditor.init('${elementId!}', {
                apiBase: '${apiBase!}',
                apiId: '${apiId!}',
                apiKey: '${apiKey!}',
                scopeCode: '${scopeCode!}',
                hiddenFieldId: '${fieldId!}',
                rulesetCodeFieldId: '${rulesetCodeFieldId!}',
                rulesetNameFieldId: '${rulesetNameFieldId!}',
                height: '${height!}',
                showDictionary: ${showDictionary?c},
                showSaveButton: ${showSaveButton?c},
                filterConfig: ${filterConfig!},
                onValidate: function(result) {
                    log('Validation: ' + (result.valid ? 'passed' : 'failed'));
                },
                onError: function(error) {
                    console.error('[JRE] Error:', error);
                }
            });

            // Store reference globally
            window['jreEditor_${fieldId!}'] = editor;
            log('Editor initialized successfully');

        } catch (e) {
            console.error('[JRE] Init error:', e);
            container.innerHTML = '<div style="color:red;padding:10px;">Error initializing editor: ' + e.message + '</div>';
        }
    }

    // Define resource base and cache version
    var resourceBase = '${resourceBase!}';
    var cacheVersion = '${jreCacheVersion}';
    log('Resource base: ' + resourceBase + ', version: ' + cacheVersion);

    // Load scripts in sequence: CodeMirror -> JRE Mode -> JRE Editor -> Init
    loadScript(resourceBase + 'codemirror.min.js&v=' + cacheVersion, function() {
        log('CodeMirror loaded, typeof CodeMirror = ' + typeof CodeMirror);

        loadScript(resourceBase + 'jre-mode.js&v=' + cacheVersion, function() {
            log('JRE mode loaded');

            loadScript(resourceBase + 'jre-editor.js&v=' + cacheVersion, function() {
                log('JRE editor loaded, typeof JREEditor = ' + typeof JREEditor);

                // Use requestAnimationFrame to ensure DOM is ready
                if (document.readyState === 'complete') {
                    initEditor();
                } else {
                    window.addEventListener('load', initEditor);
                }
            });
        });
    });

})();
</script>

<style>
/* Ensure editor displays properly in form - inline styles for highest priority */
.jre-editor-container {
    margin-top: 5px;
}
.jre-hidden-textarea {
    display: none !important;
}

/* Full width layout for Rule Editor - override Joget defaults */
.jre-form-cell-fullwidth {
    display: block !important;
    width: 100% !important;
    float: none !important;
    clear: both !important;
}

.jre-form-cell-fullwidth > .label,
.jre-form-cell-fullwidth > label.label,
.jre-form-cell-fullwidth > label {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    float: none !important;
    margin-bottom: 8px !important;
    text-align: left !important;
}

.jre-form-cell-fullwidth > .form-cell-value {
    display: block !important;
    width: 100% !important;
    max-width: 100% !important;
    float: none !important;
    margin-left: 0 !important;
    padding-left: 0 !important;
}
</style>

