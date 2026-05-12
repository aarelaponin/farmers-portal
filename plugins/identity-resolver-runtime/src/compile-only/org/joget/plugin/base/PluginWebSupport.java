package org.joget.plugin.base;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Compile-only stub matching the jakarta-namespace PluginWebSupport interface
 * shipped by the deployed Joget DX 8 runtime. The local jw-community sources
 * still have javax, but the live runtime is jakarta. This stub lets us emit
 * bytecode with the correct jakarta signature so JVM resolves it against the
 * real runtime interface at load time. NOT packaged in the JAR — see repack.sh.
 */
public interface PluginWebSupport {
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException;
}
