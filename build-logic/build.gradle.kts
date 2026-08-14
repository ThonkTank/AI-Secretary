plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("com.android.tools.build:apksig:8.7.3")
}

gradlePlugin {
    plugins {
        register("releaseConvention") {
            id = "autosecretary.release"
            implementationClass = "com.autosecretary.buildlogic.ReleaseConventionPlugin"
        }
    }
}
