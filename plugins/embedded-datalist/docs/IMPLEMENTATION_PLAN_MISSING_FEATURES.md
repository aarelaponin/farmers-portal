# Implementation Plan: Missing Features for Embedded Datalist Plugin

## Context

The Embedded Datalist plugin design document (`docs/EMBEDDED_DATALIST_PLUGIN_DESIGN.md`) specifies 4 features in the Configuration Properties section that were not implemented:

1. **showExport** - Show CSV/Excel export links
2. **showFilter** - Show datalist filter controls
3. **rowClickAction** - Row click behavior (none/popup/redirect)
4. **rowClickFormId** - Form to open on row click

These features are listed in Section 3 (Configuration Properties) but are missing from the actual implementation.

---

## Files to Modify

| File | Path | Changes |
|------|------|---------|
| Property definitions | `src/main/resources/properties/embeddedDatalist.json` | Add 4 new properties |
| i18n messages | `src/main/resources/messages/EmbeddedDatalist.properties` | Add labels for new properties |
| Java class | `src/main/java/org/joget/marketplace/EmbeddedDatalist.java` | Read properties, pass to template |
| FreeMarker template | `src/main/resources/templates/embeddedDatalist.ftl` | Add UI and JavaScript |

---

## Feature 1: showExport

### Purpose
Display CSV and Excel export links below the datalist table.

### Property Definition (embeddedDatalist.json)
Add to "Display Options" section:
```json
{
    "name": "showExport",
    "label": "@@embeddedDatalist.showExport@@",
    "type": "checkbox",
    "options": [{"value": "true", "label": ""}]
}
```

### i18n Message (EmbeddedDatalist.properties)
```properties
embeddedDatalist.showExport=Show Export Links
```

### Java Code (EmbeddedDatalist.java)
In `renderTemplate()` method, add:
```java
boolean showExport = "true".equals(getPropertyString("showExport"));
dataModel.put("showExport", showExport);
```

### Template Code (embeddedDatalist.ftl)
Add export links in the footer section. Joget's export URL pattern:
- CSV: `/web/json/data/app/{appId}/{version}/datalist/{listId}?_export=csv`
- Excel: `/web/json/data/app/{appId}/{version}/datalist/{listId}?_export=xls`

Include current filter parameters in export URL so exports respect active filters.

---

## Feature 2: showFilter

### Purpose
Display the datalist's filter controls above the table, allowing users to filter data dynamically.

### Technical Background
Joget datalist filters use specific conventions:
- **Parameter prefix**: `fn_` (e.g., `fn_status=active`)
- **Multiple values**: Semicolon-separated (e.g., `fn_category=a;b;c`)
- **Search type modifier**: `fn_{name}_searchType=startsWith|endsWith|exact|any`

### Property Definition (embeddedDatalist.json)
Add to "Display Options" section:
```json
{
    "name": "showFilter",
    "label": "@@embeddedDatalist.showFilter@@",
    "type": "checkbox",
    "options": [{"value": "true", "label": ""}]
}
```

### i18n Messages (EmbeddedDatalist.properties)
```properties
embeddedDatalist.showFilter=Show Filter Controls
embeddedDatalist.filterButton=Filter
embeddedDatalist.clearFilterButton=Clear
```

### Java Code (EmbeddedDatalist.java)
In `renderTemplate()` method, add:
```java
boolean showFilter = "true".equals(getPropertyString("showFilter"));
dataModel.put("showFilter", showFilter);
```

### Template Code (embeddedDatalist.ftl)
Implementation approach:
1. Fetch datalist definition to get filter metadata (filter names, labels, types)
2. Render filter input fields dynamically based on filter configuration
3. Add "Filter" and "Clear" buttons
4. Collect filter values and include in API calls with `fn_` prefix

CSS for filter bar:
```css
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
}
.edl-filter-field input,
.edl-filter-field select {
    padding: 6px 8px;
    border: 1px solid #ccc;
    border-radius: 3px;
}
```

---

## Feature 3: rowClickAction

### Purpose
Define the behavior when a user clicks on a table row: do nothing, open a form in popup, or redirect to a form.

### Property Definition (embeddedDatalist.json)
Add to "Advanced" section:
```json
{
    "name": "rowClickAction",
    "label": "@@embeddedDatalist.rowClickAction@@",
    "type": "selectbox",
    "options": [
        {"value": "", "label": "@@embeddedDatalist.rowClickAction.none@@"},
        {"value": "popup", "label": "@@embeddedDatalist.rowClickAction.popup@@"},
        {"value": "redirect", "label": "@@embeddedDatalist.rowClickAction.redirect@@"}
    ]
}
```

### i18n Messages (EmbeddedDatalist.properties)
```properties
embeddedDatalist.rowClickAction=Row Click Action
embeddedDatalist.rowClickAction.none=None
embeddedDatalist.rowClickAction.popup=Open Form in Popup
embeddedDatalist.rowClickAction.redirect=Redirect to Form
```

### Java Code (EmbeddedDatalist.java)
In `renderTemplate()` method, add:
```java
String rowClickAction = getPropertyString("rowClickAction");
dataModel.put("rowClickAction", rowClickAction != null ? rowClickAction : "");
```

### Template Code (embeddedDatalist.ftl)
1. Add CSS for clickable rows (cursor: pointer) when action is configured
2. Add click handler to table rows that:
   - Gets row ID from `data-id` attribute
   - For popup: Uses `JPopup.show()` to open form dialog
   - For redirect: Navigates to form URL

---

## Feature 4: rowClickFormId

### Purpose
Specify which form to open when a row is clicked (used with popup or redirect action).

