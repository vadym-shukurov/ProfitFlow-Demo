package com.profitflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces core hexagonal boundaries (packages are the module edges until a multi-module
 * split is introduced).
 */
@AnalyzeClasses(
        packages = "com.profitflow",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworks =
            noClasses()
                    .that().resideInAPackage("com.profitflow.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "org.hibernate..",
                            "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnDrivingOrDrivenAdapters =
            noClasses()
                    .that().resideInAPackage("com.profitflow.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.profitflow.adapter..",
                            "com.profitflow.infrastructure..",
                            "com.profitflow.security..");
}
