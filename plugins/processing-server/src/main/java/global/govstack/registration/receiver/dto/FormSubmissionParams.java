package global.govstack.registration.receiver.dto;

/**
 * DTO for form submission parameters using Builder pattern
 */
public class FormSubmissionParams {
    private final String formId;
    private final String processDefId;
    private final String adminUsername;
    private final String farmerUsername;
    private final String chiefReviewerUsername;
    private final String submitActivityId;
    private final String reviewActivityId;
    private final String config;

    private FormSubmissionParams(Builder builder) {
        this.formId = builder.formId;
        this.processDefId = builder.processDefId;
        this.adminUsername = builder.adminUsername;
        this.farmerUsername = builder.farmerUsername;
        this.chiefReviewerUsername = builder.chiefReviewerUsername;
        this.submitActivityId = builder.submitActivityId;
        this.reviewActivityId = builder.reviewActivityId;
        this.config = builder.config;
    }

    public String getFormId() {
        return formId;
    }

    public String getProcessDefId() {
        return processDefId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getFarmerUsername() {
        return farmerUsername;
    }

    public String getChiefReviewerUsername() {
        return chiefReviewerUsername;
    }

    public String getSubmitActivityId() {
        return submitActivityId;
    }

    public String getReviewActivityId() {
        return reviewActivityId;
    }

    public String getConfig() {
        return config;
    }

    public static class Builder {
        private String formId;
        private String processDefId;
        private String adminUsername;
        private String farmerUsername;
        private String chiefReviewerUsername;
        private String submitActivityId;
        private String reviewActivityId;
        private String config;

        public Builder formId(String formId) {
            this.formId = formId;
            return this;
        }

        public Builder processDefId(String processDefId) {
            this.processDefId = processDefId;
            return this;
        }

        public Builder adminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
            return this;
        }

        public Builder farmerUsername(String farmerUsername) {
            this.farmerUsername = farmerUsername;
            return this;
        }

        public Builder chiefReviewerUsername(String chiefReviewerUsername) {
            this.chiefReviewerUsername = chiefReviewerUsername;
            return this;
        }

        public Builder submitActivityId(String submitActivityId) {
            this.submitActivityId = submitActivityId;
            return this;
        }

        public Builder reviewActivityId(String reviewActivityId) {
            this.reviewActivityId = reviewActivityId;
            return this;
        }

        public Builder config(String config) {
            this.config = config;
            return this;
        }

        public FormSubmissionParams build() {
            return new FormSubmissionParams(this);
        }
    }
}
