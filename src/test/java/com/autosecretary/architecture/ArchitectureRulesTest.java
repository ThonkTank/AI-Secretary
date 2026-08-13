package com.autosecretary.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.runner.RunWith;

/** Only safety boundaries that protect user data and deterministic domain behavior. */
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
        packages = "com.autosecretary",
        importOptions = ImportOption.DoNotIncludeTests.class)
public final class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule domain_is_platform_free = noClasses()
            .that().resideInAPackage("com.autosecretary.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("android..", "androidx..");

    @ArchTest
    static final ArchRule ai_preview_cannot_access_persistence = noClasses()
            .that().resideInAPackage("com.autosecretary.ai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.data..", "android.database..",
                    "androidx.room..", "androidx.sqlite..");
}
