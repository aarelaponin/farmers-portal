package global.govstack.gisui.element;

import org.joget.commons.util.LogUtil;
import org.json.JSONObject;

/**
 * Fluent builder for GIS Capture configuration JSON.
 *
 * Centralizes all default values and provides a clean API for building
 * the configuration object passed to the JavaScript component.
 *
 * Usage:
 * <pre>
 * String configJson = new GisConfigBuilder()
 *     .withMapSettings("OSM", -29.5, 28.5, 10, 400, true)
 *     .withCaptureMode("BOTH", "AUTO")
 *     .withValidation(0.01, 1000.0, 3, 100, false)
 *     .withGpsSettings(true, 10.0, 15.0)
 *     .withStyle("#3388ff", 0.2, "#3388ff", 3)
 *     .build();
 * </pre>
 */
public class GisConfigBuilder {

    private static final String CLASS_NAME = GisConfigBuilder.class.getName();

    // =========================================================================
    // DEFAULT VALUES - Centralized for easy configuration
    // =========================================================================

    // Map defaults
    public static final String DEFAULT_TILE_PROVIDER = "OSM";
    public static final double DEFAULT_LATITUDE = -29.5;    // Lesotho center
    public static final double DEFAULT_LONGITUDE = 28.5;    // Lesotho center
    public static final int DEFAULT_ZOOM = 10;
    public static final int DEFAULT_MAP_HEIGHT = 400;
    public static final boolean DEFAULT_SHOW_SATELLITE = true;

    // Capture mode defaults
    public static final String DEFAULT_CAPTURE_MODE = "BOTH";
    public static final String DEFAULT_MODE = "AUTO";

    // Validation defaults
    public static final double DEFAULT_MIN_AREA_HECTARES = 0.01;
    public static final double DEFAULT_MAX_AREA_HECTARES = 1000.0;
    public static final int DEFAULT_MIN_VERTICES = 3;
    public static final int DEFAULT_MAX_VERTICES = 100;
    public static final boolean DEFAULT_ALLOW_SELF_INTERSECTION = false;

    // GPS defaults
    public static final boolean DEFAULT_GPS_HIGH_ACCURACY = true;
    public static final double DEFAULT_GPS_MIN_ACCURACY = 10.0;
    public static final double DEFAULT_AUTO_CLOSE_DISTANCE = 15.0;

    // Style defaults
    public static final String DEFAULT_FILL_COLOR = "#3388ff";
    public static final double DEFAULT_FILL_OPACITY = 0.2;
    public static final String DEFAULT_STROKE_COLOR = "#3388ff";
    public static final int DEFAULT_STROKE_WIDTH = 3;

    // Nearby parcels style defaults
    public static final String DEFAULT_NEARBY_FILL_COLOR = "#808080";
    public static final double DEFAULT_NEARBY_FILL_OPACITY = 0.15;
    public static final String DEFAULT_NEARBY_STROKE_COLOR = "#666666";
    public static final int DEFAULT_NEARBY_STROKE_WIDTH = 1;
    public static final String DEFAULT_NEARBY_STROKE_DASH = "3, 3";
    public static final int DEFAULT_NEARBY_MAX_RESULTS = 100;

    // Auto-center defaults
    public static final String DEFAULT_COUNTRY_SUFFIX = "Lesotho";
    public static final int DEFAULT_AUTO_CENTER_ZOOM = 14;

    // Simplification defaults
    public static final boolean DEFAULT_SIMPLIFICATION_ENABLED = false;
    public static final double DEFAULT_SIMPLIFICATION_TOLERANCE = 2.0;

    // =========================================================================
    // BUILDER STATE
    // =========================================================================

    private final JSONObject config;
    private final JSONObject validation;
    private final JSONObject gps;
    private final JSONObject style;
    private final JSONObject outputFields;

    private JSONObject overlap;
    private JSONObject nearbyParcels;
    private JSONObject autoCenter;
    private JSONObject simplification;

    /**
     * Create a new config builder with default values.
     */
    public GisConfigBuilder() {
        this.config = new JSONObject();
        this.validation = new JSONObject();
        this.gps = new JSONObject();
        this.style = new JSONObject();
        this.outputFields = new JSONObject();

        // Initialize with defaults
        initializeDefaults();
    }

