package global.govstack.identity.element;

import global.govstack.identity.Build;
import global.govstack.identity.model.FieldMap;
import global.govstack.identity.model.ResolveResult;
import global.govstack.identity.service.ConfigRepository;
import global.govstack.identity.service.FieldMapRepository;
import global.govstack.identity.service.ResolverService;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginWebSupport;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * GovStack RegBB Identity Verification — drop-in form element that, on input
 * blur or button click, resolves a foundational identifier (e.g. National ID)
 * via {@link ResolverService} and pre-fills target form fields.
 *
 * <p>Combines two responsibilities into one OSGi service registration so the
 * bundle stays small:
 * <ul>
 *   <li>{@link FormBuilderPaletteElement} — renders in App Composer's palette
 *       under the GovStack category; produces the runtime HTML+JS</li>
 *   <li>{@link PluginWebSupport} — exposes the resolve endpoint at
 *       {@code /jw/web/json/plugin/global.govstack.identity.element.IdentityResolverElement/service}
 *       which the element's own JS calls via fetch</li>
 * </ul>
 *
 * <p>Configurable per element instance via property panel:
 * <ul>
 *   <li>{@code resolverConfigId} (required) — picks one of the operator-managed
 *       configs in {@code app_resolver_config}</li>
 *   <li>{@code inputLabel}, {@code placeholder} — cosmetic</li>
 *   <li>{@code autoResolveOnBlur} — whether to fire on blur</li>
 *   <li>{@code showResolveButton} — show explicit Resolve button</li>
 *   <li>{@code showStatusInline} — show ✓/⚠/✕ icon next to input</li>
 * </ul>
 */
