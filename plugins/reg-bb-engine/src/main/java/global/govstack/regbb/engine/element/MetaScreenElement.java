package global.govstack.regbb.engine.element;

import global.govstack.regbb.engine.Build;
import global.govstack.regbb.engine.api.EvalContext;
import global.govstack.regbb.engine.api.EvalResult;
import global.govstack.regbb.engine.dao.MetaModelDao;
import global.govstack.regbb.engine.evaluator.RoutingEvaluator;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Citizen application screen rendered from {@code mm_screen} + {@code mm_field}
 * rows. Per spec §3.1 / §6.3 / §7.2 with Lesotho extensions per D6/D9/D17.
 *
 * <p><b>Architecture (rev. 2 — D18):</b> the previous build-006 walker emitted
 * raw HTML for each {@code mm_field} row. This rev. retires that approach and
 * makes {@code MetaScreenElement} a {@link FormContainer} that <i>synthesises
 * real Joget Element instances</i> (TextField, SelectBox, DatePicker, etc.) at
 * {@link #getChildren(FormData)} time. After synthesis, Joget's introspection
 * pipeline ({@code FormUtil.findElement}, {@code buildElementMap},
 * {@code FormRowDataListBinder.getColumns}, the App-Composer column picker,
 * validators, store binders) sees a normal Element tree and "just works" for
 * the dynamic fields — no datalist patching, no separate column-discovery
 * code path. The metadata stays canonical in {@code mm_*}; the runtime view is
 * a fully-formed Joget form.
 *
 * <p><b>Widget coverage (Phase 1 Week 2 rebuild):</b> seven simple widgets —
 * {@code text}, {@code number}, {@code date}, {@code select}, {@code radio},
 * {@code checkbox}, {@code textarea}. Complex widgets ({@code gis_polygon},
 * {@code signature}, {@code file_upload}, {@code qr_scan},
 * {@code repeating_group}) are deferred to Week 3+ — when encountered today,
 * they synthesise as a read-only {@code TextField} placeholder so the form
 * still renders.
 *
 * <p><b>Caching:</b> children are synthesised lazily and cached per
 * {@link FormData} lifecycle in a {@link WeakHashMap} so the same render pass
 * doesn't re-query the database for every {@link #getChildren} call. Joget
 * calls this method many times during a single page render (once per
 * findElement, once per buildElementMap, once per render-tree walk), so the
 * cache is meaningful.
 */
public class MetaScreenElement extends Element implements FormBuilderPaletteElement, FormContainer {

    private static final String CLASS_NAME = MetaScreenElement.class.getName();
    private static final String FORM_BUILDER_CATEGORY = "GovStack RegBB";

    /** Synthesised children, cached per FormData lifecycle. WeakHashMap so dead
     *  FormData instances don't pin the children collection in memory. */
    private final Map<FormData, Collection<Element>> childrenCache =
            Collections.synchronizedMap(new WeakHashMap<FormData, Collection<Element>>());

    @Override public String getName()                  { return "Meta Screen Element"; }
    @Override public String getVersion()               { return Build.STAMP; }
    @Override public String getDescription()           { return "Renders citizen application screens dynamically from mm_screen + mm_field rows. " + Build.STAMP; }
    @Override public String getLabel()                 { return "Meta Screen"; }
    @Override public String getClassName()             { return getClass().getName(); }
    @Override public String getFormBuilderCategory()   { return FORM_BUILDER_CATEGORY; }
    @Override public int    getFormBuilderPosition()   { return 100; }
    @Override public String getFormBuilderIcon()       { return "<i class=\"fas fa-list-alt\"></i>"; }

    @Override
    public String getFormBuilderTemplate() {
        return "<div style=\"background:#f0f4f8;border-left:4px solid #2c7a7b;"
             + "padding:8px 12px;border-radius:4px;font-size:12px;color:#1a4040;"
             + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
             + "<b>Meta Screen</b> &nbsp;·&nbsp; expands mm_field into Joget Elements at render</div>";
    }

    @Override
    public String getPropertyOptions() {
        // Per D20, cross-entity references in this app use the target's
        // business `code` (not Joget's UUID id). The screenId widget property
        // therefore stores mm_screen.code; the engine resolves it via
        // MetaModelDao.findScreenByCode at render time. The dropdown lists
        // every mm_screen by title (display) + code (the value stored).
        String screenIdField;
        try {
            JSONArray opts = new JSONArray();
            JSONObject empty = new JSONObject();
            empty.put("value", "");
            empty.put("label", "(select a screen)");
            opts.put(empty);

            org.joget.apps.form.dao.FormDataDao fdd =
                (org.joget.apps.form.dao.FormDataDao)
                    org.joget.apps.app.service.AppUtil.getApplicationContext().getBean("formDataDao");
            org.joget.apps.form.model.FormRowSet rows = fdd.find(
                "mm_screen", "mm_screen", null, null, "title", false, null, null);
            if (rows != null) {
                for (FormRow r : rows) {
                    String code  = nz(r.getProperty("code"), "");
                    String title = nz(r.getProperty("title"), code);
                    if (code.isEmpty()) continue;  // skip rows that pre-date D20 (no code)
                    JSONObject o = new JSONObject();
                    o.put("value", code);
                    o.put("label", title + " (" + code + ")");
                    opts.put(o);
                }
            }
            screenIdField = "{\"name\":\"screenId\",\"label\":\"Meta Screen (mm_screen)\","
                          + "\"description\":\"Pick the mm_screen whose mm_field rows render here. The widget stores its code (per D20).\","
                          + "\"type\":\"selectbox\",\"required\":\"true\","
                          + "\"options\":" + opts.toString() + "}";
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "Could not build mm_screen options for property panel; "
                    + "falling back to textfield: " + t.getMessage());
            screenIdField = "{\"name\":\"screenId\",\"label\":\"mm_screen.code (the screen to render)\","
                          + "\"description\":\"The mm_screen business code (DB lookup failed; entering manually).\","
                          + "\"type\":\"textfield\",\"required\":\"true\"}";
        }

        return "[{"
            + "\"title\":\"Meta Screen\","
            + "\"properties\":["
            + screenIdField + ","
            + "  {\"name\":\"readonly\",\"label\":\"Read-only mode (operator inspection per D17)\","
            + "   \"type\":\"selectbox\",\"value\":\"N\","
            + "   \"options\":[{\"value\":\"Y\",\"label\":\"Yes\"},{\"value\":\"N\",\"label\":\"No\"}]}"
            + "]"
            + "}]";
    }

    // ---------------------------------------------------------------------
    //  FormContainer hook — the heart of the rebuild.
    // ---------------------------------------------------------------------

    /**
     * Returns the synthesised children for this screen. Joget calls this
     * (directly or via {@link FormUtil#findElement}) every time it needs to
     * walk the form tree — for rendering, validation, column discovery,
     * store/load binding, the App-Composer datalist column picker, etc.
     *
     * <p>Children are synthesised once per {@link FormData} lifecycle and
     * cached, so repeat calls within a single page render do not re-query
     * the {@code mm_field} table.
     */
    @Override
    public Collection<Element> getChildren(FormData formData) {
        if (formData == null) {
            return synthesiseChildren();
        }
        // Documents-kind screens compute their children based on the
        // applied_programme field on the form — but Joget calls getChildren
        // many times across the lifecycle, including BEFORE the load binder
        // fills in the row's data. Caching the early (empty-programme) result
        // and reusing it at render time was masking programme-specific docs.
        // For documents-kind, always re-synthesise. Other kinds are
        // metadata-stable and can be cached safely.
        if (isDocumentsKind()) {
            return synthesiseChildren(formData);
        }
        Collection<Element> cached = childrenCache.get(formData);
        if (cached != null) return cached;
        Collection<Element> fresh = synthesiseChildren(formData);
        childrenCache.put(formData, fresh);
        return fresh;
    }

    /** Quick lookup of this screen's kind. Used by getChildren to decide
     *  whether to cache. Cached itself per element instance — kind doesn't
     *  change once an element is configured. */
    private Boolean cachedIsDocuments;
    private boolean isDocumentsKind() {
        if (cachedIsDocuments != null) return cachedIsDocuments;
        String code = getPropertyString("screenId");
        if (code == null || code.isEmpty()) {
            cachedIsDocuments = Boolean.FALSE;
            return false;
        }
        try {
            FormRow s = new MetaModelDao().findScreenByCode(code);
            cachedIsDocuments = (s != null && "documents".equalsIgnoreCase(s.getProperty("kind")));
        } catch (Throwable e) {
            cachedIsDocuments = Boolean.FALSE;
        }
        return cachedIsDocuments;
    }

    /** No-FormData callers (form-builder palette, design-time introspection)
     *  get an uncached synthesis. This path is rare and short-lived. */
    @Override
    public Collection<Element> getChildren() {
        return synthesiseChildren();
    }

    /**
     * Joget calls this from {@code FormDataDaoImpl.findAllElementIds} when it
     * builds the per-table column list (the input to the Hibernate mapping).
     * Without this override, only the children returned by no-arg
     * {@link #getChildren()} are seen as columns — and for documents-kind
     * screens that's just the service-level docs (no {@code applied_programme}
     * is available at design-time). Programme-specific docs would never get
     * a column, and any value the storeBinder collects for them is silently
     * dropped at the Hibernate UPDATE.
     *
     * <p>Returning the full superset of dynamic field IDs here lets Joget
     * register every possible column on the form's Hibernate mapping, so the
     * runtime FileUpload widgets — synthesised lazily per applicant by
     * {@link #synthesiseDocumentChildren} — write to columns that actually
     * exist.
     *
     * <p>Per-kind logic:
     * <ul>
     *   <li><b>documents</b>: all {@code mm_required_doc} for this screen's
     *       service, regardless of {@code registrationId} — every
     *       programme's docs registered as columns.</li>
     *   <li><b>form</b>: all {@code mm_field.storageKey} for this screen.</li>
     *   <li><b>guide / review</b>: no dynamic fields (presentation only).</li>
     * </ul>
     *
     * <p>IMPORTANT: schema reconciliation only runs at form-save time
     * (Joget's {@code actionType=0}). After changing this method's output
     * (e.g. adding a new required-doc row), re-save the host form via App
     * Composer's Save button so the column cache invalidates and the new
     * columns get added.
     */
    @Override
    public java.util.Collection<String> getDynamicFieldNames() {
        String screenCode = getPropertyString("screenId");
        if (screenCode == null || screenCode.isEmpty()) return null;
        try {
            MetaModelDao dao = new MetaModelDao();
            FormRow screen = dao.findScreenByCode(screenCode);
            if (screen == null) return null;
            String kind = nz(screen.getProperty("kind"), "form");

            if ("guide".equalsIgnoreCase(kind) || "review".equalsIgnoreCase(kind)) {
                return null;
            }

            List<String> names = new ArrayList<>();

            if ("documents".equalsIgnoreCase(kind)) {
                String serviceCode = nz(prop(screen, "serviceId"), "");
                if (!serviceCode.isEmpty()) {
                    for (FormRow doc : dao.listRequiredDocsForService(serviceCode)) {
                        String code = nz(prop(doc, "code"), "");
                        if (!code.isEmpty()) {
                            names.add("doc_" + code.toLowerCase());
                        }
                    }
                }
                return names;
            }

            // form-kind — every mm_field on this screen.
            String screenUuid = screen.getId();
            boolean hasGisPolygon = false;
            for (FormRow f : dao.listFieldsForScreen(screenUuid)) {
                String storageKey = nz(prop(f, "storageKey"), "");
                String fwidget = nz(prop(f, "widget"), "");
                // L1-6 — repeating_group's row data lives in the CHILD form's
                // table, not the parent. No column to declare on the parent.
                // Including the storageKey here would create a useless
                // c_<storageKey> column on the parent table.
                if ("repeating_group".equalsIgnoreCase(fwidget)) continue;
                // L3-1 3a — budget_hint is a CustomHTML presentation widget;
                // it computes its content at render time and persists nothing.
                // Excluded so no useless column is created on the host table.
                if ("budget_hint".equalsIgnoreCase(fwidget)) continue;
                // Eligibility summary — same shape as budget_hint: render-only
                // CustomHTML, no persisted column.
                if ("eligibility_summary".equalsIgnoreCase(fwidget)) continue;
                if (!storageKey.isEmpty()) names.add(storageKey);
                if ("gis_polygon".equalsIgnoreCase(fwidget)) {
                    hasGisPolygon = true;
                }
            }
            // L1-5 — every gis_polygon on the screen contributes six
            // companion HiddenFields. Their ids must be declared here so
            // Joget's Hibernate mapping creates the underlying columns when
            // the host form is saved (App Composer Save → actionType=0
            // schema reconciliation).
            if (hasGisPolygon) {
                for (String fid : GIS_COMPANION_FIELD_IDS) {
                    if (!names.contains(fid)) names.add(fid);
                }
            }
            return names;
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "getDynamicFieldNames failed for screenCode="
                    + screenCode + ": " + t.getMessage());
            return null;
        }
    }

    private Collection<Element> synthesiseChildren() {
        return synthesiseChildren(null);
    }

    /**
     * Build the Joget Element list this screen contributes to its parent form
     * tree. Critical for persistence: Joget's storeBinder walks getChildren()
     * to know which columns to save. Anything synthesised at render time only
     * (and not exposed here) is invisible to the storeBinder and won't persist
     * — we hit exactly that bug with documents-kind screens before this rev.
     *
     * <p>Per-kind dispatch:
     * <ul>
     *   <li><b>form (default)</b>: walk {@code mm_field} rows for this screen;
     *       one Joget element per row.</li>
     *   <li><b>documents</b>: walk {@code mm_required_doc} for this screen's
     *       service, filter by the applicant's selected programme, synthesise
     *       a {@code FileUpload} element per row so uploads persist.</li>
     *   <li><b>guide / review</b>: no children — pure presentation screens.</li>
     * </ul>
     */
    private Collection<Element> synthesiseChildren(FormData formData) {
        List<Element> out = new ArrayList<>();
        String screenCode = getPropertyString("screenId");
        if (screenCode == null || screenCode.isEmpty()) return out;

        try {
            MetaModelDao dao = new MetaModelDao();
            FormRow screen = dao.findScreenByCode(screenCode);
            if (screen == null) {
                LogUtil.warn(CLASS_NAME,
                        "synthesiseChildren: mm_screen not found for code=" + screenCode);
                return out;
            }
            String kind = nz(screen.getProperty("kind"), "form");

            if ("guide".equalsIgnoreCase(kind)) {
                return out;  // pure presentation, no data capture
            }

            if ("documents".equalsIgnoreCase(kind)) {
                synthesiseDocumentChildren(out, screen, dao, formData);
                return out;
            }

            // form-kind (default) and review-kind — walk mm_field rows.
            // Review-kind also renders a summary block at the top via
            // renderTemplate's renderReview branch; the mm_field rows
            // here are appended after that summary (e.g. the
            // submit_confirmation checkbox on APP_REVIEW). Without this,
            // a review screen would silently drop any mm_field rows
            // attached to it — see W3.4 fix May 2026.
            String screenUuid = screen.getId();
            List<FormRow> fields = dao.listFieldsForScreen(screenUuid);

            // §6.4 Conditional UI — build the applicant context once per render
            // pass; reuse for every field's visibility / requiredness check.
            // Per RegBB §6.4: visibilityDeterminantId hides the field when its
            // determinant evaluates FALSE; requirednessDeterminantId promotes
            // the field to mandatory when its determinant evaluates TRUE. Both
            // fail-open (NULL/ERROR → use default) so missing data on first
            // render of a wizard tab doesn't accidentally hide or unrequire a
            // field. The L2 cache makes the per-field eval ~3-4ms after the
            // first warm-up.
            Map<String, Object> applicantCtxData = readApplicantContext(formData, fields);
            RoutingEvaluator evaluator = null;
            for (FormRow f : fields) {
                String visDet = nz(prop(f, "visibilityDeterminantId"), "");
                String reqDet = nz(prop(f, "requirednessDeterminantId"), "");

                boolean visible       = true;       // default-visible per §6.4
                Boolean requiredOverride = null;    // null = no override

                if (!visDet.isEmpty() || !reqDet.isEmpty()) {
                    if (evaluator == null) evaluator = new RoutingEvaluator();
                    EvalContext ctx = EvalContext.builder()
                            .data(applicantCtxData)
                            .build();
                    if (!visDet.isEmpty()) {
                        EvalResult.Outcome o = evaluateOutcome(evaluator, visDet, ctx);
                        if (o == EvalResult.Outcome.FALSE) visible = false;
                        // TRUE / NULL / ERROR all leave it visible (fail-open)
                    }
                    if (!reqDet.isEmpty()) {
                        EvalResult.Outcome o = evaluateOutcome(evaluator, reqDet, ctx);
                        if (o == EvalResult.Outcome.TRUE)  requiredOverride = Boolean.TRUE;
                        if (o == EvalResult.Outcome.FALSE) requiredOverride = Boolean.FALSE;
                        // NULL / ERROR → leave default
                    }
                }

                Element child = synthesiseField(f, dao, visible, requiredOverride, formData);
                if (child != null) {
                    child.setParent(this);
                    out.add(child);
                    // L1-5 — gis_polygon needs six companion HiddenField
                    // siblings (area, perimeter, vertex_count, centroid_lat,
                    // auto_center_lat, auto_center_lon). The element references
                    // them by string id; the HiddenFields persist the values
                    // the JS computes client-side.
                    if ("gis_polygon".equalsIgnoreCase(nz(prop(f, "widget"), ""))) {
                        attachGisCompanionHiddenFields(out);
                    }
                }
            }
            // L2-3 — wire any field with a bot_pull mm_action reference.
            attachBotPullScript(out, fields, dao);
        } catch (RuntimeException e) {
            LogUtil.error(CLASS_NAME, e,
                    "synthesiseChildren failed for screenCode=" + screenCode);
        }
        return out;
    }

    /**
     * L2-3 — append a synthesised CustomHTML element carrying the bot_pull
     * dispatch JS for any field on this screen with an
     * {@code actionIds} reference to a {@code kind=bot_pull} mm_action.
     *
     * <p>For each (field, bot_pull action) pair, the JS attaches a
     * {@code change}/{@code blur} handler to the field. On blur it POSTs
     * {@code {actionCode, trigger:{<fieldId>:<value>}}} to
     * {@code /jw/api/regbb/bot_pull/eval}, then walks the response
     * {@code resolved} object and writes each {@code target → value} into
     * the matching DOM input. Fields not present on the current tab are
     * silently skipped (so the action can populate fields on later wizard
     * tabs that haven't been rendered yet).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void attachBotPullScript(List<Element> out, List<FormRow> fields, MetaModelDao dao) {
        // Collect (triggerFieldId, actionCode) pairs. Each mm_field's
        // actionIds is semicolon-separated; we filter by mm_action.kind=bot_pull.
        java.util.List<String[]> pairs = new java.util.ArrayList<>();
        for (FormRow f : fields) {
            String fieldId = nz(prop(f, "storageKey"), "");
            String actionIdsRaw = nz(prop(f, "actionIds"), "");
            if (fieldId.isEmpty() || actionIdsRaw.isEmpty()) continue;
            for (String actionCode : actionIdsRaw.split(";")) {
                String code = actionCode.trim();
                if (code.isEmpty()) continue;
                try {
                    org.joget.apps.form.dao.FormDataDao fdd =
                        (org.joget.apps.form.dao.FormDataDao) org.joget.apps.app.service.AppUtil
                            .getApplicationContext().getBean("formDataDao");
                    FormRowSet rs = fdd.find("mm_action", "mm_action",
                        "WHERE e.customProperties.code = ?",
                        new Object[]{ code }, null, false, null, null);
                    if (rs == null || rs.isEmpty()) continue;
                    String kind = nz(rs.get(0).getProperty("kind"), "");
                    if ("bot_pull".equalsIgnoreCase(kind)) {
                        pairs.add(new String[]{ fieldId, code });
                    }
                } catch (Throwable t) {
                    LogUtil.warn(CLASS_NAME,
                        "attachBotPullScript: lookup for action '" + code + "' failed: " + t.getMessage());
                }
            }
        }
        if (pairs.isEmpty()) return;

        // Build a JSON array of {trigger, action} pairs the JS will iterate.
        StringBuilder pairsJson = new StringBuilder("[");
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) pairsJson.append(",");
            pairsJson.append("{\"trigger\":\"").append(pairs.get(i)[0])
                     .append("\",\"action\":\"").append(pairs.get(i)[1]).append("\"}");
        }
        pairsJson.append("]");

        String apiId  = "API-168e3678-1f9a-46fc-8c19-d0d9a917eb73";
        String apiKey = "a5af1181f77b4a62b481725b6410e965";

        StringBuilder js = new StringBuilder();
        js.append("<script>(function(){")
          .append("var PAIRS=").append(pairsJson).append(";")
          .append("var API_ID='").append(apiId).append("';var API_KEY='").append(apiKey).append("';")
          // Set a value into whatever input(s) carry name=fieldName.
          // Handles radio/checkbox groups (multiple nodes with same name)
          // and single inputs (text/date/select). Returns true if it
          // actually changed something, false if no node matched.
          .append("function applyValue(name,value){")
          .append("  var nodes=document.querySelectorAll('[name=\"'+name+'\"]');")
          .append("  if(!nodes||nodes.length===0)return false;")
          .append("  if(nodes.length>1||nodes[0].type==='radio'||nodes[0].type==='checkbox'){")
          // Group case: select the radio/checkbox whose value matches.
          .append("    var matched=false;")
          .append("    nodes.forEach(function(n){")
          .append("      if(n.type!=='radio'&&n.type!=='checkbox')return;")
          .append("      var hit=String(n.value)===String(value);")
          .append("      if(hit){n.checked=true;matched=true;}")
          .append("    });")
          .append("    if(matched){")
          .append("      nodes.forEach(function(n){n.dispatchEvent(new Event('change',{bubbles:true}));});")
          .append("    }")
          .append("    return matched;")
          .append("  }")
          // Single input case (text/date/select).
          .append("  nodes[0].value=value;")
          .append("  nodes[0].dispatchEvent(new Event('change',{bubbles:true}));")
          .append("  return true;")
          .append("}")
          // Has the user already entered something for this field?
          // Empty for text/date/select, no-radio-checked for radio groups.
          .append("function hasUserValue(name){")
          .append("  var nodes=document.querySelectorAll('[name=\"'+name+'\"]');")
          .append("  if(!nodes||nodes.length===0)return false;")
          .append("  if(nodes.length>1||nodes[0].type==='radio'||nodes[0].type==='checkbox'){")
          .append("    for(var i=0;i<nodes.length;i++){if(nodes[i].checked)return true;}")
          .append("    return false;")
          .append("  }")
          .append("  return !!nodes[0].value;")
          .append("}")
          .append("function fire(triggerField,actionCode){")
          .append("  var el=document.querySelector('[name=\"'+triggerField+'\"]');")
          .append("  if(!el||!el.value)return;")
          .append("  var trig={};trig[triggerField]=el.value;")
          .append("  fetch('/jw/api/regbb/bot_pull/eval',{")
          .append("    method:'POST',")
          .append("    headers:{'Content-Type':'application/json','api_id':API_ID,'api_key':API_KEY},")
          .append("    body:JSON.stringify({actionCode:actionCode,trigger:trig})")
          .append("  }).then(function(r){return r.json();}).then(function(env){")
          .append("    var msg=env.message?(typeof env.message=='string'?JSON.parse(env.message):env.message):env;")
          .append("    var resolved=msg.resolved||{};")
          .append("    Object.keys(resolved).forEach(function(target){")
          .append("      var v=resolved[target];if(v==null||v==='')return;")
          .append("      if(hasUserValue(target))return;") // never overwrite existing user input
          .append("      applyValue(target,v);")
          .append("    });")
          .append("  }).catch(function(e){console.error('[L2-3] bot_pull failed',e);});")
          .append("}")
          .append("function wire(){")
          .append("  PAIRS.forEach(function(p){")
          .append("    var el=document.querySelector('[name=\"'+p.trigger+'\"]');")
          .append("    if(!el||el.dataset.botpullWired)return;")
          .append("    el.dataset.botpullWired='1';")
          .append("    el.addEventListener('blur',function(){fire(p.trigger,p.action);});")
          .append("  });")
          .append("}")
          .append("if(document.readyState=='loading'){document.addEventListener('DOMContentLoaded',wire);}else{setTimeout(wire,50);}")
          .append("})();</script>");

        // CustomHTML synthesised element renders raw HTML (its 'value' prop).
        Element ch = newElement("org.joget.apps.form.lib.CustomHTML");
        if (ch == null) return;
        Map<String, Object> p = new HashMap<>();
        p.put(FormUtil.PROPERTY_ID,    "_botpull_script");
        p.put(FormUtil.PROPERTY_LABEL, "");   // CustomHTML's freemarker
                                              // template reads .label and
                                              // throws NPE if it's null.
        p.put("value", js.toString());
        ch.setProperties(p);
        ch.setParent(this);
        out.add(ch);
    }

    /**
     * L1-5 helper — synthesise the six companion HiddenFields a gis_polygon
     * widget needs as siblings:
     *
     * <ul>
     *   <li>{@code area_hectares} — auto-computed by Leaflet/turf.js</li>
     *   <li>{@code perimeter_meters} — auto-computed</li>
     *   <li>{@code vertex_count} — auto-computed</li>
     *   <li>{@code centroid_lat} — auto-computed centroid (single string field;
     *       element's {@code centroidFieldId} expects one id)</li>
     *   <li>{@code auto_center_lat} — district/village→latitude lookup</li>
     *   <li>{@code auto_center_lon} — district/village→longitude lookup</li>
     * </ul>
     *
     * Pattern matches the legacy
     * {@code _05_dev_v2/_a_farmers-reg/_farmer-forms/f02.02.json} parcel form
     * field-for-field. Each is a HiddenField with the matching id; Joget's
     * Hibernate mapping uses the id as the column key (so columns become
     * {@code c_area_hectares}, {@code c_perimeter_meters}, etc.).
     */
    private static final String[] GIS_COMPANION_FIELD_IDS = {
        "area_hectares",
        "perimeter_meters",
        "vertex_count",
        "centroid_lat",
        "auto_center_lat",
        "auto_center_lon"
    };

    /**
     * L1-5b — per-widget allowlists for the {@code mm_field.widgetConfig}
     * JSON override mechanism (see {@link #mergeWidgetConfig}). Keys present
     * here can be overridden by the analyst-authored JSON; everything else
     * is silently dropped with a log line.
     *
     * <p>The locked keys (NOT in any allowlist) are field-binding props the
     * kernel manages — overriding them would break the companion-HiddenField
     * wiring contract:
     * <ul>
     *   <li>gis_polygon: {@code areaFieldId, perimeterFieldId, centroidFieldId,
     *       vertexCountFieldId, autoCenterLatFieldId, autoCenterLonFieldId,
     *       autoCenterDistrictFieldId, autoCenterVillageFieldId} — the
     *       kernel synthesises companion HiddenFields with matching ids and
     *       relies on these references being stable.</li>
     *   <li>All widgets: {@code id, label, validator} — the kernel sets these
     *       from {@code mm_field} columns ({@code storageKey},
     *       {@code label}, {@code defaultBehaviorOnError}); overriding them
     *       in widgetConfig would silently divorce the rendered field from
     *       its mm_field row.</li>
     * </ul>
     *
     * <p>Per-widget property catalogues are documented in the kernel SAD
     * (mm-form-gen-kernel.md §8.6). When adding a new widget, add a new
     * allowlist constant here and reference it from the synthesise case.
     */
    private static final java.util.Set<String> SIGNATURE_OVERRIDABLE_KEYS =
        new java.util.HashSet<>(java.util.Arrays.asList(
            "width", "height", "encryption", "readonly"
        ));

    private static final java.util.Set<String> FILE_UPLOAD_OVERRIDABLE_KEYS =
        new java.util.HashSet<>(java.util.Arrays.asList(
            "fileType", "maxSize", "multiple",
            "removeFile", "permissionType", "attachment"
        ));

    private static final java.util.Set<String> REPEATING_GROUP_OVERRIDABLE_KEYS =
        new java.util.HashSet<>(java.util.Arrays.asList(
            "foreignKey",
            // FormGrid reads its column definitions from the property
            // literally named "options" — verified against the legacy
            // farmerHousehold.householdMembers grid in app_form.json. Naming
            // the widgetConfig key "columns" was a mistake on first attempt
            // (May 2026) — symptom: grid renders the right row count but
            // every row appears empty, no headers, just row numbers.
            "options",            // JSONArray of column-config objects
            "pageSize",
            "height",
            "showRowNumber",
            "validateMinRow",
            "validateMaxRow",
            "errorMessage",
            "readonly",
            "enableSorting",
            "disabledDelete",
            "deleteGridData",
            "deleteSubformData",
            "deleteFiles",
            "deleteMessage",
            "uniqueKey"
        ));

    private static final java.util.Set<String> GIS_POLYGON_OVERRIDABLE_KEYS =
        new java.util.HashSet<>(java.util.Arrays.asList(
            // Map / capture
            "tileProvider", "defaultLatitude", "defaultLongitude", "defaultZoom",
            "mapHeight", "showSatelliteOption", "captureMode", "defaultMode",
            // Validation
            "minAreaHectares", "maxAreaHectares", "minVertices", "maxVertices",
            "allowSelfIntersection", "required", "requiredMessage",
            // GPS
            "gpsHighAccuracy", "gpsMinAccuracy", "autoCloseDistance",
            // Style
            "fillColor", "fillOpacity", "strokeColor", "strokeWidth",
            // Auto-centre tuning (district/village/lat/lon FIELD IDS are LOCKED)
            "enableAutoCenter", "autoCenterCountrySuffix", "autoCenterZoom",
            "autoCenterRetryOnFieldChange",
            // Overlap
            "enableOverlapCheck", "overlapFormId", "overlapGeometryField",
            "overlapDisplayFields", "overlapFilterCondition",
            // Nearby parcels
            "showNearbyParcels", "nearbyParcelsFormId",
            "nearbyParcelsGeometryField", "nearbyParcelsDisplayFields",
            "nearbyParcelsFilterCondition", "nearbyParcelsMaxResults",
            "nearbyParcelsFillColor", "nearbyParcelsFillOpacity",
            "nearbyParcelsStrokeColor",
            // Simplification
            "enableSimplification", "simplificationTolerance",
            // API auth
            "apiEndpoint", "apiId", "apiKey"
        ));

    /**
     * L1-5b — overlay any {@code mm_field.widgetConfig} JSON values onto the
     * synthesise props map, filtered through the per-widget allowlist.
     *
     * <p>Keys NOT in {@code allowedKeys} are dropped with a log warning so
     * the analyst sees clearly that their override didn't take effect.
     * Malformed JSON is also logged and the synthesise continues with
     * defaults (fail-open — bad config doesn't break the form render).
     *
     * <p>Implements the "configuration over code" principle: kernel ships
     * sensible defaults, analyst overrides via metadata, no kernel rebuild
     * needed when a screen needs different settings.
     *
     * @param props        the synthesise property map (mutated in place)
     * @param f            the mm_field row carrying widgetConfig
     * @param widget       the widget kind for logging
     * @param allowedKeys  per-widget allowlist of overridable property names
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void mergeWidgetConfig(Map<String, Object> props, FormRow f,
                                   String widget,
                                   java.util.Set<String> allowedKeys) {
        String raw = nz(prop(f, "widgetConfig"), "").trim();
        if (raw.isEmpty()) return;
        try {
            org.json.JSONObject json = new org.json.JSONObject(raw);
            int applied = 0;
            int dropped = 0;
            java.util.Iterator<String> it = json.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!allowedKeys.contains(k)) {
                    LogUtil.warn(CLASS_NAME,
                        "widgetConfig override dropped — key '" + k + "' is not in the allowlist for widget=" + widget
                        + " (storageKey=" + nz(prop(f, "storageKey"), "?") + "). "
                        + "Either it's a locked key (kernel-managed) or a typo. See kernel SAD §8.6.");
                    dropped++;
                    continue;
                }
                Object v = json.get(k);
                // Coerce types to what Joget element setters expect:
                //   - JSONArray  → java.util.List<Map>  (FormGrid options[],
                //     SelectBox options[], etc.)
                //   - JSONObject → java.util.Map<String,String>  (nested
                //     property bags)
                //   - everything else → String  (booleans, numbers, strings).
                // Without this branching, FormGrid's columns[] from
                // widgetConfig would arrive as a single string "[...]"
                // instead of a List, and Joget would render zero columns.
                Object coerced;
                if (v instanceof org.json.JSONArray) {
                    coerced = jsonArrayToList((org.json.JSONArray) v);
                } else if (v instanceof org.json.JSONObject) {
                    coerced = jsonObjectToMap((org.json.JSONObject) v);
                } else {
                    coerced = (v == null) ? "" : v.toString();
                }
                props.put(k, coerced);
                applied++;
            }
            if (applied > 0 || dropped > 0) {
                LogUtil.info(CLASS_NAME,
                    "widgetConfig: widget=" + widget
                    + " storageKey=" + nz(prop(f, "storageKey"), "?")
                    + " applied=" + applied + " dropped=" + dropped);
            }
        } catch (org.json.JSONException je) {
            LogUtil.warn(CLASS_NAME,
                "widgetConfig is not valid JSON for storageKey="
                + nz(prop(f, "storageKey"), "?")
                + " (widget=" + widget + ") — using kernel defaults. Cause: "
                + je.getMessage());
        }
    }

    /**
     * L1-5b helper — recursively convert a {@link org.json.JSONArray} into
     * a {@link java.util.List}, preserving nested JSONArray/JSONObject
     * structure as Lists/Maps. Joget's element setters (FormGrid options,
     * SelectBox options, etc.) expect List-of-Map shape, not the raw
     * JSONArray.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static java.util.List<Object> jsonArrayToList(org.json.JSONArray arr) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            Object v = arr.opt(i);
            if (v instanceof org.json.JSONArray) {
                out.add(jsonArrayToList((org.json.JSONArray) v));
            } else if (v instanceof org.json.JSONObject) {
                out.add(jsonObjectToMap((org.json.JSONObject) v));
            } else if (v == null || org.json.JSONObject.NULL.equals(v)) {
                out.add("");
            } else {
                out.add(v.toString());
            }
        }
        return out;
    }

    /**
     * L1-5b helper — recursively convert a {@link org.json.JSONObject} into
     * a {@link java.util.Map} with String keys + String/Map/List values.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Map<String, Object> jsonObjectToMap(org.json.JSONObject obj) {
        Map<String, Object> out = new HashMap<>();
        if (obj == null) return out;
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = obj.opt(k);
            if (v instanceof org.json.JSONArray) {
                out.put(k, jsonArrayToList((org.json.JSONArray) v));
            } else if (v instanceof org.json.JSONObject) {
                out.put(k, jsonObjectToMap((org.json.JSONObject) v));
            } else if (v == null || org.json.JSONObject.NULL.equals(v)) {
                out.put(k, "");
            } else {
                out.put(k, v.toString());
            }
        }
        return out;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void attachGisCompanionHiddenFields(List<Element> out) {
        // Don't add duplicates — guard so two gis_polygon fields on the same
        // screen don't synthesise twelve companion fields.
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (Element e : out) {
            String id = e.getPropertyString(FormUtil.PROPERTY_ID);
            if (id != null) existingIds.add(id);
        }
        for (String fieldId : GIS_COMPANION_FIELD_IDS) {
            if (existingIds.contains(fieldId)) continue;
            Element hidden = newElement("org.joget.apps.form.lib.HiddenField");
            if (hidden == null) continue;
            Map<String, Object> p = new HashMap<>();
            p.put(FormUtil.PROPERTY_ID,    fieldId);
            p.put(FormUtil.PROPERTY_LABEL, "");
            hidden.setProperties(p);
            hidden.setParent(this);
            out.add(hidden);
        }
    }

    /**
     * Synthesise FileUpload children for a documents-kind screen. Filtering
     * mirrors {@link #renderDocuments}: service-level docs always; programme-
     * specific docs only when registrationId == applied_programme. The id of
     * each FileUpload is {@code doc_<code lowercased>} so the resulting DB
     * column is {@code c_doc_<code>}.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void synthesiseDocumentChildren(List<Element> out, FormRow screen,
                                            MetaModelDao dao, FormData formData) {
        String serviceCode = nz(prop(screen, "serviceId"), "");
        List<FormRow> allDocs = dao.listRequiredDocsForService(serviceCode);
        if (allDocs.isEmpty()) return;

        String appliedProgramme = readAppliedProgramme(formData);
        // Diagnostic — counts/casing of registrationId reads for all docs.
        // Helps when the documents tab silently drops programme-specific rows.
        if (LogUtil.isDebugEnabled(CLASS_NAME)) {
            StringBuilder dbg = new StringBuilder();
            dbg.append("synthesiseDocumentChildren: service=").append(serviceCode)
               .append(" applied=").append(appliedProgramme)
               .append(" docs=").append(allDocs.size()).append(" [");
            for (FormRow d : allDocs) {
                dbg.append(prop(d, "code")).append("/regId=")
                   .append(prop(d, "registrationId")).append(",");
            }
            dbg.append("]");
            LogUtil.debug(CLASS_NAME, dbg.toString());
        }
        for (FormRow doc : allDocs) {
            String regId = nz(prop(doc, "registrationId"), "");
            if (!regId.isEmpty()) {
                if (appliedProgramme.isEmpty() || !regId.equals(appliedProgramme)) {
                    continue;
                }
            }

            String code     = nz(prop(doc, "code"), "");
            String label    = nz(prop(doc, "label"), nz(prop(doc, "name"), code));
            String required = nz(prop(doc, "requiredness"), "mandatory");
            String accepted = nz(prop(doc, "acceptedTypes"), "pdf,jpg,png");
            String maxSize  = nz(prop(doc, "maxSizeBytes"), "5242880");
            String help     = nz(prop(doc, "helpText"), "");
            if (code.isEmpty()) continue;

            Element fu = newElement("org.joget.apps.form.lib.FileUpload");
            if (fu == null) continue;

            Map<String, Object> p = new HashMap<>();
            p.put(FormUtil.PROPERTY_ID, "doc_" + code.toLowerCase());
            p.put(FormUtil.PROPERTY_LABEL, label);

            // Joget's FileUpload requires dot-prefixed semicolon-separated
            // extensions (jquery.fileupload.js does ext.substring(1) for the
            // MIME map lookup). Convert from the comma-separated form stored
            // in mm_required_doc.acceptedTypes.
            StringBuilder ft = new StringBuilder();
            for (String ext : accepted.split(",")) {
                String trimmed = ext.trim();
                if (trimmed.isEmpty()) continue;
                if (ft.length() > 0) ft.append(';');
                if (!trimmed.startsWith(".")) ft.append('.');
                ft.append(trimmed);
            }
            p.put("fileType", ft.toString());
            p.put("maxSize", maxSize);
            if (!help.isEmpty()) p.put("description", help);

            if ("mandatory".equalsIgnoreCase(required)) {
                Map<String, String> validatorProps = new HashMap<>();
                validatorProps.put("mandatory", "true");
                validatorProps.put("type", "");
                validatorProps.put("message", "");
                Map<String, Object> validator = new HashMap<>();
                validator.put("className", "org.joget.apps.form.lib.DefaultValidator");
                validator.put("properties", validatorProps);
                p.put("validator", validator);
            }

            fu.setProperties(p);
            fu.setParent(this);
            out.add(fu);

        }
    }

    /**
     * Override Element.formatData to contribute this screen's dynamic-field
     * values directly into the storeBinder rowSet, instead of relying on
     * Joget's executeElementFormatData to recurse into our synthesised
     * children. The recursion path was reaching MetaScreenElement.formatData
     * (build-28 confirmed that — log entry "formatData CALLED screen=
     * APP_DOCUMENTS result=rows=1") but the synthesised FileUpload children's
     * formatData never wrote the doc_doc_* values into the row. Joget's
     * storeFormData was never called because the rowSet was effectively empty
     * (datemodified on the row stayed unchanged after Save → no UPDATE
     * statement ran).
     *
     * <p>Most pragmatic Joget-native fix: instead of trying to make the
     * recursion work, this element's formatData reads the request parameters
     * directly and emits a FormRow keyed by each dynamic field id. Joget
     * merges that into the rowSet for the form's storeBinder, which then
     * does the standard saveOrUpdate.
     *
     * <p>Per-kind:
     * <ul>
     *   <li><b>documents</b>: walk {@code mm_required_doc} for the screen's
     *       service, filter by applied_programme, read each FileUpload's
     *       request param (set by the upload widget's hidden input).</li>
     *   <li><b>form</b>: walk {@code mm_field} for the screen, read each
     *       field's request param by storageKey.</li>
     *   <li><b>guide / review</b>: nothing to persist — return null.</li>
     * </ul>
     */
    @Override
    public org.joget.apps.form.model.FormRowSet formatData(FormData formData) {
        if (formData == null) return null;
        String screenCode = getPropertyString("screenId");
        if (screenCode == null || screenCode.isEmpty()) return null;
        try {
            MetaModelDao dao = new MetaModelDao();
            FormRow screen = dao.findScreenByCode(screenCode);
            if (screen == null) return null;
            String kind = nz(screen.getProperty("kind"), "form");
            // Guide screens are pure presentation. Review screens may also
            // carry mm_field rows (e.g. submit_confirmation) — fall through
            // to the standard formatData path for them.
            if ("guide".equalsIgnoreCase(kind)) return null;

            org.joget.apps.form.model.FormRow row = new org.joget.apps.form.model.FormRow();
            int contributed = 0;

            if ("documents".equalsIgnoreCase(kind)) {
                String appliedProgramme = readAppliedProgramme(formData);
                String serviceCode = nz(prop(screen, "serviceId"), "");
                for (FormRow doc : dao.listRequiredDocsForService(serviceCode)) {
                    String regId = nz(prop(doc, "registrationId"), "");
                    if (!regId.isEmpty() && (appliedProgramme.isEmpty() || !regId.equals(appliedProgramme))) {
                        continue;
                    }
                    String code = nz(prop(doc, "code"), "");
                    if (code.isEmpty()) continue;
                    String fieldId = "doc_" + code.toLowerCase();
                    String[] vals = formData.getRequestParameterValues(fieldId);
                    if (vals == null || vals.length == 0) continue;

                    // Replicate FileUpload.formatData's temp-file handling so
                    // AppService.storeFormData → FileUtil.storeFileFromFormRowSet
                    // moves each uploaded file from the temp staging area to the
                    // form's permanent uploads directory and writes only the
                    // bare filename to the DB column (no path).
                    java.util.List<String> resulted = new java.util.ArrayList<>();
                    java.util.List<String> tempPaths = new java.util.ArrayList<>();
                    for (String v : vals) {
                        if (v == null) continue;
                        java.io.File tempFile = org.joget.commons.util.FileManager.getFileByPath(v);
                        if (tempFile != null) {
                            tempPaths.add(v);
                            resulted.add(tempFile.getName());
                        } else {
                            resulted.add(v);
                        }
                    }
                    if (resulted.isEmpty()) continue;
                    String delim = org.joget.apps.form.service.FormUtil.generateElementPropertyValues(
                            resulted.toArray(new String[0]));
                    row.put(fieldId, delim);
                    if (!tempPaths.isEmpty()) {
                        row.putTempFilePath(fieldId, tempPaths.toArray(new String[0]));
                    }
                    contributed++;
                }
            } else {
                // form-kind — walk mm_field, read each storageKey from request params
                String screenUuid = screen.getId();
                boolean hasGisPolygon = false;
                for (FormRow f : dao.listFieldsForScreen(screenUuid)) {
                    String storageKey = nz(prop(f, "storageKey"), "");
                    if (storageKey.isEmpty()) continue;
                    String widget = nz(prop(f, "widget"), "text");
                    // L1-6 — repeating_group rows persist via FormGrid's own
                    // MultirowFormBinder.store path against the child form's
                    // table. The parent form has no column for this widget,
                    // so contributing a value here would only land in the
                    // dropped-by-Hibernate-mapping pile. Skip.
                    if ("repeating_group".equalsIgnoreCase(widget)) continue;
                    // L3-1 3a — budget_hint is render-only CustomHTML;
                    // it has no request param to contribute and no column
                    // to write. Skip so we don't pollute the rowSet.
                    if ("budget_hint".equalsIgnoreCase(widget)) continue;
                    // eligibility_summary — same shape, render-only.
                    if ("eligibility_summary".equalsIgnoreCase(widget)) continue;
                    // W2.8 — notification_timeline — same shape, render-only.
                    if ("notification_timeline".equalsIgnoreCase(widget)) continue;
                    // note_thread — operator-only case notes, render-only.
                    if ("note_thread".equalsIgnoreCase(widget)) continue;
                    if ("gis_polygon".equalsIgnoreCase(widget)) hasGisPolygon = true;
                    String[] vals = formData.getRequestParameterValues(storageKey);
                    if (vals == null || vals.length == 0) continue;

                    if ("file_upload".equalsIgnoreCase(widget)) {
                        // L1-4 — replicate FileUpload.formatData's temp-file
                        // handling for the form-kind case. Same shape as the
                        // documents-kind branch above: walk request param
                        // values, FileManager.getFileByPath identifies temp
                        // uploads, putTempFilePath signals to
                        // FileUtil.storeFileFromFormRowSet to MOVE the file
                        // from temp staging to the form's permanent uploads
                        // dir on save. Without this dance the column gets the
                        // raw temp path string and downloads 404.
                        java.util.List<String> resulted = new java.util.ArrayList<>();
                        java.util.List<String> tempPaths = new java.util.ArrayList<>();
                        for (String v : vals) {
                            if (v == null) continue;
                            java.io.File tempFile = org.joget.commons.util.FileManager.getFileByPath(v);
                            if (tempFile != null) {
                                tempPaths.add(v);
                                resulted.add(tempFile.getName());
                            } else {
                                resulted.add(v);
                            }
                        }
                        if (resulted.isEmpty()) continue;
                        String delim = org.joget.apps.form.service.FormUtil.generateElementPropertyValues(
                                resulted.toArray(new String[0]));
                        row.put(storageKey, delim);
                        if (!tempPaths.isEmpty()) {
                            row.putTempFilePath(storageKey, tempPaths.toArray(new String[0]));
                        }
                        contributed++;
                    } else if (vals[0] != null) {
                        row.put(storageKey, vals[0]);
                        contributed++;
                    }
                }
                // L1-5 — pull the six companion HiddenField values for any
                // gis_polygon field on this screen. The HiddenFields have
                // their own formatData via Joget's recursion, but we
                // contribute here defensively (matches the documents-kind
                // pattern: don't rely on Joget's recursion to reach our
                // synthesised children).
                if (hasGisPolygon) {
                    for (String fid : GIS_COMPANION_FIELD_IDS) {
                        String[] vv = formData.getRequestParameterValues(fid);
                        if (vv != null && vv.length > 0 && vv[0] != null) {
                            row.put(fid, vv[0]);
                            contributed++;
                        }
                    }
                }
            }

            LogUtil.info(CLASS_NAME, "[B29] formatData screen=" + screenCode
                    + " kind=" + kind + " contributed=" + contributed
                    + " keys=" + row.keySet());

            if (row.isEmpty()) return null;
            org.joget.apps.form.model.FormRowSet rs = new org.joget.apps.form.model.FormRowSet();
            rs.add(row);
            return rs;
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "formatData failed for screen=" + screenCode);
            return null;
        }
    }

    /**
     * Translate one {@code mm_field} row into a configured Joget Element.
     * Returns {@code null} when the row is missing a {@code storageKey}.
     *
     * <p>Per §6.4 Conditional UI:
     * <ul>
     *   <li>{@code visible=false} renders the field as a {@code HiddenField}
     *       so the underlying column still round-trips any prior value, but
     *       the citizen sees nothing in the wizard. (Symmetric: if the
     *       determinant flips back to TRUE on a later edit, the value reappears.)</li>
     *   <li>{@code requiredOverride}: {@code TRUE} forces mandatory regardless
     *       of {@code defaultBehaviorOnError}; {@code FALSE} forces optional;
     *       {@code null} keeps the default.</li>
     * </ul>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Element synthesiseField(FormRow f, MetaModelDao dao,
                                    boolean visible,
                                    Boolean requiredOverride,
                                    FormData formData) {
        String widget       = nz(f.getProperty("widget"), "text");
        String storageKey   = nz(f.getProperty("storageKey"), "");
        if (storageKey.isEmpty()) return null;

        // L3-1 3a — budget hint widget: a read-only CustomHTML panel showing
        // what approving THIS application would cost the envelope. Synthesised
        // before the generic widget switch because:
        //   (a) CustomHTML is its own element class (not a stock data widget);
        //   (b) the value is computed at render time from the live programme
        //       code on the form, so we want to bypass the generic
        //       props/validator scaffolding the switch builds for data fields;
        //   (c) freemarker template throws NPE if label is null — set "".
        // Same renderer feeds both this form-side widget and the operator
        // inbox column (BudgetHintFormatter), so the operator sees identical
        // numbers in both surfaces. No data persistence — the element
        // contributes no column to the form's table.
        if ("budget_hint".equals(widget)) {
            String programmeCode = readAppliedProgrammeForBudgetHint(formData);
            String html = global.govstack.regbb.engine.budget.BudgetHintRenderer
                    .renderForForm(programmeCode);
            Element ch = newElement("org.joget.apps.form.lib.CustomHTML");
            if (ch == null) return null;
            Map<String, Object> hp = new HashMap<>();
            hp.put(FormUtil.PROPERTY_ID,    storageKey);
            hp.put(FormUtil.PROPERTY_LABEL, "");   // freemarker NPE prevention
                                                   // — see CLAUDE.md "CustomHTML
                                                   // requires a non-null label".
            hp.put("value", html);
            ch.setProperties(hp);
            return ch;
        }

        // Eligibility summary widget: read-only CustomHTML panel rendering
        // the engine's verdict (status + disposition + score + failed rule)
        // for the application identified by formData.getPrimaryKeyValue().
        // Same render-only treatment as budget_hint — bypasses the validator
        // / props scaffolding the generic switch builds and contributes no
        // column to the host table.
        if ("eligibility_summary".equals(widget)) {
            String pk = "";
            try { if (formData != null) pk = nz(formData.getPrimaryKeyValue(), ""); }
            catch (Throwable ignore) {}
            String html = EligibilitySummaryRenderer.renderForForm(pk);
            Element ch = newElement("org.joget.apps.form.lib.CustomHTML");
            if (ch == null) return null;
            Map<String, Object> hp = new HashMap<>();
            hp.put(FormUtil.PROPERTY_ID,    storageKey);
            hp.put(FormUtil.PROPERTY_LABEL, "");
            hp.put("value", html);
            ch.setProperties(hp);
            return ch;
        }

        // W2.8 — Notification timeline widget: read-only CustomHTML table
        // listing every email/SMS dispatched for the application identified
        // by formData.getPrimaryKeyValue(). Same render-only shape as
        // eligibility_summary and budget_hint — contributes no column to the
        // host table; the rows are fetched at render time from
        // app_fd_notification_queue filtered by correlationId.
        if ("notification_timeline".equals(widget)) {
            String pk = "";
            try { if (formData != null) pk = nz(formData.getPrimaryKeyValue(), ""); }
            catch (Throwable ignore) {}
            String html = NotificationTimelineRenderer.renderForForm(pk);
            Element ch = newElement("org.joget.apps.form.lib.CustomHTML");
            if (ch == null) return null;
            Map<String, Object> hp = new HashMap<>();
            hp.put(FormUtil.PROPERTY_ID,    storageKey);
            hp.put(FormUtil.PROPERTY_LABEL, "");
            hp.put("value", html);
            ch.setProperties(hp);
            return ch;
        }

        // note_thread — Operator-only case-notes thread for one application.
        // Renders a read-only HTML table of case_note rows filtered by
        // application id (formData.primaryKeyValue), plus a "+ Add note"
        // button that opens the case_note_add userview menu in a new tab.
        // Server-side rendering: column labels and value-resolution (kind
        // code → display name from md_case_note_kind) happen at render
        // time, so the browser receives complete HTML. Same shape as
        // notification_timeline. Contributes no column to the host table.
        if ("note_thread".equals(widget)) {
            String pk = "";
            try { if (formData != null) pk = nz(formData.getPrimaryKeyValue(), ""); }
            catch (Throwable ignore) {}
            String html = CaseNoteThreadRenderer.renderForForm(pk);
            Element ch = newElement("org.joget.apps.form.lib.CustomHTML");
            if (ch == null) return null;
            Map<String, Object> hp = new HashMap<>();
            hp.put(FormUtil.PROPERTY_ID,    storageKey);
            hp.put(FormUtil.PROPERTY_LABEL, "");
            hp.put("value", html);
            ch.setProperties(hp);
            return ch;
        }

        // §6.4: invisible fields collapse to a HiddenField so their column
        // persists. Render no label, no validator (a hidden mandatory field
        // would block the form save and the citizen can't see the error).
        if (!visible) {
            Element hidden = newElement("org.joget.apps.form.lib.HiddenField");
            if (hidden != null) {
                Map<String, Object> hp = new HashMap<>();
                hp.put(FormUtil.PROPERTY_ID, storageKey);
                hp.put(FormUtil.PROPERTY_LABEL, "");
                hidden.setProperties(hp);
            }
            return hidden;
        }

        String label        = nz(f.getProperty("label"), storageKey);
        String helpText     = nz(f.getProperty("helpText"), "");
        String dataType     = nz(f.getProperty("dataType"), "string");
        boolean mandatory   = "required".equalsIgnoreCase(
                                  nz(f.getProperty("defaultBehaviorOnError"), "optional"));
        if (requiredOverride != null) mandatory = requiredOverride.booleanValue();

        Map<String, Object> props = new HashMap<>();
        props.put(FormUtil.PROPERTY_ID, storageKey);
        props.put(FormUtil.PROPERTY_LABEL, label);
        if (!helpText.isEmpty()) {
            props.put("description", helpText);
        }

        // Mandatory + type validator (DefaultValidator). Number widgets bolt on
        // a numeric type below.
        Map<String, String> validatorProps = new HashMap<>();
        validatorProps.put("mandatory", mandatory ? "true" : "false");
        validatorProps.put("type", "");
        validatorProps.put("message", "");
        Map<String, Object> validator = new HashMap<>();
        validator.put("className", "org.joget.apps.form.lib.DefaultValidator");
        validator.put("properties", validatorProps);
        props.put("validator", validator);

        String elementClass;
        switch (widget) {
            case "text":
                elementClass = "org.joget.apps.form.lib.TextField";
                break;
            case "number":
                elementClass = "org.joget.apps.form.lib.TextField";
                validatorProps.put("type", "number".equals(dataType) ? "double" : "integer");
                break;
            case "date":
                elementClass = "org.joget.apps.form.lib.DatePicker";
                // Joget's DatePicker uses its own format dialect (NOT Java
                // SimpleDateFormat). DatePicker.getJavaDateFormat() translates
                // yy → yyyy, mm → MM, MM → MMMMM, dd → dd. So "yy-mm-dd"
                // becomes Java "yyyy-MM-dd" (ISO 4-2-2). If we passed the Java
                // pattern directly the dialect re-translates it: "yyyy-MM-dd"
                // → "yyyyyyyy-MMMMM-dd" (the 8-digit-year / month-name bug).
                props.put("format", "yy-mm-dd");
                break;
            case "textarea":
                elementClass = "org.joget.apps.form.lib.TextArea";
                props.put("rows", "4");
                props.put("cols", "40");
                break;
            case "select":
                elementClass = "org.joget.apps.form.lib.SelectBox";
                addCatalogOptions(props, f, dao);
                break;
            case "radio":
                elementClass = "org.joget.apps.form.lib.Radio";
                addCatalogOptions(props, f, dao);
                break;
            case "checkbox":
                elementClass = "org.joget.apps.form.lib.CheckBox";
                addCatalogOptions(props, f, dao);
                break;
            case "gis_polygon":
                // L1-5 (subsidy-to-IM backlog) — Leaflet-based polygon capture
                // for parcel boundaries. The element stores GeoJSON in this
                // field's column; six companion HiddenFields receive computed
                // metrics (area, perimeter, vertex count, centroid lat,
                // auto-center lat/lon). Companion fields are synthesised as
                // siblings via attachGisCompanionHiddenFields below — the
                // element references them by their string ids.
                //
                // Source-verified against
                // gs-plugins/joget-gis-ui/.../GisPolygonCaptureElement.java
                // (renderTemplate ~line 200; companion field props at lines
                // 218-221, 322-333) and the working
                // _05_dev_v2/_a_farmers-reg/_farmer-forms/f02.02.json (the
                // legacy parcel registration form's GIS section). Lesotho
                // coordinate defaults from CLAUDE.md GIS conventions.
                elementClass = "global.govstack.gisui.element.GisPolygonCaptureElement";
                props.put("defaultLatitude",      "-29.6");
                props.put("defaultLongitude",     "28.2");
                props.put("defaultZoom",          "13");
                props.put("mapHeight",            "400");
                // captureMode + defaultMode are matched in gis-capture.js with
                // strict uppercase equality (=== 'BOTH', === 'DRAW') — lowercase
                // values silently disable the drawing UI. Source-verified
                // against gis-capture.js lines 1108-1127.
                props.put("captureMode",          "BOTH");      // walk + draw
                props.put("defaultMode",          "DRAW");
                // Companion HiddenField references — names match the fields
                // attachGisCompanionHiddenFields synthesises as siblings.
                props.put("areaFieldId",          "area_hectares");
                props.put("perimeterFieldId",     "perimeter_meters");
                props.put("centroidFieldId",      "centroid_lat");
                props.put("vertexCountFieldId",   "vertex_count");
                // Auto-center on district/village pickup.
                props.put("enableAutoCenter",            "true");
                props.put("autoCenterDistrictFieldId",   "district");
                props.put("autoCenterVillageFieldId",    "village_name");
                props.put("autoCenterLatFieldId",        "auto_center_lat");
                props.put("autoCenterLonFieldId",        "auto_center_lon");
                props.put("autoCenterCountrySuffix",     ", Lesotho");
                props.put("autoCenterZoom",              "15");
                // GIS API auth (parcelGisServer endpoint). apiKey is shared
                // across the farmerPortal app; apiId is the parcelGisServer
                // API Builder definition. URL prefix matches f02.02.json
                // convention (working in production today).
                props.put("apiEndpoint", "/jw/api/gis/gis");
                props.put("apiId",       "API-d5e0a0cc-a12a-4360-84b5-d794691c732e");
                props.put("apiKey",      "a5af1181f77b4a62b481725b6410e965");
                break;
            case "repeating_group":
                // L1-6 (subsidy-to-IM backlog) — embed an existing Joget
                // form as a multi-row child grid. The child form's id is on
                // mm_field.referencedFormId; the FK column it carries to
                // link rows back to this parent is on widgetConfig.foreignKey
                // (default "parent_id"). Columns to display are also on
                // widgetConfig.columns — JSONArray of
                // {value, label, width, format, formatType} per the
                // canonical FormGrid options shape (verified against the
                // legacy farmerHousehold.householdMembers grid in
                // app_form.json).
                //
                // Both loadBinder AND storeBinder are required (kernel SAD
                // §8.5). They're attached AFTER child.setProperties via
                // attachRepeatingGroupBinders so they land on the Element's
                // dedicated fields, not in the properties bag (where Joget
                // would never read them). Same pattern as L1-1's
                // attachCascadingOptionsBinder.
                //
                // The data does NOT round-trip through the parent form's
                // column — FormGrid persists rows directly to the child
                // form's table. So the repeating_group's storageKey is
                // EXCLUDED from getDynamicFieldNames and formatData (see
                // those methods).
                elementClass = "org.joget.plugin.enterprise.FormGrid";
                {
                    String referencedFormId = nz(prop(f, "referencedFormId"), "");
                    if (referencedFormId.isEmpty()) {
                        LogUtil.warn(CLASS_NAME,
                            "repeating_group with empty referencedFormId on storageKey="
                            + nz(prop(f, "storageKey"), "?")
                            + " — grid will not render any rows. Set mm_field.referencedFormId.");
                    }
                    props.put("formDefId", referencedFormId);
                    // Sensible defaults; widgetConfig overrides below.
                    props.put("foreignKey",    "parent_id");
                    props.put("pageSize",      "20");
                    props.put("height",        "400");
                    props.put("showRowNumber", "true");
                }
                break;
            case "file_upload":
                // L1-4 (subsidy-to-IM backlog) — one-off attachment on a
                // form-kind screen that's NOT bound to mm_required_doc.
                // Renders the same FileUpload Joget uses everywhere; the
                // file-move temp-path dance is replicated in formatData below
                // (see "form-kind file_upload contribution" branch). Defaults
                // accept PDF/JPG/PNG up to 5 MB — sensible Phase 1 baseline,
                // upgrade to per-field config (mm_field.acceptedTypes /
                // maxSizeBytes columns) when an analyst needs different limits.
                // Source-verified against
                // jw-community/wflow-core/.../FileUpload.java (formatData ~line 182,
                // putTempFilePath contract). The same dance synthesiseDocumentChildren
                // already uses for documents-kind screens.
                elementClass = "org.joget.apps.form.lib.FileUpload";
                props.put("fileType", ".pdf;.jpg;.jpeg;.png");
                props.put("maxSize",  "5120");   // KB; FileUpload multiplies by 1024
                props.put("multiple", "false");
                break;
            case "signature":
                // L1-3 (subsidy-to-IM backlog) — pen/touch signature pad that
                // persists a base64-encoded image into the field's column. The
                // enterprise Signature element is not in the community source
                // tree; canonical config shape verified against
                // _05_dev_v2/_a_farmers-reg/_farmer-forms/f01.07.json (the
                // "Declaration" section of the legacy farmer registration
                // form). Defaults: 200×80, encryption on, editable. If
                // analysts need per-screen overrides later, add dedicated
                // mm_field.widgetWidth / widgetHeight columns rather than
                // overloading existing fields.
                elementClass = "org.joget.plugin.enterprise.Signature";
                props.put("width",      "200");
                props.put("height",     "80");
                props.put("encryption", "true");
                props.put("readonly",   "False");
                break;
            case "cascading_select":
                // L1-1 (subsidy-to-IM backlog) — a SelectBox whose options
                // are dynamically loaded from a master-data form, filtered
                // by a parent field's value. Source-verified pattern from
                // jw-community/wflow-core (SelectBox.java line 223-249,
                // FormOptionsBinder.java line 118-155, FormUtil.java line
                // 2009-2058):
                //
                //   - controlField on SelectBox names the parent input.
                //   - Joget's setAjaxOptionsElementProperties at render time
                //     reads the parent's runtime value, calls
                //     binder.loadAjaxOptions(controlValues).
                //   - FormOptionsBinder uses groupingColumn (NOT
                //     extraCondition) to filter — extraCondition is taken
                //     verbatim with no #X# substitution, so it cannot drive
                //     a cascade on its own.
                //
                // For cascading_select, mm_field.optionsCatalogId carries
                // the LOOKUP FORM ID directly (e.g. "md_village"), not an
                // mm_catalog row code. mm_field.optionsFilterField names
                // the parent field (e.g. "district"). The binder itself is
                // attached AFTER child.setProperties via
                // attachCascadingOptionsBinder so it goes onto the Element's
                // optionsBinder field, not into properties (where it would
                // be ignored at render time per FormUtil line 2011 reading
                // element.getOptionsBinder()).
                elementClass = "org.joget.apps.form.lib.SelectBox";
                {
                    String parentField = nz(prop(f, "optionsFilterField"), "");
                    if (!parentField.isEmpty()) {
                        props.put("controlField", parentField);
                    }
                }
                break;
            default:
                // Unsupported / not-yet-implemented widget — render a read-only
                // placeholder TextField with the widget name in the label so the
                // operator can see at a glance that this field is staged for a
                // later week's deliverable. Keeps the form usable instead of
                // failing the whole render.
                elementClass = "org.joget.apps.form.lib.TextField";
                props.put(FormUtil.PROPERTY_LABEL,
                        label + "  [widget=" + widget + " — pending implementation]");
                props.put("readonly", "true");
                break;
        }

        // L1-5b — overlay any analyst-authored mm_field.widgetConfig
        // overrides on top of the per-case kernel defaults, filtered by the
        // per-widget allowlist. "Configuration over code" — the kernel ships
        // sensible defaults, the analyst tweaks via metadata, no kernel
        // rebuild needed when a screen needs different settings.
        java.util.Set<String> overridable = null;
        switch (widget) {
            case "signature":       overridable = SIGNATURE_OVERRIDABLE_KEYS;       break;
            case "file_upload":     overridable = FILE_UPLOAD_OVERRIDABLE_KEYS;     break;
            case "gis_polygon":     overridable = GIS_POLYGON_OVERRIDABLE_KEYS;     break;
            case "repeating_group": overridable = REPEATING_GROUP_OVERRIDABLE_KEYS; break;
            default: break;  // stock wflow-core widgets don't yet support
                             // widgetConfig overrides; future work.
        }
        if (overridable != null) {
            mergeWidgetConfig(props, f, widget, overridable);
        }

        Element child = newElement(elementClass);
        if (child != null) {
            child.setProperties(props);
            // For cascading_select, the optionsBinder must be attached AFTER
            // setProperties so it lands on the Element's optionsBinder field
            // (read at render time by FormUtil) — not in the properties bag
            // (which only validation() peeks at). See jw-community
            // FormUtil.parseBinderFromJsonObject for the canonical pattern.
            if ("cascading_select".equals(widget)) {
                attachCascadingOptionsBinder(child, f);
            }
            // L1-6 — FormGrid needs both loadBinder AND storeBinder set on
            // dedicated Element fields (kernel SAD §8.5). Attaching them
            // through the properties bag would not work — Joget reads the
            // binders via getLoadBinder()/getStoreBinder(), not from props.
            if ("repeating_group".equals(widget)) {
                attachRepeatingGroupBinders(child, f, props);
            }
        }
        return child;
    }

    /**
     * L1-6 — attach the load + store binders to a synthesised FormGrid.
     *
     * <p>Both binders are {@code MultirowFormBinder} instances pointing at
     * the same child form id and FK column. Without BOTH binders, the grid
     * either fails to LOAD existing rows on edit (storeBinder-only — symptom
     * looks like an FK mismatch) or fails to SAVE new rows (loadBinder-only).
     * Both must be configured. This is the "FormGrid requires both
     * loadBinder AND storeBinder" gotcha documented in CLAUDE.md.
     *
     * <p>Pattern matches {@link #attachCascadingOptionsBinder}: instantiate
     * via PluginManager → setProperties → setElement (so the binder knows
     * its parent for FormUtil look-ups) → install on the FormGrid via the
     * dedicated setLoadBinder/setStoreBinder reflective calls. We use
     * reflection because the FormGrid type isn't compile-time visible from
     * this bundle (joget-enterprise classes aren't exported).
     *
     * <p>The {@code formDefId} and {@code foreignKey} are the only properties
     * MultirowFormBinder reads — pulled from the FormGrid's already-set
     * properties (so any {@code widgetConfig.foreignKey} override has
     * already been applied by the time we get here).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void attachRepeatingGroupBinders(Element grid, FormRow f, Map<String, Object> props) {
        String formDefId = String.valueOf(props.getOrDefault("formDefId", ""));
        String foreignKey = String.valueOf(props.getOrDefault("foreignKey", "parent_id"));
        if (formDefId.isEmpty()) {
            LogUtil.warn(CLASS_NAME,
                "attachRepeatingGroupBinders: empty formDefId; skipping binder attach. "
                + "storageKey=" + nz(prop(f, "storageKey"), "?"));
            return;
        }
        try {
            Object pluginManager = org.joget.apps.app.service.AppUtil
                    .getApplicationContext().getBean("pluginManager");
            java.lang.reflect.Method getPlugin = pluginManager.getClass()
                    .getMethod("getPlugin", String.class);

            // Two separate binder instances — Joget treats load and store
            // binders independently, sharing one instance would risk state
            // leakage between read and write paths.
            Object loadBinder  = getPlugin.invoke(pluginManager, "org.joget.plugin.enterprise.MultirowFormBinder");
            Object storeBinder = getPlugin.invoke(pluginManager, "org.joget.plugin.enterprise.MultirowFormBinder");
            if (loadBinder == null || storeBinder == null) {
                LogUtil.warn(CLASS_NAME,
                    "MultirowFormBinder is not registered as a Joget plugin — repeating_group will not load/save rows.");
                return;
            }

            Map<String, Object> bp = new HashMap<>();
            bp.put("formDefId",  formDefId);
            bp.put("foreignKey", foreignKey);

            java.lang.reflect.Method setProps   = loadBinder.getClass().getMethod("setProperties", Map.class);
            java.lang.reflect.Method setElement = loadBinder.getClass().getMethod("setElement", Element.class);
            setProps.invoke(loadBinder,  bp);
            setProps.invoke(storeBinder, bp);
            setElement.invoke(loadBinder,  grid);
            setElement.invoke(storeBinder, grid);

            // FormGrid extends Element which has setLoadBinder + setStoreBinder.
            // Resolve to the actual binder type the setters expect.
            Class<?> loadBinderType  = Class.forName("org.joget.apps.form.model.FormLoadBinder");
            Class<?> storeBinderType = Class.forName("org.joget.apps.form.model.FormStoreBinder");
            java.lang.reflect.Method setLoad  = grid.getClass().getMethod("setLoadBinder",  loadBinderType);
            java.lang.reflect.Method setStore = grid.getClass().getMethod("setStoreBinder", storeBinderType);
            setLoad.invoke(grid,  loadBinder);
            setStore.invoke(grid, storeBinder);
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                "attachRepeatingGroupBinders failed for storageKey="
                + nz(prop(f, "storageKey"), "?")
                + " formDefId=" + formDefId
                + " foreignKey=" + foreignKey);
        }
    }

    /**
     * Instantiate a Joget element by class name.
     *
     * <p>Tries two paths in order:
     * <ol>
     *   <li>Joget {@code PluginManager.getPlugin(className)} — works for
     *       any plugin registered through any bundle's Activator. Required
     *       for elements in bundles that don't {@code Export-Package} (e.g.
     *       {@code joget-gis-ui}'s {@code GisPolygonCaptureElement} —
     *       discovered May 2026 when L1-5 silently failed because
     *       {@code Class.forName} can't see across un-exported bundle
     *       packages).</li>
     *   <li>Direct {@code Class.forName} — fallback for stock {@code wflow-core}
     *       classes (HiddenField, TextField, SelectBox, etc.) which are always
     *       visible from any bundle. Also covers the rare case where a class
     *       exists but isn't registered as a plugin.</li>
     * </ol>
     */
    private static Element newElement(String className) {
        // Path 1: PluginManager (cross-bundle-safe, via OSGi service registry).
        try {
            Object pluginManager = org.joget.apps.app.service.AppUtil
                    .getApplicationContext().getBean("pluginManager");
            java.lang.reflect.Method getPlugin = pluginManager.getClass()
                    .getMethod("getPlugin", String.class);
            Object plugin = getPlugin.invoke(pluginManager, className);
            if (plugin instanceof Element) {
                return (Element) plugin;
            }
        } catch (Throwable t) {
            LogUtil.debug(CLASS_NAME,
                    "PluginManager lookup for " + className + " failed: " + t.getMessage()
                    + " — falling through to Class.forName");
        }
        // Path 2: direct classloader lookup for wflow-core elements.
        try {
            return (Element) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            LogUtil.error(CLASS_NAME, e,
                    "Failed to instantiate Joget element " + className
                    + " (PluginManager and Class.forName both failed)");
            return null;
        }
    }


    /**
     * L1-1 cascading select. Attach a {@code FormOptionsBinder} to the
     * synthesised {@code SelectBox} so its options load dynamically from a
     * master-data form, optionally filtered by a parent field's runtime
     * value.
     *
     * <p>Source-verified mechanism (jw-community/wflow-core):
     * <ul>
     *   <li>{@code FormUtil.parseBinderFromJsonObject} (line 349) is the
     *       canonical pattern — get the binder via {@code PluginManager.getPlugin(className)},
     *       call {@code setProperties}, call {@code setElement(parent)}, then
     *       attach via {@code parent.setOptionsBinder(binder)}.</li>
     *   <li>At render time {@code FormUtil.setAjaxOptionsElementProperties}
     *       (line 2009) reads the parent's value via the SelectBox's
     *       {@code controlField}, calls
     *       {@code binder.loadAjaxOptions(controlValues)}, caches the result
     *       in {@code formData} for {@code SelectBox.renderTemplate} to read.</li>
     *   <li>The binder filters via {@code groupingColumn} — that's the
     *       property that takes the {@code controlValues} String[] and
     *       produces a parameterised
     *       {@code WHERE e.customProperties.<col> in (?,?,?)} clause
     *       ({@link FormOptionsBinder#loadAjaxOptions} lines 118-155).
     *       {@code extraCondition} is appended verbatim — no
     *       {@code #fieldName#} token substitution, so it CAN'T drive a
     *       cascade on its own.</li>
     * </ul>
     *
     * <p>Convention for cross-form FK columns (per D20):
     * {@code idColumn=code}, {@code labelColumn=name}.
     *
     * <p>For {@code widget=cascading_select}, {@code mm_field.optionsCatalogId}
     * is interpreted as the LOOKUP FORM ID (e.g. {@code md_village}) — NOT
     * an {@code mm_catalog} row code. {@code mm_field.optionsFilterField}
     * names the parent field (e.g. {@code district}).
     *
     * <p>Failure to attach (form not registered, plugin not found, etc.) is
     * logged but doesn't break the render — the SelectBox just shows no
     * options. Operator can then debug from the empty dropdown rather than
     * a stack trace.
     */
    private void attachCascadingOptionsBinder(Element selectBox, FormRow field) {
        String formId      = nz(prop(field, "optionsCatalogId"), "");
        String filterField = nz(prop(field, "optionsFilterField"), "");

        if (formId.isEmpty()) {
            LogUtil.warn(CLASS_NAME,
                    "cascading_select on field " + prop(field, "storageKey")
                    + " has no optionsCatalogId — SelectBox will be empty.");
            return;
        }

        try {
            // Step 1: instantiate via PluginManager — same as FormUtil
            // .parseBinderFromJsonObject so we get a properly-registered
            // plugin instance (not a fresh-new() that lacks Joget's plugin
            // lifecycle hooks).
            Object pluginManager = org.joget.apps.app.service.AppUtil
                    .getApplicationContext().getBean("pluginManager");
            java.lang.reflect.Method getPlugin = pluginManager.getClass()
                    .getMethod("getPlugin", String.class);
            Object binder = getPlugin.invoke(pluginManager,
                    "org.joget.apps.form.lib.FormOptionsBinder");
            if (binder == null) {
                LogUtil.warn(CLASS_NAME,
                        "FormOptionsBinder is not registered as a Joget plugin");
                return;
            }

            // Step 2: configure properties.
            Map<String, Object> bp = new HashMap<>();
            bp.put("formDefId",      formId);
            bp.put("idColumn",       "code");
            bp.put("labelColumn",    "name");
            bp.put("addEmptyOption", "true");
            bp.put("emptyLabel",     "(select)");
            bp.put("useAjax",        "true");        // required for cascade
            bp.put("extraCondition", "");
            // groupingColumn is what makes the cascade work — see
            // FormOptionsBinder.loadAjaxOptions (jw-community line 118-155).
            // When dependencyValues are supplied at runtime by Joget, this
            // builds the parameterised WHERE clause.
            if (!filterField.isEmpty()) {
                bp.put("groupingColumn", filterField);
            } else {
                bp.put("groupingColumn", "");
            }

            java.lang.reflect.Method setProps = binder.getClass()
                    .getMethod("setProperties", Map.class);
            setProps.invoke(binder, bp);

            // Step 3: link binder to host element (matches
            // FormUtil.parseBinderFromJsonObject line 366).
            try {
                java.lang.reflect.Method setElement = binder.getClass()
                        .getMethod("setElement", Element.class);
                setElement.invoke(binder, selectBox);
            } catch (NoSuchMethodException ignore) {
                // Some plugin shapes don't have setElement; non-fatal.
            }

            // Step 4: attach to the SelectBox so Element.getOptionsBinder()
            // returns it. Joget's render pipeline reads this directly
            // (FormUtil.java line 2011: element.getOptionsBinder()).
            Class<?> formLoadBinderCls = Class.forName(
                    "org.joget.apps.form.model.FormLoadBinder");
            java.lang.reflect.Method setOptionsBinder = selectBox.getClass()
                    .getMethod("setOptionsBinder", formLoadBinderCls);
            setOptionsBinder.invoke(selectBox, binder);

        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME,
                    "attachCascadingOptionsBinder failed for field "
                    + prop(field, "storageKey") + ": "
                    + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    /**
     * Resolve the {@code mm_catalog.itemsJson} for a select / radio / checkbox
     * field and inject the resulting list under {@code props["options"]}.
     *
     * <p>Today supports {@code source=static} ({@code itemsJson} is a JSON
     * array of {@code {value,label}}). {@code source=registry} (IM capability
     * lookup) is deferred per Phase 1 plan.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void addCatalogOptions(Map<String, Object> props, FormRow field, MetaModelDao dao) {
        // Per D20: mm_field.optionsCatalogId stores the catalog's business
        // code (not its UUID id); resolve via findCatalogByCode.
        //
        // The options property must be a FormRowSet of FormRow rows — Joget's
        // FormUtil.getElementPropertyOptionsMap hard-casts the property to
        // FormRowSet (see wflow-core), so a plain List<Map> would throw a
        // ClassCastException at render time. Each FormRow carries
        // value / label / grouping properties, just as the form-builder JSON
        // parser produces when it reads "options" from a static array.
        String catalogCode = nz(field.getProperty("optionsCatalogId"), "");
        FormRowSet options = new FormRowSet();
        options.setMultiRow(true);

        if (catalogCode.isEmpty()) {
            props.put("options", options);
            return;
        }
        try {
            FormRow catalog = dao.findCatalogByCode(catalogCode);
            if (catalog == null) {
                LogUtil.warn(CLASS_NAME,
                        "mm_catalog row not found for optionsCatalogId=" + catalogCode + " (looked up by code)");
                props.put("options", options);
                return;
            }
            String itemsJson = nz(catalog.getProperty("itemsJson"), "");
            if (itemsJson.isEmpty()) {
                props.put("options", options);
                return;
            }
            JSONArray arr = new JSONArray(itemsJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                FormRow opt = new FormRow();
                opt.setProperty(FormUtil.PROPERTY_VALUE,    o.optString("value"));
                opt.setProperty(FormUtil.PROPERTY_LABEL,    o.optString("label", o.optString("value")));
                opt.setProperty(FormUtil.PROPERTY_GROUPING, "");
                options.add(opt);
            }
            props.put("options", options);
        } catch (Throwable e) {
            LogUtil.warn(CLASS_NAME,
                    "addCatalogOptions failed for catalogCode=" + catalogCode + ": " + e.getMessage());
            props.put("options", options);
        }
    }

    // ---------------------------------------------------------------------
    //  Rendering — delegate to children.
    // ---------------------------------------------------------------------

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public String renderTemplate(FormData formData, Map dataModel) {
        // L4-5 — open per-render DAO + evaluator caches (idempotent if
        // MetaWizardElement already opened a scope above us; ref-counted).
        global.govstack.regbb.engine.dao.MetaModelDao.beginRequest();
        global.govstack.regbb.engine.evaluator.RoutingEvaluator.beginRequest();
        try {
            return doRenderTemplate(formData, dataModel);
        } finally {
            global.govstack.regbb.engine.evaluator.RoutingEvaluator.endRequest();
            global.govstack.regbb.engine.dao.MetaModelDao.endRequest();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String doRenderTemplate(FormData formData, Map dataModel) {
        String meta = (dataModel != null && dataModel.get("elementMetaData") != null)
                ? dataModel.get("elementMetaData").toString() : "";
        boolean inBuilder = !meta.isEmpty();
        String screenCode = getPropertyString("screenId");
        boolean readonly = "Y".equalsIgnoreCase(getPropertyString("readonly"));

        // Form-Builder canvas: static placeholder; children aren't synthesised
        // here because the builder doesn't have a runtime FormData and we want
        // the canvas representation to be a single tile, not the expanded form.
        if (inBuilder) {
            return "<div class=\"form-cell\" " + meta + ">"
                 + "<div style=\"background:#f0f4f8;border-left:4px solid #2c7a7b;"
                 + "padding:8px 12px;border-radius:4px;font-size:12px;color:#1a4040;"
                 + "pointer-events:none;\">"
                 + "<b>Meta Screen</b> &nbsp;·&nbsp; code: <code>"
                 + (screenCode.isEmpty() ? "(unset)" : htmlEscape(screenCode))
                 + "</code> &nbsp;·&nbsp; readonly: <code>" + (readonly ? "Y" : "N") + "</code>"
                 + "</div></div>";
        }

        if (screenCode.isEmpty()) {
            return errorBox("Meta Screen has no screen configured. Pick a mm_screen in the property panel.");
        }

        try {
            MetaModelDao dao = new MetaModelDao();
            // Per D20: widget property holds the screen's business code; resolve.
            FormRow screen = dao.findScreenByCode(screenCode);
            if (screen == null) {
                return errorBox("mm_screen row not found for code: " + htmlEscape(screenCode));
            }

            // Transparent container: emit no visible chrome of our own —
            // no <h3> screen title, no <p> description, no engine-stamp
            // footer. Form titles, helper text, and grouping are the form
            // designer's job, controlled via the surrounding Section.label
            // / HtmlPage / Custom HTML, exactly as for any other Joget form.
            // Build/diagnostic stamp is preserved as an HTML comment so it
            // shows in "View Source" without polluting the rendered UI.
            StringBuilder html = new StringBuilder();
            String kind = nz(screen.getProperty("kind"), "form");
            html.append("<!-- regbb-meta-screen code=").append(screenCode)
                .append(" id=").append(screen.getId())
                .append(" kind=").append(kind)
                .append(" engine=").append(Build.STAMP)
                .append(readonly ? " readonly=Y" : "")
                .append(" -->");

            // Dispatch on screen kind. Form screens render synthesised fields
            // (the existing path); guide / documents / review render different
            // content per their semantic role in the application flow.
            if ("guide".equalsIgnoreCase(kind)) {
                // L2-2 — single-window catalogue page (RegBB §6.1.6).
                // mm_screen.guideKind=catalogue switches to programme list
                // with applicability pre-evaluated. Empty / "static" → original
                // welcome-card behaviour.
                String guideKind = nz(prop(screen, "guideKind"), "static");
                if ("catalogue".equalsIgnoreCase(guideKind)) {
                    renderCatalogue(html, screen, dao, formData);
                } else {
                    renderGuide(html, screen);
                }
                return html.toString();
            }
            if ("documents".equalsIgnoreCase(kind)) {
                renderDocuments(html, screen, dao, readonly, formData);
                return html.toString();
            }
            if ("review".equalsIgnoreCase(kind)) {
                renderReview(html, screen, formData);
                // Fall through to render mm_field children BELOW the
                // review summary (e.g. submit_confirmation checkbox).
                // Children are synthesised by getChildren() per the
                // form-kind path now that the guard at line ~340 has
                // been opened up for review-kind.
            }
            // confirmation / payment kinds: defer to fields-as-form semantics
            // until those kinds get their own renderers (Week 4+).

            Collection<Element> children = getChildren(formData);
            if (children.isEmpty()) {
                // Surface the empty case visibly — silently rendering nothing
                // hides a likely misconfiguration (mm_screen exists but has
                // no mm_field rows).
                html.append("<div class=\"form-cell\">")
                    .append("<em style=\"color:#888;\">No fields defined for screen ")
                    .append(htmlEscape(screenCode)).append(".</em></div>");
            } else {
                for (Element child : children) {
                    if (readonly) {
                        child.setProperty(FormUtil.PROPERTY_READONLY, "true");
                    }
                    // Each child renders through the standard Joget pipeline
                    // (template lookup, validator decoration, error injection,
                    // form-cell wrapping) — same as if the user had dropped a
                    // plain TextField on the form themselves.
                    html.append(child.render(formData, false));
                }
            }

            return html.toString();
        } catch (RuntimeException e) {
            LogUtil.error(CLASS_NAME, e,
                    "MetaScreenElement render failed for screenCode=" + screenCode);
            return errorBox("MetaScreenElement render failed: " + htmlEscape(e.getMessage()));
        }
    }

    // ---------------------------------------------------------------------
    //  Per-kind renderers (guide / documents / review)
    // ---------------------------------------------------------------------

    /**
     * Guide screens render the screen's title + description as the welcome /
     * instructions block. No data capture. Description text is wrapped in a
     * styled card so the citizen sees a real welcome page rather than empty
     * space.
     */
    private void renderGuide(StringBuilder html, FormRow screen) {
        String title       = nz(screen.getProperty("title"), "");
        String description = nz(screen.getProperty("description"), "");
        html.append("<div class=\"form-cell\">")
            .append("<div style=\"background:#f7faf7;border-left:4px solid #2c7a7b;")
            .append("padding:1em 1.2em;border-radius:4px;margin:0.5em 0;\">");
        if (!title.isEmpty()) {
            html.append("<h3 style=\"margin:0 0 0.5em 0;color:#1a4040;\">")
                .append(htmlEscape(title)).append("</h3>");
        }
        if (!description.isEmpty()) {
            html.append("<div style=\"color:#333;line-height:1.5;white-space:pre-wrap;\">")
                .append(htmlEscape(description)).append("</div>");
        }
        html.append("</div></div>");
    }

    /**
     * L2-2 — single-window catalogue page renderer (RegBB §6.1.6).
     *
     * <p>Walks every {@code mm_registration} row scoped to the screen's
     * {@code serviceId}, evaluates each programme's
     * {@code applicabilityDeterminantId} against the citizen's known data
     * (whatever the wizard has captured so far + L2-3 NID auto-fill when
     * landed), and renders one card per programme with a status indicator:
     *
     * <ul>
     *   <li>{@code TRUE}  → green "Applicable".</li>
     *   <li>{@code FALSE} → red "Not applicable" + the rule's failMessage.</li>
     *   <li>{@code NULL}  → grey "Need more information" (data not yet
     *       captured — typical at first entry).</li>
     *   <li>{@code ERROR} → grey "Could not evaluate" + cause.</li>
     * </ul>
     *
     * <p>Click a card → JS sets the wizard's {@code applied_programme}
     * hidden field → the next tab's SelectBox shows the picked programme
     * pre-selected. The hidden field is named {@code applied_programme} so
     * the wizard's storeBinder picks it up at the next save.
     *
     * <p>The catalogue is the spec-conformant entry point per RegBB §6.1.6;
     * the legacy SelectBox on APP_APPLICANT continues to coexist and reads
     * from the same column, so manual override remains possible.
     */
    private void renderCatalogue(StringBuilder html, FormRow screen,
                                 MetaModelDao dao, FormData formData) {
        String title       = nz(prop(screen, "title"), "");
        String description = nz(prop(screen, "description"), "");
        String serviceCode = nz(prop(screen, "serviceId"), "");
        String currentPick = readAppliedProgramme(formData);

        html.append("<div class=\"form-cell\">");
        if (!title.isEmpty()) {
            html.append("<h3 style=\"margin:0 0 0.4em 0;color:#1a4040;\">")
                .append(htmlEscape(title)).append("</h3>");
        }
        if (!description.isEmpty()) {
            html.append("<p style=\"color:#444;line-height:1.5;margin:0 0 1em 0;white-space:pre-wrap;\">")
                .append(htmlEscape(description)).append("</p>");
        }

        if (serviceCode.isEmpty()) {
            html.append("<div style=\"padding:1em;background:#fff3cd;border-left:4px solid #c2a000;\">")
                .append("This catalogue screen has no <code>serviceId</code> configured. ")
                .append("Set <code>mm_screen.serviceId</code> to a service code (e.g. <code>SUBSIDY_2025</code>).")
                .append("</div></div>");
            return;
        }

        java.util.List<FormRow> regs = dao.listRegistrationsForService(serviceCode);
        if (regs.isEmpty()) {
            html.append("<div style=\"padding:1em;background:#fff3cd;border-left:4px solid #c2a000;\">")
                .append("No programmes (<code>mm_registration</code> rows) configured for service <code>")
                .append(htmlEscape(serviceCode)).append("</code>.")
                .append("</div></div>");
            return;
        }

        // Build the EvalContext once — applicantData carried over from the
        // form's load binder + any in-flight request params from earlier
        // wizard steps. NULL outcomes from rules requiring not-yet-captured
        // data are surfaced as "Need more information" cards rather than as
        // errors.
        //
        // Reads in priority: request param (in-flight typing on this or
        // previous wizard tab) → load-binder (saved row in edit mode).
        // For ?_mode=add wizards, neither path may have data until the user
        // saves; that's why fresh-application catalogue cards default to
        // NULL/grey "Need more information".
        //
        // L4-1 D-001 fix (May 2026): only consult the load binder when
        // formData has an actual primaryKeyValue set. Some Joget load
        // binders (in particular the wizard's default binder configuration)
        // return ARBITRARY rows from the form's table when no PK filter
        // matches — leaking another applicant's saved data into a fresh
        // catalogue render. Symptom: ?_mode=add catalogue showed PRG_001
        // Applicable on a brand-new application because the leaked row
        // had agro_zone='lowlands'. Discriminator: in ?_mode=add, FormData
        // either has no primaryKeyValue or has the freshly-generated one
        // for which no row exists yet; in ?_mode=edit it points at a real
        // saved row. Skipping the load binder when PK is empty closes
        // the leak without breaking the legitimate edit-mode case.
        Map<String, Object> applicantData = new HashMap<>();
        String pkValue = (formData != null) ? formData.getPrimaryKeyValue() : null;
        boolean hasSavedRecord = (pkValue != null && !pkValue.isEmpty());
        try {
            org.joget.apps.form.model.Form root = FormUtil.findRootForm(this);
            if (root != null && formData != null) {
                // Read the well-known "applicant" fields the rules typically
                // touch, plus national_id which drives capability adapters.
                String[] keys = { "national_id", "first_name", "last_name", "full_name",
                                  "gender", "date_of_birth", "contact_phone", "email",
                                  "district", "agro_zone", "village_name", "area_hectares",
                                  "tenure_type", "drought_affected_decl", "applied_programme" };
                for (String k : keys) {
                    String v = null;
                    try {
                        String[] rp = formData.getRequestParameterValues(k);
                        if (rp != null && rp.length > 0 && rp[0] != null && !rp[0].isEmpty()) v = rp[0];
                    } catch (Throwable ignore) {}
                    if ((v == null || v.isEmpty()) && hasSavedRecord) {
                        // Only consult the load binder when there's a real
                        // saved record to load from. Empty PK in ?_mode=add
                        // can't legitimately produce binder data — anything
                        // the binder returns is a leak from another row.
                        try {
                            v = formData.getLoadBinderDataProperty(root, k);
                        } catch (Throwable ignore) {}
                    }
                    if (v != null && !v.isEmpty()) applicantData.put(k, v);
                }
            }
        } catch (Throwable ignore) {}

        // L2-2 known limitation: in ?_mode=add, MultiPagedForm doesn't
        // surface previous-tab values to the load binder of an earlier tab,
        // so applicantData is empty until the wizard's first save. After
        // first save (or in ?_mode=edit), the load binder fills it and
        // rules evaluate against real data. Live in-flight re-evaluation
        // is L2-2-bis — Ajax round-trip on tab focus, Phase 2 work.

        // Diagnostic — dump the resolved applicantData as an HTML comment so
        // operators can View Source and confirm whether stale fixture data
        // is leaking into the catalogue evaluation. Cheap (executes once per
        // render) and not visible to end users; remove once L4-1 closes.
        html.append("<!-- catalogue applicantData=").append(htmlEscape(applicantData.toString()))
            .append(" hasSavedRecord=").append(hasSavedRecord)
            .append(" pk=").append(pkValue == null ? "null" : pkValue)
            .append(" -->");

        // Hidden field carrying the citizen's pick; the wizard's storeBinder
        // round-trips it into c_applied_programme on save.
        html.append("<input type=\"hidden\" name=\"applied_programme\" id=\"applied_programme\" value=\"")
            .append(htmlEscape(currentPick)).append("\">");

        // Inline styles — kept in this method so the catalogue is
        // self-contained; if more guide flavours are added later, lift this
        // into a stylesheet served by the registry-publisher.
        html.append("<style>")
            .append(".rb-cat{display:flex;flex-direction:column;gap:0.7em;margin:0.6em 0;}")
            .append(".rb-card{padding:1em 1.2em;border:2px solid #ddd;border-radius:6px;cursor:pointer;background:#fff;transition:border-color .15s,box-shadow .15s;}")
            .append(".rb-card:hover{box-shadow:0 2px 8px rgba(0,0,0,.08);}")
            .append(".rb-card.rb-pick{border-color:#1a73e8;box-shadow:0 0 0 3px rgba(26,115,232,.18);}")
            .append(".rb-card.rb-applicable{border-left:6px solid #2e7d32;}")
            .append(".rb-card.rb-ineligible{border-left:6px solid #c62828;background:#fcf3f3;cursor:not-allowed;}")
            .append(".rb-card.rb-pending{border-left:6px solid #9e9e9e;background:#fafafa;}")
            .append(".rb-card.rb-error{border-left:6px solid #c2a000;background:#fffbe6;}")
            .append(".rb-row{display:flex;gap:1em;align-items:baseline;flex-wrap:wrap;}")
            .append(".rb-name{font-size:1.05em;font-weight:600;color:#1a4040;}")
            .append(".rb-code{font-family:monospace;font-size:0.9em;color:#555;}")
            .append(".rb-status{padding:0.15em 0.5em;border-radius:3px;font-size:0.8em;font-weight:600;letter-spacing:0.02em;}")
            .append(".rb-status-ok{background:#e7f4e8;color:#1b5e20;}")
            .append(".rb-status-no{background:#fbe3e3;color:#8b0000;}")
            .append(".rb-status-pend{background:#eee;color:#555;}")
            .append(".rb-status-err{background:#fdf2c2;color:#7a5d00;}")
            .append(".rb-desc{margin-top:0.4em;color:#444;line-height:1.4;}")
            .append(".rb-reason{margin-top:0.4em;color:#8b0000;font-style:italic;}")
            .append("</style>");

        html.append("<div class=\"rb-cat\">");

        RoutingEvaluator evaluator = new RoutingEvaluator();
        for (FormRow reg : regs) {
            String code        = nz(prop(reg, "code"), "");
            String name        = nz(prop(reg, "name"), code);
            String regDesc     = nz(prop(reg, "description"), "");
            String detCode     = nz(prop(reg, "applicabilityDeterminantId"), "");

            String statusLabel;
            String statusCss;
            String cardCss;
            String reason = "";
            boolean clickable = true;

            if (detCode.isEmpty()) {
                // No rule configured → assume open programme.
                statusLabel = "Open";
                statusCss   = "rb-status-ok";
                cardCss     = "rb-applicable";
            } else {
                EvalContext ctx = EvalContext.builder().data(applicantData).build();
                EvalResult res;
                try {
                    res = evaluator.evaluate(detCode, ctx);
                } catch (Throwable t) {
                    res = EvalResult.error("eval_threw:" + t.getClass().getSimpleName(), "");
                }
                EvalResult.Outcome o = (res != null) ? res.outcome : null;
                if (o == EvalResult.Outcome.TRUE) {
                    statusLabel = "Applicable";
                    statusCss   = "rb-status-ok";
                    cardCss     = "rb-applicable";
                } else if (o == EvalResult.Outcome.FALSE) {
                    statusLabel = "Not applicable";
                    statusCss   = "rb-status-no";
                    cardCss     = "rb-ineligible";
                    clickable   = false;
                    // Use the rule's failMessage if present.
                    try {
                        FormRow det = dao.findDeterminantByCode(detCode);
                        if (det != null) {
                            reason = nz(prop(det, "failMessage"), "");
                        }
                    } catch (Throwable ignore) {}
                    if (reason.isEmpty()) reason = "This programme does not match your profile.";
                } else if (o == EvalResult.Outcome.NULL) {
                    statusLabel = "Need more information";
                    statusCss   = "rb-status-pend";
                    cardCss     = "rb-pending";
                    reason = "Some required information has not been provided yet — fill in the next tabs to evaluate.";
                } else {
                    statusLabel = "Could not evaluate";
                    statusCss   = "rb-status-err";
                    cardCss     = "rb-error";
                    reason = (res != null && res.errorCause != null) ? res.errorCause : "Unknown evaluator error.";
                }
            }

            boolean isPicked = !currentPick.isEmpty() && currentPick.equalsIgnoreCase(code);
            html.append("<div class=\"rb-card ").append(cardCss);
            if (isPicked) html.append(" rb-pick");
            html.append("\"");
            if (clickable) {
                html.append(" data-programme=\"").append(htmlEscape(code)).append("\"")
                    .append(" onclick=\"rbCatPick(this,'").append(htmlEscape(code)).append("')\"");
            } else {
                html.append(" title=\"").append(htmlEscape(reason)).append("\"");
            }
            html.append(">");

            html.append("<div class=\"rb-row\">")
                .append("<span class=\"rb-name\">").append(htmlEscape(name)).append("</span>")
                .append("<span class=\"rb-code\">").append(htmlEscape(code)).append("</span>")
                .append("<span class=\"rb-status ").append(statusCss).append("\">")
                .append(htmlEscape(statusLabel))
                .append("</span></div>");
            if (!regDesc.isEmpty()) {
                html.append("<div class=\"rb-desc\">").append(htmlEscape(regDesc)).append("</div>");
            }
            if (!reason.isEmpty()) {
                html.append("<div class=\"rb-reason\">").append(htmlEscape(reason)).append("</div>");
            }
            html.append("</div>");
        }
        html.append("</div>");  // rb-cat

        // L2-2-bis — live in-flight catalogue evaluation.
        //
        // The server-side renderCatalogue runs against an empty
        // applicantData in ?_mode=add (MultiPagedForm caches in-flight
        // values in its own state, not in the load binder). The fix is JS
        // that gathers the wizard's current form values from the DOM and
        // POSTs them to /catalogue/eval, then rewrites the card states in
        // place.
        //
        // Trigger points:
        //   1. On DOMContentLoaded — covers the case where the citizen
        //      navigates back to the catalogue after entering data on
        //      later tabs.
        //   2. On focusout of any wizard input — keeps the catalogue in
        //      sync as the citizen types (debounced ~300ms).
        String apiId       = "API-168e3678-1f9a-46fc-8c19-d0d9a917eb73";
        String apiKey      = "a5af1181f77b4a62b481725b6410e965";
        html.append("<script>")
            .append("(function(){")
            .append("var SERVICE='").append(htmlEscape(serviceCode)).append("';")
            .append("var API_ID='").append(htmlEscape(apiId)).append("';")
            .append("var API_KEY='").append(htmlEscape(apiKey)).append("';")
            // Card click → set hidden field + visual pick. Skips ineligible.
            .append("window.rbCatPick=function(card,code){")
            .append("  if(card.classList.contains('rb-ineligible'))return;")
            .append("  var hf=document.getElementById('applied_programme');")
            .append("  if(hf){hf.value=code;}")
            .append("  var cards=document.querySelectorAll('.rb-card');")
            .append("  for(var i=0;i<cards.length;i++){cards[i].classList.remove('rb-pick');}")
            .append("  card.classList.add('rb-pick');")
            .append("};")
            // Gather every named input/select/textarea from the page (the
            // wizard form), build applicantData object.
            //
            // L4-1 D-001 client-side fix: a <select> with no <option selected>
            // attribute defaults to its FIRST option. So an untouched
            // agro_zone select reads value="lowlands" even though the user
            // hasn't picked anything — and that phantom value pollutes the
            // /catalogue/eval call. Same trap for default-checked radios.
            // Discriminate three states per control:
            //   * User changed it (tracked via dataset.userSet on change)
            //   * Server pre-populated it (option[selected] in HTML, OR
            //     for radios: the checked one matches data-default)
            //   * Untouched — drop, don't include in applicantData.
            // Text/date/textarea inputs don't have this problem (no default
            // value beyond the empty string), so they're included whenever
            // their .value is non-empty as before.
            .append("function rbCatGatherData(){")
            .append("  var data={};")
            .append("  var els=document.querySelectorAll('input[name],select[name],textarea[name]');")
            .append("  for(var i=0;i<els.length;i++){")
            .append("    var el=els[i];")
            .append("    if(!el.name||el.name.charAt(0)=='_')continue;") // skip _csrf etc.
            .append("    if(el.tagName==='SELECT'){")
            .append("      var userSet=el.dataset.userSet==='true';")
            .append("      var serverSelected=!!el.querySelector('option[selected]');")
            .append("      if(!userSet&&!serverSelected)continue;")
            .append("      if(el.value)data[el.name]=el.value;")
            .append("      continue;")
            .append("    }")
            .append("    if(el.type==='radio'||el.type==='checkbox'){")
            .append("      if(!el.checked)continue;")
            // Default-checked vs user-checked: defaultChecked is the HTML
            // attribute (server-pre-populated), checked is the live state.
            // If both are true, server set it. If checked && !defaultChecked,
            // user set it. Either is "intentional" — include the value.
            // If neither, the radio isn't checked at all and we already
            // skipped above. Untouched in add-mode (no default-checked) →
            // !checked → skipped. Good.
            .append("      data[el.name]=el.value;")
            .append("      continue;")
            .append("    }")
            // input text/date/hidden, textarea
            .append("    var v=el.value;")
            .append("    if(v!==undefined&&v!==null&&v!==''){data[el.name]=v;}")
            .append("  }")
            .append("  return data;")
            .append("}")
            // Mark a select as user-set when it changes; rbCatGatherData uses
            // that flag to distinguish "user picked the first option" from
            // "select defaulted to the first option because nothing's set".
            .append("document.addEventListener('change',function(e){")
            .append("  if(e.target&&e.target.tagName==='SELECT')e.target.dataset.userSet='true';")
            .append("},true);")
            // Update one card's visual state based on outcome.
            .append("function rbCatApply(code,outcome,reason){")
            .append("  var card=document.querySelector('.rb-card[data-programme=\"'+code+'\"]');")
            .append("  if(!card)return;")
            .append("  card.classList.remove('rb-applicable','rb-ineligible','rb-pending','rb-error');")
            .append("  var pillText='Need more information',pillCss='rb-status-pend',cardCss='rb-pending';")
            .append("  if(outcome=='TRUE'||outcome=='OPEN'){pillText=outcome=='OPEN'?'Open':'Applicable';pillCss='rb-status-ok';cardCss='rb-applicable';}")
            .append("  else if(outcome=='FALSE'){pillText='Not applicable';pillCss='rb-status-no';cardCss='rb-ineligible';}")
            .append("  else if(outcome=='ERROR'){pillText='Could not evaluate';pillCss='rb-status-err';cardCss='rb-error';}")
            .append("  card.classList.add(cardCss);")
            .append("  var pill=card.querySelector('.rb-status');")
            .append("  if(pill){pill.className='rb-status '+pillCss;pill.textContent=pillText;}")
            .append("  var rsn=card.querySelector('.rb-reason');")
            .append("  if(rsn){rsn.remove();}")
            .append("  if(reason){")
            .append("    var d=document.createElement('div');d.className='rb-reason';d.textContent=reason;")
            .append("    card.appendChild(d);")
            .append("  }")
            // Block clicks on ineligible.
            .append("  if(cardCss=='rb-ineligible'){card.removeAttribute('onclick');card.title=reason||'';}")
            .append("  else{card.setAttribute('onclick',\"rbCatPick(this,'\"+code+\"')\");card.removeAttribute('title');}")
            .append("}")
            // POST current form values, apply each result.
            .append("function rbCatRefresh(){")
            .append("  var body=JSON.stringify({serviceCode:SERVICE,applicantData:rbCatGatherData()});")
            .append("  fetch('/jw/api/regbb/catalogue/eval',{")
            .append("    method:'POST',headers:{")
            .append("      'Content-Type':'application/json',")
            .append("      'api_id':API_ID,'api_key':API_KEY")
            .append("    },body:body")
            .append("  }).then(function(r){return r.json();}).then(function(env){")
            .append("    var msg=env.message?(typeof env.message=='string'?JSON.parse(env.message):env.message):env;")
            .append("    var results=msg.results||[];")
            .append("    for(var i=0;i<results.length;i++){")
            .append("      rbCatApply(results[i].code,results[i].outcome,results[i].failMessage||results[i].errorCause||'');")
            .append("    }")
            .append("  }).catch(function(e){console.error('[L2-2-bis] catalogue refresh failed',e);});")
            .append("}")
            // Debounce — multiple field changes in quick succession only fire
            // one /catalogue/eval call.
            .append("var rbDebounce=null;")
            .append("function rbCatRefreshDebounced(){clearTimeout(rbDebounce);rbDebounce=setTimeout(rbCatRefresh,300);}")
            // Initial fire on DOMContentLoaded; if already past that point,
            // fire immediately.
            .append("if(document.readyState=='loading'){document.addEventListener('DOMContentLoaded',rbCatRefresh);}else{setTimeout(rbCatRefresh,50);}")
            // Catch field changes anywhere on the page.
            .append("document.addEventListener('change',rbCatRefreshDebounced,true);")
            .append("})();")
            .append("</script>");

        html.append("</div>");  // form-cell
    }

    /**
     * Documents screens render an upload control per {@code mm_required_doc}
     * row scoped to the screen's service. Each upload is a synthesised Joget
     * {@code FileUpload} element so the file persists through the form's
     * standard storeBinder. When a Determinant evaluator lands (Week 3+),
     * this list filters per-registration based on the applicant's chosen
     * programme; for now all docs scoped to the service are listed.
     */
    private void renderDocuments(StringBuilder html, FormRow screen,
                                 MetaModelDao dao, boolean readonly,
                                 FormData formData) {
        // FileUpload children are pre-synthesised by synthesiseDocumentChildren
        // and live in getChildren(formData) — that's the path the form's
        // storeBinder walks to discover columns to persist. Render through
        // the standard pipeline so each upload widget gets its form-cell
        // wrapper, validator decoration, and error display.
        String appliedProgramme = readAppliedProgramme(formData);

        html.append("<div class=\"form-cell\"><p style=\"color:#555;\">")
            .append("Please upload the following documents. ");
        if (appliedProgramme.isEmpty()) {
            html.append("<b>No programme selected yet</b> — only service-level ")
                .append("documents are listed below; programme-specific documents ")
                .append("appear after a programme is picked on the <i>Applicant ")
                .append("Identity</i> tab.");
        } else {
            html.append("Documents for your selected programme (<code>")
                .append(htmlEscape(appliedProgramme)).append("</code>) are listed below.");
        }
        html.append("</p></div>");

        FormData fd = (formData != null) ? formData : new FormData();
        Collection<Element> children = getChildren(formData);
        boolean any = false;
        for (Element child : children) {
            if (readonly) child.setProperty(FormUtil.PROPERTY_READONLY, "true");
            html.append(child.render(fd, false));
            any = true;
        }
        if (!any) {
            html.append("<div class=\"form-cell\"><em style=\"color:#888;\">")
                .append("No documents required for this application.</em></div>");
        }
    }

    /**
     * Review screens render a confirm-and-submit affordance. A future
     * iteration walks the application data and shows a per-section summary;
     * for now we render a simple instruction so the citizen knows what to
     * do next. The actual Save/Submit buttons are part of the surrounding
     * Joget CRUD wrapper, not this element.
     */
    private void renderReview(StringBuilder html, FormRow screen, FormData formData) {
        // Eligibility outcome panel — only shown when the citizen is
        // viewing a previously submitted application (i.e. there's a
        // primary key on formData and the engine has persisted a verdict).
        // For a fresh application before save, primaryKeyValue is empty
        // and EligibilitySummaryRenderer.render returns the "not yet
        // evaluated" message — but we'd rather not display that empty
        // panel here at all, so suppress when no PK is available.
        String pk = "";
        try { if (formData != null) pk = nz(formData.getPrimaryKeyValue(), ""); }
        catch (Throwable ignore) {}
        if (!pk.isEmpty()) {
            html.append("<div class=\"form-cell\">")
                .append(EligibilitySummaryRenderer.renderForForm(pk))
                .append("</div>");
        }

        String description = nz(screen.getProperty("description"), "");
        html.append("<div class=\"form-cell\">")
            .append("<div style=\"background:#fff8e1;border-left:4px solid #c2a000;")
            .append("padding:1em 1.2em;border-radius:4px;margin:0.5em 0;\">")
            .append("<h3 style=\"margin:0 0 0.5em 0;color:#7a5d00;\">Review your application</h3>")
            .append("<p style=\"color:#444;line-height:1.5;\">")
            .append("Please review every section above carefully. Once you click ")
            .append("<b>Save</b> below, your application is submitted to the ")
            .append("Ministry of Agriculture and Food Security and you cannot ")
            .append("change it without contacting your local extension officer.")
            .append("</p>");
        if (!description.isEmpty()) {
            html.append("<p style=\"color:#666;font-size:0.9em;margin-top:0.8em;\">")
                .append(htmlEscape(description)).append("</p>");
        }
        html.append("</div></div>");
    }

    /**
     * Read the applicant's selected programme code from the active form
     * lifecycle. Tries (1) live request parameters (the user just submitted
     * the Identity tab), (2) the form's load-binder data (Edit mode — value
     * stored from a previous save). Returns empty string if neither yields a
     * value (typical for a fresh Add-mode application before the Identity
     * tab is filled).
     *
     * <p>The field is named {@code applied_programme} per the YAML fixture;
     * if the fixture's storageKey changes, this method needs the same edit.
     */
    private String readAppliedProgramme(FormData formData) {
        if (formData == null) return "";
        try {
            String[] params = formData.getRequestParameterValues("applied_programme");
            if (params != null && params.length > 0 && params[0] != null && !params[0].isEmpty()) {
                return params[0];
            }
        } catch (Throwable ignore) { /* no live request — fall through */ }
        try {
            org.joget.apps.form.model.Form root = FormUtil.findRootForm(this);
            if (root != null) {
                String v = formData.getLoadBinderDataProperty(root, "applied_programme");
                if (v != null && !v.isEmpty()) return v;
            }
        } catch (Throwable ignore) {}
        return "";
    }

    /**
     * L3-1 3a — applied_programme reader specifically for the budget_hint
     * widget. Adds a primary-key fallback on top of {@link #readAppliedProgramme}:
     * when the form's load binder is {@code WorkflowFormBinder} (operator
     * review form), {@code getLoadBinderDataProperty} typically returns null
     * for plain form columns, so the standard reader yields empty string and
     * the panel degrades to "no programme". Workaround: if we have the
     * application's primary key, query {@code app_fd_subsidy_app_2025} for
     * {@code c_applied_programme} directly. Read-only, single SELECT,
     * HARD-RULE-compliant.
     */
    private String readAppliedProgrammeForBudgetHint(FormData formData) {
        String v = readAppliedProgramme(formData);
        if (v != null && !v.isEmpty()) return v;
        if (formData == null) return "";
        // Primary-key fallback. The CRUD URL carries ?id=... which Joget
        // surfaces via formData.getPrimaryKeyValue() once the load binder
        // (any kind) has run.
        String pk = null;
        try { pk = formData.getPrimaryKeyValue(); } catch (Throwable ignore) {}
        if (pk == null || pk.isEmpty()) return "";
        String sql = "SELECT c_applied_programme FROM app_fd_subsidy_app_2025 WHERE id = ?";
        try {
            javax.sql.DataSource ds = (javax.sql.DataSource) org.joget.apps.app.service.AppUtil
                    .getApplicationContext().getBean("setupDataSource");
            try (java.sql.Connection c = ds.getConnection();
                 java.sql.PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, pk);
                try (java.sql.ResultSet rs = p.executeQuery()) {
                    if (rs.next()) {
                        String s = rs.getString(1);
                        if (s != null && !s.isEmpty()) return s;
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            LogUtil.warn(CLASS_NAME, "readAppliedProgrammeForBudgetHint pk-lookup failed: "
                    + e.getSQLState() + ":" + e.getMessage());
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "readAppliedProgrammeForBudgetHint pk-lookup failed: "
                    + t.getClass().getSimpleName() + ":" + t.getMessage());
        }
        return "";
    }

    /**
     * §6.4 Conditional UI — collect every field's current value from the live
     * request (in-flight typing on a wizard tab transition) plus the load
     * binder (values from earlier tabs already saved). Resulting map is the
     * {@code EvalContext.data} payload for visibility/requiredness Determinants.
     *
     * <p>Reads request param first (covers the most-recent values typed on
     * this tab, which haven't been persisted yet); falls back to load-binder
     * data (covers values from earlier saves).
     */
    private Map<String, Object> readApplicantContext(FormData formData, List<FormRow> fields) {
        Map<String, Object> data = new HashMap<>();
        if (formData == null || fields == null || fields.isEmpty()) return data;
        org.joget.apps.form.model.Form root = null;
        try { root = FormUtil.findRootForm(this); } catch (Throwable ignore) {}

        for (FormRow f : fields) {
            String key = nz(prop(f, "storageKey"), "");
            if (key.isEmpty() || data.containsKey(key)) continue;
            String v = readField(formData, root, key);
            if (v != null && !v.isEmpty()) data.put(key, v);
        }
        // Always include applied_programme — many Determinants reference it
        // implicitly even when it isn't on the current screen.
        if (!data.containsKey("applied_programme")) {
            String prog = readAppliedProgramme(formData);
            if (!prog.isEmpty()) data.put("applied_programme", prog);
        }
        return data;
    }

    /** Single-field reader: request param first, then load-binder data. */
    private String readField(FormData formData,
                             org.joget.apps.form.model.Form root,
                             String key) {
        try {
            String[] params = formData.getRequestParameterValues(key);
            if (params != null && params.length > 0 && params[0] != null && !params[0].isEmpty()) {
                return params[0];
            }
        } catch (Throwable ignore) {}
        if (root != null) {
            try {
                String v = formData.getLoadBinderDataProperty(root, key);
                if (v != null && !v.isEmpty()) return v;
            } catch (Throwable ignore) {}
        }
        return null;
    }

    /** Evaluate a Determinant; absorb any failure as {@link EvalResult.Outcome#ERROR}.
     *  Used by §6.4 Conditional UI where one bad rule must not blow up the whole
     *  page render. */
    private EvalResult.Outcome evaluateOutcome(RoutingEvaluator evaluator,
                                               String determinantCode,
                                               EvalContext ctx) {
        try {
            EvalResult r = evaluator.evaluate(determinantCode, ctx);
            return r == null ? EvalResult.Outcome.ERROR : r.outcome;
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME,
                    "§6.4 conditional eval failed for det=" + determinantCode + ": " + t.getMessage());
            return EvalResult.Outcome.ERROR;
        }
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private static String nz(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    /**
     * Case-tolerant FormRow property read. Joget's FormRow stores keys at the
     * casing produced by its persistence layer — on Postgres, unquoted column
     * identifiers fold to lowercase, so camelCase form-field ids like
     * {@code registrationId} land in FormRow under the lowercase key
     * {@code registrationid}. Calling {@code row.getProperty("registrationId")}
     * silently returns {@code null}, which misroutes the documents-tab filter
     * (programme-specific docs disappear). This helper tries the canonical key
     * first, then falls back to lowercase. Use for every camelCase property
     * read off an {@code mm_*} row: {@code registrationId}, {@code acceptedTypes},
     * {@code maxSizeBytes}, {@code helpText}, {@code serviceId}, {@code storageKey},
     * {@code optionsCatalogId}, {@code optionsFilterField}, etc.
     */
    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return (v != null) ? v.toString() : null;
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String htmlAttr(String s) {
        return htmlEscape(s);
    }

    private static String errorBox(String msg) {
        return "<div class=\"form-cell\">"
             + "<div style=\"padding:0.8em;background:#fde0e0;border:1px solid #c00;color:#700;\">"
             + "<strong>Meta Screen error:</strong> " + msg + "</div>"
             + "</div>";
    }
}
