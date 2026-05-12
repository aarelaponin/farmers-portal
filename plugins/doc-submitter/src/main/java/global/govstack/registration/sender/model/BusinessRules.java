package global.govstack.registration.sender.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class for business-rules.yaml configuration file
 * Defines conditional validation logic for generating validation-rules.yaml
 */
public class BusinessRules {

    private List<ConditionalRule> conditionalRules;

    public BusinessRules() {
        this.conditionalRules = new ArrayList<>();
    }

    /**
     * A single conditional validation rule
     */
    public static class ConditionalRule {
        private String triggerField;
        private String triggerValue;
        private String requiresGrid;
        private Integer minEntries;
        private List<String> requiresFields;
        private String messageTemplate;

        // Getters and setters
        public String getTriggerField() { return triggerField; }
        public void setTriggerField(String triggerField) { this.triggerField = triggerField; }

        public String getTriggerValue() { return triggerValue; }
        public void setTriggerValue(String triggerValue) { this.triggerValue = triggerValue; }

        public String getRequiresGrid() { return requiresGrid; }
        public void setRequiresGrid(String requiresGrid) { this.requiresGrid = requiresGrid; }

        public Integer getMinEntries() { return minEntries; }
        public void setMinEntries(Integer minEntries) { this.minEntries = minEntries; }

        public List<String> getRequiresFields() { return requiresFields; }
        public void setRequiresFields(List<String> requiresFields) { this.requiresFields = requiresFields; }

        public String getMessageTemplate() { return messageTemplate; }
        public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }

        /**
         * Build the condition string for validation-rules.yaml
         * @return Condition string like "cropProduction == 'yes'"
         */
        public String buildCondition() {
            return triggerField + " == '" + triggerValue + "'";
        }

        /**
         * Generate error message by replacing placeholders
         * @return Generated error message
         */
        public String generateMessage() {
            if (messageTemplate == null || messageTemplate.isEmpty()) {
                // Auto-generate message
                if (requiresGrid != null) {
                    return requiresGrid + " is required when " + triggerField + " is '" + triggerValue + "'";
                } else if (requiresFields != null && !requiresFields.isEmpty()) {
                    return String.join(", ", requiresFields) + " is required when " + triggerField + " is '" + triggerValue + "'";
                }
                return "Validation failed for condition: " + buildCondition();
            }

            // Replace placeholders in template
            String message = messageTemplate;
            message = message.replace("{trigger_field}", triggerField);
            message = message.replace("{trigger_value}", triggerValue);
            if (minEntries != null) {
                message = message.replace("{min_entries}", String.valueOf(minEntries));
            }
            return message;
        }
    }

    // Getters and setters
    public List<ConditionalRule> getConditionalRules() { return conditionalRules; }
    public void setConditionalRules(List<ConditionalRule> conditionalRules) { this.conditionalRules = conditionalRules; }
}
