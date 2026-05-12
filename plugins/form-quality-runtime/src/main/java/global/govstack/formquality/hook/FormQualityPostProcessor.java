package global.govstack.formquality.hook;

import global.govstack.formquality.Build;
import global.govstack.formquality.model.QualityIssue;
import global.govstack.formquality.model.QualityRule;
import global.govstack.formquality.service.IssueRepository;
import global.govstack.formquality.service.RuleEvaluator;
import global.govstack.formquality.service.RuleRepository;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import java.util.List;
import java.util.Map;

/**
 * Form post-processor that runs the quality rules engine after every save.
 * <p>
 * Wired into a target form by setting that form's {@code postProcessor.className}
 * JSON property to {@code global.govstack.formquality.hook.FormQualityPostProcessor}
 * and providing a {@code serviceId} property that names the rule library to use
 * (corresponds to a row in {@code qa_service}).
 * <p>
 * <b>Joget invocation contract</b> (verified against
 * {@code FormUtil.executePostFormSubmissionProccessor} in jw-community 8.x):
 * Joget calls {@link #execute(Map)} after a successful form save. The map
 * contains, among other things:
 * <ul>
 *   <li>{@code "recordId"}            — the wrapper form's primary key value</li>
 *   <li>{@code "appDef"}              — the {@code AppDefinition}</li>
 *   <li>{@code "pluginManager"}       — the OSGi {@code PluginManager}</li>
 *   <li>{@code "workflowAssignment"}  — present if invoked from a workflow</li>
 *   <li>{@code "request"}             — the {@code HttpServletRequest}</li>
 *   <li>plus plugin properties as configured in the target form's
 *       {@code postProcessor.properties} (here: {@code serviceId})</li>
 * </ul>
 * Note: Joget does NOT pass {@code FormData} into the post-processor map. The
 * record id is the only handle we get, so {@code formId} is resolved via
 * {@link RuleRepository#findPrimaryFormId(String)} — the service registry's
 * primary form for this rule library.
 * <p>
 * Per the gam-plugins methodology rule, this class never executes DDL or
 * raw SQL on form tables — all writes go through {@link FormDataDao} via
 * {@link IssueRepository}. The only raw JDBC is the read-only rule
 * evaluation in {@link RuleEvaluator}, against admin-curated SQL.
 */
public class FormQualityPostProcessor extends DefaultApplicationPlugin {

    private static final String CLASS_NAME = FormQualityPostProcessor.class.getName();

    @Override
    public String getName()        { return "Form Quality Post-Processor"; }
    @Override
    public String getDescription() {
        // Build stamp is baked into the bytecode by deploy/repack.sh so you can
        // confirm in App Composer's plugin picker that the live JAR matches the
        // source you intended to deploy.
        return "Runs quality rules on every save and persists issues to qa_issue + qa_record_status. ["
                + Build.STAMP + "]";
    }
    @Override
    public String getVersion()     { return "8.1-SNAPSHOT (" + Build.STAMP + ")"; }
    @Override
    public String getLabel()       { return getName(); }
    @Override
    public String getClassName()   { return getClass().getName(); }
    @Override
    public String getPropertyOptions() {
        // Minimal property panel — admin specifies which rule library this post-processor
        // serves. Rendered in App Composer when wiring postProcessor on a target form.
        return "[{"
            + "\"title\":\"Form Quality Runtime\","
            + "\"properties\":["
            + "  {\"name\":\"serviceId\",\"label\":\"Service ID (qa_service.serviceId)\",\"type\":\"textfield\",\"required\":\"true\"}"
            + "]"
            + "}]";
    }

    @Override
    public Object execute(Map properties) {
        try {
            String serviceId = stringProp(properties, "serviceId");
            if (serviceId == null || serviceId.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No serviceId configured on this post-processor — skipping.");
                return null;
            }

            // Joget puts the wrapper form's primary key here; FormData is not
            // passed in the post-processor map (see FormUtil source, ~line 2264).
            String recordId = stringProp(properties, "recordId");
            if (recordId == null || recordId.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No recordId in invocation context — skipping.");
                return null;
            }

            FormDataDao dao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            RuleRepository ruleRepo  = new RuleRepository(dao);
            RuleEvaluator  evaluator = new RuleEvaluator();
            IssueRepository issueRepo = new IssueRepository(dao);

            // Resolve formId via the service registry. This makes the engine
            // tolerant of whether the post-processor was wired on the wrapper
            // or any of its tab forms — they all carry the same serviceId, and
            // the canonical formId for issue persistence comes from
            // qa_service.primaryFormId.
            String formId = ruleRepo.findPrimaryFormId(serviceId);
            if (formId == null) {
                LogUtil.warn(CLASS_NAME, "qa_service.primaryFormId not set for serviceId="
                        + serviceId + " — engine cannot record issues.");
                return null;
            }

            List<QualityRule> rules = ruleRepo.findActiveRulesForService(serviceId);
            if (rules.isEmpty()) {
                LogUtil.info(CLASS_NAME, "No active rules for serviceId=" + serviceId
                        + " — nothing to evaluate.");
                return null;
            }

            List<QualityIssue> issues = evaluator.evaluate(serviceId, formId, recordId, rules);
            issueRepo.persistRun(serviceId, formId, recordId, issues);

            LogUtil.info(CLASS_NAME, "Quality run for " + formId + "/" + recordId
                    + " (serviceId=" + serviceId + "): " + rules.size() + " rules, "
                    + issues.size() + " issue(s).");

        } catch (Throwable t) {
            // Belt-and-braces: never let a quality-engine error block a form save.
            // The save already committed by the time we run; logging is enough.
            LogUtil.error(CLASS_NAME, t, "Quality post-processor failed: " + t.getMessage());
        }
        return null;
    }

    private static String stringProp(Map properties, String key) {
        Object v = properties.get(key);
        return v == null ? null : v.toString();
    }
}
