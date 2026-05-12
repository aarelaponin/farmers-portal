package global.govstack.registration.sender.service.transform;

import org.joget.commons.util.LogUtil;

/**
 * Transformer for boolean values between Joget and GovStack formats
 *
 * Joget format: "yes"/"no" or "true"/"false" strings
 * GovStack format: true/false boolean values
 */
public class BooleanTransformer implements DataTransformer {

    private static final String CLASS_NAME = BooleanTransformer.class.getName();

    @Override
    public Object encode(Object jogetValue, String transformType) {
        if (jogetValue == null) {
            return false;
        }

        String value = jogetValue.toString().trim().toLowerCase();

        // Convert Joget yes/no to boolean
        switch (value) {
            case "yes":
            case "y":
            case "true":
            case "1":
            case "checked":
            case "on":
                return true;
            case "no":
            case "n":
            case "false":
            case "0":
            case "unchecked":
            case "off":
            case "":
                return false;
            default:
                LogUtil.warn(CLASS_NAME, "Unknown boolean value for encoding: " + value);
                return false;
        }
    }

    @Override
    public Object decode(Object govstackValue, String transformType) {
        if (govstackValue == null) {
            return "no";
        }

        // Handle different input types
        if (govstackValue instanceof Boolean) {
            return ((Boolean) govstackValue) ? "yes" : "no";
        }

        String value = govstackValue.toString().trim().toLowerCase();

        // Convert boolean to Joget yes/no
        switch (value) {
            case "true":
            case "1":
            case "yes":
            case "y":
                return "yes";
            case "false":
            case "0":
            case "no":
            case "n":
            case "":
                return "no";
            default:
                LogUtil.warn(CLASS_NAME, "Unknown boolean value for decoding: " + value);
                return "no";
        }
    }

    @Override
    public boolean supports(String transformType) {
        return "yesNoBoolean".equalsIgnoreCase(transformType) ||
               "yesnoboolean".equalsIgnoreCase(transformType) ||
               "boolean".equalsIgnoreCase(transformType) ||
               "bool".equalsIgnoreCase(transformType);
    }
}