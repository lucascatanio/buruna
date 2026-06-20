package com.buruna.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Garante que domain e application de um contexto não importem domain/persistence de outro.
 * A camada web fica de fora: controllers recebem @AuthenticationPrincipal User via Spring
 * Security e extraem user.getId() antes de delegar — padrão documentado em §7.3.
 *
 * Expanda MIGRATED_CONTEXTS ao migrar cada contexto para Clean Arch.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    private static final String[] MIGRATED_CONTEXTS = {
            "engagement",
            "reading"
    };

    private static final String BASE = "com.buruna";

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void domainAndApplication_shouldNotImportInternalsOfOtherContexts() {
        for (String context : MIGRATED_CONTEXTS) {
            String[] forbidden = buildForbiddenPackages(context);
            if (forbidden.length == 0) continue;

            ArchRule rule = noClasses()
                    .that().resideInAPackage(BASE + "." + context + ".domain..")
                    .or().resideInAPackage(BASE + "." + context + ".application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(forbidden)
                    .because("cross-context access must go through another context's " +
                             "application layer only (ADR-35 §2.3)");

            rule.check(classes);
        }
    }

    private static String[] buildForbiddenPackages(String self) {
        String[] allContexts = {"engagement", "manga", "reading", "user", "auth", "admin"};
        return java.util.Arrays.stream(allContexts)
                .filter(c -> !c.equals(self))
                .flatMap(c -> java.util.Arrays.stream(new String[]{
                        BASE + "." + c + ".domain..",
                        BASE + "." + c + ".persistence..",
                        BASE + "." + c + ".repository.."
                }))
                .toArray(String[]::new);
    }
}
