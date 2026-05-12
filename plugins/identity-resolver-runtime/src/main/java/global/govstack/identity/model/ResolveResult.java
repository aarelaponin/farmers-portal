package global.govstack.identity.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of a single {@code ResolverService.resolve(configId, value)} call.
 * Mirrors the shape of the REST response defined in
 * {@code _design/identity-resolver-design.md} §4.3.
 */
public final class ResolveResult {

    public enum Status { FOUND, NOT_FOUND, MULTIPLE, ERROR }

    public static final class Candidate {
        public final String sourceRecordId;
        public final String displayLabel;
        public Candidate(String sourceRecordId, String displayLabel) {
            this.sourceRecordId = sourceRecordId;
            this.displayLabel   = displayLabel;
        }
    }

    private final Status              status;
    private final String              sourceRecordId;
    private final Map<String, String> fields;
    private final String              message;
    private final String              actionUrl;
    private final List<Candidate>     candidates;

    private ResolveResult(Status status, String sourceRecordId,
                          Map<String, String> fields, String message,
                          String actionUrl, List<Candidate> candidates) {
        this.status         = status;
        this.sourceRecordId = sourceRecordId;
        this.fields         = fields == null ? Collections.emptyMap() : fields;
        this.message        = message;
        this.actionUrl      = actionUrl;
        this.candidates     = candidates == null ? Collections.emptyList() : candidates;
    }

    public Status              getStatus()         { return status; }
    public String              getSourceRecordId() { return sourceRecordId; }
    public Map<String, String> getFields()         { return fields; }
    public String              getMessage()        { return message; }
    public String              getActionUrl()      { return actionUrl; }
    public List<Candidate>     getCandidates()     { return candidates; }

    /* ----------- factory helpers ----------- */

    public static ResolveResult found(String sourceRecordId, Map<String, String> fields) {
        return new ResolveResult(Status.FOUND, sourceRecordId,
                new LinkedHashMap<>(fields), null, null, null);
    }

    public static ResolveResult notFound(String message, String actionUrl) {
        return new ResolveResult(Status.NOT_FOUND, null, null, message, actionUrl, null);
    }

    public static ResolveResult multiple(List<Candidate> candidates) {
        return new ResolveResult(Status.MULTIPLE, null, null,
                "Multiple records match — disambiguation required.", null, candidates);
    }

    public static ResolveResult error(String message) {
        return new ResolveResult(Status.ERROR, null, null, message, null, null);
    }
}
