package com.autosecretary.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.runner.RunWith;

/** Executable dependency direction for the four production modules. */
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
        packages = "com.autosecretary",
        importOptions = {ImportOption.DoNotIncludeTests.class,
                ArchitectureRulesTest.ExcludeUnitTestOutput.class})
public final class ArchitectureRulesTest {
    public static final class ExcludeUnitTestOutput implements ImportOption {
        @Override public boolean includes(Location location) {
            String value = location.asURI().toString();
            return !value.contains("UnitTest") && !value.contains("test-classes")
                    && !value.contains("/test/");
        }
    }
    @ArchTest
    static final ArchRule core_is_platform_and_adapter_free = noClasses()
            .that().resideInAnyPackage(
                    "com.autosecretary.domain..", "com.autosecretary.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "android..", "androidx..",
                    "com.autosecretary.data..", "com.autosecretary.platform..",
                    "com.autosecretary.ui..", "com.autosecretary.widget..",
                    "com.autosecretary.background..", "com.autosecretary.app..",
                    "com.autosecretary.ai..");

    @ArchTest
    static final ArchRule infrastructure_does_not_reach_into_delivery = noClasses()
            .that().resideInAnyPackage(
                    "com.autosecretary.data..", "com.autosecretary.platform..",
                    "com.autosecretary.ai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.ui..", "com.autosecretary.widget..",
                    "com.autosecretary.background..", "com.autosecretary.app..");

    @ArchTest
    static final ArchRule presentation_uses_ports_instead_of_adapters = noClasses()
            .that().resideInAPackage("com.autosecretary.ui..")
            .and().doNotHaveFullyQualifiedName("com.autosecretary.ui.MainActivity")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.data..", "com.autosecretary.platform..",
                    "com.autosecretary.ai..", "com.autosecretary.widget..",
                    "com.autosecretary.background..", "com.autosecretary.app..");

    @ArchTest
    static final ArchRule ai_preview_cannot_access_persistence = noClasses()
            .that().resideInAPackage("com.autosecretary.ai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.data..", "android.database..",
                    "androidx.room..", "androidx.sqlite..");

    @ArchTest
    static final ArchRule production_packages_are_cycle_free = slices()
            .matching("com.autosecretary.(*)..")
            .should().beFreeOfCycles()
            // ViewBinding is generated in the resource namespace and references custom UI views.
            .ignoreDependency(
                    JavaClass.Predicates.resideInAPackage("com.autosecretary.presentation.."),
                    JavaClass.Predicates.resideInAPackage("com.autosecretary.ui.."))
            .ignoreDependency(
                    JavaClass.Predicates.resideInAPackage("com.autosecretary.ui.."),
                    JavaClass.Predicates.resideInAPackage("com.autosecretary.presentation.."));

    @ArchTest
    static final ArchRule widgets_use_injected_ports_not_the_composition_root = noClasses()
            .that().resideInAPackage("com.autosecretary.widget..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.app..", "com.autosecretary.data..",
                    "com.autosecretary.platform..");
}
