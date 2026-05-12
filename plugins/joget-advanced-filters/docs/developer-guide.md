# Developer Guide

This guide covers the architecture, build process, and extension patterns for the Advanced Filters plugin bundle.

## Project Structure

```
joget-advanced-filters/
├── pom.xml                                    # Maven config (OSGi bundle packaging)
├── CLAUDE.md                                  # AI assistant context
├── README.md                                  # Project landing page
├── docs/
│   ├── admin-guide.md                         # Admin/configuration guide
│   ├── developer-guide.md                     # This file
│   ├── images/
│   │   ├── default-filter-bar.png             # Before: default Joget filter bar
│   │   └── filter-panel-result.png            # After: styled filter panel
│   └── RPT-EXEC-001/
│       ├── RPT-EXEC-001-spec.md               # Reference report specification
│       ├── binder-sql.sql                     # JDBC binder SQL (full version)
│       ├── binder-sql-simple.sql              # JDBC binder SQL (without sub-centres/villages)
│       └── r02-farmers.json                   # DataList JSON definition
└── src/main/
    ├── java/org/joget/lst/
    │   ├── Activator.java                     # OSGi bundle activator
    │   ├── DateRangeFilterType.java           # Date range + panel decorator
    │   ├── MdmSelectFilterType.java           # MDM multi-select
    │   ├── CascadingMdmSelectFilterType.java  # Cascading MDM multi-select
    │   └── FilterPanelDecorator.java          # Standalone panel (legacy)
    └── resources/
        ├── properties/
        │   ├── dateRangeFilterType.json       # Plugin property definitions
        │   ├── mdmSelectFilterType.json
        │   ├── cascadingMdmSelectFilterType.json
        │   └── filterPanelDecorator.json
        └── templates/
            ├── filterPanelDecorator.ftl        # Legacy FreeMarker template (unused)
            └── mdmSelectFilterType.ftl         # Legacy FreeMarker template (unused)
```

## Build & Dependencies

### Maven Configuration

The project uses Maven with the `maven-bundle-plugin` for OSGi bundle packaging:

```bash
mvn clean package
# Output: target/joget-advanced-filters-8.1.0.jar
```

### Key Dependencies

| Dependency | Version | Scope | Notes |
|---|---|---|---|
| `org.joget:wflow-core` | `8.1-SNAPSHOT` | `provided` | Core Joget API. Supplied by Joget at runtime. |
| `javax.servlet:javax.servlet-api` | `3.1.0` | `provided` | Excluded from wflow-core's transitive deps and re-added explicitly. |

Both dependencies are `provided` scope -- they are available in the Joget runtime classpath and must not be bundled in the JAR.

### OSGi Bundle Configuration

The `maven-bundle-plugin` configuration in `pom.xml`:

- **`Bundle-Activator`**: `org.joget.lst.Activator` -- entry point for plugin registration.
- **`Export-Package`**: empty -- nothing is exported to other bundles.
- **`Private-Package`**: `{local-packages}` -- all project classes are private to the bundle.
- **`Import-Package`**: explicitly lists Joget packages and OSGi framework, with `*;resolution:=optional` for everything else.
- **`DynamicImport-Package`**: `*` -- allows runtime resolution of additional packages.

## Plugin Registration

All plugins are registered in `Activator.java` via standard OSGi `ServiceRegistration`:

```java
public class Activator implements BundleActivator {
    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<>();
        registrationList.add(context.registerService(
            MdmSelectFilterType.class.getName(), new MdmSelectFilterType(), null));
        registrationList.add(context.registerService(
            CascadingMdmSelectFilterType.class.getName(), new CascadingMdmSelectFilterType(), null));
        registrationList.add(context.registerService(
            FilterPanelDecorator.class.getName(), new FilterPanelDecorator(), null));
        registrationList.add(context.registerService(
            DateRangeFilterType.class.getName(), new DateRangeFilterType(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
```

Each plugin class is registered as a service using its fully qualified class name. Joget discovers filter plugins by scanning registered services that extend `DataListFilterTypeDefault`.

## Architecture Patterns

### Why No FreeMarker

Although `.ftl` template files exist in `src/main/resources/templates/`, they are **not used** at runtime. All HTML is built via `StringBuilder` in each plugin's `getTemplate()` method.

Reason: FreeMarker template loading through OSGi classloaders is unreliable in Joget's plugin architecture. Raw HTML generation avoids classloader issues entirely and keeps each plugin self-contained.

### Hash-Variable Filtering

None of the plugins generate SQL directly. `getQueryObject()` returns `null` in all three plugins.

Instead, filtering works through Joget's **hash variable** mechanism:

1. Each plugin creates encoded request parameter names using `datalist.getDataListEncodedParamName()`.
2. These parameters are submitted as part of the filter form.
3. The JDBC binder SQL references these values using hash variables like `#requestParam.d-5814095-fn_date_from?sql#`.
4. Joget's hash variable processor substitutes the actual request parameter values into the SQL at query time.

