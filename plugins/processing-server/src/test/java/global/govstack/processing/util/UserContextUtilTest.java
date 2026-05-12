package global.govstack.processing.util;

import org.joget.workflow.model.service.WorkflowUserManager;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for UserContextUtil
 * Verifies user context management works correctly with mocked WorkflowUserManager
 */
public class UserContextUtilTest {

    @Test
    public void testExecuteAsSystemUser() {
        // Mock the WorkflowUserManager
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);

        // Track if system user was set and cleared
        final AtomicBoolean systemUserSet = new AtomicBoolean(false);
        final AtomicBoolean systemUserCleared = new AtomicBoolean(false);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Boolean isSystem = invocation.getArgument(0);
                if (isSystem) {
                    systemUserSet.set(true);
                } else {
                    systemUserCleared.set(true);
                }
                return null;
            }
        }).when(mockUserManager).setSystemThreadUser(anyBoolean());

        // Execute operation
        String result = UserContextUtil.executeAsSystemUser(mockUserManager, () -> {
            assertTrue("System user must be set before operation", systemUserSet.get());
            return "test-result";
        });

        // Verify
        assertEquals("Result must be returned", "test-result", result);
        assertTrue("System user must have been set", systemUserSet.get());
        assertTrue("System user must have been cleared", systemUserCleared.get());
        verify(mockUserManager).setSystemThreadUser(true);
        verify(mockUserManager).setSystemThreadUser(false);
        verify(mockUserManager).clearCurrentThreadUser();
    }

    @Test
    public void testExecuteAsSystemUserWithNullManager() {
        // Test behavior when WorkflowUserManager is null
        String result = UserContextUtil.executeAsSystemUser(null, () -> "test-result");

        assertEquals("Operation must still execute", "test-result", result);
        // No exception should be thrown
    }

    @Test
    public void testExecuteAsSystemUserCleansUpOnException() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);

        try {
            UserContextUtil.executeAsSystemUser(mockUserManager, () -> {
                throw new RuntimeException("Test exception");
            });
            fail("Exception should have been propagated");
        } catch (RuntimeException e) {
            assertEquals("Exception must be propagated", "Test exception", e.getMessage());
        }

        // Verify cleanup happened despite exception
        verify(mockUserManager).setSystemThreadUser(true);
        verify(mockUserManager).clearCurrentThreadUser();
        verify(mockUserManager).setSystemThreadUser(false);
    }

    @Test
    public void testExecuteAsSpecificUser() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);
        when(mockUserManager.getCurrentUsername()).thenReturn("original-user");

        final AtomicReference<String> currentUser = new AtomicReference<>("original-user");

        // Track user changes
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                currentUser.set(invocation.getArgument(0));
                return null;
            }
        }).when(mockUserManager).setCurrentThreadUser(anyString());

        // Execute as different user
        String result = UserContextUtil.executeAsUser(
                mockUserManager,
                "test-user",
                "admin",
                () -> {
                    assertEquals("User must be switched", "test-user", currentUser.get());
                    return "operation-result";
                }
        );

        assertEquals("Result must be returned", "operation-result", result);
        verify(mockUserManager).setCurrentThreadUser("test-user");
        verify(mockUserManager).setCurrentThreadUser("original-user"); // Restored
    }

    @Test
    public void testExecuteAsUserRestoresToDefault() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);
        when(mockUserManager.getCurrentUsername()).thenReturn(null); // No original user

        // Execute operation
        UserContextUtil.executeAsUser(
                mockUserManager,
                "test-user",
                "default-user",
                () -> "result"
        );

        // Should restore to default since no original user
        verify(mockUserManager).setCurrentThreadUser("test-user");
        verify(mockUserManager).setCurrentThreadUser("default-user");
    }

    @Test
    public void testExecuteAsUserClearsIfNoRestore() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);
        when(mockUserManager.getCurrentUsername()).thenReturn(""); // Empty original user

        // Execute operation
        UserContextUtil.executeAsUser(
                mockUserManager,
                "test-user",
                null, // No default either
                () -> "result"
        );

        // Should clear user context
        verify(mockUserManager).setCurrentThreadUser("test-user");
        verify(mockUserManager).clearCurrentThreadUser();
    }

    @Test
    public void testExecuteAsUserCleansUpOnException() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);
        when(mockUserManager.getCurrentUsername()).thenReturn("original-user");

        try {
            UserContextUtil.executeAsUser(
                    mockUserManager,
                    "test-user",
                    "admin",
                    () -> {
                        throw new RuntimeException("Operation failed");
                    }
            );
            fail("Exception should have been propagated");
        } catch (RuntimeException e) {
            assertEquals("Operation failed", e.getMessage());
        }

        // Verify cleanup happened
        verify(mockUserManager).setCurrentThreadUser("test-user");
        verify(mockUserManager).setCurrentThreadUser("original-user");
    }

    @Test
    public void testExecuteAsUserWithNullManager() {
        // Test behavior when WorkflowUserManager is null
        String result = UserContextUtil.executeAsUser(
                null,
                "test-user",
                "admin",
                () -> "result"
        );

        assertEquals("Operation must still execute", "result", result);
        // No exception should be thrown
    }

    @Test
    public void testExecuteAsUserPreservesReturnValue() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);
        when(mockUserManager.getCurrentUsername()).thenReturn("admin");

        // Test with different return types
        String stringResult = UserContextUtil.executeAsUser(
                mockUserManager, "user", "admin", () -> "string-value"
        );
        assertEquals("string-value", stringResult);

        Integer intResult = UserContextUtil.executeAsUser(
                mockUserManager, "user", "admin", () -> 42
        );
        assertEquals(Integer.valueOf(42), intResult);

        Boolean boolResult = UserContextUtil.executeAsUser(
                mockUserManager, "user", "admin", () -> true
        );
        assertTrue(boolResult);
    }

    @Test
    public void testExecuteAsSystemUserPreservesReturnValue() {
        WorkflowUserManager mockUserManager = mock(WorkflowUserManager.class);

        // Test with different return types
        String stringResult = UserContextUtil.executeAsSystemUser(
                mockUserManager, () -> "system-result"
        );
        assertEquals("system-result", stringResult);

        Integer intResult = UserContextUtil.executeAsSystemUser(
                mockUserManager, () -> 100
        );
        assertEquals(Integer.valueOf(100), intResult);
    }
}