public class IdentityResolverElement extends Element
        implements FormBuilderPaletteElement, FormContainer, PluginWebSupport {

    private static final String CLASS_NAME = IdentityResolverElement.class.getName();

    @Override
    public String getName()        { return "Identity Resolver"; }
    @Override
    public String getDescription() {
        return "GovStack RegBB Identity Verification — looks up a foundational "
             + "identifier and pre-fills target fields. [" + Build.STAMP + "]";
    }
    @Override
    public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override
    public String getLabel()       { return getName(); }
    @Override
    public String getClassName()   { return getClass().getName(); }

    @Override
    public String getFormBuilderTemplate() {
        return "<div style=\"background:#eef4f9;border-left:4px solid #185fa5;"
             + "padding:8px 12px;border-radius:4px;font-size:12px;color:#0a3a6b;"
             + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
             + "<b>Identity Resolver</b> — looks up by foundational ID</div>";
    }

    @Override
    public String getFormBuilderCategory() { return "GovStack"; }
    @Override
    public int    getFormBuilderPosition() { return 110; }
    @Override
    public String getFormBuilderIcon()     { return "<i class=\"fas fa-id-card\"></i>"; }

    @Override
    public String getPropertyOptions() {
        return "[{"
            + "\"title\":\"Identity Resolver\","
            + "\"properties\":["
            + "  {\"name\":\"resolverConfigId\",\"label\":\"Resolver Config ID (app_resolver_config.configId)\","
            + "   \"description\":\"Which named resolver to invoke, e.g. farmerByNid.\","
            + "   \"type\":\"textfield\",\"required\":\"true\"},"
            + "  {\"name\":\"inputLabel\",\"label\":\"Input Label\","
            + "   \"type\":\"textfield\",\"value\":\"National ID\"},"
            + "  {\"name\":\"placeholder\",\"label\":\"Placeholder\","
            + "   \"type\":\"textfield\",\"value\":\"Enter National ID\"},"
            + "  {\"name\":\"autoResolveOnBlur\",\"label\":\"Auto-resolve on blur\","
            + "   \"type\":\"selectbox\",\"value\":\"Y\","
            + "   \"options\":[{\"value\":\"Y\",\"label\":\"Yes\"},{\"value\":\"N\",\"label\":\"No\"}]},"
            + "  {\"name\":\"showResolveButton\",\"label\":\"Show Resolve button\","
            + "   \"type\":\"selectbox\",\"value\":\"Y\","
            + "   \"options\":[{\"value\":\"Y\",\"label\":\"Yes\"},{\"value\":\"N\",\"label\":\"No\"}]},"
            + "  {\"name\":\"showStatusInline\",\"label\":\"Show inline status icon\","
            + "   \"type\":\"selectbox\",\"value\":\"Y\","
            + "   \"options\":[{\"value\":\"Y\",\"label\":\"Yes\"},{\"value\":\"N\",\"label\":\"No\"}]}"
            + "]"
            + "}]";
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        // Detect form-builder mode (same trick QualityBannerElement uses).
        String meta = (dataModel != null && dataModel.get("elementMetaData") != null)
                ? dataModel.get("elementMetaData").toString() : "";
        boolean inBuilder = !meta.isEmpty();

        String configId = htmlAttr(getPropertyString("resolverConfigId"));
        String label    = htmlAttr(defaultIfEmpty(getPropertyString("inputLabel"), "National ID"));
        String ph       = htmlAttr(defaultIfEmpty(getPropertyString("placeholder"), "Enter National ID"));
        boolean autoBlur = !"N".equalsIgnoreCase(getPropertyString("autoResolveOnBlur"));
        boolean showBtn  = !"N".equalsIgnoreCase(getPropertyString("showResolveButton"));
        boolean showStat = !"N".equalsIgnoreCase(getPropertyString("showStatusInline"));

        if (inBuilder) {
            return "<div class=\"form-cell\" " + meta + ">"
                 + "<div style=\"background:#eef4f9;border-left:4px solid #185fa5;"
                 + "padding:8px 12px;border-radius:4px;font-size:12px;color:#0a3a6b;"
                 + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                 + "pointer-events:none;\">"
                 + "<b>Identity Resolver</b> &nbsp;·&nbsp; config: <code>"
                 + (configId.isEmpty() ? "(unset)" : configId) + "</code> &nbsp;·&nbsp; "
                 + "input label: <code>" + label + "</code>"
                 + "</div></div>";
        }

        // Runtime HTML
        String valueField = htmlAttr(defaultIfEmpty(getPropertyString("valueField"), "national_id"));
        boolean useExternal = "Y".equalsIgnoreCase(getPropertyString("useExternalInput"));

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<div class=\"form-cell\">");
        sb.append(STYLE_BLOCK);
        sb.append("<div class=\"idrslv\">");
        sb.append("  <label class=\"idrslv-l\">").append(label).append("</label>");
        sb.append("  <div class=\"idrslv-row\">");

        if (useExternal) {
            // Button-only mode: the value lives in a sibling form field
            // (e.g. a regular TextField with id=national_id) so persistence
            // is fully owned by Joget. We just operate on that field.
            sb.append("    <span class=\"idrslv-hint\" data-valuefield=\"")
              .append(valueField).append("\">")
              .append("Reads National ID from the field above.")
              .append("</span>");
        } else {
            // Read the stored value so reopening the form pre-fills the input.
            String storedValue = "";
            try {
                String v = org.joget.apps.form.service.FormUtil.getElementPropertyValue(this, formData);
                if (v != null) storedValue = htmlAttr(v);
            } catch (Throwable ignore) {}
            if (storedValue.isEmpty() && formData != null) {
                try {
                    org.joget.apps.form.model.Form parentForm =
                            org.joget.apps.form.service.FormUtil.findRootForm(this);
                    if (parentForm != null) {
                        String v = formData.getLoadBinderDataProperty(parentForm, valueField);
                        if (v != null) storedValue = htmlAttr(v);
                    }
                } catch (Throwable ignore) {}
            }
            sb.append("    <input id=\"idrslv-input\" class=\"idrslv-input\" type=\"text\"")
              .append(" data-valuefield=\"").append(valueField).append("\"")
              .append(" value=\"").append(storedValue).append("\"")
              .append(" placeholder=\"").append(ph).append("\" autocomplete=\"off\"/>");
        }
        if (showBtn) {
            sb.append("    <button type=\"button\" class=\"idrslv-btn\" id=\"idrslv-btn\">Resolve</button>");
        }
        if (showStat) {
            sb.append("    <span id=\"idrslv-status\" class=\"idrslv-status\">&nbsp;</span>");
        }
        sb.append("  </div>");
        sb.append("  <div id=\"idrslv-msg\" class=\"idrslv-msg\" style=\"display:none\"></div>");
        sb.append("</div>");

        // JS — calls webService() of this same class via the standard plugin URL.
        sb.append("<script>(function(){");
        sb.append("var CFG='").append(jsAttr(configId)).append("';");
        sb.append("var URL='/jw/web/json/plugin/").append(getClass().getName()).append("/service';");
        sb.append("var AUTO=").append(autoBlur).append(";");
        sb.append("var EXTERNAL=").append(useExternal).append(";");
        sb.append("var VALUE_FIELD='").append(jsAttr(valueField)).append("';");
        sb.append(JS_BODY);
        sb.append("})();</script>");
        sb.append("</div>");
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* PluginWebSupport — REST endpoint                                   */
    /* ------------------------------------------------------------------ */

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String configId = request.getParameter("configId");
        String value    = request.getParameter("value");

        // ALWAYS return HTTP 200 with our own JSON body. The client dispatches
        // on the body's `status` field. Why: Joget's JsonResponseFilter wraps
        // any HTTP 4xx/5xx response in a generic {"error":{date,message,code}}
        // shape and discards our body — which would erase NOT_FOUND messages,
        // candidate lists, and ERROR diagnostics. Status code semantics live
        // inside the JSON envelope instead.
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(200);

        if (configId == null || configId.isEmpty()
                || value == null || value.isEmpty()) {
            response.getWriter().write(
                    "{\"status\":\"ERROR\",\"message\":\"Missing configId or value\"}");
            return;
        }

        try {
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            ResolverService svc = new ResolverService(
                    dao,
                    new ConfigRepository(dao),
                    new FieldMapRepository(dao));
            ResolveResult result = svc.resolve(configId, value);

            PrintWriter w = response.getWriter();
            w.write(toJson(result));
            w.flush();
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "webService failed for configId=" + configId + ", value=" + value);
            response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Internal error: "
                    + escapeJson(t.getClass().getSimpleName()) + "\"}");
        }
    }

    /** Hand-rolled JSON to avoid pulling Gson into the bundle. */
    private static String toJson(ResolveResult r) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"status\":\"").append(r.getStatus().name()).append("\"");
        if (r.getSourceRecordId() != null) {
            sb.append(",\"sourceRecordId\":\"").append(escapeJson(r.getSourceRecordId())).append("\"");
        }
        if (r.getMessage() != null) {
            sb.append(",\"message\":\"").append(escapeJson(r.getMessage())).append("\"");
        }
        if (r.getActionUrl() != null) {
            sb.append(",\"actionUrl\":\"").append(escapeJson(r.getActionUrl())).append("\"");
        }
        if (!r.getFields().isEmpty()) {
            sb.append(",\"fields\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : r.getFields().entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(e.getKey())).append("\":")
                  .append("\"").append(escapeJson(e.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
        }
        if (!r.getCandidates().isEmpty()) {
            sb.append(",\"candidates\":[");
            boolean first = true;
            for (ResolveResult.Candidate c : r.getCandidates()) {
                if (!first) sb.append(",");
                sb.append("{\"sourceRecordId\":\"").append(escapeJson(c.sourceRecordId))
                  .append("\",\"displayLabel\":\"").append(escapeJson(c.displayLabel)).append("\"}");
                first = false;
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */

    private static String defaultIfEmpty(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private static String jsAttr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("</", "<\\/").replace("\n", "\\n").replace("\r", "");
    }

    private static String htmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /* ------------------------------------------------------------------ */
    /* Inline CSS + JS — kept as constants so renderTemplate stays clean. */
    /* ------------------------------------------------------------------ */

    private static final String STYLE_BLOCK =
        "<style>"
        + ".idrslv{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
        + "margin:0 0 12px 0;}"
        + ".idrslv-l{display:block;font-size:12px;color:#445566;margin-bottom:4px;}"
        + ".idrslv-row{display:flex;align-items:stretch;gap:8px;}"
        + ".idrslv-input{flex:1;padding:7px 10px;border:1px solid #cdd5e0;"
        + "border-radius:4px;font-size:14px;}"
        + ".idrslv-input:focus{outline:none;border-color:#185fa5;}"
        + ".idrslv-btn{padding:7px 14px;background:#185fa5;color:#fff;border:none;"
        + "border-radius:4px;font-size:13px;cursor:pointer;}"
        + ".idrslv-btn:hover{background:#0a3a6b;}"
        + ".idrslv-btn:disabled{background:#9eafc4;cursor:wait;}"
        + ".idrslv-status{display:inline-flex;align-items:center;justify-content:center;"
        + "width:30px;height:30px;border-radius:50%;font-size:14px;font-weight:600;"
        + "background:#eef4f9;color:#445566;}"
        + ".idrslv-status.ok{background:#22a774;color:#fff;}"
        + ".idrslv-status.warn{background:#f57c00;color:#fff;}"
        + ".idrslv-status.err{background:#d32f2f;color:#fff;}"
        + ".idrslv-msg{margin-top:8px;padding:8px 12px;border-radius:4px;font-size:13px;}"
        + ".idrslv-msg.warn{background:#fff8e1;border-left:4px solid #f57c00;color:#7a4a00;}"
        + ".idrslv-msg.err{background:#fff5f5;border-left:4px solid #d32f2f;color:#7a1f1f;}"
        + ".idrslv-msg a{color:inherit;text-decoration:underline;font-weight:600;}"
        + "</style>";

    private static final String JS_BODY =
          "function setStatus(cls,txt){var s=document.getElementById('idrslv-status');"
        + "if(!s)return;s.className='idrslv-status '+(cls||'');s.textContent=txt||'';}"
        + "function setMsg(cls,html){var m=document.getElementById('idrslv-msg');"
        + "if(!m)return;if(!html){m.style.display='none';m.innerHTML='';return;}"
        + "m.className='idrslv-msg '+(cls||'');m.innerHTML=html;m.style.display='block';}"
        // Subform-prefix-aware target lookup — same pattern as QualityBannerElement build-006
        + "function findField(name){"
        + "var sels=['[name=\"'+name+'\"]','[name$=\"_'+name+'\"]','[id=\"'+name+'\"]','[id$=\"_'+name+'\"]'];"
        + "for(var i=0;i<sels.length;i++){var l=document.querySelectorAll(sels[i]);"
        + "if(l.length>0)return l[0];}return null;}"
        + "function applyFields(fields){if(!fields)return;"
        + "Object.keys(fields).forEach(function(target){"
        + "var inp=findField(target);if(!inp)return;"
        + "inp.value=fields[target];"
        + "try{inp.dispatchEvent(new Event('input',{bubbles:true}));"
        + "inp.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}"
        + "});}"
        + "function escHtml(s){return (s==null?'':String(s)).replace(/[&<>]/g,"
        + "function(c){return c==='&'?'&amp;':c==='<'?'&lt;':'&gt;';});}"
        + "function readValue(){"
        + "if(EXTERNAL){var f=findField(VALUE_FIELD);return f?(f.value||'').trim():'';}"
        + "var input=document.getElementById('idrslv-input');"
        + "return input?(input.value||'').trim():'';}"
        + "function doResolve(){"
        + "var btn=document.getElementById('idrslv-btn');"
        + "var v=readValue();"
        + "if(!v){setStatus('','');setMsg(null,'');return;}"
        + "if(!CFG){setStatus('err','!');setMsg('err','Resolver Config ID is not set on this element.');return;}"
        + "if(btn)btn.disabled=true;"
        + "setStatus('','…');"
        + "fetch(URL+'?configId='+encodeURIComponent(CFG)+'&value='+encodeURIComponent(v),"
        + "{credentials:'same-origin',headers:{'Accept':'application/json'}})"
        + ".then(function(r){return r.json().then(function(d){return {http:r.status,body:d};});})"
        + ".then(function(out){var d=out.body;"
        + "if(d.status==='FOUND'){setStatus('ok','\u2713');setMsg(null,'');applyFields(d.fields||{});}"
        + "else if(d.status==='NOT_FOUND'){setStatus('warn','!');"
        + "var html=escHtml(d.message||'Not registered.');"
        + "if(d.actionUrl){html+=' <a href=\"'+d.actionUrl+'\">Register now \u2192</a>';}"
        + "setMsg('warn',html);}"
        + "else if(d.status==='MULTIPLE'){setStatus('warn','?');"
        + "var html='Multiple records match. Please contact your administrator.';"
        + "if(d.candidates&&d.candidates.length){html='Multiple records match: '+"
        + "d.candidates.map(function(c){return escHtml(c.displayLabel||c.sourceRecordId);}).join(', ');}"
        + "setMsg('warn',html);}"
        + "else{setStatus('err','!');setMsg('err',escHtml(d.message||'Resolve failed.'));}})"
        + ".catch(function(e){setStatus('err','!');setMsg('err','Network error during resolve.');})"
        + ".finally(function(){if(btn)btn.disabled=false;});"
        + "}"
        // Bidirectional sync between the resolver input and the form's
        // configured value field (default: national_id). On bind, copy the
        // stored value INTO the resolver input. On every keystroke, mirror
        // the resolver input INTO the form field so Joget's storeBinder
        // picks it up on save.
        + "function syncFromField(input){var fname=input.getAttribute('data-valuefield');"
        + "if(!fname)return;var f=findField(fname);"
        + "if(f&&f.value&&!input.value){input.value=f.value;}}"
        + "function syncToField(input){var fname=input.getAttribute('data-valuefield');"
        + "if(!fname)return;var f=findField(fname);"
        + "if(f){f.value=input.value;"
        + "try{f.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}}}"
        + "function bind(){var input=document.getElementById('idrslv-input');"
        + "var btn=document.getElementById('idrslv-btn');"
        + "if(input){syncFromField(input);"
        + "input.addEventListener('input',function(){syncToField(input);});"
        + "if(AUTO)input.addEventListener('blur',doResolve);"
        + "input.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();doResolve();}});}"
        + "if(EXTERNAL){var ef=findField(VALUE_FIELD);"
        + "if(ef&&AUTO)ef.addEventListener('blur',doResolve);"
        + "if(ef)ef.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();doResolve();}});}"
        + "if(btn)btn.addEventListener('click',doResolve);}"
        + "if(document.readyState!=='loading')bind();"
        + "else document.addEventListener('DOMContentLoaded',bind);";
}
