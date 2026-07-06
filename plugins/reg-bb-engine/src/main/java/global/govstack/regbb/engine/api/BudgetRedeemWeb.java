package global.govstack.regbb.engine.api;

import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.Group;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

/**
 * Session-authenticated voucher-redemption endpoint.
 *
 * Replaces the client-side call to the API-Builder Budget API (which
 * required a hardcoded api_key in the userview HtmlPage). This endpoint is
 * served under /jw/web/json/plugin/&lt;class&gt;/service, which Joget maps
 * to ROLE_ANONYMOUS at the URL level, so authentication and authorisation
 * are enforced in-method: the caller must be a logged-in user who belongs
 * to one of the redemption groups. The heavy lifting is delegated to the
 * same VoucherRedemptionTool the API-Builder operation used, so behaviour
 * and the returned JSON are identical.
 *
 * Endpoint:
 *   POST /jw/web/json/plugin/global.govstack.regbb.engine.api.BudgetRedeemWeb/service
 *        ?appId=farmersPortal&voucherCode=..&redemptionPoint=..&redeemedBy=..&quantity=..
 */
public class BudgetRedeemWeb extends ExtDefaultPlugin implements PluginWebSupport {

    private static final String CLASS_NAME = BudgetRedeemWeb.class.getName();

    /** Groups permitted to redeem — mirrors the userview menu permission. */
    private static final String[] ALLOWED_GROUPS = {
        "role_sysadmin", "role_district_supervisor", "role_field_officer"
    };

    public String getName()        { return "Budget Redeem Web Service"; }
    public String getVersion()     { return "8.1-SNAPSHOT"; }
    public String getDescription() { return "Session-authenticated voucher redemption endpoint (no API key)."; }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter out = response.getWriter();

        ApplicationContext ac = AppUtil.getApplicationContext();
        WorkflowUserManager wum = (WorkflowUserManager) ac.getBean("workflowUserManager");
        String username = wum.getCurrentUsername();

        // 1) Authentication — must be a logged-in (non-anonymous) user.
        if (username == null || username.isEmpty()
                || WorkflowUserManager.ROLE_ANONYMOUS.equals(username)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.write("{\"status\":\"unauthorized\",\"message\":\"Login required to redeem a voucher.\"}");
            out.flush();
            return;
        }

        // 2) Authorisation — must belong to a redemption group.
        boolean allowed = false;
        try {
            ExtDirectoryManager dm = (ExtDirectoryManager) ac.getBean("directoryManager");
            Collection<Group> groups = dm.getGroupByUsername(username);
            if (groups != null) {
                for (Group g : groups) {
                    for (String ag : ALLOWED_GROUPS) {
                        if (ag.equals(g.getId())) { allowed = true; break; }
                    }
                    if (allowed) break;
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "group lookup failed for user=" + username);
        }
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.write("{\"status\":\"forbidden\",\"message\":\"You are not authorised to redeem vouchers.\"}");
            out.flush();
            return;
        }

        // 3) Method — POST only (state-changing).
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            out.write("{\"status\":\"method_not_allowed\",\"message\":\"Use POST.\"}");
            out.flush();
            return;
        }

        // 4) Parameters.
        String appId           = trim(request.getParameter("appId"));
        String voucherCode     = trim(request.getParameter("voucherCode"));
        String redemptionPoint = trim(request.getParameter("redemptionPoint"));
        String redeemedBy      = trim(request.getParameter("redeemedBy"));
        String quantity        = trim(request.getParameter("quantity"));
        if (appId.isEmpty()) appId = "farmersPortal";
        if (voucherCode.isEmpty() || redemptionPoint.isEmpty() || redeemedBy.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"status\":\"bad_request\",\"message\":\"voucherCode, redemptionPoint and redeemedBy are required.\"}");
            out.flush();
            return;
        }

        // Ensure the published AppDefinition is in the thread context (the
        // tool's DB access relies on it), mirroring LookupFieldWebService.
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef == null || !appId.equals(appDef.getAppId())) {
                AppDefinitionDao appDefDao = (AppDefinitionDao) ac.getBean("appDefinitionDao");
                Long v = appDefDao.getPublishedVersion(appId);
                if (v != null) {
                    appDef = appDefDao.loadVersion(appId, v);
                    if (appDef != null) AppUtil.setCurrentAppDefinition(appDef);
                }
            }
        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "could not set app context for " + appId + ": " + e.getMessage());
        }

        // 5) Redeem — same tool the API-Builder operation used.
        try {
            global.govstack.regbb.engine.processing.VoucherRedemptionTool tool =
                    new global.govstack.regbb.engine.processing.VoucherRedemptionTool();
            global.govstack.regbb.engine.processing.VoucherRedemptionTool.Result r =
                    tool.redeem(voucherCode, redemptionPoint, redeemedBy,
                                quantity.isEmpty() ? null : quantity);
            out.write(r.toJson());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "redeem failed for voucher=" + voucherCode
                    + " by user=" + username);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject err = new JSONObject();
            err.put("status", "error");
            err.put("voucherCode", voucherCode);
            err.put("message", "internal:" + t.getClass().getSimpleName());
            out.write(err.toString());
        }
        out.flush();
    }
}
