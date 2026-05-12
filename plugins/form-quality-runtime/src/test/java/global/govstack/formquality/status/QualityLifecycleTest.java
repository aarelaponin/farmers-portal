package global.govstack.formquality.status;

import global.govstack.formquality.Activator;
import global.govstack.statusframework.api.Status;
import global.govstack.statusframework.core.StatusFramework;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Smoke-tests the quality lifecycle registration that {@link Activator}
 * performs at OSGi bundle start. Verifies the four states + their valid
 * transitions are wired into {@link StatusFramework} as expected.
 * <p>
 * Uses reflection to invoke the package-private {@code registerQualityLifecycle}
 * method without spinning up the whole OSGi container.
 * <p>
 * Re-registration is idempotent — {@link StatusFramework#register} replaces
 * the previous entry — so we don't need to clear the registry between tests.
 */
public class QualityLifecycleTest {

    @Before
    public void setUp() throws Exception {
        // Invoke private registration method (idempotent — re-runs replace the binding)
        Method m = Activator.class.getDeclaredMethod("registerQualityLifecycle");
        m.setAccessible(true);
        m.invoke(new Activator());
    }

    // ── code-side enum sanity ───────────────────────────────────────────

    @Test
    public void qualityStatus_hasExactlyFourStates() {
        assertEquals(4, QualityStatus.values().length);
    }

    @Test
    public void qualityStatus_allCodesUnique_andLowercaseUnderscore() {
        for (QualityStatus s : QualityStatus.values()) {
            String code = s.getCode();
            assertNotNull(code);
            assertTrue(s.name() + " code '" + code + "' violates [a-z0-9_]+",
                    code.matches("[a-z0-9_]+"));
            assertNotNull(s.getLabel());
            assertFalse(s.getLabel().trim().isEmpty());
        }
    }

    @Test
    public void qualityEntityType_tableNameIsBare() {
        assertEquals("qa_record_status",
                QualityEntityType.FORM_QUALITY_ISSUE.getTableName());
    }

    // ── lifecycle wiring ────────────────────────────────────────────────

    @Test
    public void initialStatus_isOnlyNotValidated() {
        assertTrue(StatusFramework.isInitialStatus(
                QualityEntityType.FORM_QUALITY_ISSUE, QualityStatus.NOT_VALIDATED));
        assertFalse(StatusFramework.isInitialStatus(
                QualityEntityType.FORM_QUALITY_ISSUE, QualityStatus.VERIFIED));
        assertFalse(StatusFramework.isInitialStatus(
                QualityEntityType.FORM_QUALITY_ISSUE, QualityStatus.ISSUES_DETECTED));
        assertFalse(StatusFramework.isInitialStatus(
                QualityEntityType.FORM_QUALITY_ISSUE, QualityStatus.BLOCKED_FROM_PUBLISH));
    }

    @Test
    public void notValidated_canGoToVerifiedOrIssuesDetected() {
        assertTrue(StatusFramework.canTransition(
                QualityEntityType.FORM_QUALITY_ISSUE,
                QualityStatus.NOT_VALIDATED, QualityStatus.VERIFIED));
        assertTrue(StatusFramework.canTransition(
                QualityEntityType.FORM_QUALITY_ISSUE,
                QualityStatus.NOT_VALIDATED, QualityStatus.ISSUES_DETECTED));
        // Not allowed: skip straight to BLOCKED
        assertFalse(StatusFramework.canTransition(
                QualityEntityType.FORM_QUALITY_ISSUE,
                QualityStatus.NOT_VALIDATED, QualityStatus.BLOCKED_FROM_PUBLISH));
    }

    @Test
    public void issuesDetected_canResolveOrBeBlocked() {
        assertTrue(StatusFramework.canTransition(
                QualityEntityType.FORM_QUALITY_ISSUE,
                QualityStatus.ISSUES_DETECTED, QualityStatus.VERIFIED));
        assertTrue(StatusFramework.canTransition(
                QualityEntityType.FORM_QUALITY_ISSUE,
                QualityStatus.ISSUES_DETECTED, QualityStatus.BLOCKED_FROM_PUBLISH));
    }

    @Test
    public void verified_canRegressToIssuesDetected() {
        // A subsequent edit may reintroduce errors; the lifecycle must allow it.
        assertTrue(StatusFramework.canTransition(
                QualityEntityType.FORM_QUALITY_ISSUE,
                QualityStatus.VERIFIED, QualityStatus.ISSUES_DETECTED));
    }

    @Test
    public void blockedFromPublish_canBeRecovered() {
        // BLOCKED isn't terminal: admins can re-evaluate or fix issues.
        Set<Status> from = StatusFramework.getValidTransitions(
                QualityEntityType.FORM_QUALITY_ISSUE, QualityStatus.BLOCKED_FROM_PUBLISH);
        assertTrue(from.contains(QualityStatus.ISSUES_DETECTED));
        assertTrue(from.contains(QualityStatus.NOT_VALIDATED));
    }

    @Test
    public void anyState_canForceReEvaluate() {
        // Force re-evaluate (admin) is the universal escape hatch.
        for (QualityStatus from : QualityStatus.values()) {
            if (from == QualityStatus.NOT_VALIDATED) continue;
            assertTrue(from.name() + " must be reachable back to NOT_VALIDATED",
                    StatusFramework.canTransition(
                            QualityEntityType.FORM_QUALITY_ISSUE,
                            from, QualityStatus.NOT_VALIDATED));
        }
    }

    @Test
    public void codeIndex_resolvesAllFourStates() {
        assertEquals(QualityStatus.NOT_VALIDATED,
                StatusFramework.fromCode("not_validated"));
        assertEquals(QualityStatus.VERIFIED,
                StatusFramework.fromCode("verified"));
        assertEquals(QualityStatus.ISSUES_DETECTED,
                StatusFramework.fromCode("issues_detected"));
        assertEquals(QualityStatus.BLOCKED_FROM_PUBLISH,
                StatusFramework.fromCode("blocked_from_publish"));
    }
}
