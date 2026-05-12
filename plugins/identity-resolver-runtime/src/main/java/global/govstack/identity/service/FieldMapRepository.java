package global.govstack.identity.service;

import global.govstack.identity.model.FieldMap;
import global.govstack.identity.model.FieldMap.ResolveStrategy;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads field-mapping rows from {@code app_resolver_field_map}. All reads via
 * {@link FormDataDao} per the methodology rule.
 */
public class FieldMapRepository {

    private static final String CLASS_NAME = FieldMapRepository.class.getName();
    private static final String FORM = "app_resolver_field_map";

    private final FormDataDao dao;

    public FieldMapRepository(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * Returns the active field-map rows for a given resolver, ordered by
     * {@code displayOrder} ascending so the UI sees a stable resolve sequence.
     */
    public List<FieldMap> findActiveForConfig(String resolverConfigId) {
        if (resolverConfigId == null || resolverConfigId.isEmpty())
            return Collections.emptyList();

        String condition = "WHERE e.customProperties.resolverConfigId = ? "
                         + "AND   e.customProperties.isActive = ?";
        Object[] args = new Object[] { resolverConfigId, "Y" };
        FormRowSet rows = dao.find(FORM, FORM, condition, args,
                "displayOrder", false, null, null);

        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        List<FieldMap> out = new ArrayList<>(rows.size());
        for (FormRow r : rows) {
            try { out.add(toModel(r)); }
            catch (RuntimeException e) {
                LogUtil.warn(CLASS_NAME, "Skipping malformed field-map row "
                        + r.getId() + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static FieldMap toModel(FormRow r) {
        ResolveStrategy strategy;
        try {
            strategy = ResolveStrategy.valueOf(nz(r.getProperty("resolveStrategy"), "OVERWRITE"));
        } catch (IllegalArgumentException e) {
            strategy = ResolveStrategy.OVERWRITE;
        }
        int order = parseIntSafe(r.getProperty("displayOrder"), 100);
        return new FieldMap(
                r.getId(),
                r.getProperty("resolverConfigId"),
                r.getProperty("sourceFieldId"),
                r.getProperty("targetFieldId"),
                emptyToNull(r.getProperty("chainedSourceFormId")),
                nz(r.getProperty("chainedJoinField"), "parent_id"),
                "Y".equalsIgnoreCase(nz(r.getProperty("readonlyAfterResolve"), "Y")),
                strategy,
                order,
                "Y".equalsIgnoreCase(nz(r.getProperty("isActive"), "Y"))
        );
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String nz(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private static int parseIntSafe(String s, int dflt) {
        try { return s == null || s.isEmpty() ? dflt : Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return dflt; }
    }
}
