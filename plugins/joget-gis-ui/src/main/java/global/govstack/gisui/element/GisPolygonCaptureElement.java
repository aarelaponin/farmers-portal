package global.govstack.gisui.element;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBuilderPaletteElement;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * GIS Polygon Capture Form Element
 *
 * A Joget form element that provides a Leaflet.js-based map for capturing
 * geographic polygon boundaries.
 *
 * Features:
 * - Walk Mode: GPS-based capture for field use (mobile)
 * - Draw Mode: Click-to-draw for office use (desktop)
 * - Real-time area and perimeter calculation
 * - GeoJSON output format
 * - Configurable validation rules
 * - Optional overlap checking
 */
public class GisPolygonCaptureElement extends Element implements FormBuilderPaletteElement {

    private static final String CLASS_NAME = GisPolygonCaptureElement.class.getName();

    // Dangerous SQL patterns that should be rejected (security)
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
        "(?i)(;|--|\\/\\*|\\*\\/|DROP|DELETE|INSERT|UPDATE|TRUNCATE|ALTER|CREATE|EXEC|EXECUTE|UNION|INTO|OUTFILE|DUMPFILE)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Validate and sanitize a SQL filter condition.
     * Returns the sanitized condition or empty string if dangerous patterns detected.
     *
     * @param filterCondition The filter condition to validate
     * @return Sanitized filter condition or empty string if invalid
     */
    private String sanitizeFilterCondition(String filterCondition) {
        if (filterCondition == null || filterCondition.trim().isEmpty()) {
            return "";
        }

        String trimmed = filterCondition.trim();

        // Check for dangerous SQL patterns
        if (DANGEROUS_SQL_PATTERN.matcher(trimmed).find()) {
            LogUtil.warn(CLASS_NAME, "Blocked potentially dangerous filter condition: " + trimmed);
            return "";
        }

        return trimmed;
    }

