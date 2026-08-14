package com.autosecretary.buildlogic

import com.android.apksig.ApkVerifier
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

@CacheableTask
abstract class PreparePhoneReleaseTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputApk: RegularFileProperty

    @TaskAction
    fun stage() {
        val candidates = apkDirectory.asFileTree.files
            .filter { it.isFile && it.extension == "apk" }
        if (candidates.size != 1) {
            throw GradleException(
                "Expected exactly one release APK, found ${candidates.map { it.name }}",
            )
        }
        val output = outputApk.get().asFile
        output.parentFile.mkdirs()
        candidates.single().copyTo(output, overwrite = true)
        if (output.length() <= 0L) {
            throw GradleException("The staged release APK is empty")
        }
    }
}

@CacheableTask
abstract class VerifyPhoneReleaseTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apk: RegularFileProperty

    @get:Input
    abstract val expectedSignerSha256: Property<String>

    @get:Input
    abstract val maximumBytes: Property<Long>

    @TaskAction
    fun verify() {
        val candidate = apk.get().asFile
        if (!candidate.isFile || candidate.length() <= 0L) {
            throw GradleException("The phone release APK is missing")
        }
        if (candidate.length() >= maximumBytes.get()) {
            throw GradleException(
                "Release APK is ${candidate.length()} bytes; expected less than ${maximumBytes.get()}",
            )
        }
        ZipFile(candidate).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toList()
            if (names.any { it.endsWith(".task") }) {
                throw GradleException("Release APK unexpectedly contains model weights")
            }
            if ("assets/model-manifest.json" !in names) {
                throw GradleException("Release APK does not contain assets/model-manifest.json")
            }
        }
        val result = ApkVerifier.Builder(candidate).build().verify()
        if (!result.isVerified) {
            val diagnostics = (result.errors + result.warnings).joinToString("; ")
            throw GradleException("APK signature verification failed: $diagnostics")
        }
        val expected = expectedSignerSha256.get().lowercase(Locale.ROOT)
        val signers = result.signerCertificates.map { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
        if (expected !in signers) {
            throw GradleException("Release APK signer $signers does not match $expected")
        }
    }
}
