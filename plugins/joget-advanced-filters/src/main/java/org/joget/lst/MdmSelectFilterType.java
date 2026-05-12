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
 * Multi-select filter that loads options from MDM lookup tables.
 * Submits selected values as a semicolon-separated string for use
 * with JDBC hash variables (string_to_array pattern).
 */
public class MdmSelectFilterType extends DataListFilterTypeDefault {

    private static final String CLASS_NAME = MdmSelectFilterType.class.getName();

    @Override public String getName()        { return "MDM Multi-Select Filter"; }
    @Override public String getVersion()     { return "8.1.0"; }
    @Override public String getDescription() { return "Multi-select filter that loads options from MDM lookup tables"; }
    @Override public String getLabel()       { return "MDM Multi-Select Filter"; }
    @Override public String getClassName()   { return CLASS_NAME; }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(),
                "/properties/mdmSelectFilterType.json", null, true, null);
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

        // Load options from MDM table
        List<Map<String, String>> options = loadOptions(selectedSet);

        String size = getPropertyString("size");
        if (size == null || size.isEmpty()) size = "5";

        String tableName = getPropertyString("tableName");
        String hint = (tableName != null && !tableName.isEmpty())
                ? tableName + " \u00b7 Ctrl+click" : "";

        // Build a safe JS/HTML element ID from the encoded name
        String elId = encodedName.replace('.', '_').replace('-', '_');

        // Build raw HTML (no FreeMarker — avoids OSGi classloader issues)
        StringBuilder sb = new StringBuilder();

        // Wrapper for select + clear button
        sb.append("<div style=\"display:flex;gap:4px;align-items:flex-start;\">\n");

        sb.append("<select id=\"").append(escHtml(elId)).append("_sel\" multiple size=\"").append(escHtml(size)).append("\" style=\"flex:1;\">\n");
        for (Map<String, String> opt : options) {
            sb.append("<option value=\"").append(escHtml(opt.get("value"))).append("\"");
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

        sb.append("<input type=\"hidden\" id=\"").append(escHtml(elId))
          .append("\" name=\"").append(escHtml(encodedName))
          .append("\" value=\"").append(escHtml(value)).append("\"/>\n");

        if (!hint.isEmpty()) {
            sb.append("<div style=\"font-size:10px;color:#9ca3af;margin-top:2px;\">")
              .append(escHtml(tableName)).append(" \u00b7 <span id=\"")
              .append(escHtml(elId)).append("_hint\">Ctrl+click</span></div>\n");
        }

        sb.append("<script>\n");
        sb.append("(function(){\n");
        sb.append("    var selEl=document.getElementById('").append(escJs(elId)).append("_sel');\n");
        sb.append("    var hidEl=document.getElementById('").append(escJs(elId)).append("');\n");
        sb.append("    if(!selEl||!hidEl) return;\n");
        sb.append("    function sync(){var v=[];for(var i=0;i<selEl.options.length;i++){if(selEl.options[i].selected) v.push(selEl.options[i].value);}hidEl.value=v.join(';');}\n");
        sb.append("    selEl.addEventListener('change',sync);\n");
        // Clear button handler
        sb.append("    var clearBtn=document.getElementById('").append(escJs(elId)).append("_clear');\n");
        sb.append("    if(clearBtn){\n");
        sb.append("        clearBtn.addEventListener('click',function(){\n");
        sb.append("            for(var i=0;i<selEl.options.length;i++) selEl.options[i].selected=false;\n");
        sb.append("            sync();\n");
        sb.append("        });\n");
        sb.append("    }\n");
        sb.append("    var f=selEl.closest('form');\n");
        sb.append("    if(f) f.addEventListener('submit',function(){sync();});\n");
        sb.append("    var hintEl=document.getElementById('").append(escJs(elId)).append("_hint');\n");
        sb.append("    if(hintEl&&/Mac/.test(navigator.platform)) hintEl.textContent='\\u2318+click';\n");
        sb.append("})();\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    @Override
    public DataListFilterQueryObject getQueryObject(DataList datalist, String name) {
        // Filtering is handled by JDBC hash variables in the SQL
        return null;
    }

    private List<Map<String, String>> loadOptions(Set<String> selectedValues) {
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

        String sql = "SELECT " + codeCol + ", " + nameCol
                + " FROM app_fd_" + tableName
                + " ORDER BY " + nameCol;

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext()
                    .getBean("setupDataSource");
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString(1);
                    String optName = rs.getString(2);
                    if (code == null) code = "";
                    if (optName == null) optName = "";

                    Map<String, String> opt = new LinkedHashMap<>();
                    opt.put("value", code);
                    opt.put("label", optName);
                    opt.put("selected", selectedValues.contains(code) ? "true" : "");
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