### Property Definition (embeddedDatalist.json)
Add to "Advanced" section, after rowClickAction:
```json
{
    "name": "rowClickFormId",
    "label": "@@embeddedDatalist.rowClickFormId@@",
    "type": "selectbox",
    "options_ajax": "[CONTEXT_PATH]/web/json/console/app[APP_PATH]/form/options",
    "control_field": "rowClickAction",
    "control_value": "popup,redirect"
}
```

Note: `control_field` and `control_value` make this field only visible when rowClickAction is "popup" or "redirect".

### i18n Message (EmbeddedDatalist.properties)
```properties
embeddedDatalist.rowClickFormId=Form to Open
```

### Java Code (EmbeddedDatalist.java)
In `renderTemplate()` method, add:
```java
String rowClickFormId = getPropertyString("rowClickFormId");
dataModel.put("rowClickFormId", rowClickFormId != null ? rowClickFormId : "");
```

### Template Code (embeddedDatalist.ftl)
Use the form ID in the row click handler:
- Popup URL: `/web/app/{appId}/{version}/form/embed?_id={rowId}&_formId={formId}`
- Redirect URL: `/web/userview/{appId}/{version}/_/form/{formId}?id={rowId}`

---

## Implementation Order

1. **showExport** - Simplest feature, just adds export links
2. **rowClickAction + rowClickFormId** - Related features, implement together
3. **showFilter** - Most complex, requires fetching filter metadata and dynamic UI

---

## Complete Property Definition

Here is the full updated `embeddedDatalist.json`:

```json
[
    {
        "title": "@@embeddedDatalist.basicSettings@@",
        "properties": [
            {"name": "id", "label": "@@embeddedDatalist.id@@", "type": "textfield", "required": "true", "regex_validation": "^[a-zA-Z0-9_]+$", "validation_message": "@@embeddedDatalist.id.validation@@"},
            {"name": "label", "label": "@@embeddedDatalist.label@@", "type": "textfield"},
            {"name": "datalistId", "label": "@@embeddedDatalist.datalistId@@", "type": "selectbox", "required": "true", "options_ajax": "[CONTEXT_PATH]/web/json/console/app[APP_PATH]/datalist/options"}
        ]
    },
    {
        "title": "@@embeddedDatalist.filterParameters@@",
        "properties": [
            {"name": "filterParams", "label": "@@embeddedDatalist.filterParams@@", "description": "@@embeddedDatalist.filterParams.description@@", "type": "grid", "columns": [
                {"key": "paramName", "label": "@@embeddedDatalist.filterParams.paramName@@"},
                {"key": "fieldId", "label": "@@embeddedDatalist.filterParams.fieldId@@"},
                {"key": "defaultValue", "label": "@@embeddedDatalist.filterParams.defaultValue@@"}
            ]}
        ]
    },
    {
        "title": "@@embeddedDatalist.displayOptions@@",
        "properties": [
            {"name": "height", "label": "@@embeddedDatalist.height@@", "type": "textfield", "value": "400px", "description": "@@embeddedDatalist.height.description@@"},
            {"name": "showPagination", "label": "@@embeddedDatalist.showPagination@@", "type": "checkbox", "options": [{"value": "true", "label": ""}], "value": "true"},
            {"name": "pageSize", "label": "@@embeddedDatalist.pageSize@@", "type": "textfield", "value": "10"},
            {"name": "showFilter", "label": "@@embeddedDatalist.showFilter@@", "type": "checkbox", "options": [{"value": "true", "label": ""}]},
            {"name": "showExport", "label": "@@embeddedDatalist.showExport@@", "type": "checkbox", "options": [{"value": "true", "label": ""}]},
            {"name": "emptyMessage", "label": "@@embeddedDatalist.emptyMessage@@", "type": "textfield", "value": "No records found."}
        ]
    },
    {
        "title": "@@embeddedDatalist.advanced@@",
        "properties": [
            {"name": "refreshOnChange", "label": "@@embeddedDatalist.refreshOnChange@@", "type": "textfield", "description": "@@embeddedDatalist.refreshOnChange.description@@"},
            {"name": "rowClickAction", "label": "@@embeddedDatalist.rowClickAction@@", "type": "selectbox", "options": [
                {"value": "", "label": "@@embeddedDatalist.rowClickAction.none@@"},
                {"value": "popup", "label": "@@embeddedDatalist.rowClickAction.popup@@"},
                {"value": "redirect", "label": "@@embeddedDatalist.rowClickAction.redirect@@"}
            ]},
            {"name": "rowClickFormId", "label": "@@embeddedDatalist.rowClickFormId@@", "type": "selectbox", "options_ajax": "[CONTEXT_PATH]/web/json/console/app[APP_PATH]/form/options", "control_field": "rowClickAction", "control_value": "popup,redirect"},
            {"name": "customCss", "label": "@@embeddedDatalist.customCss@@", "type": "textarea", "description": "@@embeddedDatalist.customCss.description@@"}
        ]
    }
]
```

---

## Verification Plan

After implementation:

1. **Build**: `mvn clean package`
2. **Deploy**: Upload `target/embedded-datalist-8.1-SNAPSHOT.jar` to Joget Admin → Manage Plugins
3. **Test each feature**:
   - showExport: Enable checkbox, verify CSV/Excel links appear and download works
   - showFilter: Enable checkbox, verify filter inputs appear and filtering works
   - rowClickAction (popup): Select "Open Form in Popup", choose a form, click row, verify popup opens
   - rowClickAction (redirect): Select "Redirect to Form", choose a form, click row, verify navigation

---

## Estimated Scope

| File | Lines Added |
|------|-------------|
| embeddedDatalist.json | ~35 |
| EmbeddedDatalist.properties | ~15 |
| EmbeddedDatalist.java | ~20 |
| embeddedDatalist.ftl | ~150 |
| **Total** | **~220 lines** |
