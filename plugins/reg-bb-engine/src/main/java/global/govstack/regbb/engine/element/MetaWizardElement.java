package global.govstack.regbb.engine.element;

import global.govstack.regbb.engine.Build;
import global.govstack.regbb.engine.dao.MetaModelDao;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormContainer;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Citizen-facing application wizard rendered from a sequence of {@code mm_screen}
 * rows under one service. Per D24 (Lesotho extension to RegBB §6.3.2.1, see
 * convergence-framework.md §9.4).
 *
 * <p>Drop one widget on a Joget form, point it at a {@code serviceId} (or pass
 * an explicit semicolon-separated {@code screenIds}), and the engine queries
 * the matching {@code mm_screen} rows in {@code orderIndex} order and renders
 * them as tabs with a breadcrumb step indicator and Next / Previous navigation.
 * All children persist into the same form table — no fan-out across N tab
 * subforms (the {@code MultiPagedForm} pattern's data shape).
 *
 * <p>Each wizard tab is rendered by delegating to a synthesised
 * {@link MetaScreenElement} — so the per-kind dispatch (guide / form /
 * documents / review) honoured by that element applies uniformly inside the
 * wizard.
 *
 * <p>Operator parity: when {@code readonly=Y}, every tab is shown read-only
 * EXCEPT tabs whose screen kind is {@code form} (typical operator-decision
 * pattern: applicant data shown read-only, decision tab editable). A future
 * iteration takes its read-only mask from the matching {@code mm_role_screen}
 * row's {@code sectionsJson} so the operator side is fully metadata-driven
 * (D17 / D24 coupling).
 */
public class MetaWizardElement extends Element implements FormBuilderPaletteElement, FormContainer {

    private static final String CLASS_NAME = MetaWizardElement.class.getName();
    private static final String FORM_BUILDER_CATEGORY = "GovStack RegBB";

    /** Cached per-FormData synthesis of child Meta Screens, mirroring the
     *  pattern used in MetaScreenElement. */
    private final Map<FormData, Collection<Element>> childrenCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override public String getName()                { return "Meta Wizard Element"; }
    @Override public String getVersion()             { return Build.STAMP; }
    @Override public String getDescription()         { return "Renders an mm_screen sequence as a tabbed wizard. " + Build.STAMP; }
    @Override public String getLabel()               { return "Meta Wizard"; }
    @Override public String getClassName()           { return getClass().getName(); }
    @Override public String getFormBuilderCategory() { return FORM_BUILDER_CATEGORY; }
    @Override public int    getFormBuilderPosition() { return 110; }
    @Override public String getFormBuilderIcon()     { return "<i class=\"fas fa-clipboard-list\"></i>"; }

    @Override
    public String getFormBuilderTemplate() {
        return "<div style=\"background:#eef5ff;border-left:4px solid #2c5fa3;"
             + "padding:8px 12px;border-radius:4px;font-size:12px;color:#1a3a66;"
             + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
             + "<b>Meta Wizard</b> &nbsp;·&nbsp; renders mm_screen sequence as tabs</div>";
    }

    @Override
    public String getPropertyOptions() {
        // Property panel: pick a service whose screens become the wizard tabs,
        // OR override with a semicolon-separated screen-code list for ad-hoc
        // wizards (e.g. operator review using a custom subset of citizen
        // screens + an OP_DECISION tab).
        StringBuilder serviceField = new StringBuilder();
        try {
            org.json.JSONArray opts = new org.json.JSONArray();
            org.json.JSONObject empty = new org.json.JSONObject();
            empty.put("value", "");
            empty.put("label", "(select a service)");
            opts.put(empty);

            org.joget.apps.form.dao.FormDataDao fdd =
                (org.joget.apps.form.dao.FormDataDao)
                    org.joget.apps.app.service.AppUtil.getApplicationContext().getBean("formDataDao");
            org.joget.apps.form.model.FormRowSet rows = fdd.find(
                "mm_service", "mm_service", null, null, "name", false, null, null);
            if (rows != null) {
                for (FormRow r : rows) {
                    String code = nz(r.getProperty("code"), "");
                    String name = nz(r.getProperty("name"), code);
                    if (code.isEmpty()) continue;
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("value", code);
                    o.put("label", name + " (" + code + ")");
                    opts.put(o);
                }
            }
            serviceField.append("{\"name\":\"serviceId\",\"label\":\"Service (mm_service)\",")
                .append("\"description\":\"All mm_screen rows under this service become wizard tabs in orderIndex order.\",")
                .append("\"type\":\"selectbox\",\"options\":").append(opts.toString()).append("}");
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "Could not build service options for property panel: " + t.getMessage());
            serviceField.append("{\"name\":\"serviceId\",\"label\":\"Service (mm_service code)\",")
                .append("\"type\":\"textfield\"}");
        }

        // Build the role-screen dropdown (for fully-metadata-driven operator
        // wizards: pick a mm_role_screen row, inherit screen list + per-tab
        // readonly mask from its sectionsJson).
        StringBuilder roleScreenField = new StringBuilder();
        try {
            org.json.JSONArray opts = new org.json.JSONArray();
            org.json.JSONObject empty = new org.json.JSONObject();
            empty.put("value", "");
            empty.put("label", "(none — use service / screenIds instead)");
            opts.put(empty);

            org.joget.apps.form.dao.FormDataDao fdd =
                (org.joget.apps.form.dao.FormDataDao)
                    org.joget.apps.app.service.AppUtil.getApplicationContext().getBean("formDataDao");
            org.joget.apps.form.model.FormRowSet rows = fdd.find(
                "mm_role_screen", "mm_role_screen", null, null, "name", false, null, null);
            if (rows != null) {
                for (FormRow r : rows) {
                    String code = nz(r.getProperty("code"), "");
                    String name = nz(r.getProperty("name"), code);
                    if (code.isEmpty()) continue;
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("value", code);
                    o.put("label", name + " (" + code + ")");
                    opts.put(o);
                }
            }
            roleScreenField.append("{\"name\":\"roleScreenId\",\"label\":\"Role Screen (mm_role_screen)\",")
                .append("\"description\":\"Operator wizards: pick the role-screen row whose sectionsJson defines which mm_screens render and which are read-only. Overrides serviceId / screenIds / readOnlyScreens when set.\",")
                .append("\"type\":\"selectbox\",\"options\":").append(opts.toString()).append("}");
        } catch (Throwable t) {
            roleScreenField.append("{\"name\":\"roleScreenId\",\"label\":\"Role Screen (mm_role_screen code)\",")
                .append("\"type\":\"textfield\"}");
        }

        return "[{"
            + "\"title\":\"Meta Wizard\","
            + "\"properties\":["
            +     roleScreenField.toString() + ","
            +     serviceField.toString() + ","
            + "  {\"name\":\"screenIds\",\"label\":\"Screens (override; semicolon-separated codes)\","
            + "   \"description\":\"Optional. When set, overrides the service-derived screen list. Ignored when roleScreenId is set.\","
            + "   \"type\":\"textfield\"},"
            + "  {\"name\":\"readonly\",\"label\":\"All tabs read-only (operator inspection per D17)\","
            + "   \"type\":\"selectbox\",\"value\":\"N\","
            + "   \"options\":[{\"value\":\"Y\",\"label\":\"Yes\"},{\"value\":\"N\",\"label\":\"No\"}]},"
            + "  {\"name\":\"readOnlyScreens\",\"label\":\"Per-tab read-only override (semicolon-separated screen codes)\","
            + "   \"description\":\"Tabs listed here render read-only. Ignored when roleScreenId is set (sectionsJson modes are used instead).\","
            + "   \"type\":\"textfield\"}"
            + "]"
            + "}]";
    }

    // ---------------------------------------------------------------------
    //  FormContainer hook — children are synthesised MetaScreenElements,
    //  one per wizard tab.
    // ---------------------------------------------------------------------

    @Override
    public Collection<Element> getChildren(FormData formData) {
        if (formData == null) {
            return synthesiseChildren();
        }
        Collection<Element> cached = childrenCache.get(formData);
        if (cached != null) return cached;
        Collection<Element> fresh = synthesiseChildren();
        childrenCache.put(formData, fresh);
        return fresh;
    }

    @Override
    public Collection<Element> getChildren() {
        return synthesiseChildren();
    }

    private Collection<Element> synthesiseChildren() {
        List<Element> out = new ArrayList<>();
        ResolvedScreens resolved = resolveScreensWithModes();
        boolean wizardReadonly = "Y".equalsIgnoreCase(getPropertyString("readonly"));

        for (FormRow s : resolved.screens) {
            String code = nz(s.getProperty("code"), "");
            if (code.isEmpty()) continue;
            // Per-tab readonly: tab is read-only if any of:
            //   1. wizard's overall 'readonly' = Y (operator inspection mode), OR
            //   2. this code is in resolved.readOnlyCodes — derived from
            //      mm_role_screen.sectionsJson when roleScreenId is set, or
            //      from the readOnlyScreens override otherwise.
            boolean tabReadonly = wizardReadonly || resolved.readOnlyCodes.contains(code);
            try {
                MetaScreenElement child = new MetaScreenElement();
                child.setProperty("screenId", code);
                child.setProperty("readonly", tabReadonly ? "Y" : "N");
                child.setParent(this);
                out.add(child);
            } catch (Throwable e) {
                LogUtil.warn(CLASS_NAME, "Could not synthesise MetaScreenElement for code=" + code + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static Set<String> parseScreenCodeSet(String semicolonList) {
        Set<String> out = new HashSet<>();
        if (semicolonList == null || semicolonList.isEmpty()) return out;
        for (String c : semicolonList.split(";")) {
            String trimmed = c.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** Pair of (screen list, per-tab readonly mask) — what the wizard needs to
     *  render. Resolved from one of three configuration modes (in order of
     *  precedence): {@code roleScreenId} → {@code screenIds} → {@code serviceId}. */
    private static final class ResolvedScreens {
        final List<FormRow> screens;
        final Set<String>   readOnlyCodes;
        ResolvedScreens(List<FormRow> screens, Set<String> readOnlyCodes) {
            this.screens       = screens;
            this.readOnlyCodes = readOnlyCodes;
        }
    }

    /**
     * Resolve the list of screen rows + per-tab readonly mask. Three modes,
     * in order of precedence:
     *
     * <ol>
     *   <li><b>roleScreenId</b> (most metadata-driven, D24 target). Look up
     *       the {@code mm_role_screen} row, parse its {@code sectionsJson}
     *       (an array of {@code {screen, mode}} objects). Each section's
     *       {@code mode} controls per-tab readonly: {@code readonly} →
     *       read-only, anything else (typically {@code editable}) →
     *       editable. Order of tabs follows sectionsJson order.</li>
     *   <li><b>screenIds</b> (explicit). Semicolon-separated screen codes;
     *       readonly mask comes from the {@code readOnlyScreens} override
     *       property.</li>
     *   <li><b>serviceId</b> (citizen-side default). Walk every
     *       {@code mm_screen} for the service in {@code orderIndex} order;
     *       readonly mask from {@code readOnlyScreens} override.</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private ResolvedScreens resolveScreensWithModes() {
        MetaModelDao dao = new MetaModelDao();

        // Mode 1: roleScreenId — fully metadata-driven.
        String roleScreenCode = nz(getPropertyString("roleScreenId"), "");
        if (!roleScreenCode.isEmpty()) {
            FormRow rs = dao.findRoleScreenByCode(roleScreenCode);
            if (rs == null) {
                LogUtil.warn(CLASS_NAME, "roleScreenId set but mm_role_screen not found for code=" + roleScreenCode);
                return new ResolvedScreens(Collections.<FormRow>emptyList(), Collections.<String>emptySet());
            }
            String sectionsJson = nz(rs.getProperty("sectionsJson"), "");
            if (sectionsJson.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "mm_role_screen " + roleScreenCode + " has empty sectionsJson");
                return new ResolvedScreens(Collections.<FormRow>emptyList(), Collections.<String>emptySet());
            }
            try {
                JSONObject root = new JSONObject(sectionsJson);
                JSONArray sections = root.optJSONArray("sections");
                if (sections == null) {
                    LogUtil.warn(CLASS_NAME, "sectionsJson missing 'sections' array in " + roleScreenCode);
                    return new ResolvedScreens(Collections.<FormRow>emptyList(), Collections.<String>emptySet());
                }
                List<FormRow> picked   = new ArrayList<>();
                Set<String>   readOnly = new LinkedHashSet<>();
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject sec = sections.getJSONObject(i);
                    String screenCode = nz(sec.optString("screen"), "");
                    String mode       = nz(sec.optString("mode"), "editable");
                    if (screenCode.isEmpty()) continue;
                    FormRow s = dao.findScreenByCode(screenCode);
                    if (s == null) {
                        LogUtil.warn(CLASS_NAME, "sectionsJson references missing screen code=" + screenCode + " in " + roleScreenCode);
                        continue;
                    }
                    picked.add(s);
                    if ("readonly".equalsIgnoreCase(mode)) {
                        readOnly.add(screenCode);
                    }
                }
                return new ResolvedScreens(picked, readOnly);
            } catch (Throwable e) {
                LogUtil.error(CLASS_NAME, e, "Failed to parse sectionsJson for roleScreenId=" + roleScreenCode);
                return new ResolvedScreens(Collections.<FormRow>emptyList(), Collections.<String>emptySet());
            }
        }

        // Mode 2: explicit screenIds list.
        String override = nz(getPropertyString("screenIds"), "");
        Set<String> readOnly = parseScreenCodeSet(getPropertyString("readOnlyScreens"));
        if (!override.isEmpty()) {
            List<FormRow> picked = new ArrayList<>();
            for (String code : override.split(";")) {
                code = code.trim();
                if (code.isEmpty()) continue;
                FormRow r = dao.findScreenByCode(code);
                if (r != null) picked.add(r);
                else LogUtil.warn(CLASS_NAME, "screenIds override: mm_screen not found for code=" + code);
            }
            return new ResolvedScreens(picked, readOnly);
        }

        // Mode 3: derive from service. Filter by audience — citizen wizards
        // pull only screens whose audience is `citizen` or `both`. Operator
        // wizards never use Mode 3 (they use roleScreenId), so this filter
        // is unconditional here; if a future use case needs unfiltered access,
        // add an explicit `includeAllAudiences` property.
        String serviceCode = nz(getPropertyString("serviceId"), "");
        if (serviceCode.isEmpty()) return new ResolvedScreens(Collections.<FormRow>emptyList(), readOnly);

        org.joget.apps.form.dao.FormDataDao fdd =
            (org.joget.apps.form.dao.FormDataDao)
                org.joget.apps.app.service.AppUtil.getApplicationContext().getBean("formDataDao");
        org.joget.apps.form.model.FormRowSet rows = fdd.find(
            "mm_screen", "mm_screen",
            "WHERE e.customProperties.serviceId = ?",
            new Object[] { serviceCode },
            "orderIndex", false, null, null);
        if (rows == null || rows.isEmpty()) return new ResolvedScreens(Collections.<FormRow>emptyList(), readOnly);

        // Apply audience filter post-fetch — Joget's HQL doesn't easily
        // support OR clauses across customProperties.
        List<FormRow> filtered = new ArrayList<>();
        for (FormRow r : rows) {
            String audience = nz(r.getProperty("audience"), "citizen");  // default citizen for back-compat
            if ("citizen".equalsIgnoreCase(audience) || "both".equalsIgnoreCase(audience)) {
                filtered.add(r);
            }
        }
        return new ResolvedScreens(filtered, readOnly);
    }

    /** Convenience wrapper preserved for {@link #renderTemplate} which only
     *  needs the screen list. */
    private List<FormRow> resolveScreens() {
        return resolveScreensWithModes().screens;
    }

    // ---------------------------------------------------------------------
    //  Rendering — tab bar + tab content + client-side switcher.
    // ---------------------------------------------------------------------

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public String renderTemplate(FormData formData, Map dataModel) {
        // L4-5 — open per-render caches: DAO (mm_* lookups) and evaluator
        // L1 (determinant evaluations). Both ref-counted so nested
        // MetaScreenElement renders within this wizard share one scope.
        // Closed in finally so a thrown exception still clears.
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
        String roleScreenCode = nz(getPropertyString("roleScreenId"), "");
        String serviceCode    = nz(getPropertyString("serviceId"), "");
        String overrideCodes  = nz(getPropertyString("screenIds"), "");
        boolean readonly = "Y".equalsIgnoreCase(getPropertyString("readonly"));

        if (inBuilder) {
            String configLabel;
            if (!roleScreenCode.isEmpty())   configLabel = "[role-screen: " + htmlEscape(roleScreenCode) + "]";
            else if (!overrideCodes.isEmpty()) configLabel = "[explicit: " + htmlEscape(overrideCodes) + "]";
            else if (!serviceCode.isEmpty()) configLabel = htmlEscape(serviceCode);
            else                              configLabel = "(unset)";

            return "<div class=\"form-cell\" " + meta + ">"
                 + "<div style=\"background:#eef5ff;border-left:4px solid #2c5fa3;"
                 + "padding:8px 12px;border-radius:4px;font-size:12px;color:#1a3a66;"
                 + "pointer-events:none;\">"
                 + "<b>Meta Wizard</b> &nbsp;·&nbsp; config: <code>"
                 + configLabel
                 + "</code> &nbsp;·&nbsp; readonly: <code>" + (readonly ? "Y" : "N") + "</code>"
                 + "</div></div>";
        }

        // Three valid config modes per resolveScreensWithModes; only error
        // if none is set.
        if (roleScreenCode.isEmpty() && serviceCode.isEmpty() && overrideCodes.isEmpty()) {
            return errorBox("Meta Wizard has no role-screen, service, or screen list configured.");
        }

        try {
            List<FormRow> screens = resolveScreens();
            if (screens.isEmpty()) {
                String reason;
                if (!roleScreenCode.isEmpty()) {
                    reason = "Role-screen " + htmlEscape(roleScreenCode) + " has no resolvable sectionsJson screens.";
                } else if (!overrideCodes.isEmpty()) {
                    reason = "None of the override codes resolved: " + htmlEscape(overrideCodes);
                } else {
                    reason = "Service " + htmlEscape(serviceCode) + " has no screens.";
                }
                return errorBox("Meta Wizard: no mm_screen rows resolved. " + reason);
            }

            // Unique DOM id per wizard instance so multiple wizards on a page
            // don't collide. Joget can render the same form twice in some
            // dashboard contexts; the JS scopes everything by this id.
            String wizardDomId = "regbbWizard_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            StringBuilder html = new StringBuilder();
            html.append("<!-- regbb-meta-wizard service=").append(serviceCode)
                .append(" screens=").append(screens.size())
                .append(" engine=").append(Build.STAMP)
                .append(readonly ? " readonly=Y" : "")
                .append(" -->");

            html.append(emitWizardCss());
            html.append("<div id=\"").append(wizardDomId).append("\" class=\"regbb-wizard\">");

            // Identity header — sticky banner showing which applicant the
            // wizard is bound to. Emitted for both operator-mode AND citizen-
            // mode-edit (so a citizen returning to a saved draft sees their
            // own name on the Welcome guide step instead of an unidentifiable
            // page). Self-skips on _mode=add: the load binder returns nothing
            // for a fresh row, the helper returns "", no header rendered.
            // Values come from the load binder so every step shows the
            // identifier — including guide / review screens that have no
            // data fields of their own.
            html.append(emitIdentityHeader(formData));

            // Tab bar / breadcrumb.
            html.append("<ol class=\"regbb-wizard-tabs\">");
            for (int i = 0; i < screens.size(); i++) {
                FormRow s = screens.get(i);
                String title = nz(s.getProperty("title"), nz(s.getProperty("code"), "Tab " + (i + 1)));
                html.append("<li class=\"regbb-wizard-tab-link")
                    .append(i == 0 ? " active" : "")
                    .append("\" data-step=\"").append(i).append("\">")
                    .append("<span class=\"regbb-step-num\">").append(i + 1).append("</span>")
                    .append("<span class=\"regbb-step-label\">").append(htmlEscape(title)).append("</span>")
                    .append("</li>");
            }
            html.append("</ol>");

            // Tab panels — one per child element synthesised in dependency order.
            html.append("<div class=\"regbb-wizard-panels\">");
            // Per-tab readonly was already applied during synthesiseChildren —
            // each child carries its own readonly setting. We do NOT force the
            // wizard's `readonly` flag onto every child here; that would break
            // the operator pattern (citizen tabs read-only, decision tab
            // editable).
            int i = 0;
            for (Element child : getChildren(formData)) {
                html.append("<div class=\"regbb-wizard-panel")
                    .append(i == 0 ? "" : " hidden")
                    .append("\" data-step=\"").append(i).append("\">");
                html.append(child.render(formData, false));
                html.append("</div>");
                i++;
            }
            html.append("</div>");

            // Footer nav.
            html.append("<div class=\"regbb-wizard-nav\">")
                .append("<button type=\"button\" class=\"regbb-prev\" disabled>&laquo; Previous</button>")
                .append("<span class=\"regbb-progress\">Step <b>1</b> of ").append(screens.size()).append("</span>")
                .append("<button type=\"button\" class=\"regbb-next\">Next &raquo;</button>")
                .append("</div>");

            html.append("</div>"); // .regbb-wizard
            html.append(emitWizardJs(wizardDomId, screens.size()));

            // §6.4 client-side toggle — for fields with a simple-equality
            // visibility determinant (e.g. `block_farming_member == 'yes'`),
            // emit JS that hides/shows the dependent field instantly when the
            // citizen changes the dependency, no save+reload required. Complex
            // rules (AND / OR / IN / numeric comparisons) are not handled
            // client-side — server-side §6.4 still enforces them on save.
            html.append(emitConditionalUiJs(wizardDomId, screens));

            return html.toString();
        } catch (RuntimeException e) {
            LogUtil.error(CLASS_NAME, e, "MetaWizardElement render failed for service=" + serviceCode);
            return errorBox("MetaWizardElement render failed: " + htmlEscape(e.getMessage()));
        }
    }

    // ---------------------------------------------------------------------
    //  CSS / JS for the client-side tab switcher
    // ---------------------------------------------------------------------

    private static String emitWizardCss() {
        return "<style>"
             + ".regbb-wizard{margin:0.5em 0;}"
             + ".regbb-wizard-tabs{list-style:none;padding:0;margin:0 0 1.2em 0;display:flex;flex-wrap:wrap;gap:0.4em;border-bottom:2px solid #e5e7eb;}"
             + ".regbb-wizard-tab-link{display:flex;align-items:center;gap:0.5em;padding:0.6em 1em;cursor:pointer;border-bottom:3px solid transparent;color:#666;font-size:0.95em;user-select:none;}"
             + ".regbb-wizard-tab-link.active{color:#1a3a66;border-bottom-color:#2c5fa3;font-weight:600;}"
             + ".regbb-wizard-tab-link:hover:not(.active){background:#f3f4f6;}"
             + ".regbb-step-num{display:inline-flex;align-items:center;justify-content:center;width:1.6em;height:1.6em;border-radius:50%;background:#e5e7eb;color:#666;font-size:0.85em;font-weight:600;}"
             + ".regbb-wizard-tab-link.active .regbb-step-num{background:#2c5fa3;color:#fff;}"
             + ".regbb-wizard-tab-link.completed .regbb-step-num{background:#2c7a7b;color:#fff;}"
             + ".regbb-wizard-panel.hidden{display:none;}"
             + ".regbb-wizard-nav{display:flex;justify-content:space-between;align-items:center;margin-top:1.5em;padding-top:1em;border-top:1px solid #e5e7eb;}"
             + ".regbb-wizard-nav button{padding:0.5em 1.2em;border:1px solid #2c5fa3;background:#fff;color:#2c5fa3;border-radius:4px;cursor:pointer;font-size:0.95em;}"
             + ".regbb-wizard-nav button:hover:not(:disabled){background:#2c5fa3;color:#fff;}"
             + ".regbb-wizard-nav button:disabled{opacity:0.4;cursor:not-allowed;}"
             + ".regbb-progress{color:#666;font-size:0.9em;}"
             // Operator identity header — sticky so it stays visible as the
             // operator scrolls through long tabs (Documents, Decision).
             + ".regbb-id-header{position:sticky;top:0;z-index:10;background:#1a3a66;color:#fff;border-radius:4px;padding:0.55em 1em;margin:0 0 1em 0;display:flex;flex-wrap:wrap;gap:0.4em 1.6em;align-items:baseline;box-shadow:0 1px 3px rgba(0,0,0,0.15);}"
             + ".regbb-id-header .regbb-id-label{font-size:0.75em;opacity:0.7;text-transform:uppercase;letter-spacing:0.04em;margin-right:0.3em;}"
             + ".regbb-id-header .regbb-id-value{font-weight:600;font-size:0.95em;}"
             + ".regbb-id-header .regbb-id-name{font-size:1.05em;font-weight:600;}"
             + ".regbb-id-header .regbb-id-prog code{background:rgba(255,255,255,0.18);color:#fff;padding:0.1em 0.5em;border-radius:3px;font-size:0.85em;}"
             + "</style>";
    }

    /**
     * Render the identity banner shown above the wizard tab bar. Used by both
     * citizen-edit-mode and operator-mode so every step — including guide /
     * review screens that have no data fields — clearly shows which applicant
     * record the wizard is bound to. Reads from the load binder so the values
     * are present regardless of which tab is active.
     *
     * <p>Field names match the citizen wizard's mm_field storageKeys:
     * {@code full_name}, {@code national_id}, {@code applied_programme}.
     * If any one is missing the cell is silently skipped. If ALL three are
     * empty (typical for {@code _mode=add}), the helper returns "" and no
     * header renders.
     */
    private String emitIdentityHeader(FormData formData) {
        if (formData == null) return "";
        try {
            org.joget.apps.form.model.Form root = FormUtil.findRootForm(this);
            if (root == null) return "";
            String fullName  = nz(formData.getLoadBinderDataProperty(root, "full_name"), "");
            String nid       = nz(formData.getLoadBinderDataProperty(root, "national_id"), "");
            String programme = nz(formData.getLoadBinderDataProperty(root, "applied_programme"), "");
            if (fullName.isEmpty() && nid.isEmpty() && programme.isEmpty()) return "";

            StringBuilder h = new StringBuilder();
            h.append("<div class=\"regbb-id-header\">");
            if (!fullName.isEmpty()) {
                h.append("<span class=\"regbb-id-name\">").append(htmlEscape(fullName)).append("</span>");
            }
            if (!nid.isEmpty()) {
                h.append("<span><span class=\"regbb-id-label\">NID</span>")
                 .append("<span class=\"regbb-id-value\">").append(htmlEscape(nid)).append("</span></span>");
            }
            if (!programme.isEmpty()) {
                h.append("<span class=\"regbb-id-prog\"><span class=\"regbb-id-label\">Programme</span>")
                 .append("<code>").append(htmlEscape(programme)).append("</code></span>");
            }
            h.append("</div>");
            return h.toString();
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "emitOperatorIdentityHeader failed: " + t.getMessage());
            return "";
        }
    }

    /**
     * §6.4 client-side conditional UI. Walks every {@code mm_field} for
     * the current wizard's screens, picks up those with a
     * {@code visibilityDeterminantId} whose rule is a single-field
     * equality (e.g. {@code block_farming_member == 'yes'}), and emits
     * JS that toggles the dependent field instantly when the citizen
     * changes the dependency input. Complex rules (AND / OR / IN /
     * numeric comparisons) are intentionally skipped client-side —
     * server-side §6.4 still hides / requires them on every save +
     * reload, but the snappy in-page toggle only fires for the simple
     * case which covers the bulk of real conditional UI.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String emitConditionalUiJs(String wizardDomId, List<FormRow> screens) {
        try {
            global.govstack.regbb.engine.dao.MetaModelDao dao =
                    new global.govstack.regbb.engine.dao.MetaModelDao();
            List<String> bindingsJson = new ArrayList<>();
            for (FormRow screen : screens) {
                String kind = nz(prop(screen, "kind"), "form");
                if (!"form".equalsIgnoreCase(kind)) continue;
                List<FormRow> fields = dao.listFieldsForScreen(screen.getId());
                for (FormRow f : fields) {
                    String visDet      = nz(prop(f, "visibilityDeterminantId"), "");
                    String storageKey  = nz(prop(f, "storageKey"), "");
                    if (visDet.isEmpty() || storageKey.isEmpty()) continue;
                    FormRow det = dao.findDeterminantByCode(visDet);
                    if (det == null) continue;
                    String rule = nz(prop(det, "ruleJson"), "");
                    String[] eq = matchSimpleEquality(rule);
                    if (eq == null) continue;  // complex rule — server-side only
                    bindingsJson.add("{\"target\":\"" + jsEscape(storageKey)
                            + "\",\"dep\":\"" + jsEscape(eq[0])
                            + "\",\"equals\":\"" + jsEscape(eq[1]) + "\"}");
                }
            }
            if (bindingsJson.isEmpty()) return "";  // nothing to toggle

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < bindingsJson.size(); i++) {
                if (i > 0) json.append(",");
                json.append(bindingsJson.get(i));
            }
            json.append("]");

            // Client-side toggle script — listens for any change in the
            // wizard's panels, re-evaluates each binding against the
            // current dependency value, hides or shows the target's
            // surrounding cell. Native DOM only — no jQuery dependency.
            return "<script>(function(){"
                 + "var root=document.getElementById('" + wizardDomId + "');if(!root)return;"
                 + "var bindings=" + json + ";"
                 + "function findCell(name){"
                 +   "var el=root.querySelector('[name=\"' + name + '\"]');"
                 +   "if(!el)return null;"
                 +   "var c=el.closest('.form-cell');"
                 +   "if(c)return c;"
                 +   "var p=el.parentNode;"
                 +   "while(p&&p!==root){if(p.classList&&p.classList.contains('form-cell'))return p;p=p.parentNode;}"
                 +   "return el.parentNode;"
                 + "}"
                 + "function readVal(name){"
                 +   "var els=root.querySelectorAll('[name=\"' + name + '\"]');"
                 +   "for(var i=0;i<els.length;i++){"
                 +     "var e=els[i];"
                 +     "if(e.type==='radio'||e.type==='checkbox'){if(e.checked)return e.value;}"
                 +     "else if(e.tagName==='SELECT'){return e.value;}"
                 +     "else{return e.value;}"
                 +   "}"
                 +   "return '';"
                 + "}"
                 + "function applyOne(b){"
                 +   "var cell=findCell(b.target);if(!cell)return;"
                 +   "var v=readVal(b.dep);"
                 +   "cell.style.display=(v===b.equals)?'':'none';"
                 + "}"
                 + "function applyAll(){for(var i=0;i<bindings.length;i++)applyOne(bindings[i]);}"
                 + "root.addEventListener('change',applyAll,true);"
                 + "root.addEventListener('input',applyAll,true);"
                 + "applyAll();"
                 + "})();</script>";
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "emitConditionalUiJs failed: " + t.getMessage());
            return "";
        }
    }

    /** Match {@code <ident> == '<value>'} or {@code <ident> == "<value>"}.
     *  Returns {@code [field, value]} or {@code null}. Whitespace flexible.
     *  Complex rules (AND / OR / IN / != / numeric) deliberately rejected —
     *  the server-side evaluator handles those. */
    private static String[] matchSimpleEquality(String rule) {
        if (rule == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*==\\s*['\"]([^'\"]*)['\"]\\s*$"
        ).matcher(rule);
        if (!m.matches()) return null;
        return new String[]{ m.group(1), m.group(2) };
    }

    /** Case-tolerant FormRow read — matches {@code MetaScreenElement.prop}.
     *  Postgres folds unquoted column names to lowercase, so camelCase keys
     *  like {@code storageKey} may surface as {@code storagekey}. Try the
     *  canonical key first, then lowercase. */
    private static String prop(FormRow row, String key) {
        if (row == null || key == null) return null;
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return (v != null) ? v.toString() : null;
    }

    /** Escape a string for inclusion inside a JS double-quoted literal. */
    private static String jsEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"':  out.append("\\\""); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '<':  out.append("\\u003C"); break; // prevent </script>
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private static String emitWizardJs(String domId, int totalSteps) {
        return "<script>(function(){"
             + "var root=document.getElementById('" + domId + "');if(!root)return;"
             + "var total=" + totalSteps + ",cur=0;"
             + "var tabs=root.querySelectorAll('.regbb-wizard-tab-link');"
             + "var panels=root.querySelectorAll('.regbb-wizard-panel');"
             + "var prev=root.querySelector('.regbb-prev');"
             + "var next=root.querySelector('.regbb-next');"
             + "var progress=root.querySelector('.regbb-progress b');"
             + "function go(step){"
             +   "if(step<0||step>=total)return;"
             +   "for(var i=0;i<total;i++){"
             +     "tabs[i].classList.remove('active');"
             +     "if(i<step)tabs[i].classList.add('completed');else tabs[i].classList.remove('completed');"
             +     "if(i===step){tabs[i].classList.add('active');panels[i].classList.remove('hidden');}else{panels[i].classList.add('hidden');}"
             +   "}"
             +   "prev.disabled=(step===0);"
             +   "next.disabled=(step===total-1);"
             +   "if(progress)progress.textContent=(step+1);"
             +   "cur=step;"
             +   "root.scrollIntoView({behavior:'smooth',block:'start'});"
             + "}"
             + "for(var i=0;i<tabs.length;i++){"
             +   "(function(idx){tabs[idx].addEventListener('click',function(){go(idx);});})(i);"
             + "}"
             + "prev.addEventListener('click',function(){go(cur-1);});"
             + "next.addEventListener('click',function(){go(cur+1);});"
             + "})();</script>";
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private static String nz(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String errorBox(String msg) {
        return "<div class=\"form-cell\">"
             + "<div style=\"padding:0.8em;background:#fde0e0;border:1px solid #c00;color:#700;\">"
             + "<strong>Meta Wizard error:</strong> " + msg + "</div>"
             + "</div>";
    }
}
