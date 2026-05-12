package global.govstack.regbb.publisher.menu;

import global.govstack.regbb.publisher.Build;
import org.joget.apps.userview.model.UserviewMenu;

/**
 * Custom userview menu action that triggers publish of an {@code mm_service}.
 * Per spec §5.1.
 *
 * <p>Phase 1 Week 1: scaffolding stub. Week 5 wires in:
 * <ul>
 *   <li>Validation against the DSL grammar (parses every Determinant; checks
 *       refs resolve; classifies fast-path vs SQL-path).</li>
 *   <li>Status flip from {@code draft} to {@code published} via
 *       {@code FormDataDao.saveOrUpdate}.</li>
 *   <li>Engine cache invalidation hook
 *       ({@code evaluator.invalidateService(serviceId)}).</li>
 *   <li>Userview generation (citizen catalogue + wizard menu, operator inbox
 *       + applications-list datalist) per D17 — Weeks 6–8.</li>
 * </ul>
 *
 * <p>For Phase 1 Week 1, clicking this menu renders a placeholder that
 * confirms the bundle is loaded and the menu plugin is registered. The
 * placeholder displays the build stamp.
 */
public class PublishUserviewMenu extends UserviewMenu {

    @Override
    public String getName() { return "RegBB Publish Service"; }

    @Override
    public String getVersion() { return Build.STAMP; }

    @Override
    public String getDescription() {
        return "Validates and publishes an mm_service. Phase 1 Week 1 scaffolding stub. " + Build.STAMP;
    }

    @Override
    public String getLabel() { return "RegBB Publish Service"; }

    @Override
    public String getClassName() { return getClass().getName(); }

    @Override
    public String getPropertyOptions() {
        return "[{\"title\":\"Publish Service\",\"properties\":["
                + "{\"name\":\"serviceId\",\"label\":\"mm_service.id to publish\",\"type\":\"textfield\"}"
                + "]}]";
    }

    @Override
    public String getCategory() { return "GovStack RegBB"; }

    @Override
    public String getIcon() { return null; }

    @Override
    public String getRenderPage() {
        // Phase 1 Week 1 stub — Week 5 wires the validation + publish flow.
        return "<div style='padding:1em;border:1px dashed #999;'>"
                + "<strong>RegBB Publish Service</strong> — Phase 1 Week 1 scaffolding stub.<br>"
                + "Build: " + Build.STAMP + "<br>"
                + "<em>Week 5 wires validation against the DSL grammar, status flip,"
                + " engine cache invalidation, and userview generation per D17.</em>"
                + "</div>";
    }

    @Override
    public boolean isHomePageSupported() { return false; }

    @Override
    public String getDecoratedMenu() { return null; }
}