This approach gives full control over the SQL (including PostgreSQL-specific functions like `string_to_array()`) and avoids the limitations of Joget's built-in query object model.

### Encoded Parameter Names

Joget's `DataList.getDataListEncodedParamName()` generates request parameter names that encode the DataList ID:

```java
String encoded = datalist.getDataListEncodedParamName(
    DataList.PARAMETER_FILTER_PREFIX + "date_from");
// Result: "d-5814095-fn_date_from" (for DataList ID "farmersOverview")
```

The DataList ID is hashed by Joget's `ParamEncoder` (e.g. `farmersOverview` -> `5814095`). The `PARAMETER_FILTER_PREFIX` adds the `fn_` prefix. These encoded names are what appear as request parameters and what you reference in SQL hash variables.

### Hidden Input Sync

Visible UI controls (date pickers, multi-select dropdowns) don't directly submit values. Instead:

1. A visible control (e.g. `<select multiple>`) lets the user interact.
2. A hidden `<input>` field with the encoded parameter name holds the actual submitted value.
3. JavaScript listens for `change` events on the visible control and syncs the value to the hidden input.
4. An additional sync runs on form `submit` to catch any edge cases.

This pattern is necessary because Joget's filter form submission relies on specific parameter name formats, and some HTML controls (like `<select multiple>`) don't natively submit values in the format needed (semicolon-separated).

### HTML/JS Escaping

All plugins include utility methods for safe output:

```java
private static String escHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
}

private static String escJs(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\"", "\\\"").replace("\n", "\\n");
}
```

`escHtml()` is used for attribute values and text content. `escJs()` is used for string literals inside `<script>` blocks.

---

## Plugin Deep-Dives

### DateRangeFilterType

**File:** `src/main/java/org/joget/lst/DateRangeFilterType.java`

Key methods:

- **`getTemplate()`** -- The main rendering method. Reads `fromParamSuffix` and `toParamSuffix` from properties, generates two encoded parameter names via `getDataListEncodedParamName()`, reads current values from the request, applies defaults, and builds the HTML with two `<input type="date">` pickers, hidden inputs, and sync JavaScript. If `panelTitle` is set, also calls `appendPanelCss()` and `appendPanelJs()`.

- **`appendPanelCss()`** -- Outputs the full `<style>` block for the filter panel. Handles panel chrome (header, body, chevron), grid layout (`grid-template-columns: repeat(N, 1fr)`), filter cell styling, type badges, input styling, and button styling.

- **`appendPanelJs()`** -- Outputs the `<script>` block that transforms the DOM. Finds the filter form via the date picker element, hides page-size and submit cells, counts active filters, builds the panel wrapper, adds type badges, and injects Apply/Reset buttons. Uses `jQuery(document).ready()` if jQuery is available, otherwise falls back to `DOMContentLoaded`.

- **`getQueryObject()`** -- Returns `null`. Filtering is handled by hash variables.

### MdmSelectFilterType

**File:** `src/main/java/org/joget/lst/MdmSelectFilterType.java`

Key methods:

- **`getTemplate()`** -- Generates a single encoded parameter name from the filter `name`, reads the current value (semicolon-separated string), parses it into a `Set<String>` for marking selected options, calls `loadOptions()`, and builds the HTML with a `<select multiple>`, clear button (×), hidden input, hint div, and sync JavaScript.

- **`loadOptions()`** -- Queries `app_fd_{tableName}` using Joget's `setupDataSource` bean. Builds the SQL as `SELECT {codeCol}, {nameCol} FROM app_fd_{tableName} ORDER BY {nameCol}`. Returns a list of maps with `value`, `label`, and `selected` keys. Uses `sanitize()` on column/table names to strip non-alphanumeric characters (preventing SQL injection for identifier names).

- **`sanitize()`** -- Strips anything that isn't `[a-zA-Z0-9_]` from SQL identifiers. This is the safety mechanism for dynamic table/column names that can't use parameterized queries.

- **`getQueryObject()`** -- Returns `null`.

### CascadingMdmSelectFilterType

**File:** `src/main/java/org/joget/lst/CascadingMdmSelectFilterType.java`

Extends the MDM multi-select pattern with parent-child cascading and visual grouping. Key additions:

- **`getTemplate()`** -- Similar to MdmSelectFilterType but:
  - Adds `data-parent-code` attributes to options when `parentCodeColumn` is configured
  - Wraps output in a `div.ro-cascading-filter` with data attributes for grouping
  - Generates JavaScript for filter registry, parent-child filtering, and bi-directional sync
  - Calls `appendGroupingJs()` for the first filter in a group

- **`loadOptions()`** -- Extended to optionally SELECT the parent code column and include it in option maps.

- **Filter Registry** -- `window.roFilterRegistry` maps filter names to element IDs, enabling cross-filter communication:
  ```javascript
  window.roFilterRegistry['district_name'] = 'fn_d_5814095_district_name';
  ```

