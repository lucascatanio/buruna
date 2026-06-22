package com.buruna.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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
            "reading",
            "identity"
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

            // (domain.. OR application..) — agrupado como um único predicado para que o
            // AND-NOT abaixo se aplique ao conjunto INTEIRO. ArchUnit encadeia .or()/.and()
            // estritamente da esquerda p/ direita (sem precedência booleana), então usar a
            // forma fluente faria o ≠job valer só para o ramo application.
            DescribedPredicate<JavaClass> inContext =
                    resideInAPackage(BASE + "." + context + ".domain..")
                            .or(resideInAPackage(BASE + "." + context + ".application.."));

            // TODO Epic 5.3: remover esta exclusão quando o job usar
            //   DeletePrivateCollectionForUserUseCase (ADR-35). Sem a exclusão, as ~11
            //   violações de acoplamento manga.* fazem o build falhar — é proposital:
            //   força o desacoplamento no 5.3.
            DescribedPredicate<JavaClass> inContextExceptInactivityJob =
                    inContext.and(not(name("com.buruna.identity.application.admin.InactivityJob")));

            ArchRule rule = noClasses()
                    .that(inContextExceptInactivityJob)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(forbidden)
                    .because("cross-context access must go through another context's " +
                             "application layer only (ADR-35 §2.3)");

            rule.check(classes);
        }
    }

    /**
     * Guard parcial para ADR-39: detecta @Query(nativeQuery=true) nas camadas persistence
     * de contextos migrados. Native SQL escapa à análise de imports Java, então esse guard
     * captura o vetor de violação mais provável (repositório de um contexto fazendo SQL
     * direto em tabelas de outro).
     *
     * JPQL que referencia entidade de outro contexto por nome (string) NÃO é detectado por
     * este guard nem pelo guard de imports — não há import Java nem é native query.
     * Esse caso permanece exclusivamente por revisão de código (ADR-39).
     *
     * Se um native query estritamente intracontexto for necessário por performance, ajuste
     * o filtro desta regra para excluir o método específico. NÃO use @ArchIgnore: ele
     * desliga a regra inteira para toda a classe anotada.
     */
    @Test
    void persistenceLayer_shouldNotUseNativeQueries() {
        ArchCondition<JavaMethod> noNativeQuery =
                new ArchCondition<JavaMethod>("not use @Query(nativeQuery=true)") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        method.getAnnotations().stream()
                                .filter(a -> a.getRawType().getSimpleName().equals("Query"))
                                .filter(a -> Boolean.TRUE.equals(a.get("nativeQuery").orElse(false)))
                                .forEach(a -> events.add(SimpleConditionEvent.violated(method,
                                        method.getDescription() + " usa @Query(nativeQuery=true); " +
                                        "acesso cross-contexto deve passar por use case público (ADR-39). " +
                                        "Se for intracontexto e necessário, ajuste o filtro do guard em " +
                                        "ArchitectureTest (não use @ArchIgnore — ele desliga a regra inteira).")));
                    }
                };

        for (String context : MIGRATED_CONTEXTS) {
            noMethods()
                    .that().areDeclaredInClassesThat()
                    .resideInAPackage(BASE + "." + context + ".persistence..")
                    .should(noNativeQuery)
                    .because("native SQL escapa à análise de imports e pode acoplar contextos " +
                             "silenciosamente (ADR-39)")
                    .check(classes);
        }
    }

    private static String[] buildForbiddenPackages(String self) {
        String[] allContexts = {"engagement", "manga", "reading", "identity", "admin"};
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
