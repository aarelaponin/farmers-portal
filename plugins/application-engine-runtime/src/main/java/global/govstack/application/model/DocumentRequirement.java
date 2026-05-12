package global.govstack.application.model;

/**
 * One row from {@code sp_doc_requirement_row} — a single required document
 * type on the programme's Documents tab. Snapshot fields ({@code docLabel},
 * {@code acceptedFormats}, {@code maxSizeKb}) are derived at read time by
 * {@link global.govstack.application.service.ProgrammeSpecReader} from the
 * paired {@code md23documentType} row, with optional per-programme overrides
 * winning if non-empty.
 */
public final class DocumentRequirement {
    public final String id;                  // sp_doc_requirement_row.id
    public final String docTypeCode;         // FK to md23documentType.code
    public final String docLabel;            // already-resolved label (override OR md23.name)
    public final String docMandatory;        // 'Y' or 'N'
    public final String acceptedFormats;     // already-resolved (override OR md23.allowed_formats)
    public final String maxSizeKb;           // already-resolved (override OR md23.max_size_mb*1024)
    public final String displayOrder;

    public DocumentRequirement(String id,
                               String docTypeCode,
                               String docLabel,
                               String docMandatory,
                               String acceptedFormats,
                               String maxSizeKb,
                               String displayOrder) {
        this.id              = id;
        this.docTypeCode     = docTypeCode;
        this.docLabel        = docLabel;
        this.docMandatory    = docMandatory;
        this.acceptedFormats = acceptedFormats;
        this.maxSizeKb       = maxSizeKb;
        this.displayOrder    = displayOrder;
    }

    /** Best-effort human-readable label, never null. */
    public String displayLabel() {
        if (docLabel    != null && !docLabel.isEmpty())    return docLabel;
        if (docTypeCode != null && !docTypeCode.isEmpty()) return docTypeCode;
        return "Document " + id;
    }
}