- **Parent → Child Filtering** -- When parent selection changes, child options with non-matching `data-parent-code` are hidden/disabled:
  ```javascript
  parentSelEl.addEventListener('change', filterByParent);
  ```

- **Child → Parent Auto-Select** -- When a child is selected, its parent is automatically selected if not already:
  ```javascript
  selEl.addEventListener('change', function() { autoSelectParent(); sync(); });
  ```

- **`appendGroupingJs()`** -- DOM manipulation that visually groups filters:
  1. Finds all `.ro-cascading-filter` elements with matching `data-filter-group`
  2. Creates a wrapper div with `.ro-filter-group-wrapper` class
  3. Adds a group header with the group name and optional hint
  4. Moves filter content into the wrapper with → arrows between items
  5. Hides the original filter cells to maintain grid layout

- **Clear Button** -- Includes a × button that clears selections and dispatches a `change` event to trigger cascade updates.

### FilterPanelDecorator

**File:** `src/main/java/org/joget/lst/FilterPanelDecorator.java`

This is functionally identical to `DateRangeFilterType`'s panel feature but operates as a standalone plugin. Key differences:

- Uses a hidden `<span id="roFilterPanelMarker">` to find the filter form (instead of anchoring off a date picker element).
- Hides its own filter cell via `ro-decorator-cell` CSS class.
- Must be the last filter in the DataList so all other cells exist when JS runs.

This plugin exists for DataLists that need the panel styling but don't include a Date Range Filter.

---

## Extending the Bundle

### Adding a New Filter Plugin

1. **Create the plugin class** in `src/main/java/org/joget/lst/`:

   ```java
   package org.joget.lst;

   import org.joget.apps.app.service.AppUtil;
   import org.joget.apps.datalist.model.DataList;
   import org.joget.apps.datalist.model.DataListFilterQueryObject;
   import org.joget.apps.datalist.model.DataListFilterTypeDefault;

   public class MyNewFilterType extends DataListFilterTypeDefault {

       private static final String CLASS_NAME = MyNewFilterType.class.getName();

       @Override public String getName()        { return "My New Filter"; }
       @Override public String getVersion()     { return "8.1.0"; }
       @Override public String getDescription() { return "Description here"; }
       @Override public String getLabel()       { return "My New Filter"; }
       @Override public String getClassName()   { return CLASS_NAME; }

       @Override
       public String getPropertyOptions() {
           return AppUtil.readPluginResource(getClassName(),
                   "/properties/myNewFilterType.json", null, true, null);
       }

       @Override
       public String getTemplate(DataList datalist, String name, String label) {
           String encodedName = datalist.getDataListEncodedParamName(
                   DataList.PARAMETER_FILTER_PREFIX + name);

           StringBuilder sb = new StringBuilder();
           // Build your HTML here using StringBuilder
           // Use escHtml() and escJs() for safe output
           return sb.toString();
       }

       @Override
       public DataListFilterQueryObject getQueryObject(DataList datalist, String name) {
           return null; // Use hash variables in SQL instead
       }

       private static String escHtml(String s) {
           if (s == null) return "";
           return s.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\"", "&quot;");
       }

       private static String escJs(String s) {
           if (s == null) return "";
           return s.replace("\\", "\\\\").replace("'", "\\'")
                   .replace("\"", "\\\"").replace("\n", "\\n");
       }
   }
   ```

2. **Create the properties JSON** in `src/main/resources/properties/myNewFilterType.json`:

   ```json
   [{
       "title": "My New Filter Configuration",
       "properties": [
           {
               "name": "someProperty",
               "label": "Some Property",
               "description": "Description of the property",
               "type": "textfield",
               "required": "true"
           }
       ]
   }]
   ```

3. **Register the plugin** in `Activator.java`:

   ```java
   registrationList.add(context.registerService(
       MyNewFilterType.class.getName(), new MyNewFilterType(), null));
   ```

4. **Build and deploy**:
   ```bash
   mvn clean package
   ```
   Upload the new JAR via Plugin Manager.

### Conventions to Follow

- Use `StringBuilder` for HTML generation (no FreeMarker).
- Return `null` from `getQueryObject()` and use hash variables in SQL.
- Use `datalist.getDataListEncodedParamName()` for parameter names.
- Use hidden `<input>` fields with encoded names for form submission.
- Include `escHtml()` and `escJs()` utility methods.
- Sync visible controls to hidden inputs via JavaScript `change` and `submit` event listeners.

---

## Legacy Template Files

The `src/main/resources/templates/` directory contains two FreeMarker template files:

- `filterPanelDecorator.ftl`
- `mdmSelectFilterType.ftl`

These are **not loaded at runtime**. They were part of an earlier approach that used FreeMarker for template rendering, which was abandoned due to OSGi classloader issues. They remain in the repository as reference but have no effect on plugin behavior.
