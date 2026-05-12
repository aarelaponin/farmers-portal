package global.govstack.registration.sender.service.transform;

/**
 * Bidirectional data transformer interface for converting between Joget and GovStack formats
 *
 * This interface supports both encoding (Joget → GovStack) and decoding (GovStack → Joget)
 * to enable data reuse between DocSubmitter and ProcessingAPI
 */
public interface DataTransformer {

    /**
     * Transform data from Joget format to GovStack format (encoding)
     * Used by DocSubmitter when sending data to the API
     *
     * @param jogetValue The value from Joget form
     * @param transformType The type of transformation to apply
     * @return The transformed value for GovStack API
     */
    Object encode(Object jogetValue, String transformType);

    /**
     * Transform data from GovStack format to Joget format (decoding)
     * Used by ProcessingAPI when receiving data from the API
     *
     * @param govstackValue The value from GovStack API
     * @param transformType The type of transformation to apply
     * @return The transformed value for Joget form
     */
    Object decode(Object govstackValue, String transformType);

    /**
     * Check if this transformer supports the given transformation type
     *
     * @param transformType The transformation type to check
     * @return true if this transformer can handle the type
     */
    boolean supports(String transformType);
}