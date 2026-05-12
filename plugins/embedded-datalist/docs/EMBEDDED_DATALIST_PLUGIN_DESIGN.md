# Embedded Datalist Form Element Plugin - Design Document

## 1. Overview

### Plugin Name
**Embedded Datalist** (or "Inline Datalist")

### Plugin ID
`org.joget.marketplace.EmbeddedDatalist`

### Category
Form Element (appears in Form Builder palette under "Advanced" or custom category)

### Purpose
Display a read-only datalist inline within a form, with the ability to filter the datalist based on form field values. This solves the limitation where ListGrid + JDBC Datalist Binder don't work together in AJAX popup context.

### Problem Statement
Currently in Joget:
- **ListGrid** is designed for user selection from a popup, not inline display
- **ListGrid + JDBC Datalist Binder** doesn't work properly (popup shows empty)
- No native way to embed a read-only datalist filtered by parent form values
- Common use case: Show child records (e.g., farmer's parcels) in a parent form tab

### Target Users
- Joget developers building master-detail forms
- Multi-page forms needing read-only related data display
- Any scenario requiring filtered datalist embedded in a form

---

## 2. Use Cases

### UC1: Farmer's Land Parcels
Display all land parcels belonging to a farmer within the farmer registration form.
- Filter: `farm_id` = parent form's record ID (passed via `#requestParam.farm_id#` in SQL)
- Read-only display
- See `docs/f01.08.json` and `docs/f01.08-list.json` for working example

### UC2: Order Line Items
Show all line items for an order in the order details form.
- Filter: `orderId` = current order ID

### UC3: Employee Training History
Display completed trainings for an employee in their profile form.
- Filter: `employeeId` = employee record ID

### UC4: Audit Trail / History Log
Show all history entries related to a record.
- Filter: `recordId` = current record ID

---

## 3. Configuration Properties

### Basic Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `id` | TextField | Yes | Element ID |
| `label` | TextField | No | Label displayed above the datalist |
| `datalistId` | SelectBox (Datalist) | Yes | The datalist to embed |

### Filter Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `filterParams` | Grid | No | Map form fields to datalist filter parameters |
| - `paramName` | TextField | Yes | Request parameter name for datalist filter |
| - `fieldId` | TextField | Yes | Form field ID to get value from |
| - `defaultValue` | TextField | No | Default value if field is empty |

#### JDBC Datalist Binder - Filter Setup

When using JDBC Datalist Binder, use the `#requestParam.paramName#` hash variable in your SQL query with a conditional WHERE clause:

```sql
SELECT id, column1, column2
FROM my_table
WHERE ('#requestParam.farm_id#' = '' OR farm_id = '#requestParam.farm_id#')
```

**Why the conditional clause?**
- `#requestParam.farm_id#` is empty during design time in Datalist Builder
- Without the condition, the query fails and UI cannot display columns
- The `'#requestParam.farm_id#' = ''` part returns all rows when empty (design time)
- At runtime with a value, it filters correctly

**Important Notes:**
- Do NOT use `{filter_name}` syntax - it requires defining filters in the Datalist UI which is complex
- The `#requestParam.xxx#` approach reads URL parameters directly
- Wrap the hash variable in single quotes for string comparison

### Display Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `height` | TextField | No | Height (e.g., "400px", "auto") |
| `showPagination` | Checkbox | No | Show pagination controls |
| `pageSize` | TextField | No | Rows per page (default: 10) |
| `showExport` | Checkbox | No | Show CSV/Excel export links |
| `showFilter` | Checkbox | No | Show datalist filter controls |
| `emptyMessage` | TextField | No | Message when no records found |

### Advanced Configuration

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `refreshOnChange` | TextField | No | Comma-separated field IDs that trigger refresh |
| `customCss` | TextArea | No | Custom CSS styles |
| `rowClickAction` | SelectBox | No | Action on row click (none/popup/redirect) |
| `rowClickFormId` | SelectBox (Form) | No | Form to open on row click |

---

## 4. Technical Architecture

### Class Hierarchy
```
org.joget.apps.form.model.Element (abstract)
    └── org.joget.marketplace.EmbeddedDatalist
```

### Key Components

```
joget-embedded-datalist/
├── pom.xml
├── src/main/java/org/joget/marketplace/
│   ├── EmbeddedDatalist.java          # Main plugin class
│   └── Activator.java                  # OSGi activator
├── src/main/resources/
│   ├── templates/
│   │   └── embeddedDatalist.ftl        # FreeMarker template
│   ├── properties/
│   │   └── embeddedDatalist.json       # Plugin properties definition
│   └── messages/
│       └── embeddedDatalist.properties # i18n messages
```

### Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         Form Load                                │
├─────────────────────────────────────────────────────────────────┤
│ 1. Form loads with EmbeddedDatalist element                     │
│ 2. EmbeddedDatalist.renderTemplate() called                     │
│ 3. Read filter field values from FormData                       │
│ 4. Build datalist URL with filter parameters                    │
│ 5. Render HTML container + JavaScript                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Client-Side (JavaScript)                      │
├─────────────────────────────────────────────────────────────────┤
│ 1. On DOM ready, AJAX call to JSON API:                         │
│    /jw/web/json/data/app/{appId}/{version}/datalist/{listId}    │
│    ?param1=value1&param2=value2                                 │
│ 2. Receive JSON response with data array                        │
│ 3. Render HTML table with data                                  │
│ 4. (Optional) Bind refresh listeners to form fields             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Implementation Details

### 5.1 Main Plugin Class: EmbeddedDatalist.java

```java
package org.joget.marketplace;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import java.util.*;

public class EmbeddedDatalist extends Element {
    
    @Override
    public String getName() {
        return "Embedded Datalist";
    }
    
    @Override
    public String getVersion() {
        return "8.0.0";
    }
    
    @Override
    public String getDescription() {
        return "Display a read-only datalist inline within a form";
    }
    
    @Override
    public String getLabel() {
        return "Embedded Datalist";
    }
    
    @Override
    public String getClassName() {
        return getClass().getName();
    }
    
    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), 
            "/properties/embeddedDatalist.json", null, true, 
            "/messages/embeddedDatalist");
    }
    
    @Override
    public String getFormBuilderCategory() {
        return "Advanced";  // Or "Joget Marketplace"
    }
    
    @Override
    public String getFormBuilderIcon() {
        return "/plugin/org.joget.marketplace.EmbeddedDatalist/images/icon.png";
    }
    
    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "embeddedDatalist.ftl";
        
        // Get configuration
        String datalistId = getPropertyString("datalistId");
        String height = getPropertyString("height");
        String emptyMessage = getPropertyString("emptyMessage");
        boolean showPagination = "true".equals(getPropertyString("showPagination"));
        String pageSize = getPropertyString("pageSize");
        
        // Build filter parameters from form field values
        Map<String, String> filterParams = buildFilterParams(formData);
        
        // Get app context
        String appId = AppUtil.getCurrentAppDefinition().getAppId();
        String appVersion = String.valueOf(AppUtil.getCurrentAppDefinition().getVersion());
        
        // Add to data model
        dataModel.put("datalistId", datalistId);
        dataModel.put("filterParams", filterParams);
        dataModel.put("appId", appId);
        dataModel.put("appVersion", appVersion);
        dataModel.put("height", height != null && !height.isEmpty() ? height : "400px");
        dataModel.put("emptyMessage", emptyMessage != null && !emptyMessage.isEmpty() 
            ? emptyMessage : "No records found.");
        dataModel.put("showPagination", showPagination);
        dataModel.put("pageSize", pageSize != null && !pageSize.isEmpty() ? pageSize : "10");
        dataModel.put("elementId", getPropertyString("id"));
        dataModel.put("elementUniqueKey", FormUtil.getElementUniqueKey(this));
        
        // Refresh on change fields
        String refreshOnChange = getPropertyString("refreshOnChange");
        dataModel.put("refreshOnChange", refreshOnChange);
        
        return FormUtil.generateElementHtml(this, formData, template, dataModel);
    }
    
    private Map<String, String> buildFilterParams(FormData formData) {
        Map<String, String> params = new LinkedHashMap<>();
        
        Object[] filterParamsConfig = (Object[]) getProperty("filterParams");
        if (filterParamsConfig != null) {
            for (Object obj : filterParamsConfig) {
                Map<String, String> row = (Map<String, String>) obj;
                String paramName = row.get("paramName");
                String fieldId = row.get("fieldId");
                String defaultValue = row.get("defaultValue");
                
                // Get value from form data
                String value = formData.getRequestParameter(fieldId);
                if (value == null || value.isEmpty()) {
                    // Try to get from loaded data
                    value = formData.getLoadBinderDataProperty(this, fieldId);
                }
                if (value == null || value.isEmpty()) {
                    value = defaultValue;
                }
                
                if (paramName != null && !paramName.isEmpty()) {
                    params.put(paramName, value != null ? value : "");
                }
            }
        }
        
        return params;
    }
    
    @Override
    public FormRowSet formatData(FormData formData) {
        // Read-only element, no data to store
        return null;
    }
}
```

### 5.2 FreeMarker Template: embeddedDatalist.ftl

```html
<div class="form-cell embedded-datalist-container" ${elementMetaData!}>
    <#if element.properties.label?? && element.properties.label != "">
        <label class="label">${element.properties.label}</label>
    </#if>
    
    <div id="embedded-datalist-${elementUniqueKey}" 
         class="embedded-datalist-content"
         style="min-height: ${height};">
        <div class="embedded-datalist-loading">
            <i class="fas fa-spinner fa-spin"></i> Loading...
        </div>
    </div>
    
    <#if !(request.getAttribute("embeddedDatalistCssLoaded")??)>
        ${request.setAttribute("embeddedDatalistCssLoaded", true)}
        <style>
            .embedded-datalist-container { margin-bottom: 15px; }
            .embedded-datalist-content { 
                border: 1px solid #ddd; 
                border-radius: 4px; 
                overflow: auto;
            }
            .embedded-datalist-loading { 
                padding: 40px; 
                text-align: center; 
                color: #666; 
            }
            .embedded-datalist-table { 
                width: 100%; 
                border-collapse: collapse; 
            }
            .embedded-datalist-table th { 
                background: #f5f5f5; 
                border: 1px solid #ddd; 
                padding: 10px 12px; 
                text-align: left; 
                font-weight: 600;
                position: sticky;
                top: 0;
            }
            .embedded-datalist-table td { 
                border: 1px solid #ddd; 
                padding: 8px 12px; 
            }
            .embedded-datalist-table tr:nth-child(even) { background: #fafafa; }
            .embedded-datalist-table tr:hover { background: #f0f7ff; }
            .embedded-datalist-empty { 
                padding: 30px; 
                text-align: center; 
                color: #999; 
                font-style: italic; 
            }
            .embedded-datalist-error { 
                padding: 20px; 
                color: #c00; 
                background: #fff0f0; 
            }
            .embedded-datalist-footer {
                padding: 10px;
                background: #f9f9f9;
                border-top: 1px solid #ddd;
                font-size: 12px;
                color: #666;
            }
            .embedded-datalist-pagination {
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
        </style>
    </#if>
    
    <script>
    $(document).ready(function() {
        var containerId = 'embedded-datalist-${elementUniqueKey}';
        var container = $('#' + containerId);
        var datalistId = '${datalistId}';
        var appId = '${appId}';
        var appVersion = '${appVersion}';
        var pageSize = parseInt('${pageSize}') || 10;
        var emptyMessage = '${emptyMessage?js_string}';
        
        // Filter parameters from form fields
        var filterConfig = [
            <#if filterParams??>
            <#list filterParams?keys as key>
            { param: '${key}', value: '${filterParams[key]!}' }<#if key_has_next>,</#if>
            </#list>
            </#if>
        ];
        
        function loadDatalist(page) {
            page = page || 1;
            
            // Build URL with current field values
            var params = [];
            $.each(filterConfig, function(i, cfg) {
                var fieldVal = cfg.value;
                // Try to get current form field value
                var $field = $('[name="' + cfg.param + '"], [name$="_' + cfg.param + '"]');
                if ($field.length > 0) {
                    fieldVal = $field.val() || cfg.value;
                }
                if (fieldVal) {
                    params.push(cfg.param + '=' + encodeURIComponent(fieldVal));
                }
            });
            
            // Add pagination
            params.push('d-page=' + page);
            params.push('d-rows=' + pageSize);
            
            var apiUrl = '${request.contextPath}/web/json/data/app/' + appId + '/' + 
                         appVersion + '/datalist/' + datalistId;
            if (params.length > 0) {
                apiUrl += '?' + params.join('&');
            }
            
            container.html('<div class="embedded-datalist-loading"><i class="fas fa-spinner fa-spin"></i> Loading...</div>');
            
            $.ajax({
                url: apiUrl,
                type: 'GET',
                dataType: 'json',
                success: function(response) {
                    renderDatalist(response, page);
                },
                error: function(xhr, status, error) {
                    container.html('<div class="embedded-datalist-error">Error loading data: ' + error + '</div>');
                }
            });
        }
        
        function renderDatalist(response, currentPage) {
            if (!response || !response.data || response.data.length === 0) {
                container.html('<div class="embedded-datalist-empty">' + emptyMessage + '</div>');
                return;
            }
            
            var html = '<table class="embedded-datalist-table">';
            
            // Headers - use column names from response
            html += '<thead><tr>';
            if (response.columns) {
                $.each(response.columns, function(i, col) {
                    html += '<th>' + (col.label || col.name) + '</th>';
                });
            } else {
                // Fallback: use keys from first row
                $.each(response.data[0], function(key, val) {
                    if (key !== 'id') {
                        html += '<th>' + key + '</th>';
                    }
                });
            }
            html += '</tr></thead>';
            
            // Data rows
            html += '<tbody>';
            $.each(response.data, function(i, row) {
                html += '<tr data-id="' + (row.id || '') + '">';
                if (response.columns) {
                    $.each(response.columns, function(j, col) {
                        html += '<td>' + (row[col.name] || '-') + '</td>';
                    });
                } else {
                    $.each(row, function(key, val) {
                        if (key !== 'id') {
                            html += '<td>' + (val || '-') + '</td>';
                        }
                    });
                }
                html += '</tr>';
            });
            html += '</tbody></table>';
            
            // Footer with count and pagination
            var total = response.total || response.data.length;
            html += '<div class="embedded-datalist-footer">';
            html += '<div class="embedded-datalist-pagination">';
            html += '<span>Showing ' + response.data.length + ' of ' + total + ' record(s)</span>';
            
            <#if showPagination>
            if (total > pageSize) {
                var totalPages = Math.ceil(total / pageSize);
                html += '<span>';
                if (currentPage > 1) {
                    html += '<a href="#" class="edl-page" data-page="' + (currentPage-1) + '">&laquo; Prev</a> ';
                }
                html += 'Page ' + currentPage + ' of ' + totalPages;
                if (currentPage < totalPages) {
                    html += ' <a href="#" class="edl-page" data-page="' + (currentPage+1) + '">Next &raquo;</a>';
                }
                html += '</span>';
            }
            </#if>
            
            html += '</div></div>';
            
            container.html(html);
            
            // Bind pagination clicks
            container.find('.edl-page').click(function(e) {
                e.preventDefault();
                loadDatalist($(this).data('page'));
            });
        }
        
        // Initial load
        setTimeout(function() {
            loadDatalist(1);
        }, 200);
        
        // Refresh on field change
        <#if refreshOnChange?? && refreshOnChange != "">
        var refreshFields = '${refreshOnChange}'.split(',');
        $.each(refreshFields, function(i, fieldId) {
            fieldId = $.trim(fieldId);
            $(document).on('change', '[name="' + fieldId + '"], [name$="_' + fieldId + '"]', function() {
                loadDatalist(1);
            });
        });
        </#if>
    });
    </script>
</div>
```

### 5.3 Properties Definition: embeddedDatalist.json

```json
[{
    "title": "Configure Embedded Datalist",
    "properties": [{
        "name": "id",
        "label": "ID",
        "type": "textfield",
        "required": "True",
        "regex_validation": "^[a-zA-Z0-9_]+$",
        "validation_message": "Invalid ID"
    }, {
        "name": "label",
        "label": "Label",
        "type": "textfield"
    }, {
        "name": "datalistId",
        "label": "Datalist",
        "type": "selectbox",
        "required": "True",
        "options_ajax": "[CONTEXT_PATH]/web/json/console/app[APP_PATH]/datalist/options"
    }]
}, {
    "title": "Filter Parameters",
    "properties": [{
        "name": "filterParams",
        "label": "Filter Parameters",
        "description": "Map form field values to datalist request parameters",
        "type": "grid",
        "columns": [{
            "key": "paramName",
            "label": "Parameter Name"
        }, {
            "key": "fieldId", 
            "label": "Form Field ID"
        }, {
            "key": "defaultValue",
            "label": "Default Value"
        }]
    }]
}, {
    "title": "Display Options",
    "properties": [{
        "name": "height",
        "label": "Height",
        "type": "textfield",
        "value": "400px",
        "description": "e.g., 400px, 50vh, auto"
    }, {
        "name": "showPagination",
        "label": "Show Pagination",
        "type": "checkbox",
        "value": "true",
        "options": [{"value": "true", "label": ""}]
    }, {
        "name": "pageSize",
        "label": "Page Size",
        "type": "textfield",
        "value": "10"
    }, {
        "name": "emptyMessage",
        "label": "Empty Message",
        "type": "textfield",
        "value": "No records found."
    }]
}, {
    "title": "Advanced",
    "properties": [{
        "name": "refreshOnChange",
        "label": "Refresh on Field Change",
        "type": "textfield",
        "description": "Comma-separated field IDs that trigger datalist refresh"
    }, {
        "name": "customCss",
        "label": "Custom CSS",
        "type": "textarea"
    }]
}]
```

---

## 6. Plugin Properties JSON (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.joget.marketplace</groupId>
    <artifactId>embedded-datalist</artifactId>
    <version>8.0.0</version>
    <packaging>bundle</packaging>
    <name>Embedded Datalist Form Element</name>
    
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <joget.version>8.0-SNAPSHOT</joget.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.joget</groupId>
            <artifactId>wflow-core</artifactId>
            <version>${joget.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.felix</groupId>
                <artifactId>maven-bundle-plugin</artifactId>
                <version>5.1.1</version>
                <extensions>true</extensions>
                <configuration>
                    <instructions>
                        <Bundle-SymbolicName>${project.artifactId}</Bundle-SymbolicName>
                        <Bundle-Version>${project.version}</Bundle-Version>
                        <Import-Package>
                            org.joget.apps.form.model,
                            org.joget.apps.form.service,
                            org.joget.apps.app.service,
                            org.joget.commons.util,
                            javax.servlet.http,
                            *;resolution:=optional
                        </Import-Package>
                        <Embed-Dependency>*;scope=compile|runtime;inline=true</Embed-Dependency>
                    </instructions>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 7. Testing Scenarios

| Scenario | Expected Result |
|----------|-----------------|
| Form preview (no record) | Shows all datalist records or empty message |
| Form with record ID | Shows filtered datalist records |
| Empty datalist | Shows configured empty message |
| Pagination | Navigate between pages |
| Refresh on field change | Datalist reloads when trigger field changes |
| JDBC Datalist Binder | Works correctly (unlike ListGrid) |
| Form Data Binder | Works correctly |
| Multi-page form | Works in all tabs |

---

## 8. Future Enhancements

1. **Row Actions**: Click to open form in popup
2. **Column Selection**: Choose which columns to display
3. **Column Formatting**: Number, date, options formatting
4. **Export**: CSV/Excel export buttons
5. **Search**: Inline search filter
6. **Sorting**: Click column headers to sort
7. **Conditional Styling**: Row/cell colors based on values


## Key API References

### Datalist JSON API
```
GET /jw/web/json/data/app/{appId}/{version}/datalist/{datalistId}?param1=value1&param2=value2
```

Response format:
```json
{
  "total": 100,
  "data": [
    {"id": "...", "column1": "value1", "column2": "value2"},
    ...
  ]
}
```

### Joget Classes to Import
```java
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.model.AppDefinition;
import org.joget.commons.util.LogUtil;
```

## Testing Checklist
- [ ] Form preview shows datalist (without filter)
- [ ] Form with record ID shows filtered datalist
- [ ] Empty result shows empty message
- [ ] Pagination works
- [ ] JDBC Datalist Binder works
- [ ] Form Data Binder works
- [ ] Multi-page form integration works
- [ ] Refresh on field change works

## Joget Resources
- Plugin Development Guide: https://dev.joget.org/community/display/DX8/Developing+Plugins
- Form Field Element Plugin: https://dev.joget.org/community/display/DX8/Form+Field+Element+Plugin
- JSON API: https://dev.joget.org/community/display/DX8/JSON+API
- Joget Community Source: /Users/aarelaponin/IdeaProjects/joget/jw-community
- Joget API Builder source: /Users/aarelaponin/IdeaProjects/joget/api-builder
- my previous UI plugin example: /Users/aarelaponin/IdeaProjects/plugins/joget-gis-ui 

