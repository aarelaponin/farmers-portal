package global.govstack.regbb.engine.budget;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * L3-1 3a — Budget hint column formatter for the operator decision tab.
 *
 * <p>Renders an inline panel showing what approving THIS application would
 * cost the envelope, plus the resulting utilisation. Configured as a
 * column on the operator inbox datalist; reads
 * {@code applied_programme} from the row, looks up
 * BENEFIT_AMOUNT_&lt;programme&gt;.targetValue for the per-applicant
 * amount, and queries {@code budget_projection} for current envelope
 * state.
 *
 * <p>Single SELECT per row — for an inbox with ≤ 100 rows the per-row
 * cost is negligible. The materialised view is small and indexed by
 * {@code envelope_code}.
 *
 * <p>Rendering: a small block with two lines of text plus a coloured
 * utilisation bar. Colour scales 0–80% green, 80–100% amber, &gt;100% red.
 *
 * <p>Read-only — no writes. HARD-RULE-compliant: queries against the
 * Budget Engine's own tables and a derived materialised view.
 */
public class BudgetHintFormatter extends DataListColumnFormatDefault {

    private static final String CLASS_NAME = BudgetHintFormatter.class.getName();

    @Override public String getName()        { return "Budget Hint Formatter"; }
    @Override public String getVersion()     { return "8.1-SNAPSHOT"; }
    @Override public String getLabel()       { return "Budget Hint Formatter"; }
    @Override public String getDescription() { return "Inline budget hint for the operator decision tab (L3-1 3a)"; }
    @Override public String getClassName()   { return getClass().getName(); }

    /** Empty property options — no configurable knobs in 3a. Future
     *  versions could expose colour thresholds or text overrides. */
    @Override public String getPropertyOptions() {
        return "[ { \"title\":\"Budget Hint\", \"properties\":[] } ]";
    }

    @Override
    public String format(DataList dataList, DataListColumn column, Object row, Object value) {
        // Delegate the rendering to the shared BudgetHintRenderer so the
        // form-side widget (MetaScreenElement budget_hint case) and this
        // datalist column produce visually identical output.
        String programmeCode = readProgramme(row);
        return BudgetHintRenderer.render(programmeCode);
    }

    // -----------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------

    /** Datalist rows arrive as Maps keyed by column id. The operator inbox
     *  list exposes the programme CODE under either {@code programme_code}
     *  (preferred — explicit alias added in SQL) OR raw
     *  {@code c_applied_programme}. {@code applied_programme} is the
     *  human-readable name (after JOIN with mm_registration), which
     *  doesn't help us resolve the envelope, so we look for the code
     *  field first. */
    @SuppressWarnings("rawtypes")
    private static String readProgramme(Object row) {
        if (!(row instanceof Map)) return null;
        Map m = (Map) row;
        // Code-bearing fields first.
        Object v = m.get("programme_code");
        if (v == null) v = m.get("c_applied_programme");
        if (v == null) v = m.get("applied_programme_code");
        // Fallback to applied_programme — works when the datalist hasn't
        // been JOINed against mm_registration to resolve the name.
        if (v == null) v = m.get("applied_programme");
        if (v == null) v = m.get("APPLIED_PROGRAMME");
        if (v == null) return null;
        String s = v.toString();
        // If the value looks like a programme code (PRG_<YYYY>_<NNN>) use
        // it; if it looks like a human-readable name, try to recover the
        // code by querying mm_registration. Both shapes can show up
        // depending on the datalist's SQL.
        if (s.startsWith("PRG_")) return s;
        return resolveCodeFromName(s);
    }

    /** Best-effort name → code recovery. Used when the datalist SQL has
     *  already mapped applied_programme to the registration's display
     *  name via JOIN; we read mm_registration back to get the code. */
    private static String resolveCodeFromName(String name) {
        if (name == null || name.isEmpty()) return null;
        String sql = "SELECT c_code FROM app_fd_mm_registration WHERE c_name = ? LIMIT 1";
        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            try (Connection c = ds.getConnection();
                 PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, name);
                try (ResultSet rs = p.executeQuery()) {
                    if (rs.next()) return rs.getString(1);
                }
            }
        } catch (SQLException ignore) {}
        return null;
    }
}
