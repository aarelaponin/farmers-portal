package global.govstack.ruleeditor.element;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginProperty;
import org.joget.plugin.base.PluginWebSupport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

/**
 * Rule Editor Web Resources
 *
 * Serves static files (CodeMirror JS/CSS, JRE mode) from the plugin JAR.
 *
 * Access via: /jw/web/json/plugin/global.govstack.ruleeditor.element.RuleEditorResources/service?file=xxx
 */
public class RuleEditorResources extends ExtDefaultPlugin implements PluginWebSupport {

    private static final String CLASS_NAME = RuleEditorResources.class.getName();

    // Content type mappings
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();
    static {
        CONTENT_TYPES.put("js", "application/javascript; charset=utf-8");
        CONTENT_TYPES.put("css", "text/css; charset=utf-8");
        CONTENT_TYPES.put("html", "text/html; charset=utf-8");
        CONTENT_TYPES.put("json", "application/json; charset=utf-8");
    }

    // Allowed static files
    private static final String[] ALLOWED_FILES = {
        "codemirror.min.js",
        "codemirror.min.css",
        "jre-mode.js",
        "jre-editor.js",
        "jre-editor.css"
    };

    @Override
    public String getName() {
        return "Rule Editor Resources";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Serves static resources for the Rule Editor (CodeMirror, CSS, etc.)";
    }

    @Override
    public PluginProperty[] getPluginProperties() {
        return null;
    }

    @Override
    public Object execute(Map properties) {
        return null;
    }

    /**
     * Handle web requests for static resources.
     */
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String file = request.getParameter("file");

        // Set CORS headers
        response.setHeader("Access-Control-Allow-Origin", "*");

        // No file parameter - return info
        if (file == null || file.isEmpty()) {
            response.setContentType("application/json; charset=utf-8");
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"status\": \"ok\",\n");
            json.append("  \"plugin\": \"").append(CLASS_NAME).append("\",\n");
            json.append("  \"version\": \"").append(getVersion()).append("\",\n");
            json.append("  \"files\": ").append(Arrays.toString(ALLOWED_FILES)).append(",\n");
            json.append("  \"usage\": \"Add ?file=<filename> to URL\"\n");
            json.append("}");
            response.getWriter().write(json.toString());
            return;
        }

        // Serve static file
        serveStaticFile(file, response);
    }

    /**
     * Serve a static file from the JAR
     */
    private void serveStaticFile(String file, HttpServletResponse response) throws IOException {
        // Security: prevent directory traversal
        String sanitizedFile = file.replaceAll("\\.\\.", "")
                                   .replaceAll("/", "")
                                   .replaceAll("\\\\", "");

        // Check if file is allowed
        if (!isAllowedFile(sanitizedFile)) {
            LogUtil.warn(CLASS_NAME, "File not allowed: " + sanitizedFile);
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"File not found: " + sanitizedFile + "\"}");
            return;
        }

        // Try to find the resource
        String[] resourcePaths = {
            "/static/" + sanitizedFile,
            "static/" + sanitizedFile
        };

        InputStream is = null;
        for (String path : resourcePaths) {
            is = getClass().getResourceAsStream(path);
            if (is != null) break;
        }

        if (is == null) {
            ClassLoader cl = getClass().getClassLoader();
            for (String path : resourcePaths) {
                String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                is = cl.getResourceAsStream(cleanPath);
                if (is != null) break;
            }
        }

        if (is == null) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("{\"error\": \"Resource not found: " + sanitizedFile + "\"}");
            return;
        }

        try {
            // Set content type
            String extension = sanitizedFile.substring(sanitizedFile.lastIndexOf('.') + 1).toLowerCase();
            String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
            response.setContentType(contentType);
            // Disable caching to ensure latest version is always served
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");

            // Stream the file
            OutputStream os = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();

        } finally {
            is.close();
        }
    }

    private boolean isAllowedFile(String file) {
        for (String f : ALLOWED_FILES) {
            if (f.equals(file)) {
                return true;
            }
        }
        return false;
    }
}