    private void initializeDefaults() {
        try {
            // Map defaults
            config.put("tileProvider", DEFAULT_TILE_PROVIDER);
            config.put("defaultLatitude", DEFAULT_LATITUDE);
            config.put("defaultLongitude", DEFAULT_LONGITUDE);
            config.put("defaultZoom", DEFAULT_ZOOM);
            config.put("mapHeight", DEFAULT_MAP_HEIGHT);
            config.put("showSatelliteOption", DEFAULT_SHOW_SATELLITE);

            // Capture mode defaults
            config.put("captureMode", DEFAULT_CAPTURE_MODE);
            config.put("defaultMode", DEFAULT_MODE);

            // Validation defaults
            validation.put("minAreaHectares", DEFAULT_MIN_AREA_HECTARES);
            validation.put("maxAreaHectares", DEFAULT_MAX_AREA_HECTARES);
            validation.put("minVertices", DEFAULT_MIN_VERTICES);
            validation.put("maxVertices", DEFAULT_MAX_VERTICES);
            validation.put("allowSelfIntersection", DEFAULT_ALLOW_SELF_INTERSECTION);

            // GPS defaults
            gps.put("highAccuracy", DEFAULT_GPS_HIGH_ACCURACY);
            gps.put("minAccuracy", DEFAULT_GPS_MIN_ACCURACY);
            gps.put("autoCloseDistance", DEFAULT_AUTO_CLOSE_DISTANCE);

            // Style defaults
            style.put("fillColor", DEFAULT_FILL_COLOR);
            style.put("fillOpacity", DEFAULT_FILL_OPACITY);
            style.put("strokeColor", DEFAULT_STROKE_COLOR);
            style.put("strokeWidth", DEFAULT_STROKE_WIDTH);

            // Output fields (empty by default)
            outputFields.put("areaFieldId", "");
            outputFields.put("perimeterFieldId", "");
            outputFields.put("centroidFieldId", "");
            outputFields.put("vertexCountFieldId", "");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error initializing default config");
        }
    }

    // =========================================================================
    // MAP SETTINGS
    // =========================================================================

    /**
     * Configure map settings.
     */
    public GisConfigBuilder withMapSettings(String tileProvider, double latitude, double longitude,
                                            int zoom, int mapHeight, boolean showSatelliteOption) {
        try {
            config.put("tileProvider", getValueOrDefault(tileProvider, DEFAULT_TILE_PROVIDER));
            config.put("defaultLatitude", latitude != 0 ? latitude : DEFAULT_LATITUDE);
            config.put("defaultLongitude", longitude != 0 ? longitude : DEFAULT_LONGITUDE);
            config.put("defaultZoom", zoom > 0 ? zoom : DEFAULT_ZOOM);
            config.put("mapHeight", mapHeight > 0 ? mapHeight : DEFAULT_MAP_HEIGHT);
            config.put("showSatelliteOption", showSatelliteOption);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting map config");
        }
        return this;
    }

    // =========================================================================
    // CAPTURE MODE
    // =========================================================================

