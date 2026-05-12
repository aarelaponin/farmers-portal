#!/bin/bash
# Test script to verify EREL plugin JAR resources
# Run from: /Users/aarelaponin/IdeaProjects/plugins/erel-parser-api

JAR_FILE="target/erel-parser-api-8.1-SNAPSHOT.jar"

echo "====================================="
echo "EREL Plugin JAR Resource Verification"
echo "====================================="
echo ""

if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found: $JAR_FILE"
    echo "Run: mvn clean package -DskipTests"
    exit 1
fi

echo "JAR file: $JAR_FILE"
echo "Size: $(du -h $JAR_FILE | cut -f1)"
echo ""

echo "=== Checking for static resources ==="
for file in codemirror.min.js codemirror.min.css erel-mode.js erel-editor.js erel-editor.css; do
    if unzip -l "$JAR_FILE" | grep -q "static/$file"; then
        SIZE=$(unzip -l "$JAR_FILE" | grep "static/$file" | awk '{print $1}')
        echo "✓ static/$file ($SIZE bytes)"
    else
        echo "✗ MISSING: static/$file"
    fi
done

echo ""
echo "=== Checking for templates ==="
if unzip -l "$JAR_FILE" | grep -q "templates/ERELEditorElement.ftl"; then
    echo "✓ templates/ERELEditorElement.ftl"
else
    echo "✗ MISSING: templates/ERELEditorElement.ftl"
fi

echo ""
echo "=== Checking for properties ==="
for file in ERELEditorElement.json ERELServiceProvider.json; do
    if unzip -l "$JAR_FILE" | grep -q "properties/$file"; then
        echo "✓ properties/$file"
    else
        echo "✗ MISSING: properties/$file"
    fi
done

echo ""
echo "=== Java classes ==="
echo "Plugin classes:"
unzip -l "$JAR_FILE" | grep "\.class$" | grep -E "(ERELEditor|ERELService|Activator)" | awk '{print "  " $4}'

echo ""
echo "=== All JAR contents ==="
echo "(First 30 entries)"
unzip -l "$JAR_FILE" | head -35

echo ""
echo "Done."
