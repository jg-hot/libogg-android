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
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"

                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DINSTALL_DOCS=OFF"
                arguments += "-DINSTALL_PKG_CONFIG_MODULE=OFF"
                arguments += "-DINSTALL_CMAKE_PACKAGE_MODULE=OFF"
                arguments += "-DBUILD_TESTING=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("${projectDir.parentFile}/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildFeatures {
        prefabPublishing = true
    }

    prefab {
        create(project.name) {
            headers = "$projectDir/build/prefab/include"
        }
    }

    packaging {
        // avoids duplicating libs in .aar due to using prefab
        jniLibs {
            excludes += "**/*"
        }
    }
}

publishing {
    val githubPackagesUrl = "https://maven.pkg.github.com/jg-hot/libogg-android"

    repositories {
        maven {
            url = uri(githubPackagesUrl)
            credentials {
                username = properties["gpr.user"]?.toString()
                password = properties["gpr.key"]?.toString()
            }
        }
    }

    publications {
        create<MavenPublication>(project.name) {
            artifact("$projectDir/build/outputs/aar/${project.name}-release.aar")
            artifactId = "${project.name}-android"

            pom {
                distributionManagement {
                    downloadUrl = githubPackagesUrl
                }
            }
        }
    }
}

tasks.register<Copy>("copyPrefabHeaders") {
    from("${project.projectDir.parentFile}/include")
    include("**/*.h")
    into("$projectDir/build/prefab/include")
}

tasks.register<Exec>(getTestTaskName()) {
    commandLine("./ndk-test.sh")
}

tasks.named<Delete>("clean") {
    delete.add(".cxx")
}

afterEvaluate {
    tasks.named("preBuild") {
        mustRunAfter("clean")
    }

    tasks.named("copyPrefabHeaders") {
        mustRunAfter("externalNativeBuildRelease")
    }
    tasks.named("prefabReleaseConfigurePackage") {
        dependsOn("copyPrefabHeaders")
    }

    tasks.named(getTestTaskName()) {
        dependsOn("clean", "assembleRelease")
    }

    tasks.named("generatePomFileFor${project.name.cap()}Publication") {
        mustRunAfter("assembleRelease")
    }
    tasks.named("publish") {
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

// capitalize the first letter to make task names matched when written in camel case
fun String.cap(): String = this.replaceFirstChar { it.uppercase() }
