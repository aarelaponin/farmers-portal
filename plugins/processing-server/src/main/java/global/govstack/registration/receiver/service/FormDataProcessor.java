package global.govstack.registration.receiver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import global.govstack.registration.receiver.config.Constants;
import global.govstack.registration.receiver.exception.FormSubmissionException;
import global.govstack.registration.receiver.exception.ValidationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FormDataProcessor {

    /**
     * Process the farmer registration data by matching configuration fields with payload values
     *
     * @param configJson The configuration JSON as a string
     * @param payloadJson The payload JSON as a string
     * @return A Map containing field names and their corresponding values
     * @throws IOException If there's an error parsing the JSON
     * @throws ValidationException If mandatory fields are missing
     * @throws FormSubmissionException If there's an error in form processing
     */
    public static Map<String, String> processRegistrationData(String configJson, String payloadJson)
            throws IOException, ValidationException, FormSubmissionException {

        try {
            ObjectMapper mapper = new ObjectMapper();

            // Parse the configuration JSON
            JsonNode configNode = mapper.readTree(configJson);
            String formId = configNode.get(Constants.FORM_ID).asText();

            // Parse the payload JSON
            JsonNode payloadNode = mapper.readTree(payloadJson);
            JsonNode applicationData = payloadNode.get(Constants.APPLICATION_DATA);

            if (applicationData == null) {
                throw new FormSubmissionException("Missing application data in payload");
            }

            // Verify the formId matches
            if (!formId.equals(applicationData.get(Constants.FORM_ID).asText())) {
                throw new FormSubmissionException("FormId in configuration does not match formId in payload");
            }

            // Extract fields from payload and create a map for quick lookup
            Map<String, String> fieldValues = new HashMap<>();
            JsonNode payloadFields = applicationData.get(Constants.FIELDS);

            for (JsonNode field : payloadFields) {
                String fieldName = field.get(Constants.FIELD_NAME).asText();
                String fieldValue = field.get(Constants.FIELD_VALUE).asText();
                fieldValues.put(fieldName, fieldValue);
            }

            // Create the result map based on the configuration fields and validate mandatory fields
            Map<String, String> result = new HashMap<>();
            JsonNode configFields = configNode.get(Constants.FIELDS);
            List<String> missingMandatoryFields = new ArrayList<>();

            for (JsonNode configField : configFields) {
                String fieldName = configField.get(Constants.FIELD_NAME).asText();

                // Check if this field exists in the payload
                String fieldValue = fieldValues.getOrDefault(fieldName, "");
                result.put(fieldName, fieldValue);

                // Check if field is mandatory and if value is provided
                if (configField.has("mandatory") && configField.get("mandatory").asBoolean() &&
                        (fieldValue == null || fieldValue.trim().isEmpty())) {
                    missingMandatoryFields.add(fieldName);
                }
            }

            // Throw validation exception if mandatory fields are missing
            if (!missingMandatoryFields.isEmpty()) {
                throw new ValidationException("Missing mandatory fields: " + String.join(", ", missingMandatoryFields));
            }

            return result;
        } catch (IOException e) {
            throw new FormSubmissionException("Error parsing JSON: " + e.getMessage(), e);
        } catch (ValidationException e) {
            throw e;  // Re-throw validation exception
        } catch (FormSubmissionException e) {
            throw e;  // Re-throw form submission exception
        } catch (Exception e) {
            throw new FormSubmissionException("Error processing form data: " + e.getMessage(), e);
        }
    }
}