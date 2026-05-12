package global.govstack.formcreator.util;

import global.govstack.formcreator.constants.ApiConstants;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.service.WorkflowUserManager;

import java.util.function.Supplier;

/**
 * Utility class for managing user context during API operations.
 * Ensures operations are executed with proper system user privileges.
 */
public class UserContextUtil {

    private static final String CLASS_NAME = UserContextUtil.class.getName();

    /**
     * Execute a supplier function as system user (admin).
     * Properly manages user context setup and cleanup.
     *
     * @param workflowUserManager The workflow user manager
     * @param supplier The supplier function to execute
     * @param <T> Return type
     * @return Result from the supplier function
     */
    public static <T> T executeAsSystemUser(WorkflowUserManager workflowUserManager, Supplier<T> supplier) {
        String originalUser = workflowUserManager.getCurrentUsername();

        try {
            // Set system user context
            workflowUserManager.setCurrentThreadUser(ApiConstants.SystemUser.USERNAME);
            LogUtil.debug(CLASS_NAME, "Set user context to: " + ApiConstants.SystemUser.USERNAME);

            // Execute the supplier function
            return supplier.get();

        } finally {
            // Restore original user context
            if (originalUser != null && !originalUser.isEmpty()) {
                workflowUserManager.setCurrentThreadUser(originalUser);
                LogUtil.debug(CLASS_NAME, "Restored user context to: " + originalUser);
            } else {
                workflowUserManager.clearCurrentThreadUser();
                LogUtil.debug(CLASS_NAME, "Cleared user context");
            }
        }
    }

    /**
     * Execute a runnable as system user (admin).
     * Properly manages user context setup and cleanup.
     *
     * @param workflowUserManager The workflow user manager
     * @param runnable The runnable to execute
     */
    public static void executeAsSystemUser(WorkflowUserManager workflowUserManager, Runnable runnable) {
        executeAsSystemUser(workflowUserManager, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Get the current username from the workflow user manager
     *
     * @param workflowUserManager The workflow user manager
     * @return Current username or null if not set
     */
    public static String getCurrentUsername(WorkflowUserManager workflowUserManager) {
        return workflowUserManager.getCurrentUsername();
    }

    /**
     * Check if the current user is a system user (admin)
     *
     * @param workflowUserManager The workflow user manager
     * @return true if current user is system user
     */
    public static boolean isSystemUser(WorkflowUserManager workflowUserManager) {
        String currentUser = workflowUserManager.getCurrentUsername();
        return ApiConstants.SystemUser.USERNAME.equals(currentUser);
    }
}
