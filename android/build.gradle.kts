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

val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    namespace = "${project.group}.${project.name}"
    compileSdk = libs.versions.compilesdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minsdk.get().toInt()

        buildToolsVersion = libs.versions.buildtools.get()
        ndkVersion = libs.versions.ndk.get()
        ndk {
            abiFilters += abis
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
}

tasks.register<Zip>("prefabAar") {
    archiveFileName = "${project.name}-release.aar"
    destinationDirectory = file("build/outputs/prefab-aar")

    from("aar-template")
    from("${projectDir.parentFile}/include") {
        include("**/*.h")
        into("prefab/modules/${project.name}/include")
    }
    abis.forEach { abi ->
        from("build/intermediates/cmake/release/obj/$abi") {
            include("*.so")
            exclude("libc++_shared.so")
            into("prefab/modules/${project.name}/libs/android.$abi")
        }
    }
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

    tasks.named("prefabAar") {
        dependsOn("externalNativeBuildRelease")
    }

    tasks.named("generatePomFileFor${project.name.cap()}Publication") {
        mustRunAfter("prefabAar")
    }

    tasks.named("publish") {
        dependsOn("clean", "prefabAar")
    }

    tasks.named(getTestTaskName()) {
        dependsOn("clean", "externalNativeBuildRelease")
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
            artifact("build/outputs/prefab-aar/${project.name}-release.aar")
            artifactId = "${project.name}-android"

            pom {
                distributionManagement {
                    downloadUrl = githubPackagesUrl
                }
            }
        }
    }
}

fun getTestTaskName(): String = "ndkTest"

// capitalize the first letter to make task names matched when written in camel case
fun String.cap(): String = this.replaceFirstChar { it.uppercase() }
