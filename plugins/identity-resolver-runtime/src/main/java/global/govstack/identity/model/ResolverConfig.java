package global.govstack.identity.model;

/**
 * One row from {@code app_resolver_config}. Defines a named identity resolver:
 * which source form to query, which field on it matches the input, and how to
 * behave when zero or multiple records match.
 *
 * <p>See {@code _design/identity-resolver-design.md} §3.1 for column semantics.
 */
public final class ResolverConfig {

    public enum MultipleMatchesPolicy {
        /** Refuse to resolve when more than one row matches. */
        ERROR,
        /** Pick the first row deterministically (by dateCreated). */
        FIRST,
        /** Return the candidate set; UI handles disambiguation. */
        LET_USER_PICK
    }

    private final String  rowId;
    private final String  configId;
    private final String  name;
    private final String  description;
    private final String  sourceFormId;
    private final String  sourceLookupField;
    private final String  notFoundMessage;
    private final String  notFoundActionUrl;
    private final MultipleMatchesPolicy multipleMatchesPolicy;
    private final int     cacheSeconds;
    private final boolean active;

    public ResolverConfig(String rowId, String configId, String name, String description,
                          String sourceFormId, String sourceLookupField,
                          String notFoundMessage, String notFoundActionUrl,
                          MultipleMatchesPolicy multipleMatchesPolicy,
                          int cacheSeconds, boolean active) {
        this.rowId = rowId;
        this.configId = configId;
        this.name = name;
        this.description = description;
        this.sourceFormId = sourceFormId;
        this.sourceLookupField = sourceLookupField;
        this.notFoundMessage = notFoundMessage;
        this.notFoundActionUrl = notFoundActionUrl;
        this.multipleMatchesPolicy = multipleMatchesPolicy;
        this.cacheSeconds = cacheSeconds;
        this.active = active;
    }

    public String  getRowId()                 { return rowId; }
    public String  getConfigId()              { return configId; }
    public String  getName()                  { return name; }
    public String  getDescription()           { return description; }
    public String  getSourceFormId()          { return sourceFormId; }
    public String  getSourceLookupField()     { return sourceLookupField; }
    public String  getNotFoundMessage()       { return notFoundMessage; }
    public String  getNotFoundActionUrl()     { return notFoundActionUrl; }
    public MultipleMatchesPolicy getMultipleMatchesPolicy() { return multipleMatchesPolicy; }
    public int     getCacheSeconds()          { return cacheSeconds; }
    public boolean isActive()                 { return active; }
}
