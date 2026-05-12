package global.govstack.registration.receiver.service;

import org.joget.apps.app.service.AppUtil;
import global.govstack.registration.receiver.config.ConfigLoader;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.springframework.context.ApplicationContext;

/**
 * Factory for creating services with all required dependencies
 */
public class RegistrationServiceFactory {

    /**
     * Creates a fully configured ApiRequestProcessor with all dependencies
     *
     * @return Configured ApiRequestProcessor ready for use
     */
    public static ApiRequestProcessor createRequestProcessor() {
        return (ApiRequestProcessor) createService(); // RegistrationService implements ApiRequestProcessor
    }

    /**
     * Creates a fully configured RegistrationService with all dependencies
     * Kept for backward compatibility
     *
     * @return Configured RegistrationService ready for use
     */
    public static RegistrationService createService() {
        // Get application context
        ApplicationContext appContext = AppUtil.getApplicationContext();

        // Get core services from Spring context
        WorkflowUserManager workflowUserManager = getBean(appContext, "workflowUserManager", WorkflowUserManager.class);
        WorkflowManager workflowManager = getBean(appContext, "workflowManager", WorkflowManager.class);

        // Create dependencies in the right order
        ConfigLoader configLoader = new ConfigLoader();

        ConfigurationService configService = new ConfigurationService(configLoader);
        WorkflowService workflowService = new WorkflowService(workflowManager, workflowUserManager);
        ResponseBuilder responseBuilder = new ResponseBuilder(workflowManager);

        // Create and return the main service
        return new RegistrationService(
                configService,
                workflowService,
                responseBuilder,
                workflowUserManager
        );
    }

    /**
     * Helper method to get a bean from Spring context with type safety
     *
     * @param context Application context
     * @param beanName Name of the bean
     * @param requiredType Required bean type
     * @return The requested bean
     * @param <T> Type of the bean
     */
    @SuppressWarnings("unchecked")
    private static <T> T getBean(ApplicationContext context, String beanName, Class<T> requiredType) {
        Object bean = context.getBean(beanName);
        if (requiredType.isInstance(bean)) {
            return (T) bean;
        } else {
            throw new IllegalStateException("Bean '" + beanName + "' is not of required type: " + requiredType.getName());
        }
    }
}