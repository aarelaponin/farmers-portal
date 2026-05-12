#!/usr/bin/env bash
# Lightweight build for form-quality-runtime that does NOT rely on Maven going
# online. It:
#   1. Bumps the build counter and stamps the timestamp into Build.java
#   2. Compiles all .java with javac, against the local m2 + jw-community libs
#   3. Repackages the OSGi JAR while preserving META-INF/MANIFEST.MF
#
# The build counter lives in Build.java (NUMBER constant). Surfaces that
# expose it: FormQualityPostProcessor.getDescription/getVersion (App Composer
# plugin picker) and the Activator startup log line. Use those to confirm the
# live JAR matches the source you intended to deploy.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK="${JDK:-/sessions/bold-focused-pasteur/jdk}"
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
M2="${M2:-/sessions/bold-focused-pasteur/.m2/repository}"
LIBS="${LIBS:-/sessions/bold-focused-pasteur/mnt/lst-frm-prj/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"
OUT_JAR="$ROOT/form-quality-runtime-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/fqr-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/formquality/Build.java"

# 1) Bump the build counter and stamp the UTC timestamp.
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
CP="$CP:$M2/global/govstack/joget-status-framework/8.1-SNAPSHOT/joget-status-framework-8.1-SNAPSHOT.jar"
CP="$CP:$M2/org/osgi/org.osgi.core/6.0.0/org.osgi.core-6.0.0.jar"
for j in "$LIBS"/spring-*.jar "$LIBS"/javax.servlet-*.jar "$LIBS"/jakarta.servlet*.jar \
         "$LIBS"/postgresql-*.jar "$LIBS"/hibernate-core-*.jar "$LIBS"/json-*.jar \
         "$LIBS"/commons-lang3-*.jar; do
    [ -f "$j" ] && CP="$CP:$j"
done

rm -rf "$WORK/classes"
mkdir -p "$WORK/classes"
"$JAVAC" --release 11 -d "$WORK/classes" -cp "$CP" \
    $(find "$ROOT/src/main/java" -name "*.java")
echo "→ compiled $(find "$WORK/classes" -name "*.class" | wc -l) classes"

# 3) Repackage, preserving the original OSGi manifest.
rm -rf "$WORK/jar"
mkdir -p "$WORK/jar"
( cd "$WORK/jar" && unzip -q -o "$OUT_JAR" )
cp -r "$WORK/classes/." "$WORK/jar/"
( cd "$WORK/jar" && \
  "$JAR" cfm "$OUT_JAR" META-INF/MANIFEST.MF \
    $(find . -name "*.class" -o -name "*.json" | sed 's|^\./||') )

echo "→ repackaged $OUT_JAR ($(stat -c %s "$OUT_JAR") bytes)"
echo
echo "Build stamp baked into JAR:"
unzip -p "$OUT_JAR" global/govstack/formquality/Build.class | strings | grep -E '^build-|^[0-9]{4}-' | head -5
echo
echo "Done. Upload $OUT_JAR via Manage Plugins."
