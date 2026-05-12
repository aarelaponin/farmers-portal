#!/usr/bin/env bash
# Sandbox-friendly build for reg-bb-publisher — same pattern as
# identity-resolver-runtime/deploy/repack.sh.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK="${JDK:-/sessions/bold-focused-pasteur/jdk}"
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
M2="${M2:-/sessions/bold-focused-pasteur/.m2/repository}"
LIBS="${LIBS:-/sessions/bold-focused-pasteur/mnt/lst-frm-prj/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"
ENGINE_JAR="${ENGINE_JAR:-${ROOT}/../reg-bb-engine/reg-bb-engine-8.1-SNAPSHOT.jar}"
OUT_JAR="$ROOT/reg-bb-publisher-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/regbbpub-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/regbb/publisher/Build.java"

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

# 2) Compile (depends on reg-bb-engine for the API surface).
CP="$M2/org/joget/wflow-commons/8.1-SNAPSHOT/wflow-commons-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/joget/wflow-plugin-base/8.1-SNAPSHOT/wflow-plugin-base-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/joget/wflow-core/8.1-SNAPSHOT/wflow-core-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar"
CP="$CP:$M2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar"
[ -f "$ENGINE_JAR" ] && CP="$CP:$ENGINE_JAR"
for j in "$LIBS"/spring-*.jar "$LIBS"/javax.servlet-*.jar "$LIBS"/jakarta.servlet*.jar \
         "$LIBS"/postgresql-*.jar "$LIBS"/hibernate-core-*.jar "$LIBS"/json-*.jar \
         "$LIBS"/commons-lang3-*.jar; do
    [ -f "$j" ] && CP="$CP:$j"
done

rm -rf "$WORK"
mkdir -p "$WORK/classes"

find "$ROOT/src/main/java" -name "*.java" > "$WORK/sources.txt"
"$JAVAC" -release 11 -cp "$CP" -d "$WORK/classes" @"$WORK/sources.txt"
echo "→ compiled $(wc -l < "$WORK/sources.txt") source files"

# 3) Resources.
if [ -d "$ROOT/src/main/resources" ]; then
    cp -R "$ROOT/src/main/resources/." "$WORK/classes/" 2>/dev/null || true
fi

# 4) Manifest.
mkdir -p "$WORK/classes/META-INF"
if [ -f "$OUT_JAR" ]; then
    "$JAR" xf "$OUT_JAR" META-INF/MANIFEST.MF -C "$WORK/classes/" 2>/dev/null || true
    if [ -f "$ROOT/META-INF/MANIFEST.MF" ]; then
        cp "$ROOT/META-INF/MANIFEST.MF" "$WORK/classes/META-INF/MANIFEST.MF"
    fi
else
    cat > "$WORK/classes/META-INF/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: RegBB Publisher
Bundle-SymbolicName: global.govstack.reg-bb-publisher
Bundle-Version: 8.1.0.SNAPSHOT
Bundle-Activator: global.govstack.regbb.publisher.Activator
Bundle-RequiredExecutionEnvironment: JavaSE-11
Import-Package: global.govstack.regbb.engine.api;version="[8.1,9)"
DynamicImport-Package: *
EOF
fi

# 5) Build the JAR.
(cd "$WORK/classes" && "$JAR" cfm "$OUT_JAR" META-INF/MANIFEST.MF $(find . -type f -not -path "./META-INF/*"))

echo "→ wrote $OUT_JAR"
ls -la "$OUT_JAR"
