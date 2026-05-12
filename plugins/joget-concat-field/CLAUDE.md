# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Command

```bash
mvn clean package
```

Output: `target/joget-concat-field-8.1-SNAPSHOT.jar`

## Manual Testing

No automated tests. Deploy JAR to Joget via Settings > Manage Plugins > Upload Plugin, then test with a form containing source fields.

## Architecture

**Joget DX 8 Form Element Plugin** - An OSGi bundle that provides a form field for concatenating multiple field values.

### Plugin Registration Flow
1. `Activator.java` registers both plugins with OSGi on bundle start
2. `ConcatFieldResources` serves static files via `PluginWebSupport`
3. `ConcatFieldElement` renders the form element and handles server-side concatenation fallback

### Rendering Flow
1. `ConcatFieldElement.renderTemplate()` builds a JSON config from plugin properties
2. `ConcatFieldElement.ftl` renders HTML and initializes JavaScript with the config
3. JavaScript attaches event listeners to source fields and computes concatenation client-side
4. `ConcatFieldElement.formatData()` provides server-side fallback if JS didn't run

### Key Joget Patterns
- Form elements extend `Element` and implement `FormBuilderPaletteElement`
- Plugin properties are defined in JSON (`ConcatFieldElement.json`) and loaded via `AppUtil.readPluginResource()`
- Templates are FreeMarker (`.ftl`) files with embedded JS/CSS
- Field IDs in DOM may have prefixes; the JS uses multiple selector patterns to find fields

## Debugging

Set `DEBUG = true` in `ConcatFieldElement.ftl` (line 79) to enable console logging. Access the component state via `window.concatField_{fieldId}` in browser console.

## Adding New Transformations

Add to `applyTransform()` in both:
- `ConcatFieldElement.java:288-306` (server-side fallback)
- `ConcatFieldElement.ftl:126-141` (client-side)
