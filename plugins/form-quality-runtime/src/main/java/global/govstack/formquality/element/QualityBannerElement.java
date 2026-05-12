package global.govstack.formquality.element;

import global.govstack.formquality.Build;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPalette;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;

import java.util.Map;

/**
 * Drop-in form element that renders the GovStack form-quality status banner
 * (PROGRAMME · STATUS · QUALITY · ID) and an issues detail panel that auto-shows
 * only when {@code list_qa_issues_for_record} has rows for the current record.
 * <p>
 * <b>Why this exists:</b> the banner used to be three loose blocks of inline
 * CustomHTML + EmbeddedDatalist copied into every wrapper form. That's a single
 * source-of-truth violation — any banner tweak meant editing every form. This
 * element owns the HTML/CSS/JS in Java and makes the banner a property-panel
 * configuration on each form (3 fields).
 * <p>
 * <b>Required property:</b>
 * <ul>
 *   <li>{@code serviceId} — the qa_service.serviceId this form is governed by
 *       (e.g. {@code farmers_subsidy}, {@code farmer_registration},
 *       {@code parcel_registration})</li>
 * </ul>
 * <p>
 * <b>Optional properties</b> (cosmetic — drive the chips in the banner):
 * <ul>
 *   <li>{@code codeField} — name of a HiddenField on the same form whose value
 *       is shown as the bold "code" chip (e.g. {@code programCode},
 *       {@code farmerCode}, {@code parcelCode})</li>
 *   <li>{@code statusField} — name of a HiddenField on the same form whose
 *       value is shown in the status pill</li>
 *   <li>{@code label} — the small uppercase label before the code chip
 *       (default: {@code Programme})</li>
 * </ul>
 * <p>
 * <b>How the issues feed works:</b> the banner JS hits
 * {@code /jw/web/json/data/list/<appId>/list_qa_issues_for_record?recordId=<id>}.
 * The {@code <appId>} is resolved server-side from {@link AppUtil} at render
 * time, so this element works in any GovStack app — no per-app hardcoding.
 */
