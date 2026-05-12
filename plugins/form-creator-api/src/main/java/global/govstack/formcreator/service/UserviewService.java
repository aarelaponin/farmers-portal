package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UserviewDefinition;
import org.joget.apps.app.dao.UserviewDefinitionDao;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Date;

/**
 * Service class for creating and updating userview definitions.
 * This service handles both file system and database operations for userview definitions.
 */
public class UserviewService {

    private static final String CLASS_NAME = UserviewService.class.getName();
    private final JsonProcessingService jsonProcessingService;

    /**
     * Constructor with JsonProcessingService dependency injection
     *
     * @param jsonProcessingService Service for JSON generation and processing
     */
    public UserviewService(JsonProcessingService jsonProcessingService) {
        this.jsonProcessingService = jsonProcessingService;
    }

    /**
     * Create a userview definition for a form with CRUD menu.
     * This method tries to add a category to an existing userview first.
     * If no userview exists, it creates a new one.
     *
     * @param formId The form ID to create the userview for
     * @param datalistId The datalist ID to link to the userview
     * @param userviewName The display name for the userview
     * @param appDef The application definition containing the target app
     */
    public void createUserview(String formId, String datalistId, String userviewName, AppDefinition appDef) {
        try {
            LogUtil.info(CLASS_NAME, "Creating/updating userview for form: " + formId);

            // Get UserviewDefinitionDao
            UserviewDefinitionDao userviewDefDao =
                (UserviewDefinitionDao) AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.USERVIEW_DEFINITION_DAO);

            if (userviewDefDao == null) {
                LogUtil.error(CLASS_NAME, null, "UserviewDefinitionDao not available");
                return;
            }

            // Try to find existing userview (first try 'v', then find any userview)
            UserviewDefinition existingUserview = userviewDefDao.loadById(ApiConstants.Defaults.DEFAULT_USERVIEW_ID, appDef);

            // If 'v' not found, find any existing userview in the app
            if (existingUserview == null) {
                Collection<UserviewDefinition> userviews =
                    userviewDefDao.getUserviewDefinitionList(null, appDef, null, null, null, null);
                if (userviews != null && !userviews.isEmpty()) {
                    existingUserview = userviews.iterator().next();
                }
            }

            if (existingUserview != null) {
                // Add category to existing userview
                LogUtil.info(CLASS_NAME, "Found existing userview: " + existingUserview.getId() + ", adding new category");
                addCategoryToUserview(existingUserview, formId, datalistId, userviewName, appDef, userviewDefDao);
            } else {
                // Create new userview
                LogUtil.info(CLASS_NAME, "No existing userview found, creating new one");
                createNewUserview(formId, datalistId, userviewName, appDef, userviewDefDao);
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating/updating userview: " + e.getMessage());
        }
    }

