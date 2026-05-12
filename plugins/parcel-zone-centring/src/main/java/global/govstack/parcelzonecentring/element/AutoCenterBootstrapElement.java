package global.govstack.parcelzonecentring.element;

import global.govstack.parcelzonecentring.Build;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.Map;

/**
 * Renders an invisible client-side bootstrap that pre-populates the GIS
 * widget's auto_center_lat / auto_center_lon fields by reading the wizard's
 * district + agroEcologicalZone values from the DOM and looking up the
 * matching centroid in an embedded MD.95 table.
 *
 * <p><b>Why client-side instead of a server-side load binder.</b> Joget's
 * MultiPagedForm wizard with {@code partiallyStore=true} keeps the URL at
 * {@code _mode=add} until the citizen clicks the wizard's final Save. Sub-form
 * load binders therefore run with empty primary keys, and
 * {@code AbstractSubForm.populateSubFormWithParentKey} (jw-community
 * line 297) cannot propagate parent_id on first visit to the Location tab.
 * Every server-side render-time approach we tried (post-processor on
 * parcelLocation, custom storeBinder cascade with parent_id bootstrap,
 * custom loadBinder enrichment from sibling FormData) wrote correct values
 * to the database but couldn't get them into the form's HiddenField DOM
 * inputs in time for the GIS widget to read on first init.
 *
 * <p>The DOM, however, is reliable: Joget's MultiPagedForm preserves field
 * values across tabs as plain HTML inputs in the same page (hidden via CSS
 * when their tab is inactive). Reading district/zone from the DOM and
 * writing auto_center to HiddenField inputs is independent of Joget's
 * server-side render pipeline.
 *
 * <p><b>Render strategy.</b> At render time the element queries
 * {@code md_district_eco_zone} for all 25-ish (district, zone, lat, lon)
 * tuples and embeds them as a JSON object in the emitted script. Lookup is
 * synchronous client-side — no REST call, no auth, no async race with the
 * GIS widget's init. The script runs at DOM-ready (before GIS init if our
 * element is positioned earlier in the form definition) and again on every
 * change to district/zone fields so cascading dropdown updates re-centre
 * the map.
 *
 * <p><b>Required position.</b> Place this element BEFORE the
 * {@code GisPolygonCaptureElement} on the same form (or the same wizard tab
 * subform's parent if forms differ). Its emitted script must run before
 * the GIS widget's init reads {@code auto_center_lat/lon} for the first
 * time, otherwise the widget falls back to its built-in defaults and the
 * "Using pre-computed coordinates" badge does not appear.
 *
 * <p><b>Configurable properties:</b>
 * <ul>
 *   <li>{@code districtFieldId} — the form-field id of the district
 *       SelectBox in the wizard. Default: {@code district}.</li>
 *   <li>{@code zoneFieldId} — the form-field id of the agro-ecological zone
 *       SelectBox. Default: {@code agroEcologicalZone}.</li>
 *   <li>{@code latFieldId} — the form-field id of the auto-centre latitude
 *       HiddenField. Default: {@code auto_center_lat}.</li>
 *   <li>{@code lonFieldId} — the form-field id of the auto-centre longitude
 *       HiddenField. Default: {@code auto_center_lon}.</li>
 * </ul>
 *
 * <p>All four can stay at default for the parcelGeometry / parcelLocation
 * pair; the properties exist so the element can be reused on any future
 * (district, zone) pair.
 */
