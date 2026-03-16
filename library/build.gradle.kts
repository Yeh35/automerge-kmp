import gobley.gradle.GobleyHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.gobley.cargo)
    alias(libs.plugins.gobley.uniffi)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.aspect"
version = "0.1.0"

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    jvm()
    linuxX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "automerge.kmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

cargo {
    packageDirectory = layout.projectDirectory.dir("rust")
}

// Disable JVM cross-compilation for non-host targets (Linux on macOS, etc.)
val hostSuffix = when {
    GobleyHost.current.toString().contains("MacOS") && GobleyHost.current.toString().contains("Arm64") -> "MacOSArm64"
    GobleyHost.current.toString().contains("MacOS") && GobleyHost.current.toString().contains("X64") -> "MacOSX64"
    GobleyHost.current.toString().contains("Linux") && GobleyHost.current.toString().contains("Arm64") -> "LinuxArm64"
    GobleyHost.current.toString().contains("Linux") && GobleyHost.current.toString().contains("X64") -> "LinuxX64"
    else -> ""
}

afterEvaluate {
    val jvmPlatforms = listOf("Linux", "MacOS", "Windows", "MinGW")
    tasks.configureEach {
        val taskName = name
        val isJvmCrossTask = jvmPlatforms.any { taskName.contains(it) }
        val isHostTask = hostSuffix.isNotEmpty() && taskName.contains(hostSuffix)
        if (isJvmCrossTask && !isHostTask) {
            enabled = false
        }
    }
}

uniffi {
    generateFromLibrary {
        namespace = "automerge_kmp"
        packageName = "automerge.kmp"
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "automerge-kmp", version.toString())

    pom {
        name = "automerge-kmp"
        description = "Kotlin Multiplatform wrapper for Automerge CRDT via Rust + UniFFI"
        inceptionYear = "2026"
        url = "https://github.com/aspect/automerge-kmp"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "aspect"
                name = "aspect"
            }
        }
        scm {
            url = "https://github.com/aspect/automerge-kmp"
        }
    }
}
