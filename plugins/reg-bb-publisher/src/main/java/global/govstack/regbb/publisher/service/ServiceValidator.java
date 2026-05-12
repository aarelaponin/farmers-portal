package global.govstack.regbb.publisher.service;

import org.joget.commons.util.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Walks an {@code mm_service} and its child meta-records (registrations,
 * screens, fields, actions, catalogs, roles, fees, benefits, required docs,
 * determinants, role_screens) and validates every closed-set rule before the
 * publisher allows status to flip from {@code draft} to {@code published}.
 *
 * <p>Per spec §4.3.4 / §5.1. Implementation lands Week 5; this is the Week 1
 * scaffolding stub.
 *
 * <p>Validation steps the publisher will perform (Week 5):
 * <ul>
 *   <li>At least one Registration belongs to the Service.
 *   <li>Every Registration with applicabilityKind != mandatory_for_all names
 *       an applicabilityDeterminantId.
 *   <li>Every Field belongs to a Screen and references at least one Registration.
 *   <li>Every Determinant references existing entities.
 *   <li>Every Fee with type=calculated has formulaJson.
 *   <li>Every Benefit (per D8) belongs to a Registration.
 *   <li>Every required_doc belongs to a Registration.
 *   <li>Every Role belongs to a Registration.
 *   <li>Citizen screens form a non-empty ordered set.
 *   <li>Every Determinant parses against the DSL grammar
 *       (rules-grammar plugin) and is classified fast-path or SQL-path.
 *   <li>Arithmetic operators appear only in mm_fee.formulaJson and mm_benefit.formulaJson.
 *   <li>Every Action's triggerJson.target resolves and configJson is well-formed.
 *   <li>For Determinants in field/screen scope classified as SQL-path,
 *       emit a publish-time warning unless mm_determinant.allowSlowPath = true.
 *   <li>Every Catalog with source=registry has its imCapabilityRef registered
 *       in app_fd_reg_bb_im_capability.
 * </ul>
 */
public class ServiceValidator {

    public static final class ValidationResult {
        public final boolean ok;
        public final List<String> errors;
        public final List<String> warnings;
        public ValidationResult(boolean ok, List<String> errors, List<String> warnings) {
            this.ok = ok;
            this.errors = errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
            this.warnings = warnings == null ? Collections.emptyList() : Collections.unmodifiableList(warnings);
        }
    }

    /**
     * Validate a service for publish.
     *
     * @param serviceId  the {@code mm_service.id} to validate
     * @return an {@code ok=true} result if no blocking errors; warnings may still be non-empty.
     */
    public ValidationResult validate(String serviceId) {
        LogUtil.warn(ServiceValidator.class.getName(),
                "ServiceValidator.validate(" + serviceId + ") — Week 1 stub. Implementation lands Week 5.");

        List<String> errors = new ArrayList<>();
        errors.add("not_implemented_yet");
        return new ValidationResult(false, errors, Collections.emptyList());
    }
}
