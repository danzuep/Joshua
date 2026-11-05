/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
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

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(Dependencies.Sdk.classpath)
        classpath(Dependencies.Kotlin.classpath)
    }
}

plugins {
    id(Dependencies.Kotlin.Ksp.plugin) version Versions.Kotlin.ksp apply false
    id(Dependencies.Hilt.plugin) version Versions.hilt apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Downloads app/src/main/assets/translations.csv from https://ebible.org/Scriptures/translations.csv.
tasks.register("updateEbibleTranslations") {
    group = "translations"
    description = "Downloads the eBible.org translations.csv catalog"
    val output = file("app/src/main/assets/translations.csv")
    doLast {
        val csv = java.net.URL("https://eBible.org/Scriptures/translations.csv").readText()
        val header = csv.lineSequence().firstOrNull().orEmpty().removePrefix("\uFEFF").split(',').map { it.trim().trim('"') }
        check("translationId" in header && "Redistributable" in header) {
            "The downloaded CSV does not have the expected translationId and Redistributable columns"
        }
        val rowCount = csv.count { it == '\n' }
        check(rowCount >= 100) { "Only $rowCount CSV lines found, refusing to overwrite $output" }
        output.writeText(csv)
        println("Downloaded translations.csv ($rowCount rows)")
    }
}