public class AutoCenterBootstrapElement extends Element
        implements FormBuilderPaletteElement, FormContainer {

    private static final String CLASS_NAME = AutoCenterBootstrapElement.class.getName();
    private static final String ZONE_LOOKUP_FORM = "md_district_eco_zone";

    @Override public String getName()        { return "Auto Centre Bootstrap"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override public String getLabel()       { return getName(); }
    @Override public String getClassName()   { return getClass().getName(); }
    @Override public String getDescription() {
        return "Reads district + agroEcologicalZone from the wizard's DOM and "
             + "writes the matching MD.95 centroid into the GIS widget's "
             + "auto_center_lat/lon HiddenFields, before the widget initialises. "
             + "Place BEFORE the GIS Polygon Capture element on the same form. ["
             + Build.STAMP + "]";
    }

    @Override public String getFormBuilderTemplate() {
        return "<div style=\"background:#eef4f9;border-left:4px solid #2c7a7b;"
             + "padding:8px 12px;border-radius:4px;font-size:12px;color:#2c7a7b;"
             + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
             + "<b>Auto Centre Bootstrap</b> · pre-populates GIS auto_center_lat/lon "
             + "from MD.95 (invisible at runtime)"
             + "</div>";
    }

    @Override public String getFormBuilderCategory() { return "GovStack"; }
    @Override public int    getFormBuilderPosition() { return 110; }
    @Override public String getFormBuilderIcon()     { return "<i class=\"fas fa-crosshairs\"></i>"; }

    @Override
    public String getPropertyOptions() {
        return "[{"
            + "\"title\":\"Auto Centre Bootstrap\","
            + "\"properties\":["
            + "  {\"name\":\"districtFieldId\",\"label\":\"District field id\","
            + "   \"description\":\"Sibling form-field id holding the district code (e.g. district).\","
            + "   \"type\":\"textfield\",\"value\":\"district\"},"
            + "  {\"name\":\"zoneFieldId\",\"label\":\"Zone field id\","
            + "   \"description\":\"Sibling form-field id holding the agro-ecological zone code.\","
            + "   \"type\":\"textfield\",\"value\":\"agroEcologicalZone\"},"
            + "  {\"name\":\"latFieldId\",\"label\":\"Auto-centre lat field id\","
            + "   \"description\":\"Local form-field id of the GIS auto-centre latitude HiddenField.\","
            + "   \"type\":\"textfield\",\"value\":\"auto_center_lat\"},"
            + "  {\"name\":\"lonFieldId\",\"label\":\"Auto-centre lon field id\","
            + "   \"description\":\"Local form-field id of the GIS auto-centre longitude HiddenField.\","
            + "   \"type\":\"textfield\",\"value\":\"auto_center_lon\"}"
            + "]}]";
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        // Form-builder preview: render the placeholder banner; never run the
        // bootstrap JS in builder mode (would be a no-op anyway, but cleaner).
        String meta = (dataModel != null && dataModel.get("elementMetaData") != null)
                ? dataModel.get("elementMetaData").toString() : "";
        boolean inBuilder = !meta.isEmpty();
        if (inBuilder) {
            String d = htmlAttr(defaultIfEmpty(getPropertyString("districtFieldId"), "district"));
            String z = htmlAttr(defaultIfEmpty(getPropertyString("zoneFieldId"),     "agroEcologicalZone"));
            String la = htmlAttr(defaultIfEmpty(getPropertyString("latFieldId"),     "auto_center_lat"));
            String lo = htmlAttr(defaultIfEmpty(getPropertyString("lonFieldId"),     "auto_center_lon"));
            return "<div class=\"form-cell\" " + meta + ">"
                 + "<div style=\"background:#eef4f9;border-left:4px solid #2c7a7b;"
                 + "padding:8px 12px;border-radius:4px;font-size:12px;color:#2c7a7b;"
                 + "pointer-events:none;\">"
                 + "<b>Auto Centre Bootstrap</b> · "
                 + "reads <code>" + d + "</code>+<code>" + z + "</code> "
                 + "→ writes <code>" + la + "</code>+<code>" + lo + "</code>"
                 + "</div></div>";
        }

        // Runtime: emit invisible div + <script>.
        String districtField = jsAttr(defaultIfEmpty(getPropertyString("districtFieldId"), "district"));
        String zoneField     = jsAttr(defaultIfEmpty(getPropertyString("zoneFieldId"),     "agroEcologicalZone"));
        String latField      = jsAttr(defaultIfEmpty(getPropertyString("latFieldId"),      "auto_center_lat"));
        String lonField      = jsAttr(defaultIfEmpty(getPropertyString("lonFieldId"),      "auto_center_lon"));

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<div class=\"form-cell auto-centre-bootstrap\" style=\"display:none\"></div>");
        sb.append("<script>(function(){\n");
        sb.append("  var DF='").append(districtField).append("';\n");
        sb.append("  var ZF='").append(zoneField).append("';\n");
        sb.append("  var LATF='").append(latField).append("';\n");
        sb.append("  var LONF='").append(lonField).append("';\n");
        sb.append("  var T=").append(buildLookupJson()).append(";\n");
        sb.append(JS_BODY);
        sb.append("})();</script>");
        return sb.toString();
    }

    /**
     * Query md_district_eco_zone and emit a JS object literal of the form
     * {@code {"<district>:<zone>":{"lat":"...","lon":"..."},...}}.
     * Never throws — on DAO failure we emit an empty object so the bootstrap
     * silently no-ops rather than breaking the form render.
     */
    private String buildLookupJson() {
        StringBuilder json = new StringBuilder(2048);
        json.append("{");
        try {
            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRowSet rows = dao.find(ZONE_LOOKUP_FORM, ZONE_LOOKUP_FORM,
                    null, null, null, false, null, null);
            int n = 0;
            if (rows != null) {
                for (FormRow row : rows) {
                    String district = row.getProperty("district");
                    String zone     = row.getProperty("agro_zone");
                    String lat      = row.getProperty("centroid_lat");
                    String lon      = row.getProperty("centroid_lon");
                    if (isBlank(district) || isBlank(zone)
                            || isBlank(lat) || isBlank(lon)) continue;
                    if (n++ > 0) json.append(",");
                    json.append("\"").append(jsAttr(district)).append(":")
                        .append(jsAttr(zone)).append("\":{")
                        .append("\"lat\":\"").append(jsAttr(lat)).append("\",")
                        .append("\"lon\":\"").append(jsAttr(lon)).append("\"}");
                }
            }
            LogUtil.info(CLASS_NAME, "AutoCenterBootstrapElement: emitted "
                    + n + " centroids from md_district_eco_zone");
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "AutoCenterBootstrapElement: lookup table build failed — emitting empty");
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Inline JS body. Reads district/zone from the DOM, looks them up in the
     * embedded T table, writes auto_center_lat/lon to the matching DOM inputs,
     * and re-runs on field changes so cascading dropdown updates re-centre.
     *
     * <p>Lookup tries multiple selectors because Joget renders form fields in
     * different ways depending on widget type (SelectBox uses {@code [name=]},
     * sometimes {@code [id=]}, occasionally inside an {@code .form-cell}
     * wrapper). We try all of them and take the first non-empty value.
     *
     * <p>Idempotent: if auto_center is already set we skip — preserves any
     * server-side value (e.g. from our store binder on the existing
     * parcelGeometry row in edit mode).
     */
    private static final String JS_BODY =
        "  function readVal(id){\n" +
        "    var sels=['[name=\"'+id+'\"]','#'+id,\n" +
        "             '[name$=\"_'+id+'\"]','[id$=\"_'+id+'\"]'];\n" +
        "    for(var i=0;i<sels.length;i++){\n" +
        "      var els=document.querySelectorAll(sels[i]);\n" +
        "      for(var j=0;j<els.length;j++){\n" +
        "        var v=els[j].value;\n" +
        "        if(v!=null&&v!==''){return v;}\n" +
        "      }\n" +
        "    }\n" +
        "    return null;\n" +
        "  }\n" +
        "  function writeVal(id,val){\n" +
        "    var sels=['[name=\"'+id+'\"]','#'+id,\n" +
        "             '[name$=\"_'+id+'\"]','[id$=\"_'+id+'\"]'];\n" +
        "    var hit=false;\n" +
        "    for(var i=0;i<sels.length;i++){\n" +
        "      var els=document.querySelectorAll(sels[i]);\n" +
        "      for(var j=0;j<els.length;j++){\n" +
        "        els[j].value=val;\n" +
        "        try{els[j].dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}\n" +
        "        hit=true;\n" +
        "      }\n" +
        "    }\n" +
        "    return hit;\n" +
        "  }\n" +
        "  function apply(reason){\n" +
        "    var d=readVal(DF), z=readVal(ZF);\n" +
        "    if(!d||!z){console.log('[AutoCentre]',reason,'no district/zone yet');return;}\n" +
        "    var existingLat=readVal(LATF);\n" +
        "    if(existingLat&&existingLat!==''){console.log('[AutoCentre]',reason,'auto_center already set, skipping');return;}\n" +
        "    var c=T[d+':'+z];\n" +
        "    if(!c){console.log('[AutoCentre]',reason,'no centroid in MD.95 for ('+d+','+z+')');return;}\n" +
        "    var ok1=writeVal(LATF,c.lat), ok2=writeVal(LONF,c.lon);\n" +
        "    console.log('[AutoCentre]',reason,'wrote',c.lat,c.lon,'for('+d+','+z+') lat-hit='+ok1+' lon-hit='+ok2);\n" +
        "  }\n" +
        "  // Run on DOM ready and re-run if district/zone change.\n" +
        "  function init(){\n" +
        "    apply('init');\n" +
        "    var dEls=document.querySelectorAll('[name=\"'+DF+'\"],#'+DF+',[name$=\"_'+DF+'\"]');\n" +
        "    var zEls=document.querySelectorAll('[name=\"'+ZF+'\"],#'+ZF+',[name$=\"_'+ZF+'\"]');\n" +
        "    function bind(els){\n" +
        "      for(var i=0;i<els.length;i++){\n" +
        "        els[i].addEventListener('change',function(){apply('change');});\n" +
        "      }\n" +
        "    }\n" +
        "    bind(dEls); bind(zEls);\n" +
        "  }\n" +
        "  if(document.readyState==='loading'){\n" +
        "    document.addEventListener('DOMContentLoaded',init);\n" +
        "  } else {\n" +
        "    init();\n" +
        "  }\n";

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private static String defaultIfEmpty(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Escape for safe inclusion in an HTML attribute. */
    private static String htmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escape for safe inclusion in a JS double-quoted string literal. */
    private static String jsAttr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("'", "\\'");
    }
}