    /**
     * Configure capture mode.
     *
     * @param captureMode One of: BOTH, WALK, DRAW, VIEW_ONLY
     * @param defaultMode One of: AUTO, WALK, DRAW
     */
    public GisConfigBuilder withCaptureMode(String captureMode, String defaultMode) {
        try {
            config.put("captureMode", getValueOrDefault(captureMode, DEFAULT_CAPTURE_MODE));
            config.put("defaultMode", getValueOrDefault(defaultMode, DEFAULT_MODE));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting capture mode config");
        }
        return this;
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    /**
     * Configure validation rules.
     */
    public GisConfigBuilder withValidation(double minAreaHectares, double maxAreaHectares,
                                           int minVertices, int maxVertices,
                                           boolean allowSelfIntersection) {
        try {
            validation.put("minAreaHectares", minAreaHectares > 0 ? minAreaHectares : DEFAULT_MIN_AREA_HECTARES);
            validation.put("maxAreaHectares", maxAreaHectares > 0 ? maxAreaHectares : DEFAULT_MAX_AREA_HECTARES);
            validation.put("minVertices", minVertices > 0 ? minVertices : DEFAULT_MIN_VERTICES);
            validation.put("maxVertices", maxVertices > 0 ? maxVertices : DEFAULT_MAX_VERTICES);
            validation.put("allowSelfIntersection", allowSelfIntersection);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting validation config");
        }
        return this;
    }

    // =========================================================================
    // GPS SETTINGS
    // =========================================================================

    /**
     * Configure GPS settings for Walk Mode.
     */
    public GisConfigBuilder withGpsSettings(boolean highAccuracy, double minAccuracy, double autoCloseDistance) {
        try {
            gps.put("highAccuracy", highAccuracy);
            gps.put("minAccuracy", minAccuracy > 0 ? minAccuracy : DEFAULT_GPS_MIN_ACCURACY);
            gps.put("autoCloseDistance", autoCloseDistance > 0 ? autoCloseDistance : DEFAULT_AUTO_CLOSE_DISTANCE);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting GPS config");
        }
        return this;
    }

    // =========================================================================
    // STYLE
    // =========================================================================

    /**
     * Configure polygon style.
     */
    public GisConfigBuilder withStyle(String fillColor, double fillOpacity,
                                      String strokeColor, int strokeWidth) {
        try {
            style.put("fillColor", getValueOrDefault(fillColor, DEFAULT_FILL_COLOR));
            style.put("fillOpacity", fillOpacity >= 0 ? fillOpacity : DEFAULT_FILL_OPACITY);
            style.put("strokeColor", getValueOrDefault(strokeColor, DEFAULT_STROKE_COLOR));
            style.put("strokeWidth", strokeWidth > 0 ? strokeWidth : DEFAULT_STROKE_WIDTH);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting style config");
        }
        return this;
    }

    // =========================================================================
    // OUTPUT FIELDS
    // =========================================================================

    /**
     * Configure output field mappings.
     */
    public GisConfigBuilder withOutputFields(String areaFieldId, String perimeterFieldId,
                                             String centroidFieldId, String vertexCountFieldId) {
        try {
            outputFields.put("areaFieldId", getValueOrDefault(areaFieldId, ""));
            outputFields.put("perimeterFieldId", getValueOrDefault(perimeterFieldId, ""));
            outputFields.put("centroidFieldId", getValueOrDefault(centroidFieldId, ""));
            outputFields.put("vertexCountFieldId", getValueOrDefault(vertexCountFieldId, ""));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting output fields config");
        }
        return this;
    }

    // =========================================================================
    // OVERLAP CHECKING
    // =========================================================================

    /**
     * Enable and configure overlap checking.
     */
    public GisConfigBuilder withOverlapCheck(String formId, String geometryField,
                                             String displayFields, String filterCondition) {
        try {
            overlap = new JSONObject();
            overlap.put("enabled", true);
            overlap.put("formId", getValueOrDefault(formId, ""));
            overlap.put("geometryField", getValueOrDefault(geometryField, ""));
            overlap.put("displayFields", getValueOrDefault(displayFields, ""));
            overlap.put("filterCondition", getValueOrDefault(filterCondition, ""));
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting overlap config");
        }
        return this;
    }

    // =========================================================================
    // NEARBY PARCELS
    // =========================================================================

    /**
     * Enable and configure nearby parcels display.
     *
     * @param enabled One of: DISABLED, ON_LOAD, ON_DEMAND
     */
    public GisConfigBuilder withNearbyParcels(String enabled, String formId, String geometryFieldId,
                                              String displayFields, String filterCondition,
                                              int maxResults, String fillColor, double fillOpacity,
                                              String strokeColor) {
        if (enabled == null || enabled.isEmpty() || "DISABLED".equals(enabled)) {
            return this;
        }

        try {
            nearbyParcels = new JSONObject();
            nearbyParcels.put("enabled", enabled);
            nearbyParcels.put("formId", getValueOrDefault(formId, ""));
            nearbyParcels.put("geometryFieldId", getValueOrDefault(geometryFieldId, "c_geometry"));
            nearbyParcels.put("displayFields", getValueOrDefault(displayFields, ""));
            nearbyParcels.put("filterCondition", getValueOrDefault(filterCondition, ""));
            nearbyParcels.put("maxResults", maxResults > 0 ? maxResults : DEFAULT_NEARBY_MAX_RESULTS);

            // Style
            JSONObject nearbyStyle = new JSONObject();
            nearbyStyle.put("fillColor", getValueOrDefault(fillColor, DEFAULT_NEARBY_FILL_COLOR));
            nearbyStyle.put("fillOpacity", fillOpacity >= 0 ? fillOpacity : DEFAULT_NEARBY_FILL_OPACITY);
            nearbyStyle.put("strokeColor", getValueOrDefault(strokeColor, DEFAULT_NEARBY_STROKE_COLOR));
            nearbyStyle.put("strokeWidth", DEFAULT_NEARBY_STROKE_WIDTH);
            nearbyStyle.put("strokeDashArray", DEFAULT_NEARBY_STROKE_DASH);
            nearbyParcels.put("style", nearbyStyle);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting nearby parcels config");
        }
        return this;
    }

    // =========================================================================
    // AUTO-CENTER
    // =========================================================================

    /**
     * Enable and configure auto-center feature.
     */
    public GisConfigBuilder withAutoCenter(String districtFieldId, String villageFieldId,
                                           String latFieldId, String lonFieldId,
                                           String countrySuffix, int zoom,
                                           boolean retryOnFieldChange) {
        try {
            autoCenter = new JSONObject();
            autoCenter.put("enabled", true);
            autoCenter.put("districtFieldId", getValueOrDefault(districtFieldId, ""));
            autoCenter.put("villageFieldId", getValueOrDefault(villageFieldId, ""));
            autoCenter.put("latFieldId", getValueOrDefault(latFieldId, ""));
            autoCenter.put("lonFieldId", getValueOrDefault(lonFieldId, ""));
            autoCenter.put("countrySuffix", getValueOrDefault(countrySuffix, DEFAULT_COUNTRY_SUFFIX));
            autoCenter.put("zoom", zoom > 0 ? zoom : DEFAULT_AUTO_CENTER_ZOOM);
            autoCenter.put("retryOnFieldChange", retryOnFieldChange);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting auto-center config");
        }
        return this;
    }

    // =========================================================================
    // SIMPLIFICATION
    // =========================================================================

    /**
     * Enable and configure polygon simplification.
     *
     * @param enabled Whether simplification is enabled
     * @param tolerance Simplification tolerance in meters (higher = fewer vertices)
     */
    public GisConfigBuilder withSimplification(boolean enabled, double tolerance) {
        try {
            simplification = new JSONObject();
            simplification.put("enabled", enabled);
            simplification.put("tolerance", tolerance > 0 ? tolerance : DEFAULT_SIMPLIFICATION_TOLERANCE);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error setting simplification config");
        }
        return this;
    }

    // =========================================================================
    // BUILD
    // =========================================================================

    /**
     * Build the final configuration JSON string.
     */
    public String build() {
        try {
            // Add nested objects
            config.put("validation", validation);
            config.put("gps", gps);
            config.put("style", style);
            config.put("outputFields", outputFields);

            // Add optional modules
            if (overlap != null) {
                config.put("overlap", overlap);
            }
            if (nearbyParcels != null) {
                config.put("nearbyParcels", nearbyParcels);
            }
            if (autoCenter != null) {
                config.put("autoCenter", autoCenter);
            }
            if (simplification != null) {
                config.put("simplification", simplification);
            }

            return config.toString();
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error building config JSON");
            return "{}";
        }
    }

    /**
     * Get the raw JSONObject (for custom manipulation).
     */
    public JSONObject toJSONObject() {
        try {
            // Build nested objects into config
            config.put("validation", validation);
            config.put("gps", gps);
            config.put("style", style);
            config.put("outputFields", outputFields);

            if (overlap != null) {
                config.put("overlap", overlap);
            }
            if (nearbyParcels != null) {
                config.put("nearbyParcels", nearbyParcels);
            }
            if (autoCenter != null) {
                config.put("autoCenter", autoCenter);
            }
            if (simplification != null) {
                config.put("simplification", simplification);
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error converting to JSONObject");
        }
        return config;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private String getValueOrDefault(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * Parse double safely with default value.
     */
    public static double parseDoubleSafe(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse integer safely with default value.
     */
    public static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse boolean from string ("true" returns true, anything else returns default).
     */
    public static boolean parseBooleanSafe(String value, boolean defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }
}
