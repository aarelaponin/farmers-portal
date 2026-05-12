#!/usr/bin/env bash
# Build every MANDATORY Farmers Portal plugin and stage the resulting JARs.
#
# See plugins/BUILD.md for the mandatory list and the per-plugin build choice
# (mvn package vs ./deploy/repack.sh). This script picks the right build path
# per plugin: if a `deploy/repack.sh` exists AND we're explicitly asked for it
# via --repack, use that; otherwise prefer `mvn package`.
#
# Output JARs are copied into `<repo>/dist/plugins/` for easy upload to Joget.
#
# Usage:
#   plugins/build-all.sh              # mvn-based build, dist/plugins/ collects JARs
#   plugins/build-all.sh --repack     # sandbox repack.sh build (no Maven required)
#   plugins/build-all.sh --only reg-bb-engine,form-creator-api   # subset

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/.." && pwd)"
DIST="$REPO_ROOT/dist/plugins"

# ─── Mandatory plugin order — see plugins/BUILD.md ───────────────────────────
MANDATORY=(
    form-creator-api
    reg-bb-engine
    reg-bb-publisher
    joget-gis-ui
    joget-gis-server
    joget-smart-search
    joget-concat-field
    joget-advanced-filters
    embedded-datalist
    parcel-zone-centring
    form-quality-runtime
    farmer-derived-plugin
)

# ─── Flags ───────────────────────────────────────────────────────────────────
USE_REPACK=0
ONLY=""
for arg in "$@"; do
    case "$arg" in
        --repack) USE_REPACK=1 ;;
        --only=*) ONLY="${arg#--only=}" ;;
        --only)   shift; ONLY="${1:-}" ;;
        -h|--help)
            sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
    esac
done

# Filter to --only subset if requested.
if [[ -n "$ONLY" ]]; then
    IFS=',' read -ra REQUESTED <<< "$ONLY"
    FILTERED=()
    for p in "${MANDATORY[@]}"; do
        for r in "${REQUESTED[@]}"; do
            if [[ "$p" == "$r" ]]; then FILTERED+=("$p"); fi
        done
    done
    MANDATORY=("${FILTERED[@]}")
fi

# ─── Preflight: vendored Joget source must be present ────────────────────────
# Plugin compilation depends on jw-community and api-builder being checked out
# at the repo root. They're gitignored as vendored read-references — see
# plugins/BUILD.md "Prerequisite: vendored Joget source" for the clone commands.
MISSING_VENDORED=()
[[ -f "$REPO_ROOT/jw-community/wflow-core/pom.xml" ]] || MISSING_VENDORED+=("jw-community")
[[ -f "$REPO_ROOT/api-builder/apibuilder_api/pom.xml" ]] || MISSING_VENDORED+=("api-builder")
if [[ ${#MISSING_VENDORED[@]} -gt 0 ]]; then
    echo
    echo "[build-all] !! preflight failed — vendored Joget source missing:"
    for v in "${MISSING_VENDORED[@]}"; do
        echo "             $REPO_ROOT/$v/"
    done
    echo
    echo "Clone the missing tree(s) at the repo root before building:"
    echo
    if [[ " ${MISSING_VENDORED[*]} " == *" jw-community "* ]]; then
        echo "  cd $REPO_ROOT && git clone --branch 8.1-RELEASE https://github.com/jogetworkflow/jw-community.git"
    fi
    if [[ " ${MISSING_VENDORED[*]} " == *" api-builder "* ]]; then
        echo "  cd $REPO_ROOT && git clone --branch 7.0-SNAPSHOT https://github.com/jogetworkflow/api-builder.git"
    fi
    echo
    echo "See plugins/BUILD.md or INSTALL.md step 4 for the full prerequisite."
    exit 1
fi

# ─── Build loop ──────────────────────────────────────────────────────────────
mkdir -p "$DIST"
echo "[build-all] dist=$DIST  mode=$([ $USE_REPACK -eq 1 ] && echo repack || echo mvn)"
echo "[build-all] building ${#MANDATORY[@]} plugin(s)…"

FAILED=()
for p in "${MANDATORY[@]}"; do
    DIR="$REPO_ROOT/plugins/$p"
    if [[ ! -d "$DIR" ]]; then
        echo "[build-all] !! missing plugin dir: $DIR"
        FAILED+=("$p")
        continue
    fi

    echo
    echo "──── $p ────"
    cd "$DIR"

    REPACK="$DIR/deploy/repack.sh"
    if [[ $USE_REPACK -eq 1 && -x "$REPACK" ]]; then
        if "$REPACK"; then
            :
        else
            echo "[build-all] !! $p: repack.sh failed"
            FAILED+=("$p"); continue
        fi
    elif [[ -f "$DIR/pom.xml" ]]; then
        if mvn -q -DskipTests package; then
            :
        else
            echo "[build-all] !! $p: mvn package failed"
            FAILED+=("$p"); continue
        fi
    else
        echo "[build-all] !! $p: no pom.xml and no repack.sh — cannot build"
        FAILED+=("$p"); continue
    fi

    # Pick up the largest JAR produced under target/ (skips the *-sources.jar).
    JAR=$(ls -S "$DIR/target/"*.jar 2>/dev/null | grep -v sources | head -1 || true)
    if [[ -z "$JAR" ]]; then
        echo "[build-all] !! $p: no JAR found in target/"
        FAILED+=("$p"); continue
    fi
    cp -f "$JAR" "$DIST/"
    echo "[build-all] ok  $p  →  $(basename "$JAR")"
done

# ─── Summary ─────────────────────────────────────────────────────────────────
echo
if [[ ${#FAILED[@]} -eq 0 ]]; then
    echo "[build-all] all ${#MANDATORY[@]} plugin(s) built successfully."
    echo "[build-all] JARs staged in: $DIST"
    echo "[build-all] Next: upload each JAR via App Composer → Manage Plugins."
    exit 0
else
    echo "[build-all] FAILED: ${FAILED[*]}"
    exit 1
fi
