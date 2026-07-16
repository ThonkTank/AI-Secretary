package com.autosecretary.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.Test;

/**
 * Structural architecture rules, enforced on real bytecode.
 *
 * <p>Replaces the former hand-written source-parsing linter in {@code build.gradle.kts}.
 * Each test names the boundary it protects; a deliberate violation of that boundary must
 * make exactly that test fail. Runs as part of {@code testDebugUnitTest} (and thus
 * {@code checkArchitecture}, which now depends on it).
 *
 * <p>Rules intentionally NOT ported here — see the migration notes:
 * dead-code detection (IDE/lint), DB-version doc sync (code is the single source),
 * package/path alignment (compiler), and the two source-pattern conventions
 * (fragment {@code observe(getViewLifecycleOwner())} and {@code registerForActivityResult}
 * placement) which live as written conventions in CLAUDE.md.
 */
public class ArchitectureRulesTest {

    // DO_NOT_INCLUDE_TESTS misses AGP's "debugUnitTest" output path, so also drop any
    // location containing "UnitTest" — that removes the project's own test classes.
    private static final JavaClasses PROD = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("UnitTest"))
            .importPackages("com.autosecretary");

    // --- Domain purity -----------------------------------------------------

    @Test
    public void domain_does_not_depend_on_framework_app_or_database() {
        noClasses().that().resideInAPackage("com.autosecretary.features..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "android..",
                        "com.autosecretary.app..",
                        "com.autosecretary.database..")
                .because("domain must not reach the Android framework, app/, or database/ "
                        + "(androidx.room annotations on the task model are allowed)")
                .check(PROD);
    }

    @Test
    public void domain_does_not_depend_on_outer_layers() {
        noClasses().that().resideInAPackage("com.autosecretary.features..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.autosecretary.features..application..",
                        "com.autosecretary.features..ui..",
                        "com.autosecretary.features..data..")
                .because("domain must not depend on application, ui, or data (its own or foreign)")
                .check(PROD);
    }

    // --- Layer boundaries --------------------------------------------------

    @Test
    public void feature_ui_does_not_reach_data_or_database() {
        noClasses().that().resideInAPackage("com.autosecretary.features..ui..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.autosecretary.database..",
                        "com.autosecretary.features..data..")
                .because("UI must go through the application layer, never data/database directly")
                .check(PROD);
    }

    @Test
    public void ui_hosts_do_not_drive_application_use_cases_or_services() {
        DescribedPredicate<JavaClass> uiHosts =
                resideInAPackage("com.autosecretary.features..ui..")
                        .and(simpleNameEndingWith("Fragment")
                                .or(simpleNameEndingWith("Activity"))
                                .or(simpleNameEndingWith("Dialog")))
                        .as("UI hosts (Fragment/Activity/Dialog)");
        DescribedPredicate<JavaClass> applicationDrivers =
                resideInAPackage("com.autosecretary.features..application..")
                        .and(simpleNameEndingWith("UseCase")
                                .or(simpleNameEndingWith("Service")))
                        .as("application use-cases / services");
        noClasses().that(uiHosts)
                .should().dependOnClassesThat(applicationDrivers)
                .because("UI hosts drive application logic only through their ViewModel; "
                        + "binding application read-models is fine, data/database access is denied to all UI")
                .check(PROD);
    }

    @Test
    public void feature_data_does_not_depend_on_application_or_ui() {
        noClasses().that().resideInAPackage("com.autosecretary.features..data..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.autosecretary.features..application..",
                        "com.autosecretary.features..ui..")
                .because("data is a leaf: it may only see domain")
                .check(PROD);
    }

    // --- ViewModel discipline ---------------------------------------------

    @Test
    public void viewmodels_do_not_construct_views_or_retain_android_hosts() {
        noClasses().that().haveSimpleNameEndingWith("ViewModel")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "android.view..",
                        "android.widget..",
                        "android.app..",
                        "androidx.fragment..",
                        "androidx.recyclerview..",
                        "com.google.android.material..")
                .because("ViewModels must not build views, inflate layouts, or retain Android hosts "
                        + "(androidx.lifecycle is allowed)")
                .check(PROD);
    }

    @Test
    public void viewmodels_do_not_depend_on_infrastructure() {
        noClasses().that().haveSimpleNameEndingWith("ViewModel")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.autosecretary.database..",
                        "java.io..",
                        "java.nio..")
                .because("ViewModels must not own persistence or file I/O")
                .check(PROD);
    }

    @Test
    public void viewmodels_do_not_extend_android_viewmodel() {
        noClasses().that().haveSimpleNameEndingWith("ViewModel")
                .should().beAssignableTo("androidx.lifecycle.AndroidViewModel")
                .because("ViewModels must stay free of a retained Application/Context")
                .check(PROD);
    }

    // --- Feature isolation -------------------------------------------------

    @Test
    public void features_only_reach_app_through_the_application_class() {
        DescribedPredicate<JavaClass> appButNotApplicationClass =
                resideInAPackage("com.autosecretary.app..")
                        .and(not(name("com.autosecretary.app.AutoSecretaryApplication")))
                        .as("app/ (other than AutoSecretaryApplication)");
        noClasses().that().resideInAPackage("com.autosecretary.features..")
                .should().dependOnClassesThat(appButNotApplicationClass)
                .because("feature code wires only through AutoSecretaryApplication.from(context)")
                .check(PROD);
    }

    @Test
    public void shared_and_util_stay_free_of_project_internals() {
        noClasses().that().resideInAnyPackage(
                        "com.autosecretary.shared..",
                        "com.autosecretary.util..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.autosecretary.features..",
                        "com.autosecretary.app..",
                        "com.autosecretary.database..")
                .because("shared/ and util/ are leaves with no project-internal dependencies")
                .check(PROD);
    }

    @Test
    public void task_does_not_couple_to_other_features_non_domain() {
        noClasses().that().resideInAPackage("com.autosecretary.features.task..")
                .should().dependOnClassesThat().resideInAnyPackage(foreignNonDomain("budget", "meal"))
                .because("cross-feature coupling is allowed only through the foreign domain layer")
                .check(PROD);
    }

    @Test
    public void budget_does_not_couple_to_other_features_non_domain() {
        noClasses().that().resideInAPackage("com.autosecretary.features.budget..")
                .should().dependOnClassesThat().resideInAnyPackage(foreignNonDomain("task", "meal"))
                .because("cross-feature coupling is allowed only through the foreign domain layer")
                .check(PROD);
    }

    @Test
    public void meal_does_not_couple_to_other_features_non_domain() {
        noClasses().that().resideInAPackage("com.autosecretary.features.meal..")
                .should().dependOnClassesThat().resideInAnyPackage(foreignNonDomain("task", "budget"))
                .because("cross-feature coupling is allowed only through the foreign domain layer")
                .check(PROD);
    }

    /**
     * The multi-domain assistant reaches other features only through their {@code domain} layer, with
     * one explicit, named exception: the task feature's DAOs (task has no repository interface — its
     * Room POJOs are the domain model, so DAO-direct is the task convention) and the Apply/Undo
     * task-change use-cases (they own the undo snapshot the assistant must preserve). This allowlist
     * is deliberately narrow; {@link #assistant_task_seam_classes_exist} guards it against silent
     * widening if one of the named classes is renamed away.
     */
    @Test
    public void assistant_couples_to_features_only_via_domain_except_task_seam() {
        DescribedPredicate<JavaClass> foreignNonDomain =
                resideInAnyPackage(foreignNonDomain("task", "budget", "meal"));
        DescribedPredicate<HasName> seam = name(ASSISTANT_TASK_SEAM[0]);
        for (int i = 1; i < ASSISTANT_TASK_SEAM.length; i++) {
            seam = seam.or(name(ASSISTANT_TASK_SEAM[i]));
        }
        noClasses().that().resideInAPackage("com.autosecretary.features.assistant..")
                .should().dependOnClassesThat(foreignNonDomain.and(not(seam)))
                .because("the assistant reaches other features only through their domain layer, plus the "
                        + "named task seam (task DAOs + Apply/Undo task-change use-cases)")
                .check(PROD);
    }

    @Test
    public void no_feature_depends_on_the_assistant() {
        noClasses().that().resideInAnyPackage(
                        "com.autosecretary.features.task..",
                        "com.autosecretary.features.budget..",
                        "com.autosecretary.features.meal..")
                .should().dependOnClassesThat().resideInAPackage("com.autosecretary.features.assistant..")
                .because("the assistant is a leaf consumer of the other features; nothing depends back on it")
                .check(PROD);
    }

    @Test
    public void assistant_task_seam_classes_exist() {
        for (String fqn : ASSISTANT_TASK_SEAM) {
            boolean present = false;
            for (JavaClass javaClass : PROD) {
                if (javaClass.getName().equals(fqn)) {
                    present = true;
                    break;
                }
            }
            org.junit.Assert.assertTrue(
                    fqn + " is named in the assistant task-seam allowlist and must exist; a rename here "
                    + "would silently widen assistant_couples_to_features_only_via_domain_except_task_seam",
                    present);
        }
    }

    /** The concrete task classes the assistant is allowed to touch outside the domain layer. */
    private static final String[] ASSISTANT_TASK_SEAM = {
            "com.autosecretary.features.task.data.TaskDao",
            "com.autosecretary.features.task.data.TaskCategoryDao",
            "com.autosecretary.features.task.data.TaskCategoryWindowDao",
            "com.autosecretary.features.task.application.ApplyTaskChangesUseCase",
            "com.autosecretary.features.task.application.UndoTaskChangesUseCase",
    };

    // --- Ownership / naming -----------------------------------------------

    @Test
    public void only_the_composition_root_creates_executors() {
        noClasses().that().doNotHaveFullyQualifiedName("com.autosecretary.app.AppCompositionRoot")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.Executors")
                .because("AppCompositionRoot owns dbExecutor and ioExecutor; nobody else creates executors")
                .check(PROD);
    }

    @Test
    public void application_layer_has_no_presenters() {
        noClasses().that().resideInAPackage("com.autosecretary.features..application..")
                .should().haveSimpleNameContaining("Presenter")
                .because("the application layer uses UseCase/DataService names, not Presenter")
                .check(PROD);
    }

    private static String[] foreignNonDomain(String... features) {
        String[] packages = new String[features.length * 3];
        for (int i = 0; i < features.length; i++) {
            packages[i * 3] = "com.autosecretary.features." + features[i] + ".ui..";
            packages[i * 3 + 1] = "com.autosecretary.features." + features[i] + ".application..";
            packages[i * 3 + 2] = "com.autosecretary.features." + features[i] + ".data..";
        }
        return packages;
    }
}
