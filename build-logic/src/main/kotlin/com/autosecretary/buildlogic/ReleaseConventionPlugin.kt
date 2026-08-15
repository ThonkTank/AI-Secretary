package com.autosecretary.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

class ReleaseConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project == project.rootProject) {
            "autosecretary.release must be applied to the root project"
        }
        val contract = Properties().apply {
            project.file("release/release.properties").inputStream().use(::load)
        }
        val expectedSigner = contract.getProperty("expectedSignerSha256")
            ?: error("release.properties does not define expectedSignerSha256")
        val staged = project.layout.buildDirectory.file("phone-release/AutoSecretary.apk")
        val prepare = project.tasks.register(
            "preparePhoneRelease",
            PreparePhoneReleaseTask::class.java,
        ) {
            group = "distribution"
            description = "Stages the release APK through the public Android artifact API."
            outputApk.set(staged)
        }
        project.tasks.register("verifyPhoneRelease", VerifyPhoneReleaseTask::class.java) {
            group = "verification"
            description = "Verifies the staged release size, model boundary and permanent signer."
            dependsOn(prepare)
            apk.set(staged)
            expectedSignerSha256.set(expectedSigner)
            maximumBytes.set(80L * 1024L * 1024L)
        }
        project.tasks.register("modelCompatibilityTest") {
            group = "verification"
            description = "Runs the real-model tests on one connected physical Android device."
            dependsOn(":app:connectedDebugAndroidTest")
        }
        project.tasks.register(
            "verifyModelArtifact",
            ModelArtifactVerificationTask::class.java,
        ) {
            group = "verification"
            description = "Downloads and verifies the model manifest's pinned remote artifact."
            manifestFile.set(
                project.layout.projectDirectory.file("app/src/main/assets/model-manifest.json"),
            )
        }

        val app = project.project(":app")
        app.pluginManager.withPlugin("com.android.application") {
            val components = app.extensions.getByType(
                ApplicationAndroidComponentsExtension::class.java,
            )
            components.onVariants(components.selector().withBuildType("release")) { variant ->
                prepare.configure {
                    apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
                }
            }
        }
    }
}
