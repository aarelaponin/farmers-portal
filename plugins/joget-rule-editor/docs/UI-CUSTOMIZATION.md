# UI Customization Guide

This guide explains how to customize the visual appearance of the Rule Editor, including themes, colors, layout, and component styling.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Syntax Highlighting](#syntax-highlighting)
3. [Color Themes](#color-themes)
4. [Layout Customization](#layout-customization)
5. [Component Styling](#component-styling)
6. [Editor Configuration](#editor-configuration)
7. [Creating Custom Themes](#creating-custom-themes)
8. [Joget Form Integration](#joget-form-integration)

---

## Architecture Overview

The UI consists of three main files:

| File | Purpose |
|------|---------|
| `jre-mode.js` | CodeMirror syntax mode - defines token types for highlighting |
| `jre-editor.js` | Editor component - UI structure, behavior, API calls |
| `jre-editor.css` | Styling - colors, layout, responsiveness |

```
┌─────────────────────────────────────────────────────────────────┐
│  Rule Editor Container                                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Messages Panel                                            │  │
│  │  (success/error/info/loading states)                       │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Toolbar                                                   │  │
│  │  [Validate] [Save] [Clear] [Sample] [Fields] [Help]        │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────┬─────────────────────────┐  │
│  │  CodeMirror Editor              │  Field Dictionary       │  │
│  │  (main editing area)            │  (collapsible panel)    │  │
│  │                                 │                         │  │
│  └─────────────────────────────────┴─────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Help Panel (collapsible)                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Syntax Highlighting

### Token Categories (jre-mode.js)

The syntax mode assigns CSS classes to different token types:

| Token Type | CSS Class | Default Color | Elements |
|------------|-----------|---------------|----------|
| `keyword` | `.cm-keyword` | Blue, bold | RULE, WHEN, AND, OR, NOT |
| `def` | `.cm-def` | Purple, bold | TYPE, CATEGORY, MANDATORY, ORDER |
| `type` | `.cm-type` | Brown | INCLUSION, EXCLUSION, PRIORITY |
| `atom` | `.cm-atom` | Red, bold | YES, NO, true, false |
| `variable` | `.cm-variable` | Purple | Field names |
| `string` | `.cm-string` | Green | "quoted text" |
| `number` | `.cm-number` | Blue | 123, 45.67 |
| `comment` | `.cm-comment` | Gray, italic | # comments |
| `operator` | `.cm-operator` | Red | =, !=, >, <, >= |
| `bracket` | `.cm-bracket` | Gray | ( ) |

### Modifying Token Recognition (jre-mode.js)

```javascript
CodeMirror.defineMode("erel", function() {

    // Keywords - shown in blue, bold
    var keywords = new RegExp(
        "^(RULE|WHEN|AND|OR|NOT|BETWEEN|IN|CONTAINS|IS|EMPTY|" +
        "STARTS|ENDS|WITH|DEPENDS|ON|STOP|FAIL|PASS|EFFECTIVE|FROM|TO)\\b", "i"
    );

    // Clause keywords - shown in purple
    var clauses = new RegExp(
        "^(TYPE|CATEGORY|MANDATORY|ORDER|SCORE|WEIGHT|MESSAGE)\\b", "i"
    );

    // Types - shown in brown
    var types = new RegExp(
        "^(INCLUSION|EXCLUSION|PRIORITY|BONUS|" +
        "DEMOGRAPHIC|ECONOMIC|AGRICULTURAL|VULNERABILITY|HOUSEHOLD)\\b", "i"
    );

    // Booleans - shown in red
    var booleans = new RegExp("^(YES|NO|true|false|Y|N)\\b", "i");

    return {
        token: function(stream, state) {
            // ... tokenization logic ...

            if (stream.match(/^[a-zA-Z_][a-zA-Z0-9_]*/)) {
                var word = stream.current();

                if (keywords.test(word)) return "keyword";
                if (clauses.test(word)) return "def";
                if (types.test(word)) return "type";
                if (booleans.test(word)) return "atom";

                return "variable";  // Field names
            }
        }
    };
});
```

### Adding New Keywords

To highlight new keywords, add them to the appropriate regex:

```javascript
// Add SUBSIDY_TIER to types
var types = new RegExp(
    "^(INCLUSION|EXCLUSION|PRIORITY|BONUS|SUBSIDY_TIER)\\b", "i"
);

// Add BENEFIT, TIER to clauses
var clauses = new RegExp(
    "^(TYPE|CATEGORY|MANDATORY|ORDER|SCORE|WEIGHT|MESSAGE|BENEFIT|TIER)\\b", "i"
);
```

---

## Color Themes

### Default Theme Colors (jre-editor.css)

```css
/* Syntax highlighting colors */
.cm-s-default .cm-keyword { color: #0000ff; font-weight: bold; }  /* Blue */
.cm-s-default .cm-def { color: #6f42c1; font-weight: bold; }      /* Purple */
.cm-s-default .cm-type { color: #795e26; }                         /* Brown */
.cm-s-default .cm-variable { color: #6f42c1; }                     /* Purple */
.cm-s-default .cm-string { color: #22863a; }                       /* Green */
.cm-s-default .cm-number { color: #005cc5; }                       /* Blue */
.cm-s-default .cm-atom { color: #d73a49; font-weight: bold; }      /* Red */
.cm-s-default .cm-comment { color: #6a737d; font-style: italic; }  /* Gray */
.cm-s-default .cm-operator { color: #d73a49; }                     /* Red */
.cm-s-default .cm-bracket { color: #666; }                         /* Dark gray */
```

### Dark Theme Example

```css
/* Dark theme for Rule Editor */
.jre-editor-container.dark-theme .CodeMirror {
    background: #1e1e1e;
    color: #d4d4d4;
}

.jre-editor-container.dark-theme .cm-keyword { color: #569cd6; }
.jre-editor-container.dark-theme .cm-def { color: #c586c0; }
.jre-editor-container.dark-theme .cm-type { color: #dcdcaa; }
.jre-editor-container.dark-theme .cm-variable { color: #9cdcfe; }
.jre-editor-container.dark-theme .cm-string { color: #ce9178; }
.jre-editor-container.dark-theme .cm-number { color: #b5cea8; }
.jre-editor-container.dark-theme .cm-atom { color: #569cd6; }
.jre-editor-container.dark-theme .cm-comment { color: #6a9955; }
.jre-editor-container.dark-theme .cm-operator { color: #d4d4d4; }
```

### Error/Warning Line Highlighting

```css
/* Error line background */
.jre-line-error {
    background: rgba(220, 53, 69, 0.15) !important;
}

/* Warning line background */
.jre-line-warning {
    background: rgba(255, 193, 7, 0.15) !important;
}

/* Gutter markers */
.jre-gutter-error {
    color: #dc3545;
    font-size: 12px;
}

.jre-gutter-warning {
    color: #ffc107;
    font-size: 12px;
}
```

---

## Layout Customization

### Container Styling

```css
/* Main container */
.jre-editor-container {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    border: 1px solid #ccc;
    border-radius: 6px;
    overflow: hidden;
    background: #fff;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
```

### Messages Panel States

```css
/* Success state */
.jre-messages.success {
    background: #d4edda;
    color: #155724;
    border-bottom-color: #c3e6cb;
}

/* Error state */
.jre-messages.error {
    background: #f8d7da;
    color: #721c24;
    border-bottom-color: #f5c6cb;
}

/* Loading state */
.jre-messages.loading {
    background: #fff3cd;
    color: #856404;
    border-bottom-color: #ffeeba;
}

/* Info state */
.jre-messages.info {
    background: #d1ecf1;
    color: #0c5460;
    border-bottom-color: #bee5eb;
}
```

### Toolbar Styling

```css
.jre-toolbar {
    padding: 10px 16px;
    background: #f0f0f0;
    border-bottom: 1px solid #ddd;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
}

.jre-toolbar button {
    padding: 8px 16px;
    border: 1px solid #ccc;
    border-radius: 4px;
    background: #fff;
    cursor: pointer;
    font-size: 14px;
    transition: all 0.2s;
}

/* Primary button (Validate) */
.jre-btn-primary {
    background: #007bff;
    border-color: #007bff;
    color: white;
}

/* Save button */
.jre-save-btn {
    background: #28a745 !important;
    border-color: #28a745 !important;
    color: white !important;
}
```

### Dictionary Panel

```css
.jre-dict-panel {
    width: 280px;
    border-left: 1px solid #ddd;
    background: #fafafa;
}

.jre-dict-field {
    padding: 6px 8px;
    background: #fff;
    border: 1px solid #e0e0e0;
    border-radius: 4px;
    cursor: pointer;
}

.jre-dict-field:hover {
    background: #e7f1ff;
    border-color: #007bff;
}

.jre-dict-field code {
    font-family: "Monaco", "Menlo", "Consolas", monospace;
    font-size: 11px;
    color: #6f42c1;
}
```

### Responsive Design

```css
@media (max-width: 768px) {
    .jre-dict-panel {
        position: absolute;
        right: 0;
        top: 0;
        bottom: 0;
        z-index: 100;
        box-shadow: -2px 0 5px rgba(0,0,0,0.1);
    }

    .jre-help-columns {
        flex-direction: column;
    }

    .jre-toolbar {
        flex-wrap: wrap;
    }
}
```

---

## Component Styling

### Custom Button Styles

```css
/* Danger button */
.jre-btn-danger {
    background: #dc3545;
    border-color: #dc3545;
    color: white;
}

.jre-btn-danger:hover {
    background: #c82333;
}

/* Outline button */
.jre-btn-outline {
    background: transparent;
    border-color: #007bff;
    color: #007bff;
}

.jre-btn-outline:hover {
    background: #007bff;
    color: white;
}
```

### Custom Message Icons

```css
.jre-error-item.error .jre-error-icon {
    color: #dc3545;
    content: "✕";
}

.jre-error-item.warning .jre-error-icon {
    color: #ffc107;
    content: "⚠";
}

.jre-error-item.success .jre-error-icon {
    color: #28a745;
    content: "✓";
}
```

### Field Type Badges

```css
.jre-dict-field-type {
    display: inline-block;
    font-size: 10px;
    color: #666;
    background: #e9ecef;
    padding: 1px 5px;
    border-radius: 3px;
}

.jre-dict-field-grid {
    display: inline-block;
    font-size: 10px;
    color: #fff;
    background: #6f42c1;
    padding: 1px 5px;
    border-radius: 3px;
}

/* Custom type badges */
.jre-dict-field-type.number { background: #e3f2fd; color: #1565c0; }
.jre-dict-field-type.text { background: #e8f5e9; color: #2e7d32; }
.jre-dict-field-type.date { background: #fff3e0; color: #ef6c00; }
.jre-dict-field-type.boolean { background: #fce4ec; color: #c2185b; }
```

---

## Editor Configuration

### CodeMirror Options (jre-editor.js)

```javascript
var cm = CodeMirror.fromTextArea(elements.textarea, {
    mode: 'jre',              // Use Rules Script syntax mode
    lineNumbers: true,         // Show line numbers
    matchBrackets: true,       // Highlight matching brackets
    styleActiveLine: true,     // Highlight current line
    indentUnit: 2,             // Spaces per indent
    tabSize: 2,                // Tab width
    lineWrapping: true,        // Wrap long lines
    gutters: ['CodeMirror-linenumbers', 'erel-gutter'],

    // Custom key bindings
    extraKeys: {
        'Ctrl-Enter': function() { validate(); },
        'Cmd-Enter': function() { validate(); },
        'Ctrl-S': function() { save(); },
        'Cmd-S': function() { save(); },
        'Ctrl-Space': function() { showAutocomplete(); },
        'Tab': function(cm) {
            cm.replaceSelection('  ', 'end');  // Insert spaces
        }
    }
});
```

### Initialization Options

```javascript
var editor = JREEditor.init('container-id', {
    // API Configuration
    apiBase: '/jw/api/erel/erel',
    apiId: 'API-xxx',
    apiKey: 'xxx',

    // Context
    scopeCode: 'FARMER_ELIGIBILITY',
    contextType: 'ELIGIBILITY',
    contextCode: 'PROG-2025-001',
    rulesetCode: '',

    // Form Integration
    hiddenFieldId: 'eligibilityScript',
    rulesetCodeFieldId: 'rulesetCode',

    // UI Options
    height: '400px',           // Editor height
    showDictionary: true,      // Show field dictionary
    showSaveButton: true,      // Show save button

    // Callbacks
    onValidate: function(result) {
        console.log('Validation:', result);
    },
    onSave: function(result) {
        console.log('Saved:', result);
    },
    onError: function(error) {
        console.error('Error:', error);
    }
});
```

---

## Creating Custom Themes

### Step 1: Create Theme CSS

Create `erel-theme-corporate.css`:

```css
/* Corporate Blue Theme */
.jre-theme-corporate .jre-editor-container {
    border-color: #004085;
    border-radius: 0;
}

.jre-theme-corporate .jre-toolbar {
    background: linear-gradient(to bottom, #004085, #002752);
    border-bottom-color: #002752;
}

.jre-theme-corporate .jre-toolbar button {
    background: #fff;
    color: #004085;
}

.jre-theme-corporate .jre-btn-primary {
    background: #ffc107;
    border-color: #ffc107;
    color: #000;
}

.jre-theme-corporate .CodeMirror {
    font-family: "Consolas", "Courier New", monospace;
}

.jre-theme-corporate .cm-keyword { color: #004085; }
.jre-theme-corporate .cm-string { color: #28a745; }
```

### Step 2: Apply Theme

```html
<div id="editor-container" class="erel-theme-corporate"></div>

<script>
    JREEditor.init('editor-container', { /* options */ });
</script>
```

### Step 3: Theme Switcher (Optional)

```javascript
// Add to jre-editor.js

function setTheme(themeName) {
    var container = document.getElementById('editor-container');
    container.className = container.className.replace(/erel-theme-\w+/g, '');
    if (themeName) {
        container.classList.add('erel-theme-' + themeName);
    }
}

// Usage
setTheme('corporate');
setTheme('dark');
setTheme(null);  // Default
```

---

## Joget Form Integration

### Full-Width Layout Override

Joget forms use a 30%/70% label/value layout. The editor overrides this:

```css
/* Override Joget's default layout */
.jre-form-cell-fullwidth {
    display: block !important;
    width: 100% !important;
    float: none !important;
}

.jre-form-cell-fullwidth > .label {
    display: block !important;
    width: 100% !important;
    margin-bottom: 8px;
}

.jre-form-cell-fullwidth > .form-cell-value {
    display: block !important;
    width: 100% !important;
    margin-left: 0 !important;
}

/* Higher specificity for stubborn overrides */
body .form-container .form-cell.jre-form-cell-fullwidth > .form-cell-value {
    width: 100% !important;
    max-width: 100% !important;
}
```

### FreeMarker Template Customization

Edit `templates/RuleEditorElement.ftl`:

```html
<div id="${elementParamName!}_container" class="jre-editor-container">
    <!-- Loading indicator -->
    <div class="erel-loading">Loading editor...</div>
</div>

<script>
    (function() {
        // Custom initialization
        var options = {
            apiBase: '${request.contextPath}/api/erel/erel',
            apiId: '${element.properties.apiId!}',
            apiKey: '${element.properties.apiKey!}',
            scopeCode: '${element.properties.scopeCode!}',

            // Custom options
            height: '${element.properties.editorHeight!"400px"}',
            showDictionary: ${element.properties.showDictionary!true},
            showSaveButton: ${element.properties.showSaveButton!true}
        };

        // Wait for scripts to load
        var checkReady = setInterval(function() {
            if (typeof JREEditor !== 'undefined' && typeof CodeMirror !== 'undefined') {
                clearInterval(checkReady);
                JREEditor.init('${elementParamName!}_container', options);
            }
        }, 100);
    })();
</script>
```

### Form Element Properties

Edit `properties/RuleEditorElement.json` to add UI options:

```json
[
    {
        "title": "Editor Settings",
        "properties": [
            {
                "name": "editorHeight",
                "label": "Editor Height",
                "type": "textfield",
                "value": "400px"
            },
            {
                "name": "showDictionary",
                "label": "Show Field Dictionary",
                "type": "checkbox",
                "value": "true"
            },
            {
                "name": "theme",
                "label": "Theme",
                "type": "selectbox",
                "options": [
                    {"value": "", "label": "Default"},
                    {"value": "dark", "label": "Dark"},
                    {"value": "corporate", "label": "Corporate"}
                ]
            }
        ]
    }
]
```

---

## CSS Variable Reference

For easier theming, consider using CSS variables:

```css
:root {
    /* Colors */
    --erel-primary: #007bff;
    --erel-success: #28a745;
    --erel-danger: #dc3545;
    --erel-warning: #ffc107;
    --erel-info: #17a2b8;

    /* Backgrounds */
    --erel-bg-light: #f8f9fa;
    --erel-bg-dark: #1e1e1e;

    /* Text */
    --erel-text-primary: #212529;
    --erel-text-muted: #6c757d;

    /* Syntax */
    --erel-syntax-keyword: #0000ff;
    --erel-syntax-string: #22863a;
    --erel-syntax-number: #005cc5;
    --erel-syntax-comment: #6a737d;

    /* Spacing */
    --erel-border-radius: 6px;
    --erel-spacing-sm: 8px;
    --erel-spacing-md: 16px;
}

.jre-editor-container {
    border-radius: var(--erel-border-radius);
}

.cm-s-default .cm-keyword {
    color: var(--erel-syntax-keyword);
}
```

---

## Next Steps

- [EXTENDING-GRAMMAR.md](./EXTENDING-GRAMMAR.md) - Adding new language features
- [INTEGRATION.md](./INTEGRATION.md) - Integrating with other systems
- [DEVELOPER.md](./DEVELOPER.md) - General developer guide
