/*
 * Copyright (C) 2023 Xizhi Zhu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id(Dependencies.Kotlin.Multiplatform.plugin)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "${Configurations.applicationId}.shared"
        compileSdk = Versions.Sdk.compile
        minSdk = Versions.Sdk.min

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.Kotlin.jvmTarget))
        }
    }

    // JVM-only target: runs commonTest without an Android SDK (`./gradlew :shared:jvmTest`, see AGENTS.md).
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.Kotlin.jvmTarget))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(Dependencies.Kotlin.coroutinesCore)
            implementation(Dependencies.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(Dependencies.Kotlin.coroutinesTest)
        }
    }
}

// UI smoke scenario driven over adb (`./gradlew :shared:runSmokeScenario`, see .github/scripts/smoke-test.sh).
tasks.register<JavaExec>("runSmokeScenario") {
    val main = kotlin.jvm().compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles ?: files())
    workingDir = rootDir // the dumped ui*.xml land where the smoke-test workflow uploads them
    mainClass.set("${Configurations.applicationId}.testing.SmokeScenarioKt")
}