    /**
     * Add a new category with CRUD menu to an existing userview
     */
    public void addCategoryToUserview(UserviewDefinition existingUserview,
                                        String formId, String datalistId, String userviewName,
                                        AppDefinition appDef,
                                        UserviewDefinitionDao userviewDefDao) {
        try {
            String userviewId = existingUserview.getId();
            String existingJson = existingUserview.getJson();

            if (existingJson == null || existingJson.trim().isEmpty()) {
                LogUtil.error(CLASS_NAME, null, "Existing userview has no JSON content");
                return;
            }

            // Generate new category JSON
            String newCategoryJson = jsonProcessingService.generateCategoryJson(formId, datalistId, userviewName);

            // Find the categories array and insert the new category before the closing bracket
            int categoriesStart = existingJson.indexOf("\"" + ApiConstants.PropertyKeys.CATEGORIES + "\":");
            if (categoriesStart == -1) {
                LogUtil.error(CLASS_NAME, null, "Could not find categories array in existing userview JSON");
                return;
            }

            // Find the opening bracket of categories array
            int arrayStart = existingJson.indexOf("[", categoriesStart);
            if (arrayStart == -1) {
                LogUtil.error(CLASS_NAME, null, "Could not find categories array opening bracket");
                return;
            }

            // Find the matching closing bracket
            int arrayEnd = jsonProcessingService.findMatchingBracket(existingJson, arrayStart);
            if (arrayEnd == -1) {
                LogUtil.error(CLASS_NAME, null, "Could not find categories array closing bracket");
                return;
            }

            // Check if categories array is empty or has content
            String arrayContent = existingJson.substring(arrayStart + 1, arrayEnd).trim();
            boolean hasExistingCategories = !arrayContent.isEmpty() && !arrayContent.equals("");

            // Build updated JSON
            StringBuilder updatedJson = new StringBuilder();
            updatedJson.append(existingJson.substring(0, arrayEnd));

            if (hasExistingCategories) {
                // Add comma before new category
                updatedJson.append(",\n");
            }

            updatedJson.append(newCategoryJson);
            updatedJson.append(existingJson.substring(arrayEnd));

            // Update database
            existingUserview.setJson(updatedJson.toString());
            existingUserview.setDateModified(new Date());
            userviewDefDao.update(existingUserview);
            LogUtil.info(CLASS_NAME, "Updated userview in database: " + userviewId);

            // Update file system
            String jogetDir = System.getProperty("user.dir");
            String userviewFilePath = jogetDir + ApiConstants.Paths.APP_SRC + "/" + appDef.getAppId() + "/" +
                       appDef.getAppId() + "_" + appDef.getVersion() + ApiConstants.Paths.USERVIEW_DIR + "/" + userviewId + ApiConstants.Paths.JSON_EXTENSION;

            FileWriter fileWriter = new FileWriter(userviewFilePath);
            fileWriter.write(updatedJson.toString());
            fileWriter.close();
            LogUtil.info(CLASS_NAME, "Updated userview file: " + userviewFilePath);

            LogUtil.info(CLASS_NAME, "SUCCESS: Added category to existing userview: " + userviewId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error adding category to userview: " + e.getMessage());
        }
    }

    /**
     * Create a new standalone userview
     */
    public void createNewUserview(String formId, String datalistId, String userviewName,
                                    AppDefinition appDef,
                                    UserviewDefinitionDao userviewDefDao) {
        try {
            String userviewId = ApiConstants.Defaults.DEFAULT_USERVIEW_ID;  // Use 'v' as default ID
            String userviewJson = jsonProcessingService.generateUserviewDefinitionJson(formId, datalistId, userviewName, userviewId);

            if (userviewJson == null) {
                LogUtil.warn(CLASS_NAME, "Failed to generate userview JSON");
                return;
            }

            // Write to file system
            String jogetDir = System.getProperty("user.dir");
            String userviewDir = jogetDir + ApiConstants.Paths.APP_SRC + "/" + appDef.getAppId() + "/" +
                           appDef.getAppId() + "_" + appDef.getVersion() + ApiConstants.Paths.USERVIEW_DIR;

            File uvDirectory = new File(userviewDir);
            if (!uvDirectory.exists()) {
                uvDirectory.mkdirs();
            }

            String userviewFilePath = userviewDir + "/" + userviewId + ApiConstants.Paths.JSON_EXTENSION;
            FileWriter fileWriter = new FileWriter(userviewFilePath);
            fileWriter.write(userviewJson);
            fileWriter.close();
            LogUtil.info(CLASS_NAME, "Userview file created: " + userviewFilePath);

            // Save to database
            UserviewDefinition userviewDef = new UserviewDefinition();
            userviewDef.setAppId(appDef.getAppId());
            userviewDef.setAppVersion(appDef.getVersion());
            userviewDef.setId(userviewId);
            userviewDef.setName(userviewName);
            userviewDef.setJson(userviewJson);
            userviewDef.setDateCreated(new Date());
            userviewDef.setDateModified(new Date());
            userviewDef.setAppDefinition(appDef);

            userviewDefDao.add(userviewDef);
            LogUtil.info(CLASS_NAME, "Userview saved to database: " + userviewId);

            LogUtil.info(CLASS_NAME, "SUCCESS: Created new userview: " + userviewId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating new userview: " + e.getMessage());
        }
    }

    /**
     * Upsert a userview definition by id from a pre-built JSON. Used by
     * {@code POST /formcreator/userviews} to apply versioned
     * {@code _userviews/*.json} files via tooling — the canonical CI path
     * (matches the {@code DatalistService.upsertDatalist} pattern).
     *
     * @param appDef       target application
     * @param userviewId   the userview id (must match the id inside {@code userviewJson})
     * @param userviewName display label
     * @param userviewJson raw userview definition JSON
     * @return "inserted" or "updated"
     */
    public String upsertUserview(AppDefinition appDef, String userviewId,
                                 String userviewName, String userviewJson) {
        UserviewDefinitionDao dao = (UserviewDefinitionDao)
                AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.USERVIEW_DEFINITION_DAO);
        if (dao == null) {
            throw new IllegalStateException("UserviewDefinitionDao bean not available");
        }
        UserviewDefinition existing = dao.loadById(userviewId, appDef);
        if (existing != null) {
            existing.setName(userviewName != null ? userviewName : existing.getName());
            existing.setJson(userviewJson);
            existing.setDateModified(new Date());
            dao.update(existing);
            LogUtil.info(CLASS_NAME, "Userview updated: " + userviewId);
            return "updated";
        } else {
            UserviewDefinition fresh = new UserviewDefinition();
            fresh.setAppId(appDef.getAppId());
            fresh.setAppVersion(appDef.getVersion());
            fresh.setId(userviewId);
            fresh.setName(userviewName != null ? userviewName : userviewId);
            fresh.setJson(userviewJson);
            fresh.setDateCreated(new Date());
            fresh.setDateModified(new Date());
            fresh.setAppDefinition(appDef);
            dao.add(fresh);
            LogUtil.info(CLASS_NAME, "Userview inserted: " + userviewId);
            return "inserted";
        }
    }
}
