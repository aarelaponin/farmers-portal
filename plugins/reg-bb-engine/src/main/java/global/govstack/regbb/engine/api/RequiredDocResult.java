package global.govstack.regbb.engine.api;

import java.util.Collections;
import java.util.List;

/**
 * Resolved required-document description. Returned by
 * {@link DeterminantEvaluator#resolveRequiredDocs}; one element per
 * effective {@code mm_required_doc} after deduplication-across-Registrations
 * per spec §6.1.6 and conditional Determinant evaluation.
 *
 * <p>Per audit §6.2 #6, carries {@code captureFieldsJson}-equivalent
 * {@link #captureFields} so {@code MetaScreenElement} can render the
 * per-document metadata fields the citizen fills.
 */
public final class RequiredDocResult {

    public static final class CaptureField {
        public final String name;
        public final String label;
        public final String type;       // text | date | number
        public final boolean required;
        public CaptureField(String name, String label, String type, boolean required) {
            this.name = name;
            this.label = label;
            this.type = type;
            this.required = required;
        }
    }

    public final String docId;          // mm_required_doc.id
    public final String code;           // e.g. NATIONAL_ID
    public final String label;
    public final String acceptedTypes;  // CSV of MIME types
    public final long maxSizeBytes;
    public final boolean mandatory;     // resolved from requiredness + conditional Determinant
    public final String deliveryMode;   // upload | front_desk | sign_in_front_of_operator
    public final List<CaptureField> captureFields;
    public final String helpText;

    public RequiredDocResult(String docId, String code, String label,
                             String acceptedTypes, long maxSizeBytes, boolean mandatory,
                             String deliveryMode, List<CaptureField> captureFields,
                             String helpText) {
        this.docId = docId;
        this.code = code;
        this.label = label;
        this.acceptedTypes = acceptedTypes;
        this.maxSizeBytes = maxSizeBytes;
        this.mandatory = mandatory;
        this.deliveryMode = deliveryMode;
        this.captureFields = captureFields == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(captureFields);
        this.helpText = helpText;
    }
}
