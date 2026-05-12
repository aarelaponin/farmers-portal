package global.govstack.registration.sender.service.transform;

import org.joget.commons.util.LogUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service that manages all data transformers and applies transformations
 *
 * This service provides a central point for all data transformations between
 * Joget and GovStack formats, supporting both encoding and decoding
 */
public class TransformationService {

    private static final String CLASS_NAME = TransformationService.class.getName();

    private final List<DataTransformer> transformers;

    public TransformationService() {
        this.transformers = new ArrayList<>();
        registerDefaultTransformers();
    }

    /**
     * Register all default transformers
     */
    private void registerDefaultTransformers() {
        transformers.add(new DateTransformer());
        transformers.add(new BooleanTransformer());
        transformers.add(new NumericTransformer());
        transformers.add(new MultiCheckboxTransformer());

        LogUtil.info(CLASS_NAME, "Registered " + transformers.size() + " default transformers");
    }

    /**
     * Apply encoding transformation (Joget → GovStack)
     *
     * @param value The Joget value to transform
     * @param transformType The transformation type to apply
     * @return The transformed value for GovStack
     */
    public Object encode(Object value, String transformType) {
        if (transformType == null || transformType.trim().isEmpty()) {
            return value;
        }

        for (DataTransformer transformer : transformers) {
            if (transformer.supports(transformType)) {
                try {
                    Object result = transformer.encode(value, transformType);
                    LogUtil.info(CLASS_NAME, "Applied encoding transformation '" + transformType +
                                "' to value: " + value + " -> " + result);
                    return result;
                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error applying encoding transformation: " + transformType);
                    return value;
                }
            }
        }

        LogUtil.warn(CLASS_NAME, "No transformer found for type: " + transformType);
        return value;
    }

    /**
     * Apply decoding transformation (GovStack → Joget)
     *
     * @param value The GovStack value to transform
     * @param transformType The transformation type to apply
     * @return The transformed value for Joget
     */
    public Object decode(Object value, String transformType) {
        if (transformType == null || transformType.trim().isEmpty()) {
            return value;
        }

        for (DataTransformer transformer : transformers) {
            if (transformer.supports(transformType)) {
                try {
                    Object result = transformer.decode(value, transformType);
                    LogUtil.info(CLASS_NAME, "Applied decoding transformation '" + transformType +
                                "' to value: " + value + " -> " + result);
                    return result;
                } catch (Exception e) {
                    LogUtil.error(CLASS_NAME, e, "Error applying decoding transformation: " + transformType);
                    return value;
                }
            }
        }

        LogUtil.warn(CLASS_NAME, "No transformer found for type: " + transformType);
        return value;
    }

    /**
     * Apply value mapping transformation
     *
     * @param value The value to map
     * @param valueMapping The mapping configuration
     * @param direction "encode" for Joget→GovStack, "decode" for GovStack→Joget
     * @return The mapped value
     */
    public Object applyValueMapping(Object value, Map<String, Object> valueMapping, String direction) {
        if (value == null || valueMapping == null || valueMapping.isEmpty()) {
            return value;
        }

        String valueStr = value.toString();

        if ("encode".equalsIgnoreCase(direction)) {
            // Direct mapping for encoding
            if (valueMapping.containsKey(valueStr)) {
                Object mappedValue = valueMapping.get(valueStr);
                LogUtil.info(CLASS_NAME, "Applied value mapping (encode): " + valueStr + " -> " + mappedValue);
                return mappedValue;
            }
        } else if ("decode".equalsIgnoreCase(direction)) {
            // Reverse mapping for decoding
            for (Map.Entry<String, Object> entry : valueMapping.entrySet()) {
                if (valueStr.equals(String.valueOf(entry.getValue()))) {
                    LogUtil.info(CLASS_NAME, "Applied value mapping (decode): " + valueStr + " -> " + entry.getKey());
                    return entry.getKey();
                }
            }
        }

        // Return original value if no mapping found
        return value;
    }

    /**
     * Register a custom transformer
     *
     * @param transformer The transformer to register
     */
    public void registerTransformer(DataTransformer transformer) {
        transformers.add(transformer);
        LogUtil.info(CLASS_NAME, "Registered custom transformer: " + transformer.getClass().getSimpleName());
    }

    /**
     * Check if a transformation type is supported
     *
     * @param transformType The transformation type to check
     * @return true if supported
     */
    public boolean isTransformationSupported(String transformType) {
        if (transformType == null || transformType.trim().isEmpty()) {
            return false;
        }

        return transformers.stream().anyMatch(t -> t.supports(transformType));
    }
}