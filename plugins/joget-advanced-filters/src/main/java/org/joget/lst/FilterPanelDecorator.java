package org.joget.lst;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.model.DataListFilterTypeDefault;

/**
 * Decorator filter that injects CSS and JavaScript to transform the
 * standard Joget DataList filter bar into a collapsible grid panel
 * matching the RPT-EXEC-001 design.
 *
 * Add this as the LAST filter in the DataList configuration.
 * It does not perform any data filtering itself.
 *
 * Returns raw HTML (no FreeMarker) to avoid OSGi classloader issues.
 */
public class FilterPanelDecorator extends DataListFilterTypeDefault {

    private static final String CLASS_NAME = FilterPanelDecorator.class.getName();

    @Override public String getName()        { return "Filter Panel Decorator"; }
    @Override public String getVersion()     { return "8.1.0"; }
    @Override public String getDescription() { return "Transforms the DataList filter bar into a styled collapsible panel"; }
    @Override public String getLabel()       { return "Filter Panel Decorator"; }
    @Override public String getClassName()   { return CLASS_NAME; }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(),
                "/properties/filterPanelDecorator.json", null, true, null);
    }

    @Override
    public String getTemplate(DataList datalist, String name, String label) {
        String panelTitle = getPropertyString("panelTitle");
        if (panelTitle == null || panelTitle.isEmpty()) panelTitle = "FILTER CRITERIA";

        String columns = getPropertyString("columns");
        if (columns == null || columns.isEmpty()) columns = "3";

        String hierarchyHint = getPropertyString("hierarchyHint");
        if (hierarchyHint == null) hierarchyHint = "";

        StringBuilder sb = new StringBuilder();

        // Marker span (hidden) — JS uses this to find the filter form
        sb.append("<span id=\"roFilterPanelMarker\" style=\"display:none\"></span>\n");

        // ── CSS ──
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

        // Grid layout on div.filters
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

        // Each filter cell
        sb.append(".ro-filters-panel .filter-cell{\n");
        sb.append("    display:flex !important;flex-direction:column !important;\n");
        sb.append("    gap:4px !important;margin:0 !important;padding:0 !important;\n");
        sb.append("}\n");

        // Show labels on desktop
        sb.append(".ro-filters-panel .filter-cell label.mobile_label{\n");
        sb.append("    display:block !important;font-size:11px !important;font-weight:600 !important;\n");
        sb.append("    color:#6b7280 !important;text-transform:uppercase !important;\n");
        sb.append("    letter-spacing:.03em !important;margin-bottom:2px !important;\n");
        sb.append("}\n");

        // Type badges
        sb.append(".ro-type-badge{display:inline-block;font-size:9px;font-weight:600;padding:1px 6px;border-radius:3px;margin-left:6px;vertical-align:middle;text-transform:capitalize;letter-spacing:.02em}\n");
        sb.append(".ro-type-badge.ro-badge-multi{background:#dbeafe;color:#2563eb}\n");
        sb.append(".ro-type-badge.ro-badge-text{background:#dcfce7;color:#16a34a}\n");
        sb.append(".ro-type-badge.ro-badge-date{background:#dbeafe;color:#2563eb}\n");

        // Inputs
        sb.append(".ro-filters-panel input[type=text]{border:1px solid #d1d5db !important;border-radius:4px !important;padding:0 8px !important;font-size:13px !important;color:#1f2937 !important;background:#fff !important;height:34px !important;box-sizing:border-box !important;width:100% !important}\n");
        sb.append(".ro-filters-panel select{border:1px solid #d1d5db !important;border-radius:4px !important;padding:0 8px !important;font-size:13px !important;color:#1f2937 !important;background:#fff !important;width:100% !important}\n");
        sb.append(".ro-filters-panel select[multiple]{height:auto !important;padding:4px !important}\n");
        sb.append(".ro-filters-panel select[multiple] option{padding:3px 6px;border-radius:2px;margin-bottom:1px}\n");
        sb.append(".ro-filters-panel select[multiple] option:checked{background:#dbeafe;color:#2563eb}\n");

        // Hint text
        sb.append(".ro-filters-panel .filter-cell > div[style*=\"font-size:10px\"]{font-size:10px;color:#9ca3af;margin-top:2px}\n");

        // Buttons row
        sb.append(".ro-filter-buttons{grid-column:1/-1 !important;display:flex !important;gap:8px !important;align-items:center !important;padding-top:4px !important}\n");
        sb.append(".ro-btn-apply{height:34px;padding:0 22px;background:#2563eb;color:#fff;border:none;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer}\n");
        sb.append(".ro-btn-apply:hover{background:#1d4ed8}\n");
        sb.append(".ro-btn-reset{height:34px;padding:0 14px;background:#fff;color:#6b7280;border:1px solid #d1d5db;border-radius:4px;font-size:12px;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center}\n");
        sb.append(".ro-btn-reset:hover{background:#f0f3f8}\n");

        // Hide original submit, decorator cell, submit cell
        sb.append(".ro-filters-panel .filter-cell > input.form-button,\n");
        sb.append(".ro-filters-panel .filter-cell > input[type=submit]{display:none !important}\n");
        sb.append(".ro-filters-panel .ro-decorator-cell{display:none !important}\n");
        sb.append(".ro-filters-panel .filter-cell > input[type=hidden]:only-child{display:none}\n");
        sb.append(".ro-filters-panel .ro-submit-cell{display:none !important}\n");
        sb.append("</style>\n");

        // ── JavaScript ──
        sb.append("<script>\n");
        sb.append("(function(){\n");
        sb.append("function run(){\n");
        sb.append("    var marker=document.getElementById('roFilterPanelMarker');\n");
        sb.append("    if(!marker) return;\n");
        sb.append("    var form=marker.closest('form.filter_form')||marker.closest('form');\n");
        sb.append("    if(!form) return;\n");
        sb.append("    var filtersDiv=form.querySelector('div.filters');\n");
        sb.append("    if(!filtersDiv) filtersDiv=form;\n");
        sb.append("    var formParent=form.parentNode;\n");

        // Hide decorator's own cell
        sb.append("    var markerCell=marker.closest('span.filter-cell');\n");
        sb.append("    if(markerCell) markerCell.classList.add('ro-decorator-cell');\n");

        // Hide the submit-button cell
        sb.append("    var submitBtn=form.querySelector('input.form-button[type=submit],input[type=submit]');\n");
        sb.append("    if(submitBtn){var submitCell=submitBtn.closest('span.filter-cell');if(submitCell) submitCell.classList.add('ro-submit-cell');}\n");

        // Count active filters
        sb.append("    var activeCount=0;\n");
        sb.append("    var cells=filtersDiv.querySelectorAll('span.filter-cell');\n");
        sb.append("    cells.forEach(function(cell){\n");
        sb.append("        if(cell.classList.contains('ro-decorator-cell')||cell.classList.contains('ro-submit-cell')) return;\n");
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
        sb.append("        if(cell.classList.contains('ro-decorator-cell')||cell.classList.contains('ro-submit-cell')) return;\n");
        sb.append("        var lbl=cell.querySelector('label.mobile_label');\n");
        sb.append("        if(!lbl||!lbl.textContent.trim()) return;\n");
        sb.append("        var badge='';\n");
        sb.append("        if(cell.querySelector('select[multiple]')){badge='<span class=\"ro-type-badge ro-badge-multi\">Multi-select</span>';}\n");
        sb.append("        else if(cell.querySelector('input[type=date]')){badge='<span class=\"ro-type-badge ro-badge-date\">Date Picker</span>';}\n");
        sb.append("        else if(cell.querySelector('input[type=text]')){badge='<span class=\"ro-type-badge ro-badge-text\">Text</span>';}\n");
        sb.append("        if(badge) lbl.insertAdjacentHTML('beforeend',' '+badge);\n");
        sb.append("    });\n");

        // Apply + Reset buttons
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
        sb.append("    filtersDiv.appendChild(btnDiv);\n");

        sb.append("}\n"); // end run()

        // Schedule execution
        sb.append("if(typeof jQuery!=='undefined'){jQuery(document).ready(run);}\n");
        sb.append("else if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',run);}\n");
        sb.append("else{run();}\n");
        sb.append("})();\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    @Override
    public DataListFilterQueryObject getQueryObject(DataList datalist, String name) {
        return null;
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
