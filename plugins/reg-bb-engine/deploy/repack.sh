#!/usr/bin/env bash
# Build for reg-bb-engine — portable across macOS, Linux, and the
# sandbox build environment. Compiles all .java with javac, repackages
# an OSGi JAR while preserving META-INF/MANIFEST.MF.
#
# Build counter lives in Build.java (NUMBER constant). Visible in
# plugin descriptions and the Activator startup log line.
#
# Bump-only-on-success: Build.java is bumped AFTER compilation succeeds
# so a failed build doesn't leave a phantom build number.
#
# Override paths via env vars: JDK, M2, LIBS. Otherwise auto-detected.

set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
REPO_ROOT=$(cd "$ROOT/../.." && pwd)

# ---------------------------------------------------------------------------
# JDK auto-detection — needs JDK 11 or higher.
# Joget runs JDK 11; we compile with ---release 11 so newer JDKs also work.
# ---------------------------------------------------------------------------
if [ -n "${JDK:-}" ] && [ -x "$JDK/bin/javac" ]; then
    : # caller-provided JDK; trust it
elif [ "$(uname)" = "Darwin" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    # macOS: prefer JDK 11 specifically; fall back to any installed JDK >= 11
    JDK=$(/usr/libexec/java_home -v 11+ 2>/dev/null || true)
    [ -z "$JDK" ] && JDK=$(/usr/libexec/java_home -v 11 2>/dev/null || true)
fi

# Fallback: JAVA_HOME, then PATH (only if we don't already have a JDK)
if [ -z "${JDK:-}" ] || [ ! -x "$JDK/bin/javac" ]; then
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
        JDK="$JAVA_HOME"
    elif [ -x "$(command -v javac)" ] && [ -x "$(command -v jar)" ]; then
        JDK=$(dirname "$(dirname "$(command -v javac)")")
    else
        cat >&2 <<EOF
ERROR: cannot find a JDK 11+ with javac+jar.

Install options for macOS:
  brew install openjdk@11
  # then symlink so /usr/libexec/java_home finds it:
  sudo ln -sfn \$(brew --prefix openjdk@11)/libexec/openjdk.jdk \\
       /Library/Java/JavaVirtualMachines/openjdk-11.jdk

Or set JDK=/path/to/your/jdk before running.
EOF
        exit 1
    fi
fi

JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
[ -x "$JAVAC" ] || { echo "ERROR: javac not at $JAVAC" >&2; exit 1; }
[ -x "$JAR" ]   || { echo "ERROR: jar not at $JAR" >&2; exit 1; }

# Verify version. javac prints to stderr: "javac 11.0.30" or "javac 17.0.x" etc.
JAVAC_VERSION=$("$JAVAC" -version 2>&1 | awk '{print $2}' | cut -d. -f1)
if [ -z "$JAVAC_VERSION" ] || [ "$JAVAC_VERSION" -lt 11 ]; then
    echo "ERROR: $JAVAC reports version '$JAVAC_VERSION'; need JDK 11+." >&2
    echo "Selected JDK: $JDK" >&2
    cat >&2 <<EOF

You probably have an older JDK on your PATH. Either:
  - install JDK 11+:  brew install openjdk@11
  - or point this script at a newer JDK:
      JDK=\$(/usr/libexec/java_home -v 11) bash deploy/repack.sh
  - or set JAVA_HOME to a JDK 11+:
      export JAVA_HOME=\$(/usr/libexec/java_home -v 11)
EOF
    exit 1
fi
echo "→ using JDK $JAVAC_VERSION at $JDK"

# ---------------------------------------------------------------------------
# Library locations
# ---------------------------------------------------------------------------
M2="${M2:-${HOME}/.m2/repository}"
# Default LIBS: the jw-community Joget Tomcat lib dir embedded in the repo.
# This exists if jw-community has been built (mvn package on wflow-consoleweb).
LIBS="${LIBS:-${REPO_ROOT}/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"

OUT_JAR="$ROOT/reg-bb-engine-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/regbbeng-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/regbb/engine/Build.java"

# ---------------------------------------------------------------------------
# Compile (using current Build.java — no bump yet)
# ---------------------------------------------------------------------------
CP=""
add_cp() { for j in "$@"; do [ -f "$j" ] && CP="${CP:+$CP:}$j"; done; }

# Joget core jars from local Maven cache (preferred) or from jw-community/*/target/
add_cp "$M2/org/joget/wflow-commons/8.1-SNAPSHOT/wflow-commons-8.1-SNAPSHOT.jar"
add_cp "$M2/org/joget/wflow-plugin-base/8.1-SNAPSHOT/wflow-plugin-base-8.1-SNAPSHOT.jar"
add_cp "$M2/org/joget/wflow-core/8.1-SNAPSHOT/wflow-core-8.1-SNAPSHOT.jar"
add_cp "$M2/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar"
add_cp "$M2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar"

# Fallback: jw-community module target dirs (in case M2 isn't populated)
add_cp "${REPO_ROOT}/jw-community/wflow-commons/target/wflow-commons-8.1-SNAPSHOT.jar"
add_cp "${REPO_ROOT}/jw-community/wflow-plugin-base/target/wflow-plugin-base-8.1-SNAPSHOT.jar"
add_cp "${REPO_ROOT}/jw-community/wflow-core/target/wflow-core-8.1-SNAPSHOT.jar"
add_cp "${REPO_ROOT}/jw-community/wflow-wfengine/target/wflow-wfengine-8.1-SNAPSHOT.jar"
add_cp "${REPO_ROOT}/jw-community/wflow-directory/target/wflow-directory-8.1-SNAPSHOT.jar"

# api-builder (for ApiPluginAbstract used by RegBbEvalApi)
add_cp "${REPO_ROOT}/api-builder/apibuilder_api/target/apibuilder_api-8.1-SNAPSHOT.jar"

# joget-status-framework (shared lifecycle SPI)
add_cp "$M2/global/govstack/joget-status-framework/8.1-SNAPSHOT/joget-status-framework-8.1-SNAPSHOT.jar"
add_cp "${REPO_ROOT}/plugins/joget-status-framework/target/joget-status-framework-8.1-SNAPSHOT.jar"


# Joget Tomcat WEB-INF/lib for everything else: spring, servlet, postgres,
# hibernate, json, commons, felix.
if [ -d "$LIBS" ]; then
    for j in "$LIBS"/spring-*.jar "$LIBS"/javax.servlet-*.jar "$LIBS"/jakarta.servlet*.jar \
             "$LIBS"/postgresql-*.jar "$LIBS"/hibernate-core-*.jar "$LIBS"/json-*.jar \
             "$LIBS"/commons-lang3-*.jar "$LIBS"/commons-email-*.jar "$LIBS"/mail-*.jar \
             "$LIBS"/org.apache.felix.framework-*.jar; do
        [ -f "$j" ] && CP="${CP:+$CP:}$j"
    done
fi

if [ -z "$CP" ]; then
    echo "ERROR: classpath empty — no Joget jars found." >&2
    echo "Either:" >&2
    echo "  - run 'mvn install' on jw-community to populate ~/.m2 (preferred)" >&2
    echo "  - or build jw-community/wflow-consoleweb so $LIBS exists" >&2
    echo "  - or set LIBS=/path/to/joget/WEB-INF/lib" >&2
    exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/classes"

find "$ROOT/src/main/java" -name "*.java" > "$WORK/sources.txt"
echo "→ compiling $(wc -l < "$WORK/sources.txt" | tr -d ' ') source files..."

if ! "$JAVAC" --release 11 -cp "$CP" -d "$WORK/classes" @"$WORK/sources.txt"; then
    echo "ERROR: compilation failed; Build.java NOT bumped." >&2
    exit 1
fi
echo "→ compiled OK"

# ---------------------------------------------------------------------------
# Bump Build.java now (compile passed)
# ---------------------------------------------------------------------------
CURRENT=$(grep -oE 'NUMBER = [0-9]+' "$BUILD_FILE" | awk '{print $3}')
NEXT=$((CURRENT + 1))
TS=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
# Use -E for extended regex so `[0-9]+` works on both BSD sed (macOS) and
# GNU sed (Linux). `\+` is a GNU-only extension and silently fails on BSD,
# which would leave NUMBER unchanged — surprising and bad.
sed -E -i.bak \
    -e "s|public static final int    NUMBER = [0-9]+;|public static final int    NUMBER = ${NEXT};|" \
    -e "s|public static final String TIMESTAMP = \"[^\"]*\";|public static final String TIMESTAMP = \"${TS}\";|" \
    "$BUILD_FILE"
rm -f "$BUILD_FILE.bak"
# Verify the bump actually took effect — if sed silently no-ops we want to know.
ACTUAL=$(grep -oE 'NUMBER = [0-9]+' "$BUILD_FILE" | awk '{print $3}')
if [ "$ACTUAL" != "$NEXT" ]; then
    echo "ERROR: Build.java bump did not take effect (still NUMBER=$ACTUAL, expected $NEXT)." >&2
    echo "Check the sed pattern and the indentation in $BUILD_FILE." >&2
    exit 1
fi
echo "→ stamped Build.java: NUMBER=${NEXT}, TIMESTAMP=${TS}"

# Recompile just Build.java so the JAR carries the bumped stamp
"$JAVAC" --release 11 -cp "$CP" -d "$WORK/classes" "$BUILD_FILE"

# ---------------------------------------------------------------------------
# Copy resources (if any)
# ---------------------------------------------------------------------------
if [ -d "$ROOT/src/main/resources" ]; then
    cp -R "$ROOT/src/main/resources/." "$WORK/classes/" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# Inline embedded dependencies (replicates Maven's Embed-Dependency=inline=true)
# joget-status-framework is a plain (non-OSGi) JAR — its classes must live
# INSIDE this bundle's JAR or the OSGi classloader can't resolve them at
# runtime. Maven's maven-bundle-plugin does this via the pom's
# <Embed-Dependency> directive; in the sandbox we replicate by unzipping.
# Without this step, the bundle fails to start with:
#   NoClassDefFoundError: global/govstack/statusframework/api/EntityType
# (Diagnosed 2026-05-11 against build-129 — see CLAUDE.md gotchas section.)
# ---------------------------------------------------------------------------
SF_JAR=""
for candidate in \
    "$M2/global/govstack/joget-status-framework/8.1-SNAPSHOT/joget-status-framework-8.1-SNAPSHOT.jar" \
    "${REPO_ROOT}/plugins/joget-status-framework/target/joget-status-framework-8.1-SNAPSHOT.jar"
do
    [ -f "$candidate" ] && SF_JAR="$candidate" && break
done
if [ -n "$SF_JAR" ]; then
    echo "→ inlining $SF_JAR into bundle JAR"
    (cd "$WORK/classes" && "$JAR" xf "$SF_JAR" 2>/dev/null || true)
    # Remove the embedded MANIFEST.MF that would otherwise overwrite ours.
    rm -f "$WORK/classes/META-INF/MANIFEST.MF"
else
    echo "WARN: joget-status-framework JAR not found — bundle will fail to start at runtime" >&2
fi

# ---------------------------------------------------------------------------
# Repack — preserve OSGi MANIFEST.MF
# ---------------------------------------------------------------------------
mkdir -p "$WORK/classes/META-INF"
if [ -f "$OUT_JAR" ]; then
    (cd "$WORK/classes" && "$JAR" xf "$OUT_JAR" META-INF/MANIFEST.MF) 2>/dev/null || true
fi
if [ ! -f "$WORK/classes/META-INF/MANIFEST.MF" ]; then
    if [ -f "$ROOT/META-INF/MANIFEST.MF" ]; then
        cp "$ROOT/META-INF/MANIFEST.MF" "$WORK/classes/META-INF/MANIFEST.MF"
    else
        cat > "$WORK/classes/META-INF/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: RegBB Engine
Bundle-SymbolicName: global.govstack.reg-bb-engine
Bundle-Version: 8.1.0.SNAPSHOT
Bundle-Activator: global.govstack.regbb.engine.Activator
Bundle-RequiredExecutionEnvironment: JavaSE-11
Export-Package: global.govstack.regbb.engine.api;version="8.1.0",global.govstack.regbb.engine.evaluator;version="8.1.0"
DynamicImport-Package: *
EOF
    fi
fi

# Backup previous JAR
if [ -f "$OUT_JAR" ]; then
    cp "$OUT_JAR" "$OUT_JAR.bak-$(date +%s)"
fi

# Build the JAR
(cd "$WORK/classes" && "$JAR" cfm "$OUT_JAR" META-INF/MANIFEST.MF \
    $(find . -type f -not -path "./META-INF/*"))

echo "→ wrote $OUT_JAR"
ls -la "$OUT_JAR"
