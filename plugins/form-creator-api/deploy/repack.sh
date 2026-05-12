#!/usr/bin/env bash
# Sandbox-friendly build for form-creator-api — same pattern as
# reg-bb-engine/deploy/repack.sh and reg-bb-publisher/deploy/repack.sh.
# Bumps Build.java, compiles all .java with javac, repacks the OSGi JAR
# while preserving META-INF/MANIFEST.MF and the embedded dependency/ folder.
#
# Build counter lives in Build.java (NUMBER constant). Visible in the
# plugin description on Manage Plugins and in tomcat logs at startup.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK="${JDK:-/tmp/jdk-11.0.24+8}"
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
LIBS="${LIBS:-/sessions/admiring-determined-rubin/mnt/lst-frm-prj/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"
APIBUILDER="${APIBUILDER:-/sessions/admiring-determined-rubin/mnt/lst-frm-prj/api-builder/apibuilder_api/target/apibuilder_api-8.1-SNAPSHOT.jar}"
SERVLET="${SERVLET:-/tmp/javax.servlet-api-4.0.1.jar}"
OUT_JAR="$ROOT/target/form-creator-api-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/fcapi-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/formcreator/Build.java"

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

# 2) Stage workspace + extract embedded deps from current OUT_JAR (so we can
#    keep the same MANIFEST.MF and dependency/ folder on the next pack).
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/embedded"
( cd "$WORK/embedded" && "$JAR" xf "$OUT_JAR" )

# 3) Compile all sources.
CP="$APIBUILDER:$SERVLET"
for j in "$LIBS"/wflow-*.jar "$LIBS"/spring-*.jar "$LIBS"/json-*.jar \
         "$LIBS"/gson-*.jar "$LIBS"/jackson-*.jar "$LIBS"/commons-*.jar \
         "$LIBS"/displaytag-*.jar \
         "$LIBS"/org.apache.felix.framework-*.jar; do
    [ -f "$j" ] && CP="$CP:$j"
done
for j in "$WORK/embedded"/dependency/*.jar; do
    [ -f "$j" ] && CP="$CP:$j"
done

find "$ROOT/src/main/java" -name "*.java" > "$WORK/sources.txt"
"$JAVAC" --release 11 -cp "$CP" -d "$WORK/classes" @"$WORK/sources.txt"
echo "→ compiled $(wc -l < "$WORK/sources.txt") source files"

# 4) Copy resources (currently just /properties JSON if present).
if [ -d "$ROOT/src/main/resources" ]; then
    cp -R "$ROOT/src/main/resources/." "$WORK/classes/" 2>/dev/null || true
fi

# 5) Re-pack — keep the existing OSGi MANIFEST.MF and dependency/ folder.
cp -R "$WORK/embedded/META-INF" "$WORK/classes/META-INF"
cp -R "$WORK/embedded/dependency" "$WORK/classes/dependency"

(cd "$WORK/classes" && "$JAR" cfm "$OUT_JAR" META-INF/MANIFEST.MF \
    $(find . -type f -not -path "./META-INF/MANIFEST.MF"))

echo "→ wrote $OUT_JAR"
ls -la "$OUT_JAR"
