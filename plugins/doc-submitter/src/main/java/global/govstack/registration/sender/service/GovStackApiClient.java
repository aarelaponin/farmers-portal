package global.govstack.registration.sender.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joget.commons.util.LogUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP client for sending data to GovStack Processing Server API
 */
public class GovStackApiClient {

    private static final String CLASS_NAME = GovStackApiClient.class.getName();
    private final ObjectMapper mapper = new ObjectMapper();

    private String apiEndpoint;
    private String apiId;
    private String apiKey;
    private int connectionTimeout = 30000; // 30 seconds
    private int readTimeout = 60000; // 60 seconds

    /**
     * Constructor with configuration
     */
    public GovStackApiClient(String apiEndpoint, String apiId, String apiKey) {
        this.apiEndpoint = apiEndpoint;
        this.apiId = apiId;
        this.apiKey = apiKey;
    }

    /**
     * Send data to GovStack API
     * @param jsonPayload The GovStack-formatted JSON
     * @return Response from API
     */
    public ApiResponse sendToGovStack(String jsonPayload) {
        LogUtil.info(CLASS_NAME, "Sending data to GovStack API: " + apiEndpoint);

        HttpURLConnection conn = null;

        try {
            // Create connection
            URL url = new URL(apiEndpoint);
            conn = (HttpURLConnection) url.openConnection();

            // Configure connection
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            // Add authentication headers
            if (apiId != null && !apiId.trim().isEmpty()) {
                conn.setRequestProperty("api_id", apiId);
            }
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                conn.setRequestProperty("api_key", apiKey);
            }

            // Set timeouts
            conn.setConnectTimeout(connectionTimeout);
            conn.setReadTimeout(readTimeout);

            // Enable input/output
            conn.setDoOutput(true);
            conn.setDoInput(true);

            // Send request body
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(jsonPayload);
                writer.flush();
            }

            // Get response code
            int responseCode = conn.getResponseCode();
            LogUtil.info(CLASS_NAME, "API Response Code: " + responseCode);

            // Read response
            String responseBody = readResponse(conn, responseCode >= 200 && responseCode < 300);

            // Parse response
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setStatusCode(responseCode);
            apiResponse.setSuccess(responseCode >= 200 && responseCode < 300);
            apiResponse.setResponseBody(responseBody);

            // Try to extract specific fields from response
            try {
                JsonNode responseJson = mapper.readTree(responseBody);
                if (responseJson.has("success")) {
                    apiResponse.setSuccess(responseJson.get("success").asBoolean());
                }
                if (responseJson.has("applicationId")) {
                    apiResponse.setApplicationId(responseJson.get("applicationId").asText());
                }
                if (responseJson.has("message")) {
                    apiResponse.setMessage(responseJson.get("message").asText());
                }
                if (responseJson.has("errors")) {
                    apiResponse.setErrorDetails(responseJson.get("errors").toString());
                }
            } catch (Exception e) {
                LogUtil.warn(CLASS_NAME, "Could not parse response JSON: " + e.getMessage());
            }

            LogUtil.info(CLASS_NAME, "API call completed. Success: " + apiResponse.isSuccess());

            return apiResponse;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error calling GovStack API");

            ApiResponse errorResponse = new ApiResponse();
            errorResponse.setSuccess(false);
            errorResponse.setStatusCode(-1);
            errorResponse.setMessage("Error calling API: " + e.getMessage());
            errorResponse.setErrorDetails(e.toString());

            return errorResponse;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Read response from connection
     */
    private String readResponse(HttpURLConnection conn, boolean success) throws IOException {
        StringBuilder response = new StringBuilder();

        InputStream inputStream = success ? conn.getInputStream() : conn.getErrorStream();

        if (inputStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
        }

        return response.toString();
    }

    /**
     * Set custom headers if needed
     */
    public void setCustomHeaders(Map<String, String> headers, HttpURLConnection conn) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    // Getters and setters for configuration
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Response wrapper class
     */
    public static class ApiResponse {
        private boolean success;
        private int statusCode;
        private String responseBody;
        private String message;
        private String applicationId;
        private String errorDetails;

        // Getters and setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public void setResponseBody(String responseBody) {
            this.responseBody = responseBody;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getErrorDetails() {
            return errorDetails;
        }

        public void setErrorDetails(String errorDetails) {
            this.errorDetails = errorDetails;
        }
    }
}