package global.govstack.registration.sender.service.transform;

import org.joget.commons.util.LogUtil;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Transformer for date values between Joget and GovStack formats
 *
 * Joget format: yyyy-MM-dd
 * GovStack format: ISO8601 (yyyy-MM-dd'T'HH:mm:ss'Z')
 */
public class DateTransformer implements DataTransformer {

    private static final String CLASS_NAME = DateTransformer.class.getName();

    // Date format patterns
    private static final String JOGET_DATE_FORMAT = "yyyy-MM-dd";
    private static final String ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    // Formatters
    private static final SimpleDateFormat jogetFormatter = new SimpleDateFormat(JOGET_DATE_FORMAT);
    private static final SimpleDateFormat iso8601Formatter = new SimpleDateFormat(ISO8601_FORMAT);
    private static final DateTimeFormatter localDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter isoDateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public Object encode(Object jogetValue, String transformType) {
        if (jogetValue == null || jogetValue.toString().trim().isEmpty()) {
            return null;
        }

        String dateStr = jogetValue.toString().trim();

        try {
            // Handle different input formats
            if (dateStr.contains("T")) {
                // Already in ISO format, return as is
                return dateStr;
            } else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                // Convert yyyy-MM-dd to ISO8601
                LocalDate date = LocalDate.parse(dateStr, localDateFormatter);
                return date.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME) + "Z";
            } else {
                // Try to parse as Joget format and convert to ISO8601
                Date date = jogetFormatter.parse(dateStr);
                return iso8601Formatter.format(date);
            }
        } catch (ParseException | DateTimeParseException e) {
            LogUtil.warn(CLASS_NAME, "Failed to encode date: " + dateStr + " - " + e.getMessage());
            // Return original value if parsing fails
            return dateStr;
        }
    }

    @Override
    public Object decode(Object govstackValue, String transformType) {
        if (govstackValue == null || govstackValue.toString().trim().isEmpty()) {
            return null;
        }

        String dateStr = govstackValue.toString().trim();

        try {
            // Handle different input formats
            if (dateStr.contains("T")) {
                // ISO8601 format to Joget format
                // Remove timezone info if present
                if (dateStr.contains("+") || dateStr.contains("Z")) {
                    dateStr = dateStr.replaceAll("[+Z].*", "");
                }
                LocalDateTime dateTime = LocalDateTime.parse(dateStr, isoDateTimeFormatter);
                return dateTime.format(localDateFormatter);
            } else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                // Already in Joget format
                return dateStr;
            } else {
                // Try to parse and convert to Joget format
                Date date = iso8601Formatter.parse(dateStr);
                return jogetFormatter.format(date);
            }
        } catch (ParseException | DateTimeParseException e) {
            LogUtil.warn(CLASS_NAME, "Failed to decode date: " + dateStr + " - " + e.getMessage());
            // Return original value if parsing fails
            return dateStr;
        }
    }

    @Override
    public boolean supports(String transformType) {
        return "date_ISO8601".equalsIgnoreCase(transformType) ||
               "date".equalsIgnoreCase(transformType) ||
               "dateISO8601".equalsIgnoreCase(transformType);
    }
}