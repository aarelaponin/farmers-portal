package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.CrudCreationResult;
import org.joget.apps.app.model.AppDefinition;
import org.joget.commons.util.LogUtil;

/**
 * Service class for coordinating CRUD (Datalist + Userview) creation.
 * This service orchestrates both datalist and userview creation and returns a unified result.
 */
public class CrudService {

    private static final String CLASS_NAME = CrudService.class.getName();
    private final DatalistService datalistService;
    private final UserviewService userviewService;

    /**
     * Constructor with service dependencies
     *
     * @param datalistService Service for creating datalists
     * @param userviewService Service for creating userviews
     */
    public CrudService(DatalistService datalistService, UserviewService userviewService) {
        this.datalistService = datalistService;
        this.userviewService = userviewService;
    }

    /**
     * Create a complete CRUD (Datalist + Userview) for a form.
     * This method coordinates the creation of both components.
     *
     * @param formId The form ID
     * @param formName The display name for the form
     * @param appDef The application definition
     * @param formJson The form JSON definition (used to extract columns for datalist)
     * @return CrudCreationResult indicating success or failure with IDs
     */
    public CrudCreationResult createCrud(String formId, String formName, AppDefinition appDef, String formJson) {
        try {
            LogUtil.info(CLASS_NAME, "Creating CRUD for form: " + formId);

            // Generate IDs
            String datalistId = ApiConstants.IdPrefixes.LIST + formId;
            String userviewId = ApiConstants.Defaults.DEFAULT_USERVIEW_ID;  // UserviewService uses default 'v' or existing userview

            // Step 1: Create datalist
            try {
                String datalistName = "List: " + formName;
                datalistService.createDatalist(formId, datalistName, appDef, formJson);
                LogUtil.info(CLASS_NAME, "Datalist created: " + datalistId);
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Failed to create datalist: " + e.getMessage());
                return CrudCreationResult.error("Datalist creation failed: " + e.getMessage());
            }

            // Step 2: Create userview (adds category to existing userview or creates new one)
            try {
                String userviewName = formName;
                userviewService.createUserview(formId, datalistId, userviewName, appDef);
                LogUtil.info(CLASS_NAME, "Userview updated/created: " + userviewId);
            } catch (Exception e) {
                LogUtil.error(CLASS_NAME, e, "Failed to create userview: " + e.getMessage());
                return CrudCreationResult.error("Datalist created but Userview creation failed: " + e.getMessage());
            }

            LogUtil.info(CLASS_NAME, "SUCCESS: CRUD created for form: " + formId);
            return CrudCreationResult.success(datalistId, userviewId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating CRUD: " + e.getMessage());
            return CrudCreationResult.error("Exception during CRUD creation: " + e.getMessage());
        }
    }
}
