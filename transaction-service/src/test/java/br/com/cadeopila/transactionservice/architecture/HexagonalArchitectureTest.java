package br.com.cadeopila.transactionservice.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Codifica o hexagonal pragmático do projeto (seção 3.2):
 * core só JDK; application só core + {@code org.springframework.stereotype}/{@code .transaction};
 * adapters concentram o framework. Proíbe core → application/adapters e application → adapters.
 */
@AnalyzeClasses(
        packages = "br.com.cadeopila.transactionservice",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    /** Frameworks proibidos no core (inclui todo o Spring). */
    private static final DescribedPredicate<JavaClass> FRAMEWORKS_FORBIDDEN_IN_CORE = resideInAnyPackage(
            "org.springframework..", "jakarta..", "org.hibernate..",
            "com.fasterxml.jackson..", "tools.jackson..", "io.swagger..", "lombok..");

    /** Frameworks (exceto Spring) proibidos na application. */
    private static final DescribedPredicate<JavaClass> NON_SPRING_FRAMEWORKS_FORBIDDEN_IN_APPLICATION =
            resideInAnyPackage("jakarta..", "org.hibernate..",
                    "com.fasterxml.jackson..", "tools.jackson..", "io.swagger..", "lombok..");

    /** Pacotes do Spring que a application NÃO pode usar (tudo além de stereotype e transaction). */
    private static final DescribedPredicate<JavaClass> SPRING_FORBIDDEN_IN_APPLICATION =
            resideInAPackage("org.springframework..")
                    .and(not(resideInAnyPackage(
                            "org.springframework.stereotype..",
                            "org.springframework.transaction..")));

    @ArchTest
    static final ArchRule core_depende_apenas_do_jdk = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat(FRAMEWORKS_FORBIDDEN_IN_CORE)
            .because("o core não pode depender de Spring, JPA, Jackson, HTTP, Swagger ou Lombok");

    @ArchTest
    static final ArchRule core_nao_depende_de_application_nem_adapters = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapters..")
            .because("o core não conhece casos de uso nem adapters");

    @ArchTest
    static final ArchRule application_nao_depende_de_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapters..")
            .because("a camada application depende apenas do core");

    @ArchTest
    static final ArchRule application_so_usa_spring_permitido = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat(
                    NON_SPRING_FRAMEWORKS_FORBIDDEN_IN_APPLICATION.or(SPRING_FORBIDDEN_IN_APPLICATION))
            .because("a application só pode usar org.springframework.stereotype e org.springframework.transaction");
}
