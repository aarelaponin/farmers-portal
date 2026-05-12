package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/**
 * Service class for creating datalist definitions.
 * This service handles both file system and database operations for datalist definitions.
 */
public class DatalistService {

    private static final String CLASS_NAME = DatalistService.class.getName();
    private final JsonProcessingService jsonProcessingService;

    /**
     * Constructor with JsonProcessingService dependency injection
     *
     * @param jsonProcessingService Service for JSON generation and processing
     */
    public DatalistService(JsonProcessingService jsonProcessingService) {
        this.jsonProcessingService = jsonProcessingService;
    }

    /**
     * Creates a datalist definition for a form using Joget's dual storage pattern.
     * This method creates both the file system and database entries required for the datalist to be visible in Joget UI.
     *
     * @param formId The form ID to create the datalist for
     * @param datalistName The display name for the datalist
     * @param appDef The application definition containing the target app
     * @param formJson The form JSON definition (used to extract columns)
     */
    public void createDatalist(String formId, String datalistName, AppDefinition appDef, String formJson) {
        try {
            LogUtil.info(CLASS_NAME, "Creating datalist for form: " + formId);

            // Generate datalist ID
            String datalistId = ApiConstants.IdPrefixes.LIST + formId;

            // Generate datalist definition JSON
            String datalistJson = jsonProcessingService.generateDatalistDefinitionJson(formId, datalistName, datalistId, formJson);

            if (datalistJson == null) {
                LogUtil.warn(CLASS_NAME, "Failed to generate datalist JSON");
                return;
            }

            // Step 1: Write datalist definition file to file system
            String jogetDir = System.getProperty("user.dir");
            String datalistDir = jogetDir + ApiConstants.Paths.APP_SRC + "/" + appDef.getAppId() + "/" +
                           appDef.getAppId() + "_" + appDef.getVersion() + "/lists";

            // Create directory if it doesn't exist
            File listDirectory = new File(datalistDir);
            if (!listDirectory.exists()) {
                boolean created = listDirectory.mkdirs();
                LogUtil.info(CLASS_NAME, "Datalist directory created: " + created + " at " + datalistDir);
            }

            // Write datalist definition file
            String datalistFilePath = datalistDir + "/" + datalistId + ApiConstants.Paths.JSON_EXTENSION;
            FileWriter fileWriter = new FileWriter(datalistFilePath);
            fileWriter.write(datalistJson);
            fileWriter.close();
            LogUtil.info(CLASS_NAME, "Datalist file created at: " + datalistFilePath);

            // Step 2: Save datalist definition to database using DatalistDefinitionDao
            DatalistDefinitionDao datalistDefDao =
                (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.DATALIST_DEFINITION_DAO);

            if (datalistDefDao != null) {
                DatalistDefinition datalistDef = new DatalistDefinition();
                datalistDef.setAppId(appDef.getAppId());
                datalistDef.setAppVersion(appDef.getVersion());
                datalistDef.setId(datalistId);
                datalistDef.setName(datalistName);
                datalistDef.setJson(datalistJson);
                datalistDef.setDateCreated(new Date());
                datalistDef.setDateModified(new Date());
                datalistDef.setAppDefinition(appDef);

                datalistDefDao.add(datalistDef);
                LogUtil.info(CLASS_NAME, "Datalist definition saved to database with ID: " + datalistId);
            } else {
                LogUtil.warn(CLASS_NAME, "DatalistDefinitionDao not available - datalist saved to file only");
            }

            LogUtil.info(CLASS_NAME, "SUCCESS: Datalist created: " + datalistId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating datalist: " + e.getMessage());
        }
    }

    /**
     * Upsert a datalist definition by id. Used by the {@code POST /formcreator/datalists}
     * endpoint to apply versioned {@code _datalists/*.json} files via tooling
     * rather than App Composer paste — the canonical CI path.
     *
     * <p>Joget's {@link DatalistDefinitionDao} treats {@code add()} as
     * insert-only and exposes a separate {@code update()} for modifications,
     * so we probe via {@code loadById()} first and route accordingly.
     *
     * @param appDef       target application
     * @param datalistId   the datalist id (must match the id inside {@code datalistJson})
     * @param datalistName display label
     * @param datalistJson raw datalist definition JSON (pre-built externally)
     * @return "inserted" or "updated"
     */
    public String upsertDatalist(AppDefinition appDef, String datalistId,
                                 String datalistName, String datalistJson) {
        DatalistDefinitionDao dao = (DatalistDefinitionDao)
                AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.DATALIST_DEFINITION_DAO);
        if (dao == null) {
            throw new IllegalStateException("DatalistDefinitionDao bean not available");
        }
        DatalistDefinition existing = dao.loadById(datalistId, appDef);
        if (existing != null) {
            existing.setName(datalistName != null ? datalistName : existing.getName());
            existing.setJson(datalistJson);
            existing.setDateModified(new Date());
            dao.update(existing);
            LogUtil.info(CLASS_NAME, "Datalist updated: " + datalistId);
            return "updated";
        } else {
            DatalistDefinition fresh = new DatalistDefinition();
            fresh.setAppId(appDef.getAppId());
            fresh.setAppVersion(appDef.getVersion());
            fresh.setId(datalistId);
            fresh.setName(datalistName != null ? datalistName : datalistId);
            fresh.setJson(datalistJson);
            fresh.setDateCreated(new Date());
            fresh.setDateModified(new Date());
            fresh.setAppDefinition(appDef);
            dao.add(fresh);
            LogUtil.info(CLASS_NAME, "Datalist inserted: " + datalistId);
            return "inserted";
        }
    }
}
