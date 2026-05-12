package global.govstack.identity.service;

import global.govstack.identity.model.FieldMap;
import global.govstack.identity.model.ResolveResult;
import global.govstack.identity.model.ResolverConfig;
import global.govstack.identity.model.ResolverConfig.MultipleMatchesPolicy;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the GovStack Registration BB Identity Verification service for a
 * single Joget app. Given a foundational identifier (e.g. National ID), looks
 * up the matching registry record and returns a flat map of target fields per
 * the operator-authored {@code app_resolver_config} + {@code app_resolver_field_map}
 * configuration.
 *
 * <p>11-step algorithm — see {@code _design/identity-resolver-design.md} §4.1:
 * <ol>
 *   <li>Load the named config; bail if missing or inactive.</li>
 *   <li>Query {@code sourceFormId} where {@code sourceLookupField = lookupValue}.</li>
 *   <li>0 rows → NOT_FOUND (with operator-configured message + action URL).</li>
 *   <li>{@literal >}1 rows + ERROR policy → MULTIPLE error.</li>
 *   <li>LET_USER_PICK policy → MULTIPLE with candidate list.</li>
 *   <li>1 row (or FIRST policy) → proceed.</li>
 *   <li>Load active field-map rows for the config.</li>
 *   <li>For each map row: read source field directly OR via chained subform join.</li>
 *   <li>Build the target-field map.</li>
 *   <li>Cache the result if {@code cacheSeconds > 0}.</li>
 *   <li>Return FOUND.</li>
 * </ol>
 *
 * <p>This class is the single entry point used by both the form element
 * (via in-Joget call) and the REST API (via the Activator-registered handler).
 */
public class ResolverService {

    private static final String CLASS_NAME = ResolverService.class.getName();

    private final FormDataDao        dao;
    private final ConfigRepository   configRepo;
    private final FieldMapRepository fieldMapRepo;

    /**
     * Tiny per-config cache keyed by lookup value. Populated when
     * {@code cacheSeconds > 0}; entry expires after that many seconds.
     */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ResolverService(FormDataDao dao,
                           ConfigRepository configRepo,
                           FieldMapRepository fieldMapRepo) {
        this.dao          = dao;
        this.configRepo   = configRepo;
        this.fieldMapRepo = fieldMapRepo;
    }

