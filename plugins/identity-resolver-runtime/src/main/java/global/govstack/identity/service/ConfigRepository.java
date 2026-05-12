package global.govstack.identity.service;

import global.govstack.identity.model.ResolverConfig;
import global.govstack.identity.model.ResolverConfig.MultipleMatchesPolicy;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads identity-resolver configurations from the {@code app_resolver_config}
 * Joget form table. Per the gam-plugins methodology rule, all reads go through
 * {@link FormDataDao} — no raw SQL on app_fd_* tables.
 */
public class ConfigRepository {

    private static final String CLASS_NAME = ConfigRepository.class.getName();
    private static final String FORM = "app_resolver_config";

    private final FormDataDao dao;

    public ConfigRepository(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * Looks up an active config by its semantic id (e.g. {@code farmerByNid}).
     * Returns null if no active row matches.
     */
    public ResolverConfig findActiveByConfigId(String configId) {
        if (configId == null || configId.isEmpty()) return null;

        String condition = "WHERE e.customProperties.configId = ? "
                         + "AND   e.customProperties.isActive = ?";
        Object[] args = new Object[] { configId, "Y" };
        FormRowSet rows = dao.find(FORM, FORM, condition, args, null, false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return toModel(rows.get(0));
    }

    /** Lists every active config — useful for an admin "list resolvers" surface. */
    public List<ResolverConfig> listActive() {
        String condition = "WHERE e.customProperties.isActive = ?";
        Object[] args = new Object[] { "Y" };
        FormRowSet rows = dao.find(FORM, FORM, condition, args, "configId", false, null, null);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<ResolverConfig> out = new ArrayList<>(rows.size());
        for (FormRow r : rows) {
            try { out.add(toModel(r)); }
            catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME, "Skipping malformed config " + r.getId() + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static ResolverConfig toModel(FormRow r) {
        MultipleMatchesPolicy policy;
        try {
            policy = MultipleMatchesPolicy.valueOf(
                    nz(r.getProperty("multipleMatchesPolicy"), "ERROR"));
        } catch (IllegalArgumentException e) {
            policy = MultipleMatchesPolicy.ERROR;
        }
        int cache = parseIntSafe(r.getProperty("cacheSeconds"), 0);
        return new ResolverConfig(
                r.getId(),
                r.getProperty("configId"),
                r.getProperty("name"),
                r.getProperty("description"),
                r.getProperty("sourceFormId"),
                r.getProperty("sourceLookupField"),
                r.getProperty("notFoundMessage"),
                r.getProperty("notFoundActionUrl"),
                policy,
                cache,
                "Y".equalsIgnoreCase(nz(r.getProperty("isActive"), "Y"))
        );
    }

    private static String nz(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private static int parseIntSafe(String s, int dflt) {
        try { return s == null || s.isEmpty() ? dflt : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return dflt; }
    }
}
