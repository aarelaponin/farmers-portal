#!/usr/bin/env bash
# Sandbox-friendly build for decision-engine-runtime — same pattern as
# application-engine-runtime and identity-resolver-runtime.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK="${JDK:-/sessions/bold-focused-pasteur/jdk}"
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
M2="${M2:-/sessions/bold-focused-pasteur/.m2/repository}"
LIBS="${LIBS:-/sessions/bold-focused-pasteur/mnt/lst-frm-prj/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"
OUT_JAR="$ROOT/decision-engine-runtime-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/de-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/decision/Build.java"

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
CP="$CP:$M2/org/joget/wflow-directory/8.1-SNAPSHOT/wflow-directory-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/joget/wflow-wfengine/8.1-SNAPSHOT/wflow-wfengine-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar"
CP="$CP:$M2/javax/servlet/javax.servlet-api/4.0.1/javax.servlet-api-4.0.1.jar"
for j in "$LIBS"/spring-*.jar "$LIBS"/postgresql-*.jar "$LIBS"/hibernate-core-*.jar \
         "$LIBS"/json-*.jar "$LIBS"/commons-lang3-*.jar; do
    [ -f "$j" ] && CP="$CP:$j"
done

rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
"$JAVAC" --release 11 -d "$WORK/classes" -cp "$CP" \
    $(find "$ROOT/src/main/java" -name "*.java")
echo "→ compiled $(find "$WORK/classes" -name "*.class" | wc -l) classes"

# 3) Manifest.
mkdir -p "$WORK/meta"
cat > "$WORK/meta/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Decision Engine Runtime
Bundle-SymbolicName: decision-engine-runtime
Bundle-Version: 8.1.0.SNAPSHOT
Bundle-Activator: global.govstack.decision.Activator
Import-Package: org.osgi.framework;version="1.3.0"
DynamicImport-Package: *
Created-By: javac (sandbox repack)

EOF

rm -rf "$WORK/jar"
mkdir -p "$WORK/jar"
cp -r "$WORK/classes/." "$WORK/jar/"

( cd "$WORK/jar" && \
  "$JAR" cfm "$OUT_JAR" "$WORK/meta/MANIFEST.MF" \
    $(find . -type f | sed 's|^\./||') )

echo "→ repackaged $OUT_JAR ($(stat -c %s "$OUT_JAR") bytes)"
echo
echo "Build stamp baked into JAR:"
unzip -p "$OUT_JAR" global/govstack/decision/Build.class | strings | grep -E '^build-|^[0-9]{4}-' | head -5
echo
echo "Done. Upload $OUT_JAR via Manage Plugins."
