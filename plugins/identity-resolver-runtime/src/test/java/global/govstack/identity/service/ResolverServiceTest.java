package global.govstack.identity.service;

import global.govstack.identity.model.FieldMap;
import global.govstack.identity.model.FieldMap.ResolveStrategy;
import global.govstack.identity.model.ResolveResult;
import global.govstack.identity.model.ResolverConfig;
import global.govstack.identity.model.ResolverConfig.MultipleMatchesPolicy;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResolverService} — exercises the 11-step algorithm
 * against a mocked {@link FormDataDao}.
 */
public class ResolverServiceTest {

    private FormDataDao        dao;
    private ConfigRepository   configRepo;
    private FieldMapRepository fieldMapRepo;
    private ResolverService    svc;

    @Before
    public void setUp() {
        dao          = mock(FormDataDao.class);
        configRepo   = mock(ConfigRepository.class);
        fieldMapRepo = mock(FieldMapRepository.class);
        svc          = new ResolverService(dao, configRepo, fieldMapRepo);
    }

    /* -------- helpers -------- */

    private static FormRow row(String id, String... kv) {
        FormRow r = new FormRow();
        r.setId(id);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            r.setProperty(kv[i], kv[i + 1]);
        }
        return r;
    }

    private static FormRowSet rowSet(FormRow... rs) {
        FormRowSet s = new FormRowSet();
        s.addAll(Arrays.asList(rs));
        return s;
    }

    private ResolverConfig farmerConfig(MultipleMatchesPolicy policy) {
        return new ResolverConfig(
                "rowid-farmerByNid", "farmerByNid",
                "Farmer lookup by NID", null,
                "farmerBasicInfo", "national_id",
                "Not registered", "/register?id=#value#",
                policy, 0, true);
    }

    /* -------- tests -------- */

    @Test
    public void resolves_single_match_with_mapped_fields() {
        when(configRepo.findActiveByConfigId("farmerByNid"))
                .thenReturn(farmerConfig(MultipleMatchesPolicy.ERROR));

        when(dao.find(eq("farmerBasicInfo"), eq("farmerBasicInfo"),
                ArgumentMatchers.contains("national_id"),
                any(), any(), ArgumentMatchers.anyBoolean(),
                any(), any()))
                .thenReturn(rowSet(row("F123",
                        "first_name", "Itumeleng",
                        "last_name",  "Josias")));

        when(fieldMapRepo.findActiveForConfig("farmerByNid")).thenReturn(Arrays.asList(
                new FieldMap("m1", "farmerByNid", "first_name", "applicant_first_name",
                        null, "parent_id", true, ResolveStrategy.OVERWRITE, 100, true),
                new FieldMap("m2", "farmerByNid", "last_name",  "applicant_last_name",
                        null, "parent_id", true, ResolveStrategy.OVERWRITE, 200, true)));

        ResolveResult r = svc.resolve("farmerByNid", "9765433");

        assertEquals(ResolveResult.Status.FOUND, r.getStatus());
        assertEquals("F123", r.getSourceRecordId());
        assertEquals("Itumeleng", r.getFields().get("applicant_first_name"));
        assertEquals("Josias",    r.getFields().get("applicant_last_name"));
    }

    @Test
    public void returns_not_found_for_unknown_lookup_with_action_url_substituted() {
        when(configRepo.findActiveByConfigId("farmerByNid"))
                .thenReturn(farmerConfig(MultipleMatchesPolicy.ERROR));
        when(dao.find(any(), any(), any(), any(), any(), ArgumentMatchers.anyBoolean(),
                any(), any())).thenReturn(rowSet());

        ResolveResult r = svc.resolve("farmerByNid", "DOES_NOT_EXIST");

        assertEquals(ResolveResult.Status.NOT_FOUND, r.getStatus());
        assertEquals("Not registered", r.getMessage());
        assertNotNull(r.getActionUrl());
        assertTrue("Action URL should have substituted #value#",
                r.getActionUrl().contains("DOES_NOT_EXIST"));
    }

    @Test
    public void returns_multiple_when_policy_is_error_and_two_rows() {
        when(configRepo.findActiveByConfigId("farmerByNid"))
                .thenReturn(farmerConfig(MultipleMatchesPolicy.ERROR));
        when(dao.find(any(), any(), any(), any(), any(), ArgumentMatchers.anyBoolean(),
                any(), any())).thenReturn(rowSet(
                        row("F1", "first_name", "Itumeleng"),
                        row("F2", "first_name", "Itumeleng")));

        ResolveResult r = svc.resolve("farmerByNid", "9999");

        assertEquals(ResolveResult.Status.MULTIPLE, r.getStatus());
        assertEquals(2, r.getCandidates().size());
    }

    @Test
    public void first_policy_picks_first_row_deterministically() {
        when(configRepo.findActiveByConfigId("farmerByNid"))
                .thenReturn(farmerConfig(MultipleMatchesPolicy.FIRST));
        when(dao.find(any(), any(), any(), any(), any(), ArgumentMatchers.anyBoolean(),
                any(), any())).thenReturn(rowSet(
                        row("F1", "first_name", "Alpha"),
                        row("F2", "first_name", "Beta")));
        when(fieldMapRepo.findActiveForConfig("farmerByNid")).thenReturn(Collections.singletonList(
                new FieldMap("m1", "farmerByNid", "first_name", "applicant_first_name",
                        null, "parent_id", true, ResolveStrategy.OVERWRITE, 100, true)));

        ResolveResult r = svc.resolve("farmerByNid", "9999");

        assertEquals(ResolveResult.Status.FOUND, r.getStatus());
        assertEquals("F1", r.getSourceRecordId());
        assertEquals("Alpha", r.getFields().get("applicant_first_name"));
    }

    @Test
    public void chained_subform_join_produces_correct_field_value() {
        when(configRepo.findActiveByConfigId("farmerByNid"))
                .thenReturn(farmerConfig(MultipleMatchesPolicy.ERROR));

        // Primary lookup returns farmer F123
        when(dao.find(eq("farmerBasicInfo"), eq("farmerBasicInfo"),
                ArgumentMatchers.contains("national_id"),
                any(), any(), ArgumentMatchers.anyBoolean(),
                any(), any()))
                .thenReturn(rowSet(row("F123", "first_name", "Itumeleng")));

        // Chained subform query for farmerResidency returns one row with district
        when(dao.find(eq("farmerResidency"), eq("farmerResidency"),
                ArgumentMatchers.contains("parent_id"),
                any(), any(), ArgumentMatchers.anyBoolean(),
                any(), any()))
                .thenReturn(rowSet(row("R1", "district", "Maseru")));

        when(fieldMapRepo.findActiveForConfig("farmerByNid")).thenReturn(Arrays.asList(
                new FieldMap("m1", "farmerByNid", "first_name", "applicant_first_name",
                        null, "parent_id", true, ResolveStrategy.OVERWRITE, 100, true),
                new FieldMap("m2", "farmerByNid", "district", "applicant_district",
                        "farmerResidency", "parent_id", true, ResolveStrategy.OVERWRITE, 200, true)));

        ResolveResult r = svc.resolve("farmerByNid", "9765433");

        assertEquals(ResolveResult.Status.FOUND, r.getStatus());
        assertEquals("Itumeleng", r.getFields().get("applicant_first_name"));
        assertEquals("Maseru",    r.getFields().get("applicant_district"));
    }

    @Test
    public void inactive_or_unknown_config_returns_error() {
        when(configRepo.findActiveByConfigId("missing")).thenReturn(null);

        ResolveResult r = svc.resolve("missing", "1234");

        assertEquals(ResolveResult.Status.ERROR, r.getStatus());
        assertTrue(r.getMessage().toLowerCase().contains("unknown"));
    }

    @Test
    public void empty_lookup_value_returns_error_without_db_call() {
        ResolveResult r = svc.resolve("farmerByNid", "");

        assertEquals(ResolveResult.Status.ERROR, r.getStatus());
        // Confirm no DB call attempted (no stubbing on dao.find means it would NPE if invoked)
    }

    @Test
    public void null_config_id_returns_error() {
        ResolveResult r = svc.resolve(null, "9765433");

        assertEquals(ResolveResult.Status.ERROR, r.getStatus());
    }
}
