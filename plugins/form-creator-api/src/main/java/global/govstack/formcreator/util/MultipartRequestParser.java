package global.govstack.formcreator.util;

import global.govstack.formcreator.exception.ValidationException;
import org.joget.commons.util.LogUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for parsing multipart/form-data requests.
 * Handles both form fields and file uploads.
 */
public class MultipartRequestParser {

    private static final String CLASS_NAME = MultipartRequestParser.class.getName();

    /**
     * Parsed multipart data container
     */
    public static class MultipartData {
        private final Map<String, String> fields;
        private final Map<String, FileUpload> files;

        public MultipartData() {
            this.fields = new HashMap<>();
            this.files = new HashMap<>();
        }

        public Map<String, String> getFields() {
            return fields;
        }

        public Map<String, FileUpload> getFiles() {
            return files;
        }

        public void addField(String name, String value) {
            fields.put(name, value);
        }

        public void addFile(String name, String filename, byte[] content) {
            files.put(name, new FileUpload(filename, content));
        }
    }

    /**
     * File upload container
     */
    public static class FileUpload {
        private final String filename;
        private final byte[] content;

        public FileUpload(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }

        public String getFilename() {
            return filename;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentAsString() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    /**
     * Check if the request is multipart/form-data
     *
     * @param request HttpServletRequest
     * @return true if multipart
     */
    public static boolean isMultipartRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/form-data");
    }

    /**
     * Parse multipart request using simple boundary-based parsing.
     * This is a basic implementation for testing purposes.
     *
     * @param request HttpServletRequest
     * @return MultipartData with fields and files
     * @throws ValidationException if parsing fails
     */
    public static MultipartData parseMultipartRequest(HttpServletRequest request) {
        if (!isMultipartRequest(request)) {
            throw new ValidationException("Request is not multipart/form-data");
        }

        try {
            String contentType = request.getContentType();
            String boundary = extractBoundary(contentType);

            if (boundary == null) {
                throw new ValidationException("Missing boundary in multipart request");
            }

            LogUtil.debug(CLASS_NAME, "Parsing multipart request with boundary: " + boundary);

            // Read request body
            byte[] bodyBytes = readRequestBody(request);
            String body = new String(bodyBytes, StandardCharsets.UTF_8);

            // Parse multipart data
            MultipartData multipartData = parseMultipartBody(body, boundary);

            LogUtil.info(CLASS_NAME, "Parsed multipart request - Fields: " + multipartData.getFields().keySet() +
                        ", Files: " + multipartData.getFiles().keySet());

            return multipartData;

        } catch (IOException e) {
            LogUtil.error(CLASS_NAME, e, "Error reading multipart request");
            throw new ValidationException("Failed to read multipart request: " + e.getMessage(), e);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error parsing multipart request");
            throw new ValidationException("Failed to parse multipart request: " + e.getMessage(), e);
        }
    }

    /**
     * Extract boundary from Content-Type header
     */
    private static String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring("boundary=".length());
            }
        }
        return null;
    }

    /**
     * Read entire request body as bytes
     */
    private static byte[] readRequestBody(HttpServletRequest request) throws IOException {
        InputStream inputStream = request.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] data = new byte[8192];
        int bytesRead;

        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        return buffer.toByteArray();
    }

    /**
     * Parse multipart body into fields and files
     */
    private static MultipartData parseMultipartBody(String body, String boundary) {
        MultipartData data = new MultipartData();

        // Split by boundary
        String boundaryDelimiter = "--" + boundary;
        String[] parts = body.split(boundaryDelimiter);

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty() || part.equals("--")) {
                continue;
            }

            parsePart(part, data);
        }

        return data;
    }

    /**
     * Parse a single multipart part
     */
    private static void parsePart(String part, MultipartData data) {
        try {
            // Split headers from content
            int contentStart = part.indexOf("\r\n\r\n");
            if (contentStart == -1) {
                contentStart = part.indexOf("\n\n");
                if (contentStart == -1) {
                    return;
                }
                contentStart += 2;
            } else {
                contentStart += 4;
            }

            String headers = part.substring(0, contentStart);
            String content = part.substring(contentStart).trim();

            // Remove trailing \r\n
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }

            // Parse Content-Disposition header
            String name = extractHeaderValue(headers, "name");
            String filename = extractHeaderValue(headers, "filename");

            if (name == null) {
                return;
            }

            if (filename != null && !filename.isEmpty()) {
                // This is a file upload
                byte[] fileContent = content.getBytes(StandardCharsets.UTF_8);
                data.addFile(name, filename, fileContent);
                LogUtil.debug(CLASS_NAME, "Parsed file upload: " + name + " (" + filename + "), size: " + fileContent.length);
            } else {
                // This is a form field
                data.addField(name, content);
                LogUtil.debug(CLASS_NAME, "Parsed form field: " + name + " = " + content);
            }

        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error parsing multipart part: " + e.getMessage());
        }
    }

    /**
     * Extract value from Content-Disposition header
     * Example: Content-Disposition: form-data; name="fieldName"; filename="file.txt"
     */
    private static String extractHeaderValue(String headers, String attribute) {
        String searchPattern = attribute + "=\"";
        int startIndex = headers.indexOf(searchPattern);

        if (startIndex == -1) {
            return null;
        }

        startIndex += searchPattern.length();
        int endIndex = headers.indexOf("\"", startIndex);

        if (endIndex == -1) {
            return null;
        }

        return headers.substring(startIndex, endIndex);
    }
}
