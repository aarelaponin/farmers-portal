#!/usr/bin/env bash
# Build the parcel-zone-centring OSGi bundle without Maven.
#
# Strategy: form-quality-runtime ships an existing JAR with a valid OSGi
# MANIFEST. We start from a copy of that JAR's manifest (with name +
# Bundle-Activator swapped), compile our .java sources with javac, and
# repackage. Same approach as form-quality-runtime/deploy/repack.sh.
#
# The build counter lives in Build.java. Surfaces that expose it:
# ParcelZoneCentroidPostProcessor.getDescription/getVersion (App Composer
# plugin picker) and the Activator startup log line. Use those to confirm
# the live JAR matches the source you intended to deploy.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK="${JDK:-/tmp/jdk-11.0.31+11}"
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
M2="${M2:-/sessions/admiring-determined-rubin/.m2/repository}"
LIBS="${LIBS:-/sessions/admiring-determined-rubin/mnt/lst-frm-prj/jw-community/wflow-consoleweb/target/jw/WEB-INF/lib}"
OUT_JAR="$ROOT/parcel-zone-centring-8.1-SNAPSHOT.jar"
WORK="${WORK:-/tmp/pzc-build}"

BUILD_FILE="$ROOT/src/main/java/global/govstack/parcelzonecentring/Build.java"
TEMPLATE_JAR="$ROOT/../form-quality-runtime/form-quality-runtime-8.1-SNAPSHOT.jar"

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

# 3) Build the OSGi JAR. First time: derive from form-quality-runtime's manifest
#    by swapping Bundle-Name / Bundle-SymbolicName / Bundle-Activator.
#    Subsequent builds: reuse our own previous JAR's manifest.
rm -rf "$WORK/jar"
mkdir -p "$WORK/jar"

if [ -f "$OUT_JAR" ]; then
    echo "→ rebuilding from previous $OUT_JAR"
    ( cd "$WORK/jar" && unzip -q -o "$OUT_JAR" )
else
    echo "→ first build: deriving manifest from $TEMPLATE_JAR"
    if [ ! -f "$TEMPLATE_JAR" ]; then
        echo "ERROR: template JAR not found: $TEMPLATE_JAR"
        echo "Need an existing OSGi JAR to copy the manifest skeleton from."
        exit 1
    fi
    ( cd "$WORK/jar" && unzip -q -o "$TEMPLATE_JAR" )
    # Wipe everything except META-INF — we only wanted the manifest skeleton.
    # The template ships its own classes (global/) and resources (forms/);
    # neither belongs in our bundle.
    find "$WORK/jar" -mindepth 1 -maxdepth 1 ! -name 'META-INF' -exec rm -rf {} +
    # Edit the manifest to point at our bundle
    sed -i.bak \
        -e "s|^Bundle-Name:.*|Bundle-Name: Parcel Zone Centring|" \
        -e "s|^Bundle-SymbolicName:.*|Bundle-SymbolicName: parcel-zone-centring|" \
        -e "s|^Bundle-Activator:.*|Bundle-Activator: global.govstack.parcelzonecentring.Activator|" \
        "$WORK/jar/META-INF/MANIFEST.MF"
    rm -f "$WORK/jar/META-INF/MANIFEST.MF.bak"
fi

# Wipe stale classes (in case Build.NUMBER changed signatures, etc.)
rm -rf "$WORK/jar/global"
cp -r "$WORK/classes/." "$WORK/jar/"

( cd "$WORK/jar" && \
  "$JAR" cfm "$OUT_JAR" META-INF/MANIFEST.MF \
    $(find . -name "*.class" -o -name "*.json" 2>/dev/null | sed 's|^\./||') )

echo "→ packaged $OUT_JAR ($(stat -c %s "$OUT_JAR") bytes)"
echo
echo "Manifest:"
unzip -p "$OUT_JAR" META-INF/MANIFEST.MF
echo
echo "Build stamp baked into JAR:"
unzip -p "$OUT_JAR" global/govstack/parcelzonecentring/Build.class | strings | grep -E '^build-|^[0-9]{4}-' | head -5
echo
echo "Done. Upload $OUT_JAR via Manage Plugins (or the form-creator-api if available)."
