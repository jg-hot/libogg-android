@file:Suppress("UnstableApiUsage")

require(gradle.gradleVersion == "8.9") {
    "Gradle version 8.9 required (current version: ${gradle.gradleVersion})"
}

plugins {
    alias(libs.plugins.library)
    id("maven-publish")
}

// project.name ("ogg") defined in settings.gradle.kts
project.group = "org.xiph"
project.version = "1.3.5-android-r1"

android {
    namespace = "${project.group}.${project.name}"
    compileSdk = libs.versions.compilesdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minsdk.get().toInt()

        buildToolsVersion = libs.versions.buildtools.get()
        ndkVersion = libs.versions.ndk.get()
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"

                arguments += "-DBUILD_SHARED_LIBS=OFF"
                arguments += "-DINSTALL_DOCS=OFF"
                arguments += "-DINSTALL_PKG_CONFIG_MODULE=OFF"
                arguments += "-DINSTALL_CMAKE_PACKAGE_MODULE=OFF"
                arguments += "-DBUILD_TESTING=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("${project.projectDir.parentFile}/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildFeatures {
        prefabPublishing = true
    }

    prefab {
        create(project.name) {
            headers = "${project.projectDir.parentFile}/include"
        }
    }

    packaging {
        // avoids duplicating libs in .aar due to using prefab
        jniLibs {
            excludes += "**/*"
        }
    }
}

tasks.register<Exec>(getTestTaskName()) {
    commandLine("./ndk-test.sh")
}

tasks.named<Delete>("clean") {
    delete.add(".cxx")
}

publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>(project.name) {
            artifact("${project.projectDir}/build/outputs/aar/${project.name}-release.aar")
            artifactId = "${project.name}-android"
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure {
        mustRunAfter("clean")
    }
    tasks.named(getTestTaskName()).configure {
        dependsOn("clean", "assembleRelease")
    }

    tasks.named("generatePomFileFor${project.name.cap()}Publication") {
        mustRunAfter("assembleRelease")
    }
    tasks.named("publishToMavenLocal").configure {
        dependsOn("clean", "assembleRelease")
    }

    // suggests running ":ndkTest" task instead of default testing tasks
    listOf(
        "check",
        "test",
        "testDebugUnitTest",
        "testReleaseUnitTest",
        "connectedCheck",
        "connectedAndroidTest",
        "connectedDebugAndroidTest",
    ).forEach {
        tasks.named(it) {
            doLast {
                println(":$it task not supported; use :${getTestTaskName()} to run tests via adb")
            }
        }
    }
}

fun getTestTaskName(): String = "ndkTest"

fun isTestBuild(): Boolean = gradle.startParameter.taskNames.contains(getTestTaskName())

// capitalize the first letter to make task names matched when written in camel case
fun String.cap(): String = this.replaceFirstChar { it.uppercase() }
