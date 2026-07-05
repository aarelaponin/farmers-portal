package org.joget.lst;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report Filter Panel — a configurable parameter panel that hosts any report
 * (JasperReports menu, JDBC datalist, SQL chart) below it and drives it with
 * clean request parameters.
 *
 * <p>It renders a styled, collapsible panel of inputs (date, date range, text,
 * number, select, multi-select; options static or from a DB lookup), and embeds
 * the target report menu in a same-origin auto-height iframe. Pressing
 * <b>Apply</b> rebuilds the iframe URL with {@code ?param=value...}; the hosted
 * report reads those via {@code #requestParam.param#} (Jasper) or hash variables
 * (JDBC). One panel, every report, fully declarative — configured through the
 * userview JSON and pushed with {@code push_userview.py}, no per-report HTML.
 *
 * <p>Self-scoping: a parameter marked {@code bound} takes its value from a hash
 * variable (e.g. {@code #currentUser.username#}) and is rendered read-only, so a
 * customer-facing statement can only ever show the logged-in customer's data
 * (the hosted report's SQL must also filter on it — defence in depth).
 */
public class ReportFilterPanel extends UserviewMenu {

    private static final String CLASS_NAME = ReportFilterPanel.class.getName();

    @Override public String getCategory()    { return "Reporting"; }
    @Override public String getName()         { return "Report Filter Panel"; }
    @Override public String getVersion()      { return "8.1.0"; }
    @Override public String getDescription()  { return "Configurable parameter panel that hosts and drives any report"; }
    @Override public String getLabel()        { return "Report Filter Panel"; }
    @Override public String getClassName()    { return CLASS_NAME; }
    @Override public String getIcon()         { return "<i class=\"fas fa-sliders-h\"></i>"; }
    @Override public boolean isHomePageSupported() { return true; }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(),
                "/properties/reportFilterPanel.json", null, true, null);
    }

    @Override public String getDecoratedMenu() { return null; }

    @Override
    public String getRenderPage() {
        String panelTitle = prop("panelTitle", "FILTER CRITERIA");
        String columns    = prop("columns", "3");
        String applyLabel = prop("applyLabel", "Apply");
        String targetMenuId = prop("targetMenuId", "");
        String minHeight  = prop("frameMinHeight", "300");
        boolean collapsed = "true".equals(prop("collapsedByDefault", ""));

        List<Map<String, String>> params = readParams();

        // Build the embed URL for the hosted report from the CURRENT request URI
        // (robust: no dependency on injected appId/userviewId/key properties).
        String embedBase = buildEmbedBase(targetMenuId);

        // Initial query string from current request values / defaults
        String initialQs = buildInitialQueryString(params);

        StringBuilder sb = new StringBuilder();
        sb.append(panelCss());

        int activeCount = countActive(params);
        sb.append("<div class=\"rfp-panel").append(collapsed ? "" : " rfp-open").append("\" id=\"rfpPanel\">\n");
        sb.append("  <div class=\"rfp-header\" onclick=\"rfpToggle()\">\n");
        sb.append("    <span class=\"rfp-title\">").append(escHtml(panelTitle));
        if (activeCount > 0) sb.append(" <span class=\"rfp-count\">").append(activeCount).append("</span>");
        sb.append("</span>\n");
        sb.append("    <svg class=\"rfp-chev\" viewBox=\"0 0 20 20\" fill=\"currentColor\"><path d=\"M5.5 7.5L10 12l4.5-4.5\" stroke=\"currentColor\" stroke-width=\"1.5\" fill=\"none\"/></svg>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"rfp-body\">\n");
        sb.append("    <div class=\"rfp-grid\" style=\"grid-template-columns:repeat(").append(escHtml(columns)).append(",1fr);\">\n");

        StringBuilder js = new StringBuilder();   // descriptors for Apply
        for (Map<String, String> p : params) {
            sb.append(renderCell(p, js));
        }

        sb.append("      <div class=\"rfp-buttons\">\n");
        sb.append("        <button type=\"button\" class=\"rfp-apply\" onclick=\"rfpApply()\">").append(escHtml(applyLabel)).append("</button>\n");
        sb.append("        <button type=\"button\" class=\"rfp-reset\" onclick=\"rfpReset()\">Reset</button>\n");
        sb.append("        <span style=\"flex:1\"></span>\n");
        sb.append("        <a id=\"rfpPdf\" class=\"rfp-export\" target=\"_blank\" rel=\"noopener\" style=\"display:none\"><i class=\"fas fa-file-pdf\"></i> PDF</a>\n");
        sb.append("        <a id=\"rfpXls\" class=\"rfp-export\" target=\"_blank\" rel=\"noopener\" style=\"display:none\"><i class=\"fas fa-file-excel\"></i> Excel</a>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // Hosted report iframe (same-origin auto-height)
        sb.append("<iframe id=\"rfpFrame\" src=\"").append(escHtml(embedBase + (initialQs.isEmpty() ? "" : "?" + initialQs)))
          .append("\" style=\"width:100%;border:none;min-height:").append(escHtml(minHeight)).append("px;\" scrolling=\"auto\"></iframe>\n");

        // Behaviour
        sb.append("<script>\n");
        sb.append("var RFP_BASE=").append(jsStr(embedBase)).append(";\n");
        sb.append("var RFP_FIELDS=[").append(js).append("];\n");
        sb.append("function rfpToggle(){document.getElementById('rfpPanel').classList.toggle('rfp-open');}\n");
        sb.append("function rfpVal(id){var e=document.getElementById(id);if(!e)return '';");
        sb.append("if(e.multiple){var a=[];for(var i=0;i<e.options.length;i++){if(e.options[i].selected)a.push(e.options[i].value);}return a.join(';');}return e.value||'';}\n");
        sb.append("function rfpQuery(){var parts=[];RFP_FIELDS.forEach(function(f){");
        sb.append("var v=rfpVal(f.id);if(v!=='')parts.push(encodeURIComponent(f.p)+'='+encodeURIComponent(v));");
        sb.append("if(f.to){var v2=rfpVal(f.toId);if(v2!=='')parts.push(encodeURIComponent(f.to)+'='+encodeURIComponent(v2));}");
        sb.append("});return parts.join('&');}\n");
        sb.append("function rfpApply(){var q=rfpQuery();document.getElementById('rfpFrame').src=RFP_BASE+(q?('?'+q):'');}\n");
        sb.append("function rfpReset(){RFP_FIELDS.forEach(function(f){var e=document.getElementById(f.id);if(e){if(e.multiple){for(var i=0;i<e.options.length;i++)e.options[i].selected=false;}else if(!e.readOnly){e.value=e.getAttribute('data-default')||'';}}if(f.toId){var e2=document.getElementById(f.toId);if(e2)e2.value=e2.getAttribute('data-default')||'';}});rfpApply();}\n");
        // Mirror the hosted report's PDF/Excel export links onto the panel buttons, carrying the current filter.
        sb.append("function rfpWireExports(){try{var fr=document.getElementById('rfpFrame');var d=fr.contentWindow.document;var as=d.querySelectorAll('.exportlinks a, a');var pdf='',xls='';for(var i=0;i<as.length;i++){var a=as[i];var t=((a.textContent||'')+' '+(a.href||'')).toLowerCase();if(!pdf&&t.indexOf('pdf')>=0)pdf=a.href;else if(!xls&&(t.indexOf('xls')>=0||t.indexOf('excel')>=0))xls=a.href;}rfpSetExport('rfpPdf',pdf);rfpSetExport('rfpXls',xls);}catch(e){}}\n");
        // The hosted report's own export link already carries the current params (it was rendered with them), so use it as-is — appending rfpQuery() would duplicate params.
        sb.append("function rfpSetExport(id,base){var b=document.getElementById(id);if(!b)return;if(base){b.style.display='inline-flex';b.href=base;b.removeAttribute('onclick');b.onclick=null;}else{b.style.display='none';}}\n");
        // auto-height
        sb.append("(function(){var f=document.getElementById('rfpFrame');if(!f)return;");
        sb.append("function fit(){try{var d=f.contentWindow.document;var h=Math.max(d.body.scrollHeight,d.body.offsetHeight,d.documentElement.scrollHeight,d.documentElement.offsetHeight);var mx=Math.floor((window.innerHeight||800)*0.85);if(h>0)f.style.height=Math.min(h+45,mx)+'px';}catch(e){}}");
        sb.append("function rfpSync(){fit();rfpWireExports();}");
        sb.append("f.addEventListener('load',function(){rfpSync();setTimeout(rfpSync,300);setTimeout(rfpSync,1000);setTimeout(rfpSync,2500);});");
        sb.append("setInterval(fit,2000);})();\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    // ---- parameter cell rendering -----------------------------------------

    private String renderCell(Map<String, String> p, StringBuilder js) {
        String name  = sval(p, "paramName");
        if (name.isEmpty()) return "";
        String label = sval(p, "label"); if (label.isEmpty()) label = name;
        String type  = sval(p, "type"); if (type.isEmpty()) type = "text";
        String def   = sval(p, "default");
        boolean bound = "true".equals(sval(p, "bound"));
        String colspan = sval(p, "colspan");
        String id = "rfp_" + safe(name);

        String current = getRequestParameterString(name);
        String resolvedDefault = resolve(def);
        if (bound) current = resolvedDefault;                 // self-scoped: force value
        else if (current == null || current.isEmpty()) current = resolvedDefault;
        if (current == null) current = "";

        StringBuilder sb = new StringBuilder();
        sb.append("      <div class=\"rfp-cell\"");
        if (!colspan.isEmpty()) sb.append(" style=\"grid-column:span ").append(escHtml(colspan)).append(";\"");
        sb.append(">\n");
        sb.append("        <label class=\"rfp-label\">").append(escHtml(label));
        sb.append(" <span class=\"rfp-badge rfp-b-").append(escHtml(typeBadge(type))).append("\">").append(escHtml(typeBadge(type))).append("</span>");
        sb.append("</label>\n");

        if ("daterange".equals(type)) {
            String toName = sval(p, "toParamName"); if (toName.isEmpty()) toName = name + "To";
            String toId = "rfp_" + safe(toName);
            String toCur = getRequestParameterString(toName);
            String toDef = sval(p, "defaultTo");
            String toResolved = resolve(toDef);
            if (toCur == null || toCur.isEmpty()) toCur = toResolved;
            if (toCur == null) toCur = "";
            sb.append("        <div style=\"display:flex;gap:8px;\">\n");
            sb.append("          <input type=\"date\" id=\"").append(id).append("\" value=\"").append(escHtml(current)).append("\" data-default=\"").append(escHtml(resolvedDefault)).append("\" style=\"flex:1;\"/>\n");
            sb.append("          <input type=\"date\" id=\"").append(toId).append("\" value=\"").append(escHtml(toCur)).append("\" data-default=\"").append(escHtml(toResolved)).append("\" style=\"flex:1;\"/>\n");
            sb.append("        </div>\n");
            appendField(js, name, id, toName, toId);
        } else if ("select".equals(type) || "multiselect".equals(type)) {
            boolean multi = "multiselect".equals(type);
            List<String[]> opts = loadOptions(p);
            sb.append("        <select id=\"").append(id).append("\"").append(multi ? " multiple size=\"5\"" : "").append(bound ? " disabled" : "").append(">\n");
            if (!multi) sb.append("          <option value=\"\">(all)</option>\n");
            java.util.Set<String> cur = new java.util.HashSet<>();
            for (String v : current.split(";")) if (!v.trim().isEmpty()) cur.add(v.trim());
            for (String[] o : opts) {
                sb.append("          <option value=\"").append(escHtml(o[0])).append("\"").append(cur.contains(o[0]) ? " selected" : "").append(">").append(escHtml(o[1])).append("</option>\n");
            }
            sb.append("        </select>\n");
            appendField(js, name, id, null, null);
        } else {
            String inputType = "date".equals(type) ? "date" : ("number".equals(type) ? "number" : "text");
            sb.append("        <input type=\"").append(inputType).append("\" id=\"").append(id).append("\" value=\"").append(escHtml(current)).append("\" data-default=\"").append(escHtml(resolvedDefault)).append("\"").append(bound ? " readonly" : "").append("/>\n");
            appendField(js, name, id, null, null);
        }
        sb.append("      </div>\n");
        return sb.toString();
    }

    private void appendField(StringBuilder js, String p, String id, String toP, String toId) {
        if (js.length() > 0) js.append(",");
        js.append("{p:").append(jsStr(p)).append(",id:").append(jsStr(id));
        if (toP != null) js.append(",to:").append(jsStr(toP)).append(",toId:").append(jsStr(toId));
        js.append("}");
    }

    // ---- options ----------------------------------------------------------

    private List<String[]> loadOptions(Map<String, String> p) {
        List<String[]> out = new ArrayList<>();
        String source = sval(p, "optionsSource");
        if ("static".equals(source) || source.isEmpty()) {
            String raw = sval(p, "staticOptions");          // code|label;code|label
            for (String pair : raw.split(";")) {
                if (pair.trim().isEmpty()) continue;
                String[] kv = pair.split("\\|", 2);
                String code = kv[0].trim();
                String lbl = kv.length > 1 ? kv[1].trim() : code;
                if (!code.isEmpty() || !lbl.isEmpty()) out.add(new String[]{code, lbl});
            }
            if (!out.isEmpty() || !"lookup".equals(source)) return out;
        }
        // lookup
        String table = safe(sval(p, "lookupTable"));
        if (table.isEmpty()) return out;
        String codeCol = sval(p, "lookupCode"); if (codeCol.isEmpty()) codeCol = "c_code";
        String nameCol = sval(p, "lookupName"); if (nameCol.isEmpty()) nameCol = "c_name";
        codeCol = safe(codeCol); nameCol = safe(nameCol);
        String where = sval(p, "lookupWhere");
        String order = sval(p, "lookupOrder");
        String sql = "SELECT " + codeCol + ", " + nameCol + " FROM app_fd_" + table;
        if (!where.trim().isEmpty()) sql += " WHERE " + where;        // trusted config
        sql += " ORDER BY " + (order.trim().isEmpty() ? nameCol : safe(order));
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString(1); String nm = rs.getString(2);
                    out.add(new String[]{code == null ? "" : code, nm == null ? (code == null ? "" : code) : nm});
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "ReportFilterPanel option lookup failed: app_fd_" + table);
        }
        return out;
    }

    // ---- helpers ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> readParams() {
        List<Map<String, String>> list = new ArrayList<>();
        Object o = getProperty("parameters");
        if (o instanceof Object[]) {
            for (Object row : (Object[]) o) {
                if (row instanceof Map) {
                    Map<String, String> m = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) row).entrySet()) {
                        m.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                    }
                    list.add(m);
                }
            }
        }
        return list;
    }

    private String buildInitialQueryString(List<Map<String, String>> params) {
        StringBuilder qs = new StringBuilder();
        for (Map<String, String> p : params) {
            String name = sval(p, "paramName"); if (name.isEmpty()) continue;
            String def = sval(p, "default");
            boolean bound = "true".equals(sval(p, "bound"));
            String cur = getRequestParameterString(name);
            String resolved = resolve(def);
            if (bound) cur = resolved;
            else if (cur == null || cur.isEmpty()) cur = resolved;
            if (cur != null && !cur.isEmpty()) append(qs, name, cur);
            if ("daterange".equals(sval(p, "type"))) {
                String toName = sval(p, "toParamName"); if (toName.isEmpty()) toName = name + "To";
                String toCur = getRequestParameterString(toName);
                String toDef = sval(p, "defaultTo");
                String toResolved = resolve(toDef);
                if (toCur == null || toCur.isEmpty()) toCur = toResolved;
                if (toCur != null && !toCur.isEmpty()) append(qs, toName, toCur);
            }
        }
        return qs.toString();
    }

    private int countActive(List<Map<String, String>> params) {
        int n = 0;
        for (Map<String, String> p : params) {
            String name = sval(p, "paramName"); if (name.isEmpty()) continue;
            String v = getRequestParameterString(name);
            if (v != null && !v.isEmpty()) n++;
        }
        return n;
    }

    private void append(StringBuilder qs, String k, String v) {
        try {
            if (qs.length() > 0) qs.append("&");
            qs.append(URLEncoder.encode(k, "UTF-8")).append("=").append(URLEncoder.encode(v, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) { }
    }

    private String typeBadge(String type) {
        if ("daterange".equals(type) || "date".equals(type)) return "date";
        if ("select".equals(type) || "multiselect".equals(type)) return "select";
        if ("number".equals(type)) return "number";
        return "text";
    }

    private String prop(String name, String dflt) {
        String v = getPropertyString(name);
        return (v == null || v.isEmpty()) ? dflt : v;
    }

    /**
     * Build the embed URL for the hosted report by taking the current request
     * URI (…/web/userview/&lt;app&gt;/&lt;uv&gt;/&lt;key&gt;/&lt;thisMenu&gt;),
     * switching it to the embed path, and replacing the trailing menu id with
     * the target report's id. This is exactly how Joget's own embeddable menus
     * derive the embed link, and avoids any reliance on injected properties.
     */
    private String buildEmbedBase(String targetMenuId) {
        try {
            javax.servlet.http.HttpServletRequest req =
                    org.joget.workflow.util.WorkflowUtil.getHttpServletRequest();
            if (req != null) {
                String uri = req.getRequestURI();
                if (uri != null && uri.contains("/web/userview/")) {
                    String b = uri.replace("/web/userview/", "/web/embed/userview/");
                    int s = b.lastIndexOf('/');
                    if (s >= 0) return b.substring(0, s + 1) + targetMenuId;
                }
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "embed URL build from request failed");
        }
        // Fallback: assemble from app definition (userviewId still required)
        String ctx = AppUtil.getRequestContextPath();
        String appId = prop("appId", "");
        if (appId.isEmpty()) {
            AppDefinition ad = AppUtil.getCurrentAppDefinition();
            if (ad != null) appId = ad.getAppId();
        }
        String userviewId = prop("userviewId", "v");
        String key = getKey();
        if (key == null || key.isEmpty()) key = "_";
        return ctx + "/web/embed/userview/" + appId + "/" + userviewId + "/" + key + "/" + targetMenuId;
    }

    /**
     * Resolve a default that may be a hash variable (e.g. #date.now#,
     * #currentUser.username#). Invoked reflectively so the bundle has no
     * compile-time dependency on the WorkflowAssignment type carried in
     * AppUtil.processHashVariable's signature (which lives in a different jar).
     */
    private static String resolve(String def) {
        if (def == null) return "";
        if (def.equalsIgnoreCase("today") || def.equalsIgnoreCase("now") || def.equalsIgnoreCase("#date.now#")) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        if (!def.contains("#")) return def;
        try {
            Class<?> appUtil = Class.forName("org.joget.apps.app.service.AppUtil");
            for (java.lang.reflect.Method m : appUtil.getMethods()) {
                if (m.getName().equals("processHashVariable") && m.getParameterTypes().length == 4) {
                    Object r = m.invoke(null, def, null, null, null);
                    return r == null ? def : r.toString();
                }
            }
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "hash-variable resolve failed for: " + def);
        }
        return def;
    }
    private static String sval(Map<String, String> m, String k) { String v = m.get(k); return v == null ? "" : v.trim(); }
    private static String safe(String s) { return s == null ? "" : s.replaceAll("[^a-zA-Z0-9_]", ""); }
    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String jsStr(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
    }

    private String panelCss() {
        return "<style>\n"
            + ".rfp-panel{background:#fff;border:1px solid #d1d5db;border-radius:6px;margin-bottom:16px;overflow:hidden;font-family:sans-serif}\n"
            + ".rfp-header{display:flex;justify-content:space-between;align-items:center;padding:10px 20px;cursor:pointer;user-select:none}\n"
            + ".rfp-header:hover{background:#fafbfc}\n"
            + ".rfp-panel.rfp-open .rfp-header{border-bottom:1px solid #d1d5db}\n"
            + ".rfp-title{font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:#6b7280;font-weight:600;display:flex;align-items:center;gap:8px}\n"
            + ".rfp-count{font-size:11px;background:#2563eb;color:#fff;padding:1px 7px;border-radius:10px;font-weight:600}\n"
            + ".rfp-chev{width:18px;height:18px;color:#6b7280;transition:transform .25s}\n"
            + ".rfp-panel.rfp-open .rfp-chev{transform:rotate(180deg)}\n"
            + ".rfp-body{max-height:0;overflow:hidden;transition:max-height .3s ease}\n"
            + ".rfp-panel.rfp-open .rfp-body{max-height:1200px}\n"
            + ".rfp-grid{display:grid;gap:14px;padding:16px 20px}\n"
            + ".rfp-cell{display:flex;flex-direction:column;gap:4px}\n"
            + ".rfp-label{font-size:11px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:.03em}\n"
            + ".rfp-badge{display:inline-block;font-size:9px;font-weight:600;padding:1px 6px;border-radius:3px;margin-left:4px;text-transform:capitalize}\n"
            + ".rfp-b-date{background:#dbeafe;color:#2563eb}.rfp-b-text{background:#dcfce7;color:#16a34a}.rfp-b-select{background:#ede9fe;color:#7c3aed}.rfp-b-number{background:#fef3c7;color:#b45309}\n"
            + ".rfp-cell input,.rfp-cell select{border:1px solid #d1d5db;border-radius:4px;padding:0 8px;font-size:13px;color:#1f2937;background:#fff;height:34px;box-sizing:border-box;width:100%}\n"
            + ".rfp-cell select[multiple]{height:auto;padding:4px}\n"
            + ".rfp-buttons{grid-column:1/-1;display:flex;gap:8px;align-items:center;padding-top:4px}\n"
            + ".rfp-apply{height:34px;padding:0 22px;background:#2563eb;color:#fff;border:none;border-radius:4px;font-size:12px;font-weight:600;cursor:pointer}\n"
            + ".rfp-apply:hover{background:#1d4ed8}\n"
            + ".rfp-reset{height:34px;padding:0 14px;background:#fff;color:#6b7280;border:1px solid #d1d5db;border-radius:4px;font-size:12px;cursor:pointer}\n"
            + ".rfp-reset:hover{background:#f0f3f8}\n"
            + ".rfp-export{height:34px;padding:0 14px;align-items:center;gap:6px;border:1px solid #d1d5db;border-radius:4px;font-size:12px;color:#374151;text-decoration:none;cursor:pointer;background:#fff}\n"
            + ".rfp-export:hover{background:#f0f3f8;border-color:#9ca3af}\n"
            + "</style>\n";
    }
}