public class QualityBannerElement extends Element
        implements FormBuilderPaletteElement, FormContainer {

    @Override
    public String getName()        { return "Form Quality Banner"; }
    @Override
    public String getDescription() {
        return "GovStack form-quality status banner with auto-rendered issues panel. ["
                + Build.STAMP + "]";
    }
    @Override
    public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override
    public String getLabel()       { return getName(); }
    @Override
    public String getClassName()   { return getClass().getName(); }

    @Override
    public String getFormBuilderTemplate() {
        // What shows on the form-builder canvas — operators see this placeholder
        // before the form is rendered for real.
        return "<div style=\"background:#0f4c75;color:#fff;padding:8px 14px;border-radius:4px;"
             + "font-size:13px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
             + "<span style=\"opacity:0.75;font-size:10px;text-transform:uppercase;letter-spacing:0.8px;\">Quality</span> "
             + "<span style=\"background:rgba(255,255,255,0.18);padding:2px 9px;border-radius:10px;font-weight:600;font-size:11px;\">"
             + "Form Quality Banner"
             + "</span>"
             + "</div>";
    }

    @Override
    public String getFormBuilderCategory() { return "GovStack"; }
    @Override
    public int    getFormBuilderPosition() { return 100; }
    @Override
    public String getFormBuilderIcon()     { return "<i class=\"fas fa-shield-alt\"></i>"; }

    @Override
    public String getPropertyOptions() {
        // Inline JSON keeps the property panel self-contained so we don't need
        // a separate /properties/*.json resource shipped in the JAR.
        return "[{"
            + "\"title\":\"Form Quality Banner\","
            + "\"properties\":["
            + "  {\"name\":\"serviceId\",\"label\":\"Service ID (qa_service.serviceId)\","
            + "   \"description\":\"Which rule library this form is governed by, e.g. farmers_subsidy.\","
            + "   \"type\":\"textfield\",\"required\":\"true\"},"
            + "  {\"name\":\"label\",\"label\":\"Banner Label\","
            + "   \"description\":\"Small uppercase label before the code chip. Defaults to 'Programme'.\","
            + "   \"type\":\"textfield\",\"value\":\"Programme\"},"
            + "  {\"name\":\"codeField\",\"label\":\"Code Field ID\","
            + "   \"description\":\"Name of a HiddenField on this form whose value to show as the bold 'code' chip (e.g. programCode, farmerCode).\","
            + "   \"type\":\"textfield\"},"
            + "  {\"name\":\"statusField\",\"label\":\"Status Field ID\","
            + "   \"description\":\"Name of a HiddenField on this form whose value to show in the status pill (e.g. status, regStatus).\","
            + "   \"type\":\"textfield\"}"
            + "]"
            + "}]";
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        // The Joget form-builder JS only treats the rendered HTML as a
        // selectable element if (a) the outermost div has class="form-cell"
        // and (b) it carries the data-cbuilder-* attributes from
        // elementMetaData. Without both, clicks bubble up to the parent
        // Column/Section. (Verified against jw-community Element.render +
        // FormUtil.generateElementMetaData + Element.decorateWithBuilderProperties.)
        String meta = (dataModel != null && dataModel.get("elementMetaData") != null)
                ? dataModel.get("elementMetaData").toString() : "";
        boolean inBuilder = !meta.isEmpty();

        if (inBuilder) {
            String svc = htmlAttr(defaultIfEmpty(getPropertyString("serviceId"), "(unset)"));
            String code = htmlAttr(defaultIfEmpty(getPropertyString("codeField"), "—"));
            String stat = htmlAttr(defaultIfEmpty(getPropertyString("statusField"), "—"));
            // outermost div: class="form-cell" + meta — that's what binds the
            // form-builder click handler. The inner styling is deliberately
            // simple so the click target is the whole row.
            return "<div class=\"form-cell\" " + meta + ">"
                 + "<div style=\"background:#eef4f9;border-left:4px solid #0f4c75;"
                 + "padding:8px 12px;border-radius:4px;font-size:12px;color:#0f4c75;"
                 + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                 + "pointer-events:none;\">"
                 + "<b>Form Quality Banner</b> &nbsp;·&nbsp; "
                 + "service: <code>" + svc + "</code> &nbsp;·&nbsp; "
                 + "code field: <code>" + code + "</code> &nbsp;·&nbsp; "
                 + "status field: <code>" + stat + "</code>"
                 + "</div>"
                 + "</div>";
        }

        // Runtime: same wrapping div with class="form-cell" so the host theme
        // styles us consistently with native form elements.
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = (appDef != null) ? appDef.getAppId() : "default";

        String serviceId   = jsAttr(getPropertyString("serviceId"));
        String label       = htmlAttr(defaultIfEmpty(getPropertyString("label"), "Programme"));
        String codeField   = jsAttr(getPropertyString("codeField"));
        String statusField = jsAttr(getPropertyString("statusField"));

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<div class=\"form-cell\">");
        sb.append(STYLE_BLOCK);
        sb.append("<div id=\"qbnr\" class=\"qbnr\">");
        sb.append("<span class=\"qbnr-l\">").append(label).append("</span>");
        sb.append("<span id=\"qbnr-code\" class=\"qbnr-code\">…</span>");
        sb.append("<span class=\"qbnr-sep\">·</span>");
        sb.append("<span id=\"qbnr-status\" class=\"qbnr-pill\">…</span>");
        sb.append("<span class=\"qbnr-sep\">·</span>");
        sb.append("<span class=\"qbnr-l\">Quality</span>");
        sb.append("<span id=\"qbnr-quality\" class=\"qbnr-pill qbnr-quality\">…</span>");
        sb.append("<span class=\"qbnr-sep qbnr-sep-r\">·</span>");
        sb.append("<span class=\"qbnr-l\">ID</span>");
        sb.append("<span id=\"qbnr-id\" class=\"qbnr-id\">…</span>");
        sb.append("</div>");
        sb.append("<div id=\"qbnr-issues\" class=\"qbnr-issues\" style=\"display:none\">");
        sb.append("<div class=\"qbnr-issues-h\">Quality issues</div>");
        sb.append("<table class=\"qbnr-issues-t\"><thead><tr>");
        sb.append("<th>Severity</th><th>Tab</th><th>Rule</th><th>Message</th>");
        sb.append("</tr></thead><tbody id=\"qbnr-issues-b\"></tbody></table></div>");

        // Inline JS — single fetch populates the pill, code/status chips, and
        // the issues table. No external script files, no jQuery dependency.
        sb.append("<script>(function(){");
        sb.append("var APP='").append(jsAttr(appId)).append("';");
        sb.append("var SVC='").append(serviceId).append("';");
        sb.append("var CODE_F='").append(codeField).append("';");
        sb.append("var STAT_F='").append(statusField).append("';");
        sb.append(JS_BODY);
        sb.append("})();</script>");
        sb.append("</div>");

        return sb.toString();
    }

    /* ----------------------------------------------------------------------
     * Render-time helpers — no Joget dependencies, just string sanitising.
     * -------------------------------------------------------------------- */

    private static String defaultIfEmpty(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    /** Escape for safe inclusion in a JS string literal (single-quoted). */
    private static String jsAttr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("</", "<\\/").replace("\n", "\\n").replace("\r", "");
    }

    /** Escape for safe inclusion in HTML text content. */
    private static String htmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /* ----------------------------------------------------------------------
     * The static HTML/CSS/JS that defines the banner. Kept as constants so
     * the renderTemplate() flow stays readable. CSS class names are scoped
     * with `qbnr-` to avoid colliding with the host theme.
     * -------------------------------------------------------------------- */

    private static final String STYLE_BLOCK =
        "<style>"
        + ".qbnr{background:#0f4c75;color:#fff;padding:6px 14px;border-radius:4px;"
        + "margin:0 0 10px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
        + "font-size:13px;display:flex;align-items:center;gap:14px;line-height:1.3;}"
        + ".qbnr-l{opacity:0.75;font-size:10px;text-transform:uppercase;letter-spacing:0.8px;}"
        + ".qbnr-code{font-weight:600;font-size:14px;}"
        + ".qbnr-sep{opacity:0.5;}"
        + ".qbnr-sep-r{margin-left:auto;}"
        + ".qbnr-pill{background:rgba(255,255,255,0.18);padding:2px 9px;border-radius:10px;"
        + "font-weight:600;font-size:11px;letter-spacing:0.4px;color:#fff;}"
        + ".qbnr-id{font-family:Menlo,Monaco,Consolas,monospace;font-size:12px;opacity:0.9;}"
        + ".qbnr-issues{border-left:4px solid #d32f2f;background:#fff5f5;"
        + "padding:8px 12px;border-radius:4px;margin:0 0 10px 0;"
        + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;}"
        + ".qbnr-issues-h{font-weight:600;font-size:12px;color:#7a1f1f;"
        + "text-transform:uppercase;letter-spacing:0.6px;margin-bottom:6px;}"
        + ".qbnr-issues-t{width:100%;border-collapse:collapse;font-size:13px;}"
        + ".qbnr-issues-t th{text-align:left;padding:4px 8px;color:#555;font-weight:600;"
        + "border-bottom:1px solid #f0d4d4;}"
        + ".qbnr-issues-t td{padding:4px 8px;vertical-align:top;border-bottom:1px solid #f6e6e6;}"
        + ".qbnr-issues-t td.sev-error{color:#d32f2f;font-weight:600;}"
        + ".qbnr-issues-t td.sev-warning{color:#f57c00;font-weight:600;}"
        + "</style>";

    private static final String JS_BODY =
        "function setQual(text,bg,fg){var q=document.getElementById('qbnr-quality');"
      + "if(!q)return;q.textContent=text;q.style.background=bg;q.style.color=fg||'#fff';}"
      + "function rowHtml(r){var sev=(r.severity||'').toUpperCase();"
      + "var cls=sev==='ERROR'?'sev-error':(sev==='WARNING'?'sev-warning':'');"
      + "function esc(s){return (s==null?'':String(s)).replace(/[&<>]/g,function(c){return c==='&'?'&amp;':c==='<'?'&lt;':'&gt;';});}"
      + "return '<tr><td class=\"'+cls+'\">'+esc(sev)+'</td><td>'+esc(r.tabCode||'')+"
      + "'</td><td>'+esc(r.ruleCode||'')+'</td><td>'+esc(r.message||'')+'</td></tr>';}"
      // RECORD ID — from URL, set once and never changes during the session
      + "var URL_ID=(new URLSearchParams(location.search)).get('id')||'';"
      // FIELD CHIP UPDATERS — keep retrying until the named input has a value,
      // and live-update on user edits. Joget renders forms after DOMContentLoaded
      // so on first run our inputs are either missing or empty; without the
      // retry/observer we'd permanently fall through to URL_ID and show the UUID.
      // findField — match the named input regardless of how Joget prefixed it.
      // Joget's AbstractSubForm.updateElementParameterNames rewrites a subform
      // field's `name` attribute to `<subformId>_<fieldId>` (and recursively
      // nests it for deeper subforms), so the literal `name="national_id"`
      // selector misses subform fields. We try exact match first, then suffix
      // match (`*_national_id`), then ID-based fallbacks.
      + "function findField(name){"
      + "var sels=["
      + "'[name=\"'+name+'\"]',"                     // exact (top-level field)
      + "'[name$=\"_'+name+'\"]',"                  // subform-prefixed
      + "'[id=\"'+name+'\"]',"                       // by id
      + "'[id$=\"_'+name+'\"]'"                     // subform-prefixed id
      + "];"
      + "for(var i=0;i<sels.length;i++){"
      + "var list=document.querySelectorAll(sels[i]);"
      // Prefer a populated input; fall back to first match for live binding
      + "for(var j=0;j<list.length;j++){"
      + "if(list[j].value)return list[j];"
      + "}"
      + "if(list.length>0&&i===0)return list[0];"   // exact-name empty: take it
      + "}"
      + "return null;"
      + "}"
      + "function bindFieldChip(name,chipId){"
      + "if(!name)return;"
      + "var chip=document.getElementById(chipId);"
      + "if(!chip)return;"
      + "var attempts=0,MAX=40,bound=false;"   // 40*250ms = 10s window
      + "function pull(){"
      + "var inp=findField(name);"
      + "if(inp){"
      + "if(inp.value){chip.textContent=inp.value;}"
      + "if(!bound){bound=true;"
      + "inp.addEventListener('input',function(){chip.textContent=inp.value||'\\u2013';});"
      + "inp.addEventListener('change',function(){chip.textContent=inp.value||'\\u2013';});"
      + "}}"
      + "if(!(inp&&inp.value)&&attempts++<MAX){setTimeout(pull,250);}"
      + "}"
      + "pull();"
      + "}"
      + "function go(){"
      + "var c=document.getElementById('qbnr-code'),"
      + "s=document.getElementById('qbnr-status'),"
      + "i=document.getElementById('qbnr-id');"
      // Initial chip values: code falls back to URL_ID if no codeField configured;
      // otherwise stays as '…' until pull() finds the input with a value.
      + "if(c)c.textContent=CODE_F?'\\u2026':(URL_ID||'\\u2013');"
      + "if(s)s.textContent=STAT_F?'\\u2026':'\\u2013';"
      + "if(i)i.textContent=URL_ID||'\\u2013';"
      + "bindFieldChip(CODE_F,'qbnr-code');"
      + "bindFieldChip(STAT_F,'qbnr-status');"
      + "if(!URL_ID){setQual('?','rgba(255,255,255,0.18)');return;}"
      + "var url='/jw/web/json/data/list/'+APP+'/list_qa_issues_for_record?recordId='"
      + "+encodeURIComponent(URL_ID)+'&start=0&rows=200';"
      + "fetch(url,{credentials:'same-origin',headers:{'Accept':'application/json'}})"
      + ".then(function(r){return r.ok?r.json():Promise.reject(r.status);})"
      + ".then(function(d){var rows=(d&&d.data)||[];var err=0,warn=0;"
      + "rows.forEach(function(r){var sev=(r.severity||'').toUpperCase();"
      + "if(sev==='ERROR')err++;else if(sev==='WARNING')warn++;});"
      + "if(err===0&&warn===0){setQual('\\u2713 Clean','#22a774');"
      + "var p=document.getElementById('qbnr-issues');if(p)p.style.display='none';}"
      + "else if(err>0){setQual('\\u26a0 '+err+' error'+(err>1?'s':'')"
      + "+(warn?' / '+warn+' warn':''),'#d32f2f');}"
      + "else{setQual('\\u26a0 '+warn+' warning'+(warn>1?'s':''),'#f57c00');}"
      + "if(err>0||warn>0){var p=document.getElementById('qbnr-issues');"
      + "var b=document.getElementById('qbnr-issues-b');"
      + "if(p&&b){b.innerHTML=rows.map(rowHtml).join('');p.style.display='block';}}})"
      + ".catch(function(){setQual('?','rgba(255,255,255,0.18)');});}"
      + "if(document.readyState!=='loading'){go();}"
      + "else{document.addEventListener('DOMContentLoaded',go);}";
}
