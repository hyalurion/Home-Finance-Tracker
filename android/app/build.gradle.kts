import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
}

val buildDate: String? = SimpleDateFormat("yyyyMMdd").format(Date())
val buildTime: String? = SimpleDateFormat("HHmm").format(Date())

fun getLocalProperty(key: String): String? {
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(localPropertiesFile.inputStream())
    }
    return properties.getProperty(key)
}

android {
    namespace = "com.chronie.homemoney"
    compileSdk = libs.versions.compileSdk.get().toInt()
    // NDK r30+ is required for platform 36+ support (r28 maxes out at API 35)
    ndkVersion = "30.0.15729638"
    // Use the locally installed build-tools (AGP 9.4 defaults to 36.0.0,
    // which is not installed on this machine)
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.chronie.homemoney"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        ndk {
            abiFilters += "arm64-v8a"
        }

        val defaultBuildNumber = buildTime
        val buildNumber = if (project.hasProperty("buildNumber")) project.findProperty("buildNumber") as String else defaultBuildNumber

        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "1.${buildDate}.${buildNumber}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++23 -fexceptions"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(getLocalProperty("RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = getLocalProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = getLocalProperty("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = getLocalProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Sync classes log through android.util.Log. Returning defaults keeps the
            // merge/dedup logic testable on the JVM without pulling in Robolectric.
            isReturnDefaultValues = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    // Package the MNN prebuilt .so files (vision-enabled) alongside the
    // CMake-built sync_engine / mnn_bridge libraries. The directory is
    // populated by scripts/build-mnn-android.ps1; when absent the bridge
    // builds as a stub and the app still installs.
    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/cpp/mnn/prebuilt")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.grpc.protoc.gen.get().toString()
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
            task.plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.material)
    implementation(libs.m3color)
    implementation(libs.material.icons.extended)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.security.crypto)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)

    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    implementation(libs.fastexcel)
    implementation(libs.fastexcel.reader)
    implementation(libs.aalto.xml)
    implementation(libs.xz)

    implementation(libs.opencv)

    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.javalite)
    implementation(libs.javax.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.charty)
}

try {
    val servicesJSON = file("google-services.json")
    if (servicesJSON.exists()) {
        apply(plugin = "com.google.gms.google-services")
    }
} catch (_: Exception) {
    logger.info("google-services.json not found, google-services plugin not applied. Push Notifications won't work")
}

tasks.register("generateLicenseJson") {
    group = "license"
    description = "Generate license.json from all runtime dependencies"
    
    doLast {
        try {
            val outputFile = file("$projectDir/src/main/assets/licenses.json")
            outputFile.parentFile.mkdirs()
            
            val dependencies = mutableListOf<Map<String, String>>()
            val seen = mutableSetOf<String>()
            
            val runtimeConfigs = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
            
            runtimeConfigs.forEach { configName ->
                try {
                    val config = configurations.findByName(configName)
                    if (config != null && config.isCanBeResolved) {
                        config.resolvedConfiguration.firstLevelModuleDependencies.forEach { dep ->
                            val key = "${dep.moduleGroup}:${dep.moduleName}:${dep.moduleVersion}"
                            if (key !in seen && !dep.moduleGroup.startsWith("com.chronie")) {
                                seen.add(key)
                                dependencies.add(mapOf(
                                    "group" to dep.moduleGroup,
                                    "name" to dep.moduleName,
                                    "version" to dep.moduleVersion,
                                    "fullName" to "${dep.moduleGroup}:${dep.moduleName}"
                                ))
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            
            val sorted = dependencies.sortedBy { it["fullName"] }
            val json = buildString {
                appendLine("[")
                sorted.forEachIndexed { index, dep ->
                    appendLine("  {")
                    appendLine("    \"group\": \"${dep["group"]}\",")
                    appendLine("    \"name\": \"${dep["name"]}\",")
                    appendLine("    \"version\": \"${dep["version"]}\",")
                    appendLine("    \"fullName\": \"${dep["fullName"]}\"")
                    appendLine("  }${if (index < sorted.size - 1) "," else ""}")
                }
                appendLine("]")
            }
            
            outputFile.writeText(json)
            println("Generated licenses.json with ${dependencies.size} dependencies")
        } catch (e: Exception) {
            logger.warn("Failed to generate licenses.json: ${e.message}")
        }
    }
}

afterEvaluate {
    tasks.named("preBuild") {
        dependsOn("generateLicenseJson")
    }
}
