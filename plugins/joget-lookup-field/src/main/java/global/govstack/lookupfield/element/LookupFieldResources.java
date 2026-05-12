package global.govstack.lookupfield.element;

import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * Plugin for serving static resources (CSS files) for the Lookup Field element.
 * Implements PluginWebSupport to handle HTTP requests for static files.
 *
 * Access via: /jw/web/json/plugin/global.govstack.lookupfield.element.LookupFieldResources/service?file=<filename>
 */
public class LookupFieldResources extends ExtDefaultPlugin implements PluginWebSupport {

    private static final String CLASS_NAME = LookupFieldResources.class.getName();

    public String getName() {
        return "Lookup Field Resources";
    }

    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    public String getDescription() {
        return "Serves static resources for the Lookup Field form element";
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String file = request.getParameter("file");

        if (file == null || file.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'file' parameter");
            return;
        }

        // Security: prevent directory traversal
        if (file.contains("..") || file.contains("/") || file.contains("\\")) {
            LogUtil.warn(CLASS_NAME, "Blocked potential directory traversal: " + file);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid file path");
            return;
        }

        // Determine content type
        String contentType = getContentType(file);
        response.setContentType(contentType);

        // Set caching headers
        String version = request.getParameter("v");
        if (version != null && !version.isEmpty()) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        } else {
            response.setHeader("Cache-Control", "public, max-age=3600");
        }

        // Load from classpath
        String resourcePath = "/static/" + file;
        try {
            URL resourceUrl = getClass().getResource(resourcePath);
            if (resourceUrl == null) {
                LogUtil.warn(CLASS_NAME, "Resource not found: " + resourcePath);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found: " + file);
                return;
            }

            try (InputStream in = resourceUrl.openStream();
                 OutputStream out = response.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error serving resource: " + file);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading resource");
        }
    }

    /**
     * Get MIME content type based on file extension.
     */
    private String getContentType(String filename) {
        String lower = filename.toLowerCase();

        if (lower.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (lower.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else {
            return "application/octet-stream";
        }
    }
}
