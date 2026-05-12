#!/usr/bin/env bash
# Sandbox-friendly build for identity-resolver-runtime — same pattern as
# form-quality-runtime/deploy/repack.sh. Bumps Build.java, compiles all
# .java with javac, repackages an OSGi JAR while preserving META-INF/MANIFEST.MF.
#
# Build counter lives in Build.java (NUMBER constant). Visible in plugin
# descriptions (Phase 2.3+) and the Activator startup log line.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK="${JDK:-/sessions/bold-focused-pasteur/jdk}"
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
M2="${M2:-/sessions/bold-focused-pasteur/.m2/repository}"
LIBS="${LIBS:-/sessions/bold-focused-pasteur/mnt/lst-frm-prj/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"
OUT_JAR="$ROOT/identity-resolver-runtime-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/idr-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/identity/Build.java"

# 1) Bump build counter + stamp.
CURRENT=$(grep -oE 'NUMBER = [0-9]+' "$BUILD_FILE" | awk '{print $3}')
NEXT=$((CURRENT + 1))
TS=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
sed -i.bak \
    -e "s|public static final int    NUMBER = [0-9]\+;|public static final int    NUMBER = ${NEXT};|" \
    -e "s|public static final String TIMESTAMP = \".*\";|public static final String TIMESTAMP = \"${TS}\";|" \
    "$BUILD_FILE"
rm -f "$BUILD_FILE.bak"
echo "→ stamped Build.java: NUMBER=${NEXT}, TIMESTAMP=${TS}"

# 2) Compile.
CP="$M2/org/joget/wflow-commons/8.1-SNAPSHOT/wflow-commons-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/joget/wflow-plugin-base/8.1-SNAPSHOT/wflow-plugin-base-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/joget/wflow-core/8.1-SNAPSHOT/wflow-core-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar"
CP="$CP:$M2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar"
for j in "$LIBS"/spring-*.jar "$LIBS"/javax.servlet-*.jar "$LIBS"/jakarta.servlet*.jar \
         "$LIBS"/postgresql-*.jar "$LIBS"/hibernate-core-*.jar "$LIBS"/json-*.jar \
         "$LIBS"/commons-lang3-*.jar; do
    [ -f "$j" ] && CP="$CP:$j"
done

rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
# Compile against the local jakarta stub of PluginWebSupport so the bytecode's
# method signature uses jakarta.servlet — matching the deployed Joget DX 8
# runtime. The stub itself is NOT packaged; runtime resolves to the real
# interface via OSGi DynamicImport.
"$JAVAC" --release 11 -d "$WORK/classes" -cp "$CP" \
    $(find "$ROOT/src/main/java" "$ROOT/src/compile-only" -name "*.java")
# Drop the compile-only stub class — runtime supplies the real one
rm -rf "$WORK/classes/org/joget/plugin/base"
find "$WORK/classes/org" -type d -empty -delete 2>/dev/null || true
echo "→ compiled $(find "$WORK/classes" -name "*.class" | wc -l) classes"

# 3) Build manifest manually (no prior JAR to extract).
mkdir -p "$WORK/meta"
cat > "$WORK/meta/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Identity Resolver Runtime
Bundle-SymbolicName: identity-resolver-runtime
Bundle-Version: 8.1.0.SNAPSHOT
Bundle-Activator: global.govstack.identity.Activator
Import-Package: org.osgi.framework;version="1.3.0"
DynamicImport-Package: *
Created-By: javac (sandbox repack)

EOF

# 4) Stage classes only — no forms / no datalists.
#
# Why: Joget scans the bundle JAR's forms/ and datalists/ directories on plugin
# upload and verifies every referenced className is resolvable. Joget core
# classes like org.joget.apps.datalist.lib.LinkDataListAction are NOT inside
# this bundle, so Joget falsely warns "plugin not installed". The JSON forms +
# datalists are deployed separately into app_form / app_datalist via SQL, so
# bundling them here is redundant anyway. Source-of-truth stays in
# src/main/resources/{forms,datalists}/ for diff-friendliness.
rm -rf "$WORK/jar"
mkdir -p "$WORK/jar"
cp -r "$WORK/classes/." "$WORK/jar/"

( cd "$WORK/jar" && \
  "$JAR" cfm "$OUT_JAR" "$WORK/meta/MANIFEST.MF" \
    $(find . -type f | sed 's|^\./||') )

echo "→ repackaged $OUT_JAR ($(stat -c %s "$OUT_JAR") bytes)"
echo
echo "Build stamp baked into JAR:"
unzip -p "$OUT_JAR" global/govstack/identity/Build.class | strings | grep -E '^build-|^[0-9]{4}-' | head -5
echo
echo "Done. Upload $OUT_JAR via Manage Plugins."