    /**
     * Resolve a foundational identifier into target field values per the named
     * configuration. Never throws — returns an {@link ResolveResult.Status#ERROR}
     * result for any internal failure so the caller can render a sensible UI.
     */
    public ResolveResult resolve(String configId, String lookupValue) {
        // (sanity — the consumer is supposed to validate, but defend anyway)
        if (configId == null || configId.isEmpty()) {
            return ResolveResult.error("Missing configId");
        }
        if (lookupValue == null || lookupValue.isEmpty()) {
            return ResolveResult.error("Empty lookup value");
        }

        try {
            // 1. Load config.
            ResolverConfig cfg = configRepo.findActiveByConfigId(configId);
            if (cfg == null) {
                return ResolveResult.error("Unknown or inactive resolver config: " + configId);
            }

            // 10a. Check cache before doing any work.
            String cacheKey = configId + "::" + lookupValue;
            if (cfg.getCacheSeconds() > 0) {
                CacheEntry cached = cache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    return cached.result;
                }
            }

            // 2. Query source form. Joget's FormDataDao expects the actual
            // physical table name as the second argument — NOT the formId.
            // For wrapper forms tableName == formId, but for many real forms
            // they differ (e.g. farmerResidency.tableName = farm_location).
            // Resolve via AppService.
            String sourceTable = resolveTableName(cfg.getSourceFormId());
            // Dotted access on customProperties — Hibernate 6 in DX 8 rejects
            // bracket notation as "Index operator applied to non-plural path".
            // Underscored field names work via dotted access too.
            String condition = "WHERE e.customProperties." + escapeIdent(cfg.getSourceLookupField()) + " = ?";
            FormRowSet rows = dao.find(cfg.getSourceFormId(), sourceTable,
                    condition, new Object[] { lookupValue }, null, false, null, null);

            int matchCount = (rows == null) ? 0 : rows.size();

            // 3. NOT_FOUND.
            if (matchCount == 0) {
                String url = cfg.getNotFoundActionUrl();
                if (url != null && !url.isEmpty()) {
                    url = url.replace("#value#", urlEncode(lookupValue));
                }
                return ResolveResult.notFound(cfg.getNotFoundMessage(), url);
            }

            // 4 & 5. Multiple matches — policy decides.
            if (matchCount > 1) {
                MultipleMatchesPolicy policy = cfg.getMultipleMatchesPolicy();
                if (policy == MultipleMatchesPolicy.ERROR) {
                    return ResolveResult.multiple(toCandidates(rows, cfg));
                }
                if (policy == MultipleMatchesPolicy.LET_USER_PICK) {
                    return ResolveResult.multiple(toCandidates(rows, cfg));
                }
                // FIRST → fall through, take rows.get(0)
            }

            // 6. Pick the chosen row.
            FormRow matched = rows.get(0);

            // For chained subforms in a Joget wizard pattern: the wizard
            // wrapper (e.g. farms_registry) holds the canonical record id;
            // each tab subform (farmerBasicInfo, farmerResidency, ...)
            // carries the wrapper's id as its `parent_id`. So to chain from
            // the matched row to a sibling tab subform, we must use the
            // matched row's `parent_id` value, NOT its own `id`.
            String matchedParentId = matched.getProperty("parent_id");
            String chainedJoinValue = (matchedParentId != null && !matchedParentId.isEmpty())
                    ? matchedParentId : matched.getId();

            // 7. Load active field-map.
            List<FieldMap> maps = fieldMapRepo.findActiveForConfig(configId);

            // 8 + 9. Build target map.
            Map<String, String> out = new LinkedHashMap<>();
            for (FieldMap m : maps) {
                String value;
                if (m.isChained()) {
                    value = readChained(chainedJoinValue, m);
                } else {
                    value = matched.getProperty(m.getSourceFieldId());
                }
                out.put(m.getTargetFieldId(), value == null ? "" : value);
            }

            ResolveResult result = ResolveResult.found(matched.getId(), out);

            // 10b. Cache.
            if (cfg.getCacheSeconds() > 0) {
                cache.put(cacheKey, new CacheEntry(result,
                        System.currentTimeMillis() + cfg.getCacheSeconds() * 1000L));
            }

            // 11.
            return result;

        } catch (Throwable t) {
            LogUtil.error(CLASS_NAME, t,
                    "Resolve failed for configId=" + configId + ", value=" + lookupValue);
            return ResolveResult.error("Internal error: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Reads {@code map.sourceFieldId} from the chained subform whose
     * {@code chainedJoinField} equals the matched primary record's id.
     * Returns null if no chained row exists.
     */
    private String readChained(String matchedRowId, FieldMap map) {
        String chainedTable = resolveTableName(map.getChainedSourceFormId());
        String condition = "WHERE e.customProperties." + escapeIdent(map.getChainedJoinField()) + " = ?";
        FormRowSet rows = dao.find(map.getChainedSourceFormId(), chainedTable,
                condition, new Object[] { matchedRowId },
                "dateCreated", false, null, null);
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0).getProperty(map.getSourceFieldId());
    }

    /**
     * Cache for formId → physical table name lookups. Cleared on bundle
     * restart; immutable per session within a running bundle.
     */
    private static final java.util.Map<String, String> TABLE_NAME_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves a Joget formId to its physical table name. For wrapper forms
     * the two are identical; for many tab subforms they differ (e.g.
     * farmerResidency.tableName = farm_location). Queries app_form via the
     * setupDataSource directly — works without an app-scope context (which
     * the webService URL doesn't have).
     */
    private String resolveTableName(String formId) {
        if (formId == null || formId.isEmpty()) return formId;
        String cached = TABLE_NAME_CACHE.get(formId);
        if (cached != null) return cached;

        DataSource ds;
        try {
            ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "setupDataSource bean not available: " + t.getMessage());
            return formId;
        }

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT tablename FROM app_form WHERE formid = ? ORDER BY appversion DESC LIMIT 1")) {
            ps.setString(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String t = rs.getString(1);
                    if (t != null && !t.isEmpty()) {
                        TABLE_NAME_CACHE.put(formId, t);
                        return t;
                    }
                }
            }
        } catch (Throwable t) {
            LogUtil.warn(CLASS_NAME, "Table-name lookup failed for " + formId
                    + ": " + t.getMessage());
        }
        return formId;
    }


    private List<ResolveResult.Candidate> toCandidates(FormRowSet rows, ResolverConfig cfg) {
        List<ResolveResult.Candidate> candidates = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size() && i < 20; i++) {
            FormRow r = rows.get(i);
            String label = r.getId();
            // Best-effort label: try common name fields, fall back to id
            for (String guess : new String[] { "name", "fullName", "first_name", "primaryName" }) {
                String v = r.getProperty(guess);
                if (v != null && !v.isEmpty()) { label = v; break; }
            }
            candidates.add(new ResolveResult.Candidate(r.getId(), label));
        }
        return candidates;
    }

    /**
     * Defensive identifier escape for the customProperties path. The path is
     * not user-supplied (only operator-supplied via {@code app_resolver_config}),
     * but we still defang anything that isn't a sane field-name char.
     */
    private static String escapeIdent(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    /* ----------- cache ----------- */

    private static final class CacheEntry {
        final ResolveResult result;
        final long          expiresAtMs;

        CacheEntry(ResolveResult result, long expiresAtMs) {
            this.result      = result;
            this.expiresAtMs = expiresAtMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }
}
