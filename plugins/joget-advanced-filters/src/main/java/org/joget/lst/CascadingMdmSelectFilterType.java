package org.joget.lst;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.model.DataListFilterTypeDefault;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Cascading multi-select filter that loads options from MDM lookup tables
 * with parent-child hierarchy support. Child filters are filtered client-side
 * based on parent selection.
 *
 * Features:
 * - Parent code attribute on options for client-side filtering
 * - Filter registry for cross-filter communication
 * - Bi-directional sync: parent→child filtering, child→parent auto-select
 * - Visual filter grouping with headers
 */
public class CascadingMdmSelectFilterType extends DataListFilterTypeDefault {

    private static final String CLASS_NAME = CascadingMdmSelectFilterType.class.getName();

    @Override public String getName()        { return "Cascading MDM Multi-Select Filter"; }
    @Override public String getVersion()     { return "8.1.0"; }
    @Override public String getDescription() { return "Cascading multi-select filter with parent-child hierarchy"; }
    @Override public String getLabel()       { return "Cascading MDM Multi-Select Filter"; }
    @Override public String getClassName()   { return CLASS_NAME; }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(),
                "/properties/cascadingMdmSelectFilterType.json", null, true, null);
    }

    @Override
    public String getTemplate(DataList datalist, String name, String label) {
        String encodedName = datalist.getDataListEncodedParamName(
                DataList.PARAMETER_FILTER_PREFIX + name);

        // Current value (semicolon-separated string)
        String value = getValue(datalist, name, getPropertyString("defaultValue"));
        if (value == null) value = "";

        // Parse into set for marking selected options
        Set<String> selectedSet = new HashSet<>();
        if (!value.isEmpty()) {
            for (String v : value.split(";")) {
                String trimmed = v.trim();
                if (!trimmed.isEmpty()) selectedSet.add(trimmed);
            }
        }

        // Load options from MDM table (with parent codes if configured)
        String parentCodeColumn = getPropertyString("parentCodeColumn");
        boolean hasParent = parentCodeColumn != null && !parentCodeColumn.isEmpty();
        List<Map<String, String>> options = loadOptions(selectedSet, hasParent ? parentCodeColumn : null);

        String size = getPropertyString("size");
        if (size == null || size.isEmpty()) size = "5";

        String tableName = getPropertyString("tableName");
        String hint = (tableName != null && !tableName.isEmpty())
                ? tableName + " · Ctrl+click" : "";

        // Build a safe JS/HTML element ID from the encoded name
        String elId = encodedName.replace('.', '_').replace('-', '_');

        // Get cascading configuration
        String parentFilterName = getPropertyString("parentFilterName");
        boolean isChildFilter = parentFilterName != null && !parentFilterName.isEmpty();

        // Get filter grouping configuration
        String filterGroupName = getPropertyString("filterGroupName");
        String filterGroupOrder = getPropertyString("filterGroupOrder");
        String filterGroupHint = getPropertyString("filterGroupHint");
        boolean isFirstInGroup = "1".equals(filterGroupOrder);

        StringBuilder sb = new StringBuilder();

        // Wrap in a div with data attributes for JS grouping
        boolean hasFilterGroup = filterGroupName != null && !filterGroupName.isEmpty();
        if (hasFilterGroup) {
            sb.append("<div class=\"ro-cascading-filter\"");
            sb.append(" data-filter-group=\"").append(escHtml(filterGroupName)).append("\"");
            sb.append(" data-filter-group-order=\"").append(escHtml(filterGroupOrder != null ? filterGroupOrder : "1")).append("\"");
            if (filterGroupHint != null && !filterGroupHint.isEmpty()) {
                sb.append(" data-filter-group-hint=\"").append(escHtml(filterGroupHint)).append("\"");
            }
            sb.append(" data-filter-label=\"").append(escHtml(label)).append("\"");
            sb.append(">\n");
        }

        // Wrapper for select + clear button
        sb.append("<div style=\"display:flex;gap:4px;align-items:flex-start;\">\n");

        // Build select element with data-parent-code attributes
        sb.append("<select id=\"").append(escHtml(elId)).append("_sel\" multiple size=\"").append(escHtml(size)).append("\" style=\"flex:1;\">\n");
        for (Map<String, String> opt : options) {
            sb.append("<option value=\"").append(escHtml(opt.get("value"))).append("\"");
            if (hasParent && opt.get("parentCode") != null) {
                sb.append(" data-parent-code=\"").append(escHtml(opt.get("parentCode"))).append("\"");
            }
            if ("true".equals(opt.get("selected"))) sb.append(" selected");
            sb.append(">").append(escHtml(opt.get("label"))).append("</option>\n");
        }
        sb.append("</select>\n");

        // Clear button (subtle)
        sb.append("<button type=\"button\" id=\"").append(escHtml(elId)).append("_clear\"");
        sb.append(" title=\"Clear selection\"");
        sb.append(" style=\"padding:2px 6px;background:transparent;border:1px solid #e5e7eb;");
        sb.append("border-radius:3px;cursor:pointer;font-size:12px;color:#9ca3af;line-height:1;\"");
        sb.append(" onmouseover=\"this.style.background='#fee2e2';this.style.borderColor='#fca5a5';this.style.color='#dc2626';\"");
        sb.append(" onmouseout=\"this.style.background='transparent';this.style.borderColor='#e5e7eb';this.style.color='#9ca3af';\"");
        sb.append(">&times;</button>\n");

        sb.append("</div>\n");

        // Hidden input for form submission
        sb.append("<input type=\"hidden\" id=\"").append(escHtml(elId))
          .append("\" name=\"").append(escHtml(encodedName))
          .append("\" value=\"").append(escHtml(value)).append("\"/>\n");

        // Hint with table name and multi-select instruction
        if (!hint.isEmpty()) {
            sb.append("<div style=\"font-size:10px;color:#9ca3af;margin-top:2px;\">")
              .append(escHtml(tableName)).append(" · <span id=\"")
              .append(escHtml(elId)).append("_hint\">Ctrl+click</span></div>\n");
        }

        // JavaScript for sync and cascading
        sb.append("<script>\n");
        sb.append("(function(){\n");

        // Initialize filter registry
        sb.append("    window.roFilterRegistry = window.roFilterRegistry || {};\n");
        sb.append("    window.roFilterRegistry['").append(escJs(name)).append("'] = '").append(escJs(elId)).append("';\n");

        sb.append("    var selEl = document.getElementById('").append(escJs(elId)).append("_sel');\n");
        sb.append("    var hidEl = document.getElementById('").append(escJs(elId)).append("');\n");
        sb.append("    if (!selEl || !hidEl) return;\n");

        // Sync function
        sb.append("    function sync() {\n");
        sb.append("        var v = [];\n");
        sb.append("        for (var i = 0; i < selEl.options.length; i++) {\n");
        sb.append("            if (selEl.options[i].selected && !selEl.options[i].disabled) v.push(selEl.options[i].value);\n");
        sb.append("        }\n");
        sb.append("        hidEl.value = v.join(';');\n");
        sb.append("    }\n");

        // Get selected values helper
        sb.append("    function getSelectedValues(sel) {\n");
        sb.append("        var vals = [];\n");
        sb.append("        for (var i = 0; i < sel.options.length; i++) {\n");
        sb.append("            if (sel.options[i].selected && !sel.options[i].disabled) vals.push(sel.options[i].value);\n");
        sb.append("        }\n");
        sb.append("        return vals;\n");
        sb.append("    }\n");

        // Clear button handler
        sb.append("    var clearBtn = document.getElementById('").append(escJs(elId)).append("_clear');\n");
        sb.append("    if (clearBtn) {\n");
        sb.append("        clearBtn.addEventListener('click', function() {\n");
        sb.append("            for (var i = 0; i < selEl.options.length; i++) selEl.options[i].selected = false;\n");
        sb.append("            sync();\n");
        sb.append("            selEl.dispatchEvent(new Event('change'));\n"); // Trigger cascade
        sb.append("        });\n");
        sb.append("    }\n");

        if (isChildFilter) {
            // This is a child filter - set up parent filtering
            sb.append("    function setupParentFiltering() {\n");
            sb.append("        var parentElId = window.roFilterRegistry['").append(escJs(parentFilterName)).append("'];\n");
            sb.append("        if (!parentElId) { setTimeout(setupParentFiltering, 50); return; }\n");
            sb.append("        var parentSelEl = document.getElementById(parentElId + '_sel');\n");
            sb.append("        var parentHidEl = document.getElementById(parentElId);\n");
            sb.append("        if (!parentSelEl) { setTimeout(setupParentFiltering, 50); return; }\n");

            // Parent → Child filtering function
            sb.append("        function filterByParent() {\n");
            sb.append("            var selectedParents = getSelectedValues(parentSelEl);\n");
            sb.append("            for (var i = 0; i < selEl.options.length; i++) {\n");
            sb.append("                var opt = selEl.options[i];\n");
            sb.append("                var parentCode = opt.dataset.parentCode;\n");
            sb.append("                var isOrphaned = selectedParents.length > 0 && parentCode && selectedParents.indexOf(parentCode) === -1;\n");
            sb.append("                opt.hidden = isOrphaned;\n");
            sb.append("                opt.disabled = isOrphaned;\n");
            sb.append("                if (isOrphaned && opt.selected) opt.selected = false;\n");
            sb.append("            }\n");
            sb.append("            sync();\n");
            sb.append("        }\n");

            // Child → Parent auto-select function
            sb.append("        function autoSelectParent() {\n");
            sb.append("            var neededParents = {};\n");
            sb.append("            for (var i = 0; i < selEl.options.length; i++) {\n");
            sb.append("                var opt = selEl.options[i];\n");
            sb.append("                if (opt.selected && !opt.disabled && opt.dataset.parentCode) {\n");
            sb.append("                    neededParents[opt.dataset.parentCode] = true;\n");
            sb.append("                }\n");
            sb.append("            }\n");
            sb.append("            var parentChanged = false;\n");
            sb.append("            for (var i = 0; i < parentSelEl.options.length; i++) {\n");
            sb.append("                var popt = parentSelEl.options[i];\n");
            sb.append("                if (neededParents[popt.value] && !popt.selected) {\n");
            sb.append("                    popt.selected = true;\n");
            sb.append("                    parentChanged = true;\n");
            sb.append("                }\n");
            sb.append("            }\n");
            sb.append("            if (parentChanged && parentHidEl) {\n");
            sb.append("                var pv = [];\n");
            sb.append("                for (var i = 0; i < parentSelEl.options.length; i++) {\n");
            sb.append("                    if (parentSelEl.options[i].selected) pv.push(parentSelEl.options[i].value);\n");
            sb.append("                }\n");
            sb.append("                parentHidEl.value = pv.join(';');\n");
            sb.append("            }\n");
            sb.append("        }\n");

            // Attach listeners
            sb.append("        parentSelEl.addEventListener('change', filterByParent);\n");
            sb.append("        selEl.addEventListener('change', function() { autoSelectParent(); sync(); });\n");

            // Initial filtering based on current parent selection
            sb.append("        filterByParent();\n");
            sb.append("    }\n");
            sb.append("    setupParentFiltering();\n");
        } else {
            // This is a parent filter - just sync on change
            sb.append("    selEl.addEventListener('change', sync);\n");
        }

        // Form submit sync
        sb.append("    var f = selEl.closest('form');\n");
        sb.append("    if (f) f.addEventListener('submit', function() { sync(); });\n");

        // macOS hint
        sb.append("    var hintEl = document.getElementById('").append(escJs(elId)).append("_hint');\n");
        sb.append("    if (hintEl && /Mac/.test(navigator.platform)) hintEl.textContent = '\\u2318+click';\n");

        sb.append("})();\n");
        sb.append("</script>\n");

        // Close the wrapper div if we opened one
        if (hasFilterGroup) {
            sb.append("</div>\n");
        }

        // If this is the first filter in a group, add the grouping JavaScript
        if (isFirstInGroup && hasFilterGroup) {
            appendGroupingJs(sb, filterGroupName);
        }

        return sb.toString();
    }

    @Override
    public DataListFilterQueryObject getQueryObject(DataList datalist, String name) {
        // Filtering is handled by JDBC hash variables in the SQL
        return null;
    }

    private List<Map<String, String>> loadOptions(Set<String> selectedValues, String parentCodeColumn) {
        List<Map<String, String>> options = new ArrayList<>();
        String tableName = getPropertyString("tableName");
        if (tableName == null || tableName.isEmpty()) return options;

        String codeCol = getPropertyString("codeColumn");
        String nameCol = getPropertyString("nameColumn");
        if (codeCol == null || codeCol.isEmpty()) codeCol = "c_code";
        if (nameCol == null || nameCol.isEmpty()) nameCol = "c_name";

        tableName = sanitize(tableName);
        codeCol = sanitize(codeCol);
        nameCol = sanitize(nameCol);

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ").append(codeCol).append(", ").append(nameCol);

        boolean hasParentCode = parentCodeColumn != null && !parentCodeColumn.isEmpty();
        if (hasParentCode) {
            parentCodeColumn = sanitize(parentCodeColumn);
            sqlBuilder.append(", ").append(parentCodeColumn).append(" AS parent_code");
        }

        sqlBuilder.append(" FROM app_fd_").append(tableName)
                  .append(" ORDER BY ").append(nameCol);

        String sql = sqlBuilder.toString();

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext()
                    .getBean("setupDataSource");
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString(1);
                    String optName = rs.getString(2);
                    String parentCode = hasParentCode ? rs.getString(3) : null;

                    if (code == null) code = "";
                    if (optName == null) optName = "";

                    Map<String, String> opt = new LinkedHashMap<>();
                    opt.put("value", code);
                    opt.put("label", optName);
                    opt.put("selected", selectedValues.contains(code) ? "true" : "");
                    if (hasParentCode && parentCode != null) {
                        opt.put("parentCode", parentCode);
                    }
                    options.add(opt);
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading options from app_fd_" + tableName);
        }
        return options;
    }

    /** Strip anything that isn't a safe SQL identifier character. */
    private String sanitize(String identifier) {
        return identifier.replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * Appends JavaScript that reorganizes DOM to visually group filters
     * with matching data-filter-group attributes.
     */
    private void appendGroupingJs(StringBuilder sb, String groupName) {
        sb.append("<script>\n");
        sb.append("(function(){\n");
        sb.append("function roGroupFilters_").append(escJs(groupName.replaceAll("[^a-zA-Z0-9]", ""))).append("(){\n");

        // Find all filters in this group
        sb.append("    var groupName='").append(escJs(groupName)).append("';\n");
        sb.append("    var filters=document.querySelectorAll('.ro-cascading-filter[data-filter-group=\"'+groupName+'\"]');\n");
        sb.append("    if(filters.length<2) return;\n"); // Need at least 2 filters to group

        // Sort by order
        sb.append("    var sorted=Array.prototype.slice.call(filters).sort(function(a,b){\n");
        sb.append("        return parseInt(a.dataset.filterGroupOrder||'99')-parseInt(b.dataset.filterGroupOrder||'99');\n");
        sb.append("    });\n");

        // Get the first filter's parent cell and the filters container
        sb.append("    var firstCell=sorted[0].closest('span.filter-cell');\n");
        sb.append("    if(!firstCell) return;\n");
        sb.append("    var filtersContainer=firstCell.parentNode;\n");

        // Get hint from first filter
        sb.append("    var hint=sorted[0].dataset.filterGroupHint||'';\n");

        // Create wrapper that will contain all grouped filter cells
        sb.append("    var wrapper=document.createElement('div');\n");
        sb.append("    wrapper.className='ro-filter-group-wrapper';\n");

        // Create group header
        sb.append("    var header=document.createElement('div');\n");
        sb.append("    header.className='ro-filter-group-header';\n");
        sb.append("    header.innerHTML='<span class=\"ro-group-name\">'+groupName+'</span>';\n");
        sb.append("    if(hint) header.innerHTML+='<span class=\"ro-group-hint\">'+hint+'</span>';\n");
        sb.append("    wrapper.appendChild(header);\n");

        // Create container for filter items with arrows between them
        sb.append("    var itemsRow=document.createElement('div');\n");
        sb.append("    itemsRow.className='ro-filter-group-items';\n");

        // Move each filter cell's content into the wrapper
        sb.append("    sorted.forEach(function(filter,idx){\n");
        sb.append("        var cell=filter.closest('span.filter-cell');\n");
        sb.append("        if(!cell) return;\n");

        // Add arrow before non-first items
        sb.append("        if(idx>0){\n");
        sb.append("            var arrow=document.createElement('div');\n");
        sb.append("            arrow.className='ro-hierarchy-arrow';\n");
        sb.append("            arrow.innerHTML='→';\n");
        sb.append("            itemsRow.appendChild(arrow);\n");
        sb.append("        }\n");

        // Create item wrapper
        sb.append("        var item=document.createElement('div');\n");
        sb.append("        item.className='ro-filter-group-item';\n");

        // Add label
        sb.append("        var lbl=document.createElement('label');\n");
        sb.append("        lbl.textContent=filter.dataset.filterLabel||'';\n");
        sb.append("        item.appendChild(lbl);\n");

        // Move filter content
        sb.append("        while(filter.firstChild) item.appendChild(filter.firstChild);\n");

        // Hide hint divs in grouped mode (too cluttered)
        sb.append("        item.querySelectorAll('div[style*=\"font-size:10px\"]').forEach(function(h){ h.style.display='none'; });\n");

        sb.append("        itemsRow.appendChild(item);\n");

        sb.append("    });\n");

        sb.append("    wrapper.appendChild(itemsRow);\n");

        // Insert wrapper BEFORE the first cell as a standalone grid item
        sb.append("    filtersContainer.insertBefore(wrapper, firstCell);\n");

        // Hide ALL grouped filter cells (keeps grid flow intact)
        sb.append("    sorted.forEach(function(filter){\n");
        sb.append("        var cell=filter.closest('span.filter-cell');\n");
        sb.append("        if(cell) cell.style.display='none';\n");
        sb.append("    });\n");

        sb.append("}\n"); // end function

        // Schedule after DOM ready
        sb.append("if(typeof jQuery!=='undefined'){jQuery(document).ready(roGroupFilters_").append(escJs(groupName.replaceAll("[^a-zA-Z0-9]", ""))).append(");}\n");
        sb.append("else if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',roGroupFilters_").append(escJs(groupName.replaceAll("[^a-zA-Z0-9]", ""))).append(");}\n");
        sb.append("else{setTimeout(roGroupFilters_").append(escJs(groupName.replaceAll("[^a-zA-Z0-9]", ""))).append(",0);}\n");
        sb.append("})();\n");
        sb.append("</script>\n");
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
