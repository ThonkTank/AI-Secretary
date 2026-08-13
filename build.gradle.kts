import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
}

val versionProperties = Properties().apply {
    file("version.properties").inputStream().use(::load)
}
val releaseVersionCode = providers.gradleProperty("versionCode")
    .orElse(versionProperties.getProperty("VERSION_CODE")).get().toInt()
val releaseVersionName = providers.gradleProperty("versionName")
    .orElse(versionProperties.getProperty("VERSION_NAME")).get()
val productionKeystore = providers.gradleProperty("productionKeystore").orNull
    ?: System.getenv("PRODUCTION_KEYSTORE")
val productionStorePassword = providers.gradleProperty("productionStorePassword").orNull
    ?: System.getenv("PRODUCTION_STORE_PASSWORD")
val productionKeyAlias = providers.gradleProperty("productionKeyAlias").orNull
    ?: System.getenv("PRODUCTION_KEY_ALIAS")
val productionKeyPassword = providers.gradleProperty("productionKeyPassword").orNull
    ?: System.getenv("PRODUCTION_KEY_PASSWORD")
val productionSigningReady = listOf(
    productionKeystore, productionStorePassword, productionKeyAlias, productionKeyPassword
).all { !it.isNullOrBlank() }
val requireProductionSigning = providers.gradleProperty("requireProductionSigning")
    .map(String::toBoolean).orElse(false).get()
if (requireProductionSigning && !productionSigningReady) {
    throw GradleException("Production signing was required, but its four credentials are incomplete")
}
val bundledModelSha256 = "0f7147f1c22eaf758b819bbf7841793e4c90096c9352cde7fbe5c631f2265ef5"
val bundledModelUrl = providers.gradleProperty("bundledModelUrl").orElse(
    "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/"
        + "8018587998a60359c204699be38bc8940f5379a4/"
        + "gemma3-270m-it-q8.task?download=true"
)
val bundledAssetsDirectory = layout.buildDirectory.dir("bundled-ai/assets")
val bundledModelFile = bundledAssetsDirectory.map {
    it.file("models/autosecretary-gemma3-270m-it-q8.task")
}
val bundledModelCacheFile = file(
    ".gradle/bundled-ai/autosecretary-gemma3-270m-it-q8.task"
)

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareBundledModel = tasks.register("prepareBundledModel") {
    group = "build setup"
    description = "Downloads and verifies the local Gemma model embedded in the APK."
    inputs.property("modelUrl", bundledModelUrl)
    inputs.property("modelSha256", bundledModelSha256)
    outputs.file(bundledModelFile)
    doLast {
        val target = bundledModelFile.get().asFile
        target.parentFile.mkdirs()
        bundledModelCacheFile.parentFile.mkdirs()
        val targetValid = target.isFile && sha256(target) == bundledModelSha256
        val cacheValid = bundledModelCacheFile.isFile
                && sha256(bundledModelCacheFile) == bundledModelSha256
        if (targetValid && cacheValid) return@doLast
        val cacheTemporary = File(
            bundledModelCacheFile.parentFile, bundledModelCacheFile.name + ".partial"
        )
        val targetTemporary = File(target.parentFile, target.name + ".partial")
        cacheTemporary.delete()
        targetTemporary.delete()
        try {
            if (!cacheValid) {
                bundledModelCacheFile.delete()
                if (targetValid) {
                    Files.copy(target.toPath(), cacheTemporary.toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
                } else {
                    val connection = URI(bundledModelUrl.get()).toURL().openConnection().apply {
                        connectTimeout = 15_000
                        readTimeout = 120_000
                    }
                    connection.getInputStream().buffered().use { input ->
                        cacheTemporary.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val actual = sha256(cacheTemporary)
                check(actual == bundledModelSha256) {
                    "Bundled model checksum mismatch: expected $bundledModelSha256, got $actual"
                }
                Files.move(
                    cacheTemporary.toPath(),
                    bundledModelCacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            }
            if (targetValid) return@doLast
            Files.copy(
                bundledModelCacheFile.toPath(), targetTemporary.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            val copied = sha256(targetTemporary)
            check(copied == bundledModelSha256) {
                "Cached model checksum mismatch: expected $bundledModelSha256, got $copied"
            }
            Files.move(
                targetTemporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            cacheTemporary.delete()
            targetTemporary.delete()
        }
    }
}

android {
    namespace = "com.autosecretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autosecretary"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Auto Secretary"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    signingConfigs {
        if (productionSigningReady) {
            create("production") {
                storeFile = file(productionKeystore!!)
                storePassword = productionStorePassword
                keyAlias = productionKeyAlias
                keyPassword = productionKeyPassword
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            if (productionSigningReady) signingConfig = signingConfigs.getByName("production")
            // The reviewed device candidate and the later release build may differ only by the
            // gate-report commit. Embedding HEAD would make otherwise identical APKs hash differ.
            vcsInfo {
                include = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "task"
    }

    sourceSets.getByName("main").assets.srcDir(bundledAssetsDirectory)

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")
            || name.lowercase().contains("lint")) {
        dependsOn(prepareBundledModel)
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.work:work-runtime:2.10.1")
    implementation("com.google.android.material:material:1.12.0")

    // Local model execution. Model input and output never leave the device.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    annotationProcessor("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("com.tngtech.archunit:archunit-junit4:1.4.1")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

tasks.register("checkArchitecture") {
    group = "verification"
    description = "Runs behavior tests and the small set of safety-boundary rules."
    dependsOn(tasks.named("testDebugUnitTest"))
}

tasks.named("check").configure {
    dependsOn("checkArchitecture")
}

android.applicationVariants.all {
    val variant = this
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        output.outputFileName = if (variant.name == "release") {
            "AutoSecretary.apk"
        } else {
            "AutoSecretary-${variant.name}.apk"
        }
    }
}
