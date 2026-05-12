package global.govstack.application.model;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot of a programme's spec, materialised by {@code ProgrammeSpecReader}
 * and consumed by {@code SeedingService}. Read-only, value-typed.
 */
public final class ProgrammeSpec {
    public final String programmeId;          // sp_program.id (e.g. prog001)
    public final String programmeCode;        // human-readable code (PRG-2025-001)
    public final String programmeStatus;      // e.g. ACTIVE / DRAFT
    public final String tabEligibilityId;     // sp_program.c_tab_eligibility
    public final String tabBenefitsId;        // sp_program.c_tab_benefits
    public final String tabBeneficiaryId;     // sp_program.c_tab_beneficiary
    public final String tabMonitoringId;      // sp_program.c_tab_monitoring
    public final String tabDocumentsId;       // sp_program.c_tab_documents
    public final List<EligibilityCriterion> criteria;
    public final List<BenefitItem>          benefits;
    public final List<DocumentRequirement>  documents;

    public ProgrammeSpec(String programmeId, String programmeCode, String programmeStatus,
                         String tabEligibilityId, String tabBenefitsId,
                         String tabBeneficiaryId, String tabMonitoringId,
                         String tabDocumentsId,
                         List<EligibilityCriterion> criteria,
                         List<BenefitItem> benefits,
                         List<DocumentRequirement> documents) {
        this.programmeId = programmeId;
        this.programmeCode = programmeCode;
        this.programmeStatus = programmeStatus;
        this.tabEligibilityId = tabEligibilityId;
        this.tabBenefitsId = tabBenefitsId;
        this.tabBeneficiaryId = tabBeneficiaryId;
        this.tabMonitoringId = tabMonitoringId;
        this.tabDocumentsId = tabDocumentsId;
        this.criteria = criteria == null ? Collections.emptyList() : criteria;
        this.benefits = benefits == null ? Collections.emptyList() : benefits;
        this.documents = documents == null ? Collections.emptyList() : documents;
    }
}
