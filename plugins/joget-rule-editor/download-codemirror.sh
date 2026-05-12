#!/bin/bash
# Download CodeMirror files for bundling in the plugin
# Run this script from the erel-parser-api directory

STATIC_DIR="src/main/resources/static"
mkdir -p "$STATIC_DIR"

echo "Downloading CodeMirror 5.65.15..."

# CodeMirror core
curl -s "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/codemirror.min.js" -o "$STATIC_DIR/codemirror.min.js"
curl -s "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/codemirror.min.css" -o "$STATIC_DIR/codemirror.min.css"

# Addons
curl -s "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/addon/edit/matchbrackets.min.js" >> "$STATIC_DIR/codemirror.min.js"
curl -s "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.15/addon/selection/active-line.min.js" >> "$STATIC_DIR/codemirror.min.js"

echo "Downloaded CodeMirror files to $STATIC_DIR"
echo ""
echo "Files created:"
ls -la "$STATIC_DIR"
