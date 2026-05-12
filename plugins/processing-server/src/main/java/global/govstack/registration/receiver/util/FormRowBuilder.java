package global.govstack.registration.receiver.util;

import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import global.govstack.registration.receiver.config.Constants;

import java.util.Map;

/**
 * Builder for FormRow objects
 */
public class FormRowBuilder {
    private final FormRow row;

    /**
     * Constructor with ID
     *
     * @param id The ID for the form row
     */
    public FormRowBuilder(String id) {
        row = new FormRow();
        row.setId(id);
    }

    /**
     * Add a standard field
     *
     * @param key Field key
     * @param value Field value
     * @return Builder instance for chaining
     */
    public FormRowBuilder addField(String key, String value) {
        if (key != null && value != null) {
            if (Constants.ID.equals(key)) {
                row.put(Constants.FIELD_APPLICATION_ID, value);
            } else {
                row.put(key, value);
            }
        }
        return this;
    }

    /**
     * Add fields from a map
     *
     * @param fields Map of field key-value pairs
     * @return Builder instance for chaining
     */
    public FormRowBuilder addFields(Map<String, String> fields) {
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                addField(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * Set the application ID field
     *
     * @param value Application ID value
     * @return Builder instance for chaining
     */
    public FormRowBuilder withApplicationId(String value) {
        if (value != null) {
            row.put(Constants.FIELD_APPLICATION_ID, value);
        }
        return this;
    }

    /**
     * Build the FormRow object
     *
     * @return Completed FormRow
     */
    public FormRow build() {
        return row;
    }

    /**
     * Build and add to a FormRowSet
     *
     * @param rowSet The FormRowSet to add to
     * @return The updated FormRowSet
     */
    public FormRowSet addToRowSet(FormRowSet rowSet) {
        if (rowSet == null) {
            rowSet = new FormRowSet();
        }
        rowSet.add(build());
        return rowSet;
    }

    /**
     * Create a new FormRowSet containing this row
     *
     * @return A new FormRowSet containing the built row
     */
    public FormRowSet buildRowSet() {
        FormRowSet rowSet = new FormRowSet();
        rowSet.add(build());
        return rowSet;
    }
}