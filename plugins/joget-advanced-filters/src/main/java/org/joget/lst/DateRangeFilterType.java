package org.joget.lst;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.model.DataListFilterTypeDefault;
import org.joget.workflow.util.WorkflowUtil;

import javax.servlet.http.HttpServletRequest;

/**
 * Date range filter that renders two date pickers (From / To) in one
 * filter cell. Each picker maps to its own encoded request parameter
 * for use with hash variables in JDBC SQL.
 *
 * Filtering is handled by hash variables in the SQL — getQueryObject()
 * returns null.
 */
public class DateRangeFilterType extends DataListFilterTypeDefault {

    private static final String CLASS_NAME = DateRangeFilterType.class.getName();

    @Override public String getName()        { return "Date Range Filter"; }
    @Override public String getVersion()     { return "8.1.0"; }
    @Override public String getDescription() { return "Two date pickers (From/To) in one filter cell"; }
    @Override public String getLabel()       { return "Date Range Filter"; }
    @Override public String getClassName()   { return CLASS_NAME; }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(),
                "/properties/dateRangeFilterType.json", null, true, null);
    }

    @Override
    public String getTemplate(DataList datalist, String name, String label) {
        String fromSuffix = getPropertyString("fromParamSuffix");
        String toSuffix   = getPropertyString("toParamSuffix");
        if (fromSuffix == null || fromSuffix.isEmpty()) fromSuffix = "date_from";
        if (toSuffix   == null || toSuffix.isEmpty())   toSuffix   = "date_to";

        // Encoded parameter names — these become the request param keys
        String fromEncoded = datalist.getDataListEncodedParamName(
                DataList.PARAMETER_FILTER_PREFIX + fromSuffix);
        String toEncoded = datalist.getDataListEncodedParamName(
                DataList.PARAMETER_FILTER_PREFIX + toSuffix);

        // Read current values from request
        String fromValue = "";
        String toValue   = "";
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null) {
            String fv = request.getParameter(fromEncoded);
            String tv = request.getParameter(toEncoded);
            if (fv != null && !fv.isEmpty()) fromValue = fv;
            if (tv != null && !tv.isEmpty()) toValue   = tv;
        }

        // Apply defaults if still empty
        if (fromValue.isEmpty()) {
            String def = getPropertyString("defaultFromValue");
            if (def != null && !def.isEmpty()) fromValue = def;
        }
        if (toValue.isEmpty()) {
            String def = getPropertyString("defaultToValue");
            if (def != null && !def.isEmpty()) toValue = def;
        }

        // Safe JS/HTML element ID prefix
        String elId = (fromEncoded + "_" + toEncoded)
                .replace('.', '_').replace('-', '_');

        // Build raw HTML (no FreeMarker — avoids OSGi classloader issues)
        StringBuilder sb = new StringBuilder();

        sb.append("<div style=\"display:flex;gap:8px;align-items:flex-end;\">\n");

        // From date picker
        sb.append("  <div style=\"flex:1;\">\n");
        sb.append("    <div style=\"font-size:10px;color:#6b7280;margin-bottom:2px;\">From</div>\n");
        sb.append("    <input type=\"date\" id=\"").append(escHtml(elId)).append("_from\"");
        sb.append(" value=\"").append(escHtml(fromValue)).append("\"");
        sb.append(" style=\"width:100%;height:34px;border:1px solid #d1d5db;border-radius:4px;");
        sb.append("padding:0 8px;font-size:13px;color:#1f2937;box-sizing:border-box;\"/>\n");
        sb.append("  </div>\n");

        // To date picker
        sb.append("  <div style=\"flex:1;\">\n");
        sb.append("    <div style=\"font-size:10px;color:#6b7280;margin-bottom:2px;\">To</div>\n");
        sb.append("    <input type=\"date\" id=\"").append(escHtml(elId)).append("_to\"");
        sb.append(" value=\"").append(escHtml(toValue)).append("\"");
        sb.append(" style=\"width:100%;height:34px;border:1px solid #d1d5db;border-radius:4px;");
        sb.append("padding:0 8px;font-size:13px;color:#1f2937;box-sizing:border-box;\"/>\n");
        sb.append("  </div>\n");

        sb.append("</div>\n");

        // Hidden inputs with encoded param names (these are what get submitted)
        sb.append("<input type=\"hidden\" id=\"").append(escHtml(elId)).append("_from_hid\"");
        sb.append(" name=\"").append(escHtml(fromEncoded)).append("\"");
        sb.append(" value=\"").append(escHtml(fromValue)).append("\"/>\n");

        sb.append("<input type=\"hidden\" id=\"").append(escHtml(elId)).append("_to_hid\"");
        sb.append(" name=\"").append(escHtml(toEncoded)).append("\"");
        sb.append(" value=\"").append(escHtml(toValue)).append("\"/>\n");

        // JS to sync date pickers → hidden inputs
        sb.append("<script>\n");
        sb.append("(function(){\n");
        sb.append("    var fp=document.getElementById('").append(escJs(elId)).append("_from');\n");
        sb.append("    var tp=document.getElementById('").append(escJs(elId)).append("_to');\n");
        sb.append("    var fh=document.getElementById('").append(escJs(elId)).append("_from_hid');\n");
        sb.append("    var th=document.getElementById('").append(escJs(elId)).append("_to_hid');\n");
        sb.append("    if(!fp||!tp||!fh||!th) return;\n");
        sb.append("    function sync(){fh.value=fp.value;th.value=tp.value;}\n");
        sb.append("    fp.addEventListener('change',sync);\n");
        sb.append("    tp.addEventListener('change',sync);\n");
        sb.append("    var f=fp.closest('form');\n");
        sb.append("    if(f) f.addEventListener('submit',function(){sync();});\n");
        sb.append("})();\n");
        sb.append("</script>\n");

        // ── Filter Panel Decorator ──
        String panelTitle = getPropertyString("panelTitle");
        if (panelTitle != null && !panelTitle.isEmpty()) {
            String columns = getPropertyString("panelColumns");
            if (columns == null || columns.isEmpty()) columns = "3";
            String hierarchyHint = getPropertyString("panelHierarchyHint");
            if (hierarchyHint == null) hierarchyHint = "";

            appendPanelCss(sb, columns);
            appendPanelJs(sb, elId, panelTitle, hierarchyHint);
        }

        return sb.toString();
    }

    @Override
    public DataListFilterQueryObject getQueryObject(DataList datalist, String name) {
        // Filtering handled by hash variables in the SQL
        return null;
    }

    // ── Filter Panel CSS ──
    private void appendPanelCss(StringBuilder sb, String columns) {
        sb.append("<style>\n");
        sb.append(".ro-filters-panel{background:#fff;border:1px solid #d1d5db;border-radius:6px;margin-bottom:16px;overflow:hidden}\n");
        sb.append(".ro-filters-header{display:flex;justify-content:space-between;align-items:center;padding:10px 20px;cursor:pointer;user-select:none;border-bottom:1px solid transparent;transition:border-color .2s}\n");
        sb.append(".ro-filters-header:hover{background:#fafbfc}\n");
        sb.append(".ro-filters-panel.ro-open .ro-filters-header{border-bottom-color:#d1d5db}\n");
        sb.append(".ro-fh-title{font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:#6b7280;font-weight:600;display:flex;align-items:center;gap:8px}\n");
        sb.append(".ro-active-count{font-size:11px;background:#2563eb;color:#fff;padding:1px 7px;border-radius:10px;font-weight:600}\n");
        sb.append(".ro-chevron{width:18px;height:18px;color:#6b7280;transition:transform .25s ease}\n");
        sb.append(".ro-filters-panel.ro-open .ro-chevron{transform:rotate(180deg)}\n");
        sb.append(".ro-filters-body{max-height:0;overflow:hidden;transition:max-height .3s ease}\n");
        sb.append(".ro-filters-panel.ro-open .ro-filters-body{max-height:800px}\n");
        sb.append(".ro-filters-panel .filter_form{margin:0 !important;border:none !important;box-shadow:none !important}\n");
        sb.append(".ro-filters-panel .filter_form .mobile_search_trigger{display:none !important}\n");
        sb.append(".ro-filters-panel .filters{\n");
        sb.append("    display:grid !important;\n");
        sb.append("    grid-template-columns:repeat(").append(escHtml(columns)).append(", 1fr) !important;\n");
        sb.append("    gap:14px !important;\n");
        sb.append("    padding:16px 20px !important;\n");
        sb.append("    text-align:left !important;\n");
        sb.append("    margin:0 !important;\n");
        sb.append("}\n");
        sb.append(".ro-filters-panel .filter-cell{\n");
        sb.append("    display:flex !important;flex-direction:column !important;\n");
        sb.append("    gap:4px !important;margin:0 !important;padding:0 !important;\n");
        sb.append("}\n");
        sb.append(".ro-filters-panel .filter-cell label.mobile_label{\n");
        sb.append("    display:block !important;font-size:11px !important;font-weight:600 !important;\n");
        sb.append("    color:#6b7280 !important;text-transform:uppercase !important;\n");
        sb.append("    letter-spacing:.03em !important;margin-bottom:2px !important;\n");
        sb.append("}\n");
        sb.append(".ro-type-badge{display:inline-block;font-size:9px;font-weight:600;padding:1px 6px;border-radius:3px;margin-left:6px;vertical-align:middle;text-transform:capitalize;letter-spacing:.02em}\n");
        sb.append(".ro-type-badge.ro-badge-multi{background:#dbeafe;color:#2563eb}\n");
        sb.append(".ro-type-badge.ro-badge-text{background:#dcfce7;color:#16a34a}\n");
        sb.append(".ro-type-badge.ro-badge-date{background:#dbeafe;color:#2563eb}\n");
        sb.append(".ro-filters-panel input[type=text]{border:1px solid #d1d5db !important;border-radius:4px !important;padding:0 8px !important;font-size:13px !important;color:#1f2937 !important;background:#fff !important;height:34px !important;box-sizing:border-box !important;width:100% !important}\n");
        sb.append(".ro-filters-panel select{border:1px solid #d1d5db !important;border-radius:4px !important;padding:0 8px !important;font-size:13px !important;color:#1f2937 !important;background:#fff !important;width:100% !important}\n");
        sb.append(".ro-filters-panel select[multiple]{height:auto !important;padding:4px !important}\n");
        sb.append(".ro-filters-panel select[multiple] option{padding:3px 6px;border-radius:2px;margin-bottom:1px}\n");
        sb.append(".ro-filters-panel select[multiple] option:checked{background:#dbeafe;color:#2563eb}\n");
        sb.append(".ro-filters-panel .filter-cell > div[style*=\"font-size:10px\"]{font-size:10px;color:#9ca3af;margin-top:2px}\n");
        sb.append(".ro-filter-buttons{grid-column:1/-1 !important;display:flex !important;gap:8px !important;align-items:center !important;padding-top:4px !important}\n");
        sb.append(".ro-btn-apply{height:34px;padding:0 22px;background:#2563eb;color:#fff;border:none;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer}\n");
        sb.append(".ro-btn-apply:hover{background:#1d4ed8}\n");
        sb.append(".ro-btn-reset{height:34px;padding:0 14px;background:#fff;color:#6b7280;border:1px solid #d1d5db;border-radius:4px;font-size:12px;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center}\n");
        sb.append(".ro-btn-reset:hover{background:#f0f3f8}\n");
        sb.append(".ro-filters-panel .filter-cell > input.form-button,\n");
        sb.append(".ro-filters-panel .filter-cell > input[type=submit]{display:none !important}\n");
        sb.append(".ro-filters-panel .ro-submit-cell{display:none !important}\n");
        sb.append(".ro-filters-panel .ro-pagesize-cell{display:none !important}\n");
        // Page size wrapper in button row
        sb.append(".ro-pagesize-wrapper{display:flex;align-items:center;gap:6px;margin-left:auto;font-size:12px;color:#6b7280}\n");
        sb.append(".ro-pagesize-wrapper select{height:34px;padding:0 8px;border:1px solid #d1d5db;border-radius:4px;font-size:12px;background:#fff}\n");
        sb.append(".ro-filters-panel .filter-cell > input[type=hidden]:only-child{display:none}\n");
        sb.append(".ro-filter-group-header{display:flex;justify-content:space-between;align-items:center;padding:8px 0;margin-bottom:4px;border-bottom:1px solid #e5e7eb}\n");
        sb.append(".ro-filter-group-header .ro-group-name{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.04em;color:#374151}\n");
        sb.append(".ro-filter-group-header .ro-group-hint{font-size:10px;color:#9ca3af;font-style:italic}\n");

        // Filter group wrapper for cascading filters
        sb.append(".ro-filter-group-wrapper{\n");
        sb.append("    grid-column:1/-1;\n");
        sb.append("    background:#eff6ff;\n");
        sb.append("    border:1px solid #bfdbfe;\n");
        sb.append("    border-left:4px solid #3b82f6;\n");
        sb.append("    border-radius:0 8px 8px 0;\n");
        sb.append("    padding:16px 20px;\n");
        sb.append("    margin:8px 0;\n");
        sb.append("    overflow-x:auto;\n");
        sb.append("}\n");
        sb.append(".ro-filter-group-wrapper .ro-filter-group-header{\n");
        sb.append("    padding-bottom:12px;\n");
        sb.append("    margin-bottom:12px;\n");
        sb.append("    border-bottom:1px solid #bfdbfe;\n");
        sb.append("}\n");
        sb.append(".ro-filter-group-wrapper .ro-group-name{\n");
        sb.append("    font-size:12px;\n");
        sb.append("    font-weight:700;\n");
        sb.append("    text-transform:uppercase;\n");
        sb.append("    letter-spacing:.05em;\n");
        sb.append("    color:#1d4ed8;\n");
        sb.append("}\n");
        sb.append(".ro-filter-group-wrapper .ro-group-hint{\n");
        sb.append("    font-size:10px;\n");
        sb.append("    color:#64748b;\n");
        sb.append("    font-style:italic;\n");
        sb.append("}\n");

        // Filter group items row
        sb.append(".ro-filter-group-items{\n");
        sb.append("    display:flex;\n");
        sb.append("    flex-wrap:nowrap;\n");
        sb.append("    gap:0;\n");
        sb.append("    align-items:flex-start;\n");
        sb.append("}\n");

        // Hierarchy arrow between filters
        sb.append(".ro-hierarchy-arrow{\n");
        sb.append("    display:flex;\n");
        sb.append("    align-items:center;\n");
        sb.append("    justify-content:center;\n");
        sb.append("    color:#93c5fd;\n");
        sb.append("    font-size:20px;\n");
        sb.append("    padding:0 12px;\n");
        sb.append("    flex:0 0 auto;\n");
        sb.append("    min-width:40px;\n");
        sb.append("    align-self:center;\n");
        sb.append("    margin-top:14px;\n"); // Offset for label height
        sb.append("}\n");

        // Individual filter item within group
        sb.append(".ro-filter-group-item{\n");
        sb.append("    display:flex;\n");
        sb.append("    flex-direction:column;\n");
        sb.append("    gap:6px;\n");
        sb.append("    flex:1 1 200px;\n");
        sb.append("    min-width:160px;\n");
        sb.append("    max-width:300px;\n");
        sb.append("}\n");
        sb.append(".ro-filter-group-item>label{\n");
        sb.append("    font-size:11px;\n");
        sb.append("    font-weight:600;\n");
        sb.append("    text-transform:uppercase;\n");
        sb.append("    color:#1e40af;\n");
        sb.append("    letter-spacing:.03em;\n");
        sb.append("    white-space:nowrap;\n");
        sb.append("    overflow:hidden;\n");
        sb.append("    text-overflow:ellipsis;\n");
        sb.append("}\n");
        sb.append(".ro-filter-group-item select[multiple]{\n");
        sb.append("    width:100%;\n");
        sb.append("    background:#fff;\n");
        sb.append("}\n");
        sb.append(".ro-filter-group-item>div[style*=\"display:flex\"]{\n");
        sb.append("    width:100%;\n");
        sb.append("}\n");
        // Clear button styling within groups
        sb.append(".ro-filter-group-item button[id$=\"_clear\"]{\n");
        sb.append("    align-self:flex-start;\n");
        sb.append("    margin-top:2px;\n");
        sb.append("}\n");

        sb.append("</style>\n");
    }

    // ── Filter Panel JavaScript ──
    private void appendPanelJs(StringBuilder sb, String elId, String panelTitle, String hierarchyHint) {
        sb.append("<script>\n");
        sb.append("(function(){\n");
        sb.append("function roInitPanel(){\n");

        // Find the form via the date picker element (already in the DOM)
        sb.append("    var anchor=document.getElementById('").append(escJs(elId)).append("_from');\n");
        sb.append("    if(!anchor) return;\n");
        sb.append("    var form=anchor.closest('form.filter_form')||anchor.closest('form');\n");
        sb.append("    if(!form) return;\n");
        sb.append("    var filtersDiv=form.querySelector('div.filters');\n");
        sb.append("    if(!filtersDiv) filtersDiv=form;\n");
        sb.append("    var formParent=form.parentNode;\n");

        // Hide the page-size selector cell
        sb.append("    var psInput=form.querySelector('select[name$=\"-ps\"],input[name$=\"-ps\"]');\n");
        sb.append("    if(psInput){var psCell=psInput.closest('span.filter-cell');if(psCell) psCell.classList.add('ro-pagesize-cell');}\n");

        // Hide the submit-button cell
        sb.append("    var submitBtn=form.querySelector('input.form-button[type=submit],input[type=submit]');\n");
        sb.append("    if(submitBtn){var submitCell=submitBtn.closest('span.filter-cell');if(submitCell) submitCell.classList.add('ro-submit-cell');}\n");

        // Count active filters
        sb.append("    var activeCount=0;\n");
        sb.append("    var cells=filtersDiv.querySelectorAll('span.filter-cell');\n");
        sb.append("    cells.forEach(function(cell){\n");
        sb.append("        if(cell.classList.contains('ro-submit-cell')) return;\n");
        sb.append("        cell.querySelectorAll('input[type=hidden]').forEach(function(inp){\n");
        sb.append("            if(inp.name&&inp.name.indexOf('fn_')!==-1&&inp.value&&inp.value.trim()!=='') activeCount++;\n");
        sb.append("        });\n");
        sb.append("        cell.querySelectorAll('input[type=text]').forEach(function(inp){\n");
        sb.append("            if(inp.name&&inp.name.indexOf('fn_')!==-1&&inp.value&&inp.value.trim()!=='') activeCount++;\n");
        sb.append("        });\n");
        sb.append("    });\n");

        // Build panel wrapper
        sb.append("    var panel=document.createElement('div');\n");
        sb.append("    panel.className='ro-filters-panel ro-open';\n");
        sb.append("    panel.id='roFiltersPanel';\n");

        // Header
        sb.append("    var header=document.createElement('div');\n");
        sb.append("    header.className='ro-filters-header';\n");
        sb.append("    var h='<div class=\"ro-fh-title\">';\n");
        sb.append("    h+='<svg class=\"ro-chevron\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\"><polyline points=\"6 9 12 15 18 9\"/></svg>';\n");
        sb.append("    h+='").append(escJs(panelTitle)).append("';\n");
        sb.append("    if(activeCount>0){h+=' <span class=\"ro-active-count\">'+activeCount+' active</span>';}\n");
        sb.append("    h+='</div>';\n");
        if (!hierarchyHint.isEmpty()) {
            sb.append("    h+='<div style=\"font-size:11px;color:#9ca3af;\">").append(escJs(hierarchyHint)).append("</div>';\n");
        }
        sb.append("    header.innerHTML=h;\n");
        sb.append("    header.addEventListener('click',function(e){\n");
        sb.append("        if(e.target.closest('.ro-filters-body')) return;\n");
        sb.append("        panel.classList.toggle('ro-open');\n");
        sb.append("    });\n");

        // Body
        sb.append("    var body=document.createElement('div');\n");
        sb.append("    body.className='ro-filters-body';\n");

        // Assemble
        sb.append("    formParent.insertBefore(panel,form);\n");
        sb.append("    body.appendChild(form);\n");
        sb.append("    panel.appendChild(header);\n");
        sb.append("    panel.appendChild(body);\n");

        // Type badges
        sb.append("    cells.forEach(function(cell){\n");
        sb.append("        if(cell.classList.contains('ro-submit-cell')) return;\n");
        sb.append("        var lbl=cell.querySelector('label.mobile_label');\n");
        sb.append("        if(!lbl||!lbl.textContent.trim()) return;\n");
        sb.append("        var badge='';\n");
        sb.append("        if(cell.querySelector('select[multiple]')){badge='<span class=\"ro-type-badge ro-badge-multi\">Multi-select</span>';}\n");
        sb.append("        else if(cell.querySelector('input[type=date]')){badge='<span class=\"ro-type-badge ro-badge-date\">Date Picker</span>';}\n");
        sb.append("        else if(cell.querySelector('input[type=text]')){badge='<span class=\"ro-type-badge ro-badge-text\">Text</span>';}\n");
        sb.append("        if(badge) lbl.insertAdjacentHTML('beforeend',' '+badge);\n");
        sb.append("    });\n");

        // Apply + Reset buttons + Page Size
        sb.append("    var btnDiv=document.createElement('span');\n");
        sb.append("    btnDiv.className='filter-cell ro-filter-buttons';\n");
        sb.append("    var applyBtn=document.createElement('button');\n");
        sb.append("    applyBtn.type='submit';\n");
        sb.append("    applyBtn.className='ro-btn-apply';\n");
        sb.append("    applyBtn.textContent='Apply Filters';\n");
        sb.append("    btnDiv.appendChild(applyBtn);\n");
        sb.append("    var resetLink=document.createElement('a');\n");
        sb.append("    resetLink.href=window.location.pathname;\n");
        sb.append("    resetLink.className='ro-btn-reset';\n");
        sb.append("    resetLink.textContent='Reset';\n");
        sb.append("    btnDiv.appendChild(resetLink);\n");

        // Move page size selector to button row
        sb.append("    if(psInput){\n");
        sb.append("        var psWrapper=document.createElement('div');\n");
        sb.append("        psWrapper.className='ro-pagesize-wrapper';\n");
        sb.append("        var psLabel=document.createElement('span');\n");
        sb.append("        psLabel.textContent='Page size:';\n");
        sb.append("        psWrapper.appendChild(psLabel);\n");
        sb.append("        var psClone=psInput.cloneNode(true);\n");
        sb.append("        psClone.addEventListener('change',function(){psInput.value=this.value;});\n");
        sb.append("        psWrapper.appendChild(psClone);\n");
        sb.append("        btnDiv.appendChild(psWrapper);\n");
        sb.append("    }\n");

        sb.append("    filtersDiv.appendChild(btnDiv);\n");

        sb.append("}\n"); // end roInitPanel()

        // Schedule after DOM ready so all filter cells exist
        sb.append("if(typeof jQuery!=='undefined'){jQuery(document).ready(roInitPanel);}\n");
        sb.append("else if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',roInitPanel);}\n");
        sb.append("else{setTimeout(roInitPanel,0);}\n");
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
