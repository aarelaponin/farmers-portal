package global.govstack.farmreg.registration.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test for MappingValidator to ensure coverage between form_structure.yaml and services.yml
 */
public class MappingValidatorTest {

    @Test
    public void testMappingCoverage() {
        MappingValidator validator = new MappingValidator();
        MappingValidator.ValidationReport report = validator.validate();

        // Print report
        System.out.println(report.generateReport());

        // Assert no errors
        assertFalse("Validation should not have errors", report.hasErrors());

        // Assert overall coverage is at least 80%
        double coverage = report.getOverallCoverage();
        assertTrue("Coverage should be at least 80%, but was: " + coverage + "%",
                   coverage >= 80.0);
    }

    @Test
    public void testGridFormsCoverage() {
        MappingValidator validator = new MappingValidator();
        MappingValidator.ValidationReport report = validator.validate();

        // Find grid form coverages
        for (MappingValidator.FormCoverage fc : report.getFormCoverages()) {
            if (fc.formName.contains("householdMemberForm") ||
                fc.formName.contains("cropManagementForm") ||
                fc.formName.contains("livestockDetailsForm")) {

                System.out.println(String.format("Grid %s: %.1f%% coverage (%d/%d fields)",
                    fc.formName, fc.coverage, fc.mappedFields, fc.totalFields));

                // Grid forms should have 100% coverage
                assertTrue(String.format("%s should have 100%% coverage, but has %.1f%%",
                    fc.formName, fc.coverage),
                    fc.coverage == 100.0);
            }
        }
    }
}
