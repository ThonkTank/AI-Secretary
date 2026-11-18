import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

// Keep the generated daemon criteria path-independent. The Foojay resolver in settings supplies
// platform-specific download URLs, so a checkout without a system JDK 21 remains reproducible.
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion.set(JavaLanguageVersion.of(21))
}

subprojects {
    val modulePath = path
    if (modulePath == ":core-domain" || modulePath == ":today-core") {
        val expectedProjects = if (modulePath == ":core-domain") emptySet() else setOf(":core-domain")
        plugins.withId("java-library") {
            val productionClasspaths = listOf("compileClasspath", "runtimeClasspath").associateWith { name ->
                configurations.named(name).flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
                    artifacts.map { artifact ->
                        val component = artifact.id.componentIdentifier
                        (component as? ProjectComponentIdentifier)?.projectPath ?: component.displayName
                    }.toSet()
                }
            }
            val exportedProjects = configurations.named("apiElements").map { configuration ->
                configuration.allDependencies.map { dependency ->
                    (dependency as? ProjectDependency)?.path ?: dependency.toString()
                }.toSet()
            }
            val verifyProductionClasspath by tasks.registering {
                group = "verification"
                description = "Checks the Java module's production classpaths and API dependencies."
                productionClasspaths.forEach { (name, components) -> inputs.property(name, components) }
                inputs.property("apiElements", exportedProjects)
                doLast {
                    (productionClasspaths + ("apiElements" to exportedProjects)).forEach { (name, components) ->
                        val actual = components.get()
                        check(actual == expectedProjects) {
                            "$modulePath $name: expected $expectedProjects, found $actual"
                        }
                    }
                }
            }
            tasks.named<Test>("test") {
                dependsOn(verifyProductionClasspath)
            }
        }
    }
}