    /**
     * Get property value with a default fallback.
     *
     * @param value The property value (may be null or empty)
     * @param defaultValue The default value to use if value is null/empty
     * @return The value or default
     */
    private String getPropertyWithDefault(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * Safely parse a double value with a default fallback.
     *
     * @param value The string value to parse
     * @param defaultValue The default value if parsing fails
     * @return The parsed double or default
     */
    private double parseDoubleSafe(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LogUtil.debug(CLASS_NAME, "Invalid double value '" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Safely parse an integer value with a default fallback.
     *
     * @param value The string value to parse
     * @param defaultValue The default value if parsing fails
     * @return The parsed integer or default
     */
    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogUtil.debug(CLASS_NAME, "Invalid integer value '" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }

    @Override
    public String getName() {
        return "GIS Polygon Capture";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Leaflet.js-based map component for capturing polygon boundaries using Walk Mode (GPS) or Draw Mode (desktop)";
    }

    @Override
    public String getLabel() {
        return "GIS Polygon Capture";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getFormBuilderCategory() {
        return "GovStack";
    }

    @Override
    public int getFormBuilderPosition() {
        return 200;
    }

    @Override
    public String getFormBuilderIcon() {
        return "<i class=\"fa fa-map-marker\"></i>";
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<div class='form-cell'><label class='label'>GIS Polygon Capture</label><div class='form-cell-value'>[Map placeholder]</div></div>";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/GisPolygonCaptureElement.json",
            null,
            true,
            null
        );
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "GisPolygonCaptureElement.ftl";

        // =================================================================
        // Phase 1: Extract basic properties
        // =================================================================
        String fieldId = getPropertyString("id");
        String apiEndpoint = getPropertyWithDefault(getPropertyString("apiEndpoint"), "/jw/api/gis/gis");
        String apiId = getPropertyString("apiId");
        String apiKey = getPropertyString("apiKey");
        int mapHeight = parseIntSafe(getPropertyString("mapHeight"), GisConfigBuilder.DEFAULT_MAP_HEIGHT);

        // =================================================================
        // Phase 2: Extract record ID for edit mode (delegated to helper)
        // =================================================================
        String recordId = RecordIdExtractor.extract(formData, this);
        LogUtil.debug(CLASS_NAME, "RecordId final value: " + recordId);

        // =================================================================
        // Phase 3: Get current GeoJSON value
        // =================================================================
        String value = FormUtil.getElementPropertyValue(this, formData);
        if (value == null) {
            value = "";
        }

        // =================================================================
        // Phase 4: Build configuration JSON using fluent builder
        // =================================================================
        String configJson = buildConfigJson();

        // =================================================================
        // Phase 5: Populate data model
        // =================================================================
        String resourceBase = "/jw/web/json/plugin/" + GisResourcesPlugin.class.getName() + "/service?file=";

        dataModel.put("fieldId", fieldId);
        dataModel.put("value", value);
        dataModel.put("resourceBase", resourceBase);
        dataModel.put("apiBase", apiEndpoint);
        dataModel.put("apiId", getPropertyWithDefault(apiId, ""));
        dataModel.put("apiKey", getPropertyWithDefault(apiKey, ""));
        dataModel.put("elementId", "gis_" + fieldId + "_" + System.currentTimeMillis());
        dataModel.put("config", configJson);
        dataModel.put("mapHeight", mapHeight);

        // Output field IDs for JavaScript
        dataModel.put("areaFieldId", getPropertyWithDefault(getPropertyString("areaFieldId"), ""));
        dataModel.put("perimeterFieldId", getPropertyWithDefault(getPropertyString("perimeterFieldId"), ""));
        dataModel.put("centroidFieldId", getPropertyWithDefault(getPropertyString("centroidFieldId"), ""));
        dataModel.put("vertexCountFieldId", getPropertyWithDefault(getPropertyString("vertexCountFieldId"), ""));

        // Record ID for edit mode (to exclude from overlap checking)
        dataModel.put("recordId", recordId);

        // Cache busting version for JS/CSS resources (use timestamp for dev, version for prod)
        dataModel.put("gisCacheVersion", String.valueOf(System.currentTimeMillis()));

        // =================================================================
        // Phase 6: Render template
        // =================================================================
        return FormUtil.generateElementHtml(this, formData, template, dataModel);
    }

    /**
     * Build the configuration JSON using GisConfigBuilder.
     * This method extracts all properties and constructs the config object.
     *
     * @return JSON string for the JavaScript component
     */
    private String buildConfigJson() {
        // Sanitize filter conditions for security
        String overlapFilter = sanitizeFilterCondition(getPropertyString("overlapFilterCondition"));
        String nearbyFilter = sanitizeFilterCondition(getPropertyString("nearbyParcelsFilterCondition"));

        GisConfigBuilder builder = new GisConfigBuilder();

        // Map settings
        builder.withMapSettings(
            getPropertyString("tileProvider"),
            parseDoubleSafe(getPropertyString("defaultLatitude"), GisConfigBuilder.DEFAULT_LATITUDE),
            parseDoubleSafe(getPropertyString("defaultLongitude"), GisConfigBuilder.DEFAULT_LONGITUDE),
            parseIntSafe(getPropertyString("defaultZoom"), GisConfigBuilder.DEFAULT_ZOOM),
            parseIntSafe(getPropertyString("mapHeight"), GisConfigBuilder.DEFAULT_MAP_HEIGHT),
            !"false".equals(getPropertyWithDefault(getPropertyString("showSatelliteOption"), "true"))
        );

        // Capture mode
        builder.withCaptureMode(
            getPropertyString("captureMode"),
            getPropertyString("defaultMode")
        );

        // Validation rules
        builder.withValidation(
            parseDoubleSafe(getPropertyString("minAreaHectares"), GisConfigBuilder.DEFAULT_MIN_AREA_HECTARES),
            parseDoubleSafe(getPropertyString("maxAreaHectares"), GisConfigBuilder.DEFAULT_MAX_AREA_HECTARES),
            parseIntSafe(getPropertyString("minVertices"), GisConfigBuilder.DEFAULT_MIN_VERTICES),
            parseIntSafe(getPropertyString("maxVertices"), GisConfigBuilder.DEFAULT_MAX_VERTICES),
            "true".equals(getPropertyString("allowSelfIntersection"))
        );

        // GPS settings
        builder.withGpsSettings(
            !"false".equals(getPropertyWithDefault(getPropertyString("gpsHighAccuracy"), "true")),
            parseDoubleSafe(getPropertyString("gpsMinAccuracy"), GisConfigBuilder.DEFAULT_GPS_MIN_ACCURACY),
            parseDoubleSafe(getPropertyString("autoCloseDistance"), GisConfigBuilder.DEFAULT_AUTO_CLOSE_DISTANCE)
        );

        // Style
        builder.withStyle(
            getPropertyWithDefault(getPropertyString("fillColor"), GisConfigBuilder.DEFAULT_FILL_COLOR),
            parseDoubleSafe(getPropertyString("fillOpacity"), GisConfigBuilder.DEFAULT_FILL_OPACITY),
            getPropertyWithDefault(getPropertyString("strokeColor"), GisConfigBuilder.DEFAULT_STROKE_COLOR),
            parseIntSafe(getPropertyString("strokeWidth"), GisConfigBuilder.DEFAULT_STROKE_WIDTH)
        );

        // Output fields
        builder.withOutputFields(
            getPropertyString("areaFieldId"),
            getPropertyString("perimeterFieldId"),
            getPropertyString("centroidFieldId"),
            getPropertyString("vertexCountFieldId")
        );

        // Overlap checking (optional)
        if ("true".equals(getPropertyString("enableOverlapCheck"))) {
            builder.withOverlapCheck(
                getPropertyString("overlapFormId"),
                getPropertyString("overlapGeometryField"),
                getPropertyString("overlapDisplayFields"),
                overlapFilter
            );
        }

        // Nearby parcels (optional)
        String showNearbyParcels = getPropertyString("showNearbyParcels");
        if (showNearbyParcels != null && !showNearbyParcels.isEmpty() && !"DISABLED".equals(showNearbyParcels)) {
            builder.withNearbyParcels(
                showNearbyParcels,
                getPropertyString("nearbyParcelsFormId"),
                getPropertyString("nearbyParcelsGeometryField"),
                getPropertyString("nearbyParcelsDisplayFields"),
                nearbyFilter,
                parseIntSafe(getPropertyString("nearbyParcelsMaxResults"), GisConfigBuilder.DEFAULT_NEARBY_MAX_RESULTS),
                getPropertyString("nearbyParcelsFillColor"),
                parseDoubleSafe(getPropertyString("nearbyParcelsFillOpacity"), GisConfigBuilder.DEFAULT_NEARBY_FILL_OPACITY),
                getPropertyString("nearbyParcelsStrokeColor")
            );
        }

        // Auto-center (optional)
        if ("true".equals(getPropertyString("enableAutoCenter"))) {
            builder.withAutoCenter(
                getPropertyString("autoCenterDistrictFieldId"),
                getPropertyString("autoCenterVillageFieldId"),
                getPropertyString("autoCenterLatFieldId"),
                getPropertyString("autoCenterLonFieldId"),
                getPropertyString("autoCenterCountrySuffix"),
                parseIntSafe(getPropertyString("autoCenterZoom"), GisConfigBuilder.DEFAULT_AUTO_CENTER_ZOOM),
                !"false".equals(getPropertyString("autoCenterRetryOnFieldChange"))
            );
        }

        // Simplification (optional)
        if ("true".equals(getPropertyString("enableSimplification"))) {
            builder.withSimplification(
                true,
                parseDoubleSafe(getPropertyString("simplificationTolerance"), GisConfigBuilder.DEFAULT_SIMPLIFICATION_TOLERANCE)
            );
        }

        return builder.build();
    }

    @Override
    public FormData formatDataForValidation(FormData formData) {
        return formData;
    }

    @Override
    public Boolean selfValidate(FormData formData) {
        String fieldId = FormUtil.getElementParameterName(this);
        String value = formData.getRequestParameter(fieldId);

        // Check if required
        String required = getPropertyString("required");
        if ("true".equals(required) && (value == null || value.isEmpty())) {
            String errorMsg = getPropertyString("requiredMessage");
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = "Please capture a polygon boundary";
            }
            formData.addFormError(fieldId, errorMsg);
            return false;
        }

        // Comprehensive GeoJSON validation if value provided
        if (value != null && !value.isEmpty()) {
            String validationError = validateGeoJSON(value);
            if (validationError != null) {
                formData.addFormError(fieldId, validationError);
                return false;
            }
        }

        return true;
    }

    /**
     * Validate GeoJSON structure and coordinate bounds.
     *
     * @param geojsonStr The GeoJSON string to validate
     * @return Error message if invalid, null if valid
     */
    private String validateGeoJSON(String geojsonStr) {
        try {
            JSONObject geojson = new JSONObject(geojsonStr);
            String type = geojson.optString("type", "");

            // Handle Feature type - extract geometry
            JSONObject geometry;
            if ("Feature".equals(type)) {
                geometry = geojson.optJSONObject("geometry");
                if (geometry == null) {
                    return "Feature is missing geometry";
                }
            } else if ("Polygon".equals(type)) {
                geometry = geojson;
            } else {
                return "Invalid geometry type. Expected Polygon or Feature.";
            }

            // Verify geometry type is Polygon
            String geometryType = geometry.optString("type", "");
            if (!"Polygon".equals(geometryType)) {
                return "Invalid geometry type. Expected Polygon.";
            }

            // Validate coordinates exist
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null || coordinates.length() == 0) {
                return "Polygon is missing coordinates";
            }

            // Get the outer ring (first coordinate array)
            JSONArray outerRing = coordinates.optJSONArray(0);
            if (outerRing == null || outerRing.length() < 4) {
                return "Polygon must have at least 4 coordinate points (3 vertices + closing point)";
            }

            // Validate each coordinate
            for (int i = 0; i < outerRing.length(); i++) {
                JSONArray coord = outerRing.optJSONArray(i);
                if (coord == null || coord.length() < 2) {
                    return "Invalid coordinate at position " + i;
                }

                double lng = coord.optDouble(0, Double.NaN);
                double lat = coord.optDouble(1, Double.NaN);

                if (Double.isNaN(lng) || Double.isNaN(lat)) {
                    return "Invalid coordinate values at position " + i;
                }

                // Validate coordinate bounds
                if (lat < -90 || lat > 90) {
                    return "Latitude out of bounds at position " + i + ": " + lat;
                }
                if (lng < -180 || lng > 180) {
                    return "Longitude out of bounds at position " + i + ": " + lng;
                }
            }

            // Verify polygon is closed (first coord == last coord)
            JSONArray firstCoord = outerRing.optJSONArray(0);
            JSONArray lastCoord = outerRing.optJSONArray(outerRing.length() - 1);
            if (firstCoord != null && lastCoord != null) {
                double firstLng = firstCoord.optDouble(0, Double.NaN);
                double firstLat = firstCoord.optDouble(1, Double.NaN);
                double lastLng = lastCoord.optDouble(0, Double.NaN);
                double lastLat = lastCoord.optDouble(1, Double.NaN);

                if (Math.abs(firstLng - lastLng) > 0.0000001 || Math.abs(firstLat - lastLat) > 0.0000001) {
                    return "Polygon is not closed (first and last coordinates must match)";
                }
            }

            return null; // Valid

        } catch (Exception e) {
            LogUtil.debug(CLASS_NAME, "GeoJSON validation error: " + e.getMessage());
            return "Invalid GeoJSON format: " + e.getMessage();
        }
    }
}
