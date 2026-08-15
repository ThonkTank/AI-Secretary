package com.autosecretary.buildlogic

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale

/** Verifies the pinned remote model bytes without adding them to normal builds or the APK. */
@DisableCachingByDefault(because = "Remote reachability and bytes must be checked on every invocation")
abstract class ModelArtifactVerificationTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val source = JsonSlurper().parse(manifestFile.get().asFile) as? Map<*, *>
            ?: throw GradleException("Model manifest must be a JSON object")
        val schemaVersion = (source["schemaVersion"] as? Number)?.toInt()
            ?: throw GradleException("Model manifest schemaVersion is missing")
        if (schemaVersion != 1) {
            throw GradleException("Unsupported model manifest schemaVersion: $schemaVersion")
        }
        val revision = requiredString(source, "revision")
        val url = URI(requiredString(source, "url"))
        requireHttps(url)
        if (!url.path.contains("/resolve/$revision/")) {
            throw GradleException("Model URL does not contain the pinned revision")
        }
        val expectedSize = (source["sizeBytes"] as? Number)?.toLong()
            ?: throw GradleException("Model manifest sizeBytes is missing")
        if (expectedSize !in 1..MAXIMUM_MODEL_BYTES) {
            throw GradleException("Model manifest sizeBytes is outside the accepted range")
        }
        val expectedSha256 = requiredString(source, "sha256").lowercase(Locale.ROOT)
        if (!expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
            throw GradleException("Model manifest sha256 is invalid")
        }

        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        var current = url
        repeat(MAXIMUM_REDIRECTS + 1) { redirects ->
            val request = HttpRequest.newBuilder(current)
                .timeout(Duration.ofMinutes(20))
                .header("User-Agent", "AutoSecretary model verifier")
                .GET()
                .build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw GradleException("Model verification was interrupted", interrupted)
            } catch (error: Exception) {
                throw GradleException("Model download failed", error)
            }
            if (response.statusCode() in 300..399) {
                response.body().close()
                if (redirects == MAXIMUM_REDIRECTS) {
                    throw GradleException("Model download exceeded $MAXIMUM_REDIRECTS redirects")
                }
                val location = response.headers().firstValue("location").orElseThrow {
                    GradleException("Model redirect does not contain a location")
                }
                current = current.resolve(location)
                requireHttps(current)
                return@repeat
            }
            if (response.statusCode() != 200) {
                response.body().close()
                throw GradleException("Model download returned HTTP ${response.statusCode()}")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var actualSize = 0L
            response.body().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    actualSize += read
                    if (actualSize > expectedSize) {
                        throw GradleException("Model is larger than its manifest size")
                    }
                    digest.update(buffer, 0, read)
                }
            }
            if (actualSize != expectedSize) {
                throw GradleException(
                    "Model size mismatch: expected $expectedSize bytes, received $actualSize",
                )
            }
            val actualSha256 = digest.digest().joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            if (actualSha256 != expectedSha256) {
                throw GradleException(
                    "Model checksum mismatch: expected $expectedSha256, received $actualSha256",
                )
            }
            logger.lifecycle(
                "Verified pinned model revision {} ({} bytes, sha256 {}).",
                revision,
                actualSize,
                actualSha256,
            )
            return
        }
        throw GradleException("Model download did not produce a response")
    }

    private fun requiredString(source: Map<*, *>, name: String): String {
        return (source[name] as? String)?.takeIf(String::isNotBlank)
            ?: throw GradleException("Model manifest $name is missing")
    }

    private fun requireHttps(uri: URI) {
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            throw GradleException("Model URL and every redirect must use HTTPS")
        }
    }

    private companion object {
        const val MAXIMUM_REDIRECTS = 5
        const val MAXIMUM_MODEL_BYTES = 800L * 1024L * 1024L
    }
}
