package global.govstack.gisui.element;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GisResourcesPlugin.
 *
 * Tests cover:
 * - Directory traversal protection
 * - File extension whitelist validation
 * - Content type detection
 * - Security headers
 */
@ExtendWith(MockitoExtension.class)
class GisResourcesPluginTest {

    private GisResourcesPlugin plugin;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        plugin = new GisResourcesPlugin();
    }

    // =============================================
    // Directory Traversal Protection Tests
    // =============================================

    @Nested
    @DisplayName("Directory Traversal Protection")
    class DirectoryTraversalTests {

        @Test
        @DisplayName("Basic directory traversal pattern should be detected")
        void basicTraversal() throws Exception {
            assertTrue(invokeContainsTraversalPattern(".."));
            assertTrue(invokeContainsTraversalPattern("../"));
            assertTrue(invokeContainsTraversalPattern("..\\"));
        }

        @Test
        @DisplayName("Path separator should be detected")
        void pathSeparator() throws Exception {
            assertTrue(invokeContainsTraversalPattern("foo/bar"));
            assertTrue(invokeContainsTraversalPattern("foo\\bar"));
        }

        @Test
        @DisplayName("URL-encoded traversal patterns should be detected")
        void urlEncodedTraversal() throws Exception {
            assertTrue(invokeContainsTraversalPattern("%2e%2e"));
            assertTrue(invokeContainsTraversalPattern("%2f"));
            assertTrue(invokeContainsTraversalPattern("%5c"));
        }

        @Test
        @DisplayName("Valid filename should pass")
        void validFilename() throws Exception {
            assertFalse(invokeContainsTraversalPattern("gis-capture.js"));
            assertFalse(invokeContainsTraversalPattern("gis-capture.css"));
            assertFalse(invokeContainsTraversalPattern("valid-file.json"));
        }
    }

    // =============================================
    // File Extension Whitelist Tests
    // =============================================

    @Nested
    @DisplayName("File Extension Whitelist")
    class FileExtensionTests {

        @Test
        @DisplayName("JavaScript files should be allowed")
        void javascriptAllowed() throws Exception {
            assertTrue(invokeHasAllowedExtension("script.js"));
            assertTrue(invokeHasAllowedExtension("SCRIPT.JS"));
        }

        @Test
        @DisplayName("CSS files should be allowed")
        void cssAllowed() throws Exception {
            assertTrue(invokeHasAllowedExtension("style.css"));
            assertTrue(invokeHasAllowedExtension("STYLE.CSS"));
        }

        @Test
        @DisplayName("JSON files should be allowed")
        void jsonAllowed() throws Exception {
            assertTrue(invokeHasAllowedExtension("config.json"));
        }

        @Test
        @DisplayName("Image files should be allowed")
        void imagesAllowed() throws Exception {
            assertTrue(invokeHasAllowedExtension("icon.png"));
            assertTrue(invokeHasAllowedExtension("photo.jpg"));
            assertTrue(invokeHasAllowedExtension("photo.jpeg"));
            assertTrue(invokeHasAllowedExtension("animation.gif"));
            assertTrue(invokeHasAllowedExtension("logo.svg"));
        }

        @Test
        @DisplayName("Font files should be allowed")
        void fontsAllowed() throws Exception {
            assertTrue(invokeHasAllowedExtension("font.woff"));
            assertTrue(invokeHasAllowedExtension("font.woff2"));
            assertTrue(invokeHasAllowedExtension("font.ttf"));
            assertTrue(invokeHasAllowedExtension("font.eot"));
        }

        @Test
        @DisplayName("Executable files should be rejected")
        void executablesRejected() throws Exception {
            assertFalse(invokeHasAllowedExtension("malware.exe"));
            assertFalse(invokeHasAllowedExtension("script.sh"));
            assertFalse(invokeHasAllowedExtension("script.bat"));
        }

        @Test
        @DisplayName("Server-side script files should be rejected")
        void serverScriptsRejected() throws Exception {
            assertFalse(invokeHasAllowedExtension("page.php"));
            assertFalse(invokeHasAllowedExtension("page.jsp"));
            assertFalse(invokeHasAllowedExtension("page.aspx"));
            assertFalse(invokeHasAllowedExtension("Class.java"));
            assertFalse(invokeHasAllowedExtension("Class.class"));
        }

        @Test
        @DisplayName("No extension should be rejected")
        void noExtension() throws Exception {
            assertFalse(invokeHasAllowedExtension("README"));
            assertFalse(invokeHasAllowedExtension("passwd"));
        }
    }

    // =============================================
    // Content Type Detection Tests
    // =============================================

    @Nested
    @DisplayName("Content Type Detection")
    class ContentTypeTests {

        @Test
        @DisplayName("JavaScript should return correct content type")
        void javascriptContentType() throws Exception {
            String contentType = invokeGetContentType("script.js");
            assertEquals("application/javascript; charset=UTF-8", contentType);
        }

        @Test
        @DisplayName("CSS should return correct content type")
        void cssContentType() throws Exception {
            String contentType = invokeGetContentType("style.css");
            assertEquals("text/css; charset=UTF-8", contentType);
        }

        @Test
        @DisplayName("JSON should return correct content type")
        void jsonContentType() throws Exception {
            String contentType = invokeGetContentType("config.json");
            assertEquals("application/json; charset=UTF-8", contentType);
        }

        @Test
        @DisplayName("PNG should return correct content type")
        void pngContentType() throws Exception {
            String contentType = invokeGetContentType("icon.png");
            assertEquals("image/png", contentType);
        }

        @Test
        @DisplayName("JPEG should return correct content type")
        void jpegContentType() throws Exception {
            assertEquals("image/jpeg", invokeGetContentType("photo.jpg"));
            assertEquals("image/jpeg", invokeGetContentType("photo.jpeg"));
        }

        @Test
        @DisplayName("SVG should return correct content type")
        void svgContentType() throws Exception {
            String contentType = invokeGetContentType("logo.svg");
            assertEquals("image/svg+xml", contentType);
        }

        @Test
        @DisplayName("Unknown extension should return octet-stream")
        void unknownContentType() throws Exception {
            String contentType = invokeGetContentType("unknown.xyz");
            assertEquals("application/octet-stream", contentType);
        }
    }

    // =============================================
    // Web Service Integration Tests
    // =============================================

    @Nested
    @DisplayName("Web Service Security")
    class WebServiceSecurityTests {

        @Test
        @DisplayName("Missing file parameter should return bad request")
        void missingFileParameter() throws Exception {
            when(request.getParameter("file")).thenReturn(null);

            plugin.webService(request, response);

            verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
        }

        @Test
        @DisplayName("Empty file parameter should return bad request")
        void emptyFileParameter() throws Exception {
            when(request.getParameter("file")).thenReturn("");

            plugin.webService(request, response);

            verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
        }

        @Test
        @DisplayName("Directory traversal attempt should return forbidden")
        void directoryTraversalAttempt() throws Exception {
            when(request.getParameter("file")).thenReturn("../../../etc/passwd");

            plugin.webService(request, response);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        }

        @Test
        @DisplayName("URL-encoded traversal attempt should return forbidden")
        void urlEncodedTraversalAttempt() throws Exception {
            when(request.getParameter("file")).thenReturn("%2e%2e%2f%2e%2e%2fetc%2fpasswd");

            plugin.webService(request, response);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        }

        @Test
        @DisplayName("Disallowed file type should return forbidden")
        void disallowedFileType() throws Exception {
            when(request.getParameter("file")).thenReturn("malicious.php");

            plugin.webService(request, response);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        }
    }

    // =============================================
    // Plugin Metadata Tests
    // =============================================

    @Nested
    @DisplayName("Plugin Metadata")
    class PluginMetadataTests {

        @Test
        @DisplayName("Plugin name should be set")
        void pluginName() {
            assertEquals("GIS Resources Provider", plugin.getName());
        }

        @Test
        @DisplayName("Plugin version should match project version")
        void pluginVersion() {
            assertEquals("8.1-SNAPSHOT", plugin.getVersion());
        }

        @Test
        @DisplayName("Plugin description should be set")
        void pluginDescription() {
            assertNotNull(plugin.getDescription());
            assertTrue(plugin.getDescription().contains("GIS"));
        }
    }

    // =============================================
    // Helper Methods for Reflection
    // =============================================

    private boolean invokeContainsTraversalPattern(String path) throws Exception {
        Method method = GisResourcesPlugin.class.getDeclaredMethod("containsTraversalPattern", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(plugin, path);
    }

    private boolean invokeHasAllowedExtension(String filename) throws Exception {
        Method method = GisResourcesPlugin.class.getDeclaredMethod("hasAllowedExtension", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(plugin, filename);
    }

    private String invokeGetContentType(String filename) throws Exception {
        Method method = GisResourcesPlugin.class.getDeclaredMethod("getContentType", String.class);
        method.setAccessible(true);
        return (String) method.invoke(plugin, filename);
    }
}
