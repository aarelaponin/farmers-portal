package global.govstack.statusframework.core;

import global.govstack.statusframework.api.EntityType;
import global.govstack.statusframework.api.InvalidTransitionException;
import global.govstack.statusframework.api.Status;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatusFramework}.
 * <p>
 * Uses local enums {@link TestEntity} and {@link TestStatus} to exercise the
 * framework without depending on any specific consumer (gam-plugins,
 * form-quality-runtime, etc.). Each test starts with a clean registry.
 */
public class StatusFrameworkTest {

    @Mock
    private FormDataDao mockDao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        StatusFramework.clearRegistry();
    }

    @After
    public void tearDown() {
        StatusFramework.clearRegistry();
    }

    // ════════════════════════════════════════════════════════════════
    //  Local fixture types
    // ════════════════════════════════════════════════════════════════

    enum TestEntity implements EntityType {
        ORDER("test_order"),
        SHIPMENT("test_shipment");

        private final String tableName;
        TestEntity(String t) { this.tableName = t; }
        @Override public String getTableName() { return tableName; }
    }

    enum TestStatus implements Status {
        DRAFT("draft", "Draft"),
        SUBMITTED("submitted", "Submitted"),
        APPROVED("approved", "Approved"),
        REJECTED("rejected", "Rejected"),
        CANCELLED("cancelled", "Cancelled"),
        SHIPPED("shipped", "Shipped"),
        DELIVERED("delivered", "Delivered");

        private final String code, label;
        TestStatus(String c, String l) { this.code = c; this.label = l; }
        @Override public String getCode()  { return code; }
        @Override public String getLabel() { return label; }
    }

    private void registerOrderLifecycle() {
        Map<Status, Set<Status>> tx = new LinkedHashMap<>();
        tx.put(TestStatus.DRAFT,     setOf(TestStatus.SUBMITTED, TestStatus.CANCELLED));
        tx.put(TestStatus.SUBMITTED, setOf(TestStatus.APPROVED, TestStatus.REJECTED));
        tx.put(TestStatus.APPROVED,  setOf(TestStatus.SHIPPED));
        tx.put(TestStatus.REJECTED,  Collections.emptySet());
        tx.put(TestStatus.CANCELLED, Collections.emptySet());
        tx.put(TestStatus.SHIPPED,   setOf(TestStatus.DELIVERED));
        tx.put(TestStatus.DELIVERED, Collections.emptySet());
        StatusFramework.register(TestEntity.ORDER, tx, setOf(TestStatus.DRAFT));
    }

    private static Set<Status> setOf(Status... s) {
        return new LinkedHashSet<>(Arrays.asList(s));
    }

    private FormRow rowWithStatus(String code) {
        FormRow row = new FormRow();
        if (code != null) row.setProperty("status", code);
        return row;
    }

    // ════════════════════════════════════════════════════════════════
    //  Registration
    // ════════════════════════════════════════════════════════════════

    @Test
    public void register_storesTransitions() {
        registerOrderLifecycle();
        Set<Status> targetsFromDraft = StatusFramework.getValidTransitions(
                TestEntity.ORDER, TestStatus.DRAFT);
        assertEquals(setOf(TestStatus.SUBMITTED, TestStatus.CANCELLED), targetsFromDraft);
    }

    @Test
    public void register_storesInitialStatuses() {
        registerOrderLifecycle();
        assertTrue(StatusFramework.isInitialStatus(TestEntity.ORDER, TestStatus.DRAFT));
        assertFalse(StatusFramework.isInitialStatus(TestEntity.ORDER, TestStatus.APPROVED));
    }

    @Test
    public void register_buildsCodeIndex() {
        registerOrderLifecycle();
        assertEquals(TestStatus.DRAFT,     StatusFramework.fromCode("draft"));
        assertEquals(TestStatus.SUBMITTED, StatusFramework.fromCode("submitted"));
        assertEquals(TestStatus.DELIVERED, StatusFramework.fromCode("delivered"));
    }

    @Test
    public void fromCode_caseInsensitive() {
        registerOrderLifecycle();
        assertEquals(TestStatus.APPROVED, StatusFramework.fromCode("APPROVED"));
        assertEquals(TestStatus.APPROVED, StatusFramework.fromCode("Approved"));
        assertEquals(TestStatus.APPROVED, StatusFramework.fromCode("aPpRoVeD"));
    }

    @Test
    public void fromCode_unknownReturnsNull() {
        registerOrderLifecycle();
        assertNull(StatusFramework.fromCode("nonexistent"));
        assertNull(StatusFramework.fromCode(""));
        assertNull(StatusFramework.fromCode(null));
    }

    @Test
    public void register_isImmutableAfterRegistration() {
        Map<Status, Set<Status>> mutable = new LinkedHashMap<>();
        mutable.put(TestStatus.DRAFT, new HashSet<>(Arrays.asList(TestStatus.APPROVED)));
        StatusFramework.register(TestEntity.ORDER, mutable, setOf(TestStatus.DRAFT));

        // Mutating the original map after registration must NOT affect the registry
        mutable.get(TestStatus.DRAFT).add(TestStatus.REJECTED);

        assertFalse(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DRAFT, TestStatus.REJECTED));
    }

    @Test(expected = NullPointerException.class)
    public void register_nullEntityThrows() {
        StatusFramework.register(null, Collections.emptyMap(), Collections.emptySet());
    }

    // ════════════════════════════════════════════════════════════════
    //  canTransition
    // ════════════════════════════════════════════════════════════════

    @Test
    public void canTransition_validHappyPath() {
        registerOrderLifecycle();
        assertTrue(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DRAFT, TestStatus.SUBMITTED));
        assertTrue(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.APPROVED, TestStatus.SHIPPED));
    }

    @Test
    public void canTransition_invalidPath() {
        registerOrderLifecycle();
        assertFalse(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DRAFT, TestStatus.SHIPPED));
        assertFalse(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DELIVERED, TestStatus.DRAFT));
    }

    @Test
    public void canTransition_nullCurrentAllowsInitialOnly() {
        registerOrderLifecycle();
        assertTrue(StatusFramework.canTransition(TestEntity.ORDER, null, TestStatus.DRAFT));
        assertFalse(StatusFramework.canTransition(TestEntity.ORDER, null, TestStatus.APPROVED));
    }

    @Test
    public void canTransition_unregisteredEntity_returnsFalse() {
        assertFalse(StatusFramework.canTransition(
                TestEntity.SHIPMENT, TestStatus.DRAFT, TestStatus.SUBMITTED));
    }

    @Test
    public void canTransition_nullArgs_returnFalse() {
        registerOrderLifecycle();
        assertFalse(StatusFramework.canTransition(null, TestStatus.DRAFT, TestStatus.APPROVED));
        assertFalse(StatusFramework.canTransition(TestEntity.ORDER, TestStatus.DRAFT, null));
    }

    // ════════════════════════════════════════════════════════════════
    //  getValidTransitions
    // ════════════════════════════════════════════════════════════════

    @Test
    public void getValidTransitions_terminalStatusReturnsEmpty() {
        registerOrderLifecycle();
        assertTrue(StatusFramework.getValidTransitions(
                TestEntity.ORDER, TestStatus.DELIVERED).isEmpty());
        assertTrue(StatusFramework.getValidTransitions(
                TestEntity.ORDER, TestStatus.REJECTED).isEmpty());
    }

    @Test
    public void getValidTransitions_unknownStatusReturnsEmpty() {
        registerOrderLifecycle();
        // SHIPPED is registered → has DELIVERED as target
        assertEquals(setOf(TestStatus.DELIVERED),
                StatusFramework.getValidTransitions(TestEntity.ORDER, TestStatus.SHIPPED));
    }

    // ════════════════════════════════════════════════════════════════
    //  transition() execution
    // ════════════════════════════════════════════════════════════════

    @Test
    public void transition_writesEntityAndAudit() throws Exception {
        registerOrderLifecycle();
        when(mockDao.load(null, "test_order", "O001"))
                .thenReturn(rowWithStatus("draft"));

        StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                TestStatus.SUBMITTED, "test", "submit it");

        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetArg = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, times(2))
                .saveOrUpdate(any(), tableArg.capture(), rowSetArg.capture());

        // Both writes happened
        List<String> tables = tableArg.getAllValues();
        assertTrue("entity-row write missing", tables.contains("test_order"));
        assertTrue("audit-row write missing", tables.contains("audit_log"));
    }

    @Test
    public void transition_setsNewStatusOnEntityRow() throws Exception {
        registerOrderLifecycle();
        FormRow current = rowWithStatus("draft");
        when(mockDao.load(null, "test_order", "O001")).thenReturn(current);

        StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                TestStatus.SUBMITTED, "test", "ok");

        // Inspect the entity row that was saved
        ArgumentCaptor<FormRowSet> rowSetArg = ArgumentCaptor.forClass(FormRowSet.class);
        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        verify(mockDao, times(2)).saveOrUpdate(any(), tableArg.capture(), rowSetArg.capture());
        FormRow entityRow = null;
        for (int i = 0; i < tableArg.getAllValues().size(); i++) {
            if ("test_order".equals(tableArg.getAllValues().get(i))) {
                entityRow = rowSetArg.getAllValues().get(i).get(0);
                break;
            }
        }
        assertNotNull(entityRow);
        assertEquals("submitted", entityRow.getProperty("status"));
    }

    @Test
    public void transition_writesAuditRowWithCorrectFields() throws Exception {
        registerOrderLifecycle();
        when(mockDao.load(null, "test_order", "O001"))
                .thenReturn(rowWithStatus("draft"));

        long before = Instant.now().toEpochMilli();
        StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                TestStatus.SUBMITTED, "unit-test", "submit");
        long after = Instant.now().toEpochMilli();

        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetArg = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, times(2)).saveOrUpdate(any(), tableArg.capture(), rowSetArg.capture());
        FormRow audit = null;
        for (int i = 0; i < tableArg.getAllValues().size(); i++) {
            if ("audit_log".equals(tableArg.getAllValues().get(i))) {
                audit = rowSetArg.getAllValues().get(i).get(0);
                break;
            }
        }
        assertNotNull("no audit row written", audit);
        assertNotNull(audit.getId());
        assertEquals("ORDER",      audit.getProperty("entity_type"));
        assertEquals("O001",       audit.getProperty("entity_id"));
        assertEquals("draft",      audit.getProperty("from_status"));
        assertEquals("submitted",  audit.getProperty("to_status"));
        assertEquals("unit-test",  audit.getProperty("triggered_by"));
        assertEquals("submit",     audit.getProperty("reason"));
        long ts = Instant.parse(audit.getProperty("timestamp")).toEpochMilli();
        assertTrue("timestamp out of bounds", ts >= before && ts <= after);
    }

    @Test
    public void transition_initialFromStatus_recordsLiteralNullString() throws Exception {
        registerOrderLifecycle();
        FormRow blank = new FormRow();
        when(mockDao.load(null, "test_order", "O002")).thenReturn(blank);

        StatusFramework.transition(mockDao, TestEntity.ORDER, "O002",
                TestStatus.DRAFT, "ui", "create");

        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetArg = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, times(2)).saveOrUpdate(any(), tableArg.capture(), rowSetArg.capture());
        FormRow audit = null;
        for (int i = 0; i < tableArg.getAllValues().size(); i++) {
            if ("audit_log".equals(tableArg.getAllValues().get(i))) {
                audit = rowSetArg.getAllValues().get(i).get(0);
                break;
            }
        }
        assertNotNull(audit);
        assertEquals("null", audit.getProperty("from_status"));
        assertEquals("draft", audit.getProperty("to_status"));
    }

    @Test(expected = InvalidTransitionException.class)
    public void transition_invalidThrows() throws Exception {
        registerOrderLifecycle();
        when(mockDao.load(null, "test_order", "O001"))
                .thenReturn(rowWithStatus("draft"));
        StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                TestStatus.SHIPPED, "test", "skip steps");
    }

    @Test
    public void transition_invalidDoesNotWriteToAudit() {
        registerOrderLifecycle();
        when(mockDao.load(null, "test_order", "O001"))
                .thenReturn(rowWithStatus("draft"));
        try {
            StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                    TestStatus.SHIPPED, "test", "");
            fail("should have thrown");
        } catch (InvalidTransitionException expected) {
            // Verify NEITHER entity-row NOR audit-row was written
            verify(mockDao, never()).saveOrUpdate(any(), anyString(), any());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void transition_recordNotFoundThrows() throws Exception {
        registerOrderLifecycle();
        when(mockDao.load(null, "test_order", "MISSING")).thenReturn(null);
        StatusFramework.transition(mockDao, TestEntity.ORDER, "MISSING",
                TestStatus.SUBMITTED, "test", "");
    }

    @Test(expected = IllegalStateException.class)
    public void transition_unrecognizedStatusInDbThrows() throws Exception {
        registerOrderLifecycle();
        FormRow weird = rowWithStatus("garbage_value");
        when(mockDao.load(null, "test_order", "O001")).thenReturn(weird);
        StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                TestStatus.SUBMITTED, "test", "");
    }

    @Test
    public void transition_customTableOverload_usesProvidedTable() throws Exception {
        registerOrderLifecycle();
        when(mockDao.load(null, "alt_order_table", "O001"))
                .thenReturn(rowWithStatus("draft"));

        StatusFramework.transition(mockDao, "alt_order_table", TestEntity.ORDER, "O001",
                TestStatus.SUBMITTED, "test", "alt table");

        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        verify(mockDao, times(2)).saveOrUpdate(any(), tableArg.capture(), any());
        assertTrue("alt table not used",
                tableArg.getAllValues().contains("alt_order_table"));
    }

    @Test
    public void transition_emptyStatusField_treatsAsInitial() throws Exception {
        registerOrderLifecycle();
        FormRow empty = rowWithStatus("");
        when(mockDao.load(null, "test_order", "O001")).thenReturn(empty);

        StatusFramework.transition(mockDao, TestEntity.ORDER, "O001",
                TestStatus.DRAFT, "test", "");

        verify(mockDao, times(2)).saveOrUpdate(any(), anyString(), any());
    }

    // ════════════════════════════════════════════════════════════════
    //  Re-registration semantics
    // ════════════════════════════════════════════════════════════════

    @Test
    public void register_secondCallReplacesFirst() {
        Map<Status, Set<Status>> first = new LinkedHashMap<>();
        first.put(TestStatus.DRAFT, setOf(TestStatus.APPROVED));
        StatusFramework.register(TestEntity.ORDER, first, setOf(TestStatus.DRAFT));
        assertTrue(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DRAFT, TestStatus.APPROVED));

        Map<Status, Set<Status>> second = new LinkedHashMap<>();
        second.put(TestStatus.DRAFT, setOf(TestStatus.REJECTED));
        StatusFramework.register(TestEntity.ORDER, second, setOf(TestStatus.DRAFT));
        assertFalse(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DRAFT, TestStatus.APPROVED));
        assertTrue(StatusFramework.canTransition(
                TestEntity.ORDER, TestStatus.DRAFT, TestStatus.REJECTED));
    }
}
