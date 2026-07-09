package global.govstack.regbb.engine.api;

import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.directory.model.Group;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Session-authenticated, read-only datalist endpoint for the dashboards and
 * reports.
 *
 * The dashboards previously fetched their data from Joget's built-in
 * /web/json/data/list/&lt;app&gt;/&lt;list&gt; endpoint, but that URL is
 * restricted at the security layer to admin-type roles (ROLE_ADMIN,
 * ROLE_APP_DESIGNER, …). Ordinary dashboard users (District Supervisor,
 * Finance Officer) only hold ROLE_USER and were therefore blocked.
 *
 * This plugin is served at /web/json/plugin/&lt;class&gt;/service, which IS
 * open to ROLE_USER at the URL layer, so we enforce authentication and
 * authorisation in-method: the caller must be a logged-in user who is an
 * administrator OR belongs to a dashboard role. It returns the same
 * {total, data:[{column: value}]} shape the built-in endpoint returns, so no
 * client-side parsing change is needed. No API key is involved.
 *
 * Endpoint:
 *   GET /jw/web/json/plugin/global.govstack.regbb.engine.api.DashboardDataWeb/service
 *       ?appId=farmersPortal&listId=dl_dashboard_kpi&start=0&rows=100000
 */
public class DashboardDataWeb extends ExtDefaultPlugin implements PluginWebSupport {

    private static final String CLASS_NAME = DashboardDataWeb.class.getName();

    /** Roles permitted to read dashboard/report data — mirrors the userview categories. */
    private static final String[] ALLOWED_GROUPS = {
        "role_sysadmin", "role_district_supervisor", "role_finance_officer"
    };

    public String getName()        { return "Dashboard Data Web Service"; }
    public String getVersion()     { return "8.1-SNAPSHOT"; }
    public String getDescription() { return "Session-authenticated read-only datalist endpoint for dashboards/reports (no API key)."; }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static Integer parseIntOrNull(String s) {
        try { return (s == null || s.trim().isEmpty()) ? null : Integer.valueOf(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

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
            out.write("{\"error\":{\"code\":\"401\",\"message\":\"Login required.\"}}");
            out.flush();
            return;
        }

        // 2) Authorisation — administrator OR a dashboard role.
        boolean allowed = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!allowed) {
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
        }
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.write("{\"error\":{\"code\":\"403\",\"message\":\"Not authorised.\"}}");
            out.flush();
            return;
        }

        // 3) Parameters.
        String appId  = trim(request.getParameter("appId"));
        String listId = trim(request.getParameter("listId"));
        if (appId.isEmpty()) appId = "farmersPortal";
        if (listId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"error\":{\"code\":\"400\",\"message\":\"listId is required.\"}}");
            out.flush();
            return;
        }
        listId = SecurityUtil.validateStringInput(listId);
        Integer start = parseIntOrNull(request.getParameter("start"));
        Integer rows  = parseIntOrNull(request.getParameter("rows"));

        try {
            AppService appService = (AppService) ac.getBean("appService");
            AppDefinition appDef = appService.getPublishedAppDefinition(appId);
            if (appDef == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":{\"code\":\"404\",\"message\":\"app not found\"}}");
                out.flush();
                return;
            }
            AppUtil.setCurrentAppDefinition(appDef);

            DatalistDefinitionDao dao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
            DatalistDefinition dld = dao.loadById(listId, appDef);
            if (dld == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":{\"code\":\"404\",\"message\":\"datalist not found\"}}");
                out.flush();
                return;
            }

            DataListService dataListService = (DataListService) ac.getBean("dataListService");
            DataList dataList = dataListService.fromJson(dld.getJson());

            int total = dataList.getTotal();
            DataListColumn[] columns = dataList.getColumns();
            DataListCollection results = dataList.getRows(rows, start);

            JSONObject o = new JSONObject();
            o.put("total", total);
            JSONArray data = new JSONArray();
            for (Iterator i = results.iterator(); i.hasNext(); ) {
                Map row = (Map) i.next();
                JSONObject ro = new JSONObject();
                for (int j = 0; j < columns.length; j++) {
                    DataListColumn column = columns[j];
                    String columnName = column.getName();
                    Object value = row.get(columnName);
                    ro.put(columnName, (value == null) ? "" : String.valueOf(value));
                }
                data.put(ro);
            }
            o.put("data", data);
            out.write(o.toString());
        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t, "datalist read failed for list=" + listId);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":{\"code\":\"500\",\"message\":\"internal error\"}}");
        }
        out.flush();
    }
}
