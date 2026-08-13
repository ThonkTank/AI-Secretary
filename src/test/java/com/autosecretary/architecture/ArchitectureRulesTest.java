package com.autosecretary.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
        packages = "com.autosecretary",
        importOptions = ImportOption.DoNotIncludeTests.class)
public final class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule domain_is_platform_free = noClasses()
            .that().resideInAPackage("com.autosecretary.domain..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "android..", "androidx..", "com.autosecretary.application..",
                    "com.autosecretary.data..", "com.autosecretary.platform..",
                    "com.autosecretary.ui..", "com.autosecretary.app..",
                    "com.autosecretary.ai..", "com.autosecretary.background..",
                    "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule application_depends_only_inward = noClasses()
            .that().resideInAPackage("com.autosecretary.application..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "android..", "androidx..", "com.autosecretary.data..",
                    "com.autosecretary.platform..", "com.autosecretary.ui..",
                    "com.autosecretary.app..", "com.autosecretary.ai..",
                    "com.autosecretary.background..", "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule data_does_not_reach_user_or_platform_adapters = noClasses()
            .that().resideInAPackage("com.autosecretary.data..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.ui..", "com.autosecretary.app..",
                    "com.autosecretary.ai..", "com.autosecretary.platform..",
                    "com.autosecretary.background..", "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule platform_implements_ports_without_reaching_other_adapters = noClasses()
            .that().resideInAPackage("com.autosecretary.platform..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.data..", "com.autosecretary.ui..",
                    "com.autosecretary.app..", "com.autosecretary.ai..",
                    "com.autosecretary.background..", "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule ui_never_reaches_adapter_packages = noClasses()
            .that().resideInAPackage("com.autosecretary.ui..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.data..", "com.autosecretary.platform..",
                    "com.autosecretary.background..", "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule ui_uses_composition_facade_only_at_android_entrypoint = noClasses()
            .that().resideInAPackage("com.autosecretary.ui..")
            .and().doNotHaveFullyQualifiedName("com.autosecretary.ui.MainActivity")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage("com.autosecretary.app..");

    @ArchTest
    static final ArchRule ai_cannot_persist = noClasses()
            .that().resideInAPackage("com.autosecretary.ai..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.autosecretary.data..", "com.autosecretary.app..",
                    "com.autosecretary.ui..", "com.autosecretary.platform..",
                    "com.autosecretary.background..", "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule ai_cannot_bypass_ports_with_database_apis = noClasses()
            .that().resideInAPackage("com.autosecretary.ai..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "android.database..", "androidx.room..", "androidx.sqlite..");

    @ArchTest
    static final ArchRule view_models_do_not_know_android_or_concrete_infrastructure = noClasses()
            .that().haveSimpleNameEndingWith("ViewModel")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "android..", "com.autosecretary.data..",
                    "com.autosecretary.platform..", "com.autosecretary.app..",
                    "com.autosecretary.background..", "com.autosecretary.widget..");

    @ArchTest
    static final ArchRule view_models_do_not_own_android_message_queues = noClasses()
            .that().haveSimpleNameEndingWith("ViewModel")
            .should().dependOnClassesThat(new DescribedPredicate<>("are Handler or Looper") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaClass type) {
                    return type.getName().equals("android.os.Handler")
                            || type.getName().equals("android.os.Looper");
                }
            });

    @ArchTest
    static final ArchRule only_app_executors_creates_thread_pools = noClasses()
            .that().doNotHaveFullyQualifiedName("com.autosecretary.app.AppExecutors")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().callMethodWhere(new DescribedPredicate<>("calls an Executors factory") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall call) {
                    return call.getTargetOwner().getName().equals("java.util.concurrent.Executors")
                            && call.getName().startsWith("new");
                }
            });

    @ArchTest
    static final ArchRule only_app_executors_constructs_threads_or_executor_services = noClasses()
            .that().doNotHaveFullyQualifiedName("com.autosecretary.app.AppExecutors")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().callConstructorWhere(new DescribedPredicate<>(
                    "constructs a thread or executor service") {
                @Override
                public boolean test(JavaConstructorCall call) {
                    String name = call.getTargetOwner().getName();
                    return name.equals("java.lang.Thread")
                            || name.equals("java.util.concurrent.ThreadPoolExecutor")
                            || name.equals("java.util.concurrent.ScheduledThreadPoolExecutor")
                            || name.equals("java.util.concurrent.ForkJoinPool")
                            || name.equals("android.os.HandlerThread");
                }
            });

    @ArchTest
    static final ArchRule concrete_adapters_are_created_only_in_composition_root = noClasses()
            .that().resideOutsideOfPackage("com.autosecretary.app..")
            .and().haveSimpleNameNotEndingWith("Test")
            .should().callConstructorWhere(new DescribedPredicate<>("constructs a concrete adapter") {
                @Override
                public boolean test(JavaConstructorCall call) {
                    String name = call.getTargetOwner().getName();
                    return name.equals("com.autosecretary.data.RoomWorkItemRepository")
                            || name.equals("com.autosecretary.data.LegacyArchiveImportGateway")
                            || name.equals("com.autosecretary.platform.DeviceCalendarGateway")
                            || name.equals("com.autosecretary.platform.AndroidLocationGateway")
                            || name.equals("com.autosecretary.platform.PreferencesPlanningSettingsRepository")
                            || name.equals("com.autosecretary.platform.PreferencesAiConsentGateway")
                            || name.equals("com.autosecretary.platform.update.GitHubReleaseUpdateGateway")
                            || name.equals("com.autosecretary.platform.update.UpdateInstaller")
                            || name.equals("com.autosecretary.platform.SystemAppClock")
                            || name.equals("com.autosecretary.ai.OnDeviceBulkEditor");
                }
            });

    @ArchTest
    static final ArchRule composition_root_contains_no_rendering_code = noClasses()
            .that().resideInAPackage("com.autosecretary.app..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "android.view..", "android.widget..", "androidx.appcompat..",
                    "androidx.recyclerview..", "com.google.android.material..");
}
