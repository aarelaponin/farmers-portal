package global.govstack.registration.receiver.util;

import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.service.WorkflowUserManager;
import java.util.function.Supplier;

/**
 * Utility class for managing user context in workflow operations
 * Ensures proper cleanup of user context to prevent security and resource issues
 */
public class UserContextUtil {
    private static final String CLASS_NAME = UserContextUtil.class.getName();

    /**
     * Execute an operation as the system user with guaranteed cleanup
     *
     * @param workflowUserManager The workflow user manager
     * @param operation The operation to execute
     * @return The result of the operation
     * @param <T> The type of the result
     */
    public static <T> T executeAsSystemUser(WorkflowUserManager workflowUserManager, Supplier<T> operation) {
        if (workflowUserManager == null) {
            LogUtil.warn(CLASS_NAME, "WorkflowUserManager is null, operation will run without user context");
            return operation.get();
        }

        try {
            // Set system thread user for operations
            workflowUserManager.setSystemThreadUser(true);
            LogUtil.debug(CLASS_NAME, "Set system user context");
            return operation.get();
        } finally {
            // Always clean up user context properly, even if an exception occurs
            cleanUpUserContext(workflowUserManager);
        }
    }

    /**
     * Execute an operation as a specific user with guaranteed cleanup
     *
     * @param workflowUserManager The workflow user manager
     * @param username The username to execute as
     * @param defaultRestoreUser User to restore to if original user is empty
     * @param operation The operation to execute
     * @return The result of the operation
     * @param <T> The type of the result
     */
    public static <T> T executeAsUser(WorkflowUserManager workflowUserManager, String username,
                                      String defaultRestoreUser, Supplier<T> operation) {
        if (workflowUserManager == null) {
            LogUtil.warn(CLASS_NAME, "WorkflowUserManager is null, operation will run without user context");
            return operation.get();
        }

        // Store the original user to restore later
        String originalUser = workflowUserManager.getCurrentUsername();
        LogUtil.debug(CLASS_NAME, "Current user before context switch: " +
                (originalUser != null ? originalUser : "none"));

        try {
            // Set the specified user for the current thread
            workflowUserManager.setCurrentThreadUser(username);
            LogUtil.debug(CLASS_NAME, "Set user context to: " + username);

            // Execute the operation with the new user context
            return operation.get();
        } finally {
            try {
                // Restore user context based on available information
                if (originalUser != null && !originalUser.isEmpty()) {
                    // Restore original user
                    workflowUserManager.setCurrentThreadUser(originalUser);
                    LogUtil.debug(CLASS_NAME, "Restored original user context: " + originalUser);
                } else if (defaultRestoreUser != null && !defaultRestoreUser.isEmpty()) {
                    // Restore to default user if original was empty
                    workflowUserManager.setCurrentThreadUser(defaultRestoreUser);
                    LogUtil.debug(CLASS_NAME, "Restored to default user context: " + defaultRestoreUser);
                } else {
                    // Clear user context as last resort
                    workflowUserManager.clearCurrentThreadUser();
                    LogUtil.debug(CLASS_NAME, "Cleared user context - no original or default user");
                }
            } catch (Exception e) {
                // Log but don't rethrow - this is cleanup code
                LogUtil.error(CLASS_NAME, e, "Error restoring user context");
            }
        }
    }

    /**
     * Safely clean up the user context
     * Called from finally blocks to ensure cleanup
     *
     * @param workflowUserManager The workflow user manager
     */
    private static void cleanUpUserContext(WorkflowUserManager workflowUserManager) {
        if (workflowUserManager != null) {
            try {
                workflowUserManager.clearCurrentThreadUser();
                workflowUserManager.setSystemThreadUser(false);
                LogUtil.debug(CLASS_NAME, "Cleaned up system user context");
            } catch (Exception e) {
                // Log but don't rethrow - this is cleanup code
                LogUtil.error(CLASS_NAME, e, "Error cleaning up user context");
            }
        }
    }
}