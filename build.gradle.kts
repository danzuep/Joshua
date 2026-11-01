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

// Regenerates app/src/main/assets/ebible_downloads.tsv from https://ebible.org/download.php
// (Territory, Language, Language (English), Vernacular Title, English Title, Home = translation id).
tasks.register("updateEbibleDownloads") {
    group = "translations"
    description = "Regenerates ebible_downloads.tsv from the eBible.org download page"
    val output = file("app/src/main/assets/ebible_downloads.tsv")
    doLast {
        fun text(html: String) = html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { String(Character.toChars(it.groupValues[1].toInt())) }
            .replace(Regex("\\s+"), " ").trim()

        val page = java.net.URL("https://ebible.org/download.php").readText()
        val idRegex = Regex("""href\s*=\s*["'][^"']*[?&]id=([^"'&#]+)""", RegexOption.IGNORE_CASE)
        val seen = hashSetOf<String>()
        val rows = page.split(Regex("<tr\\b", RegexOption.IGNORE_CASE)).drop(1)
            .filter { !Regex("<th\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .mapNotNull { tr ->
                val cells = tr.split(Regex("<td\\b[^>]*>", RegexOption.IGNORE_CASE)).drop(1)
                if (cells.size < 5) return@mapNotNull null
                val id = idRegex.find(cells[4])?.groupValues?.get(1) ?: return@mapNotNull null
                if (!seen.add(id)) return@mapNotNull null
                cells.take(5).map { text(it) } + id
            }
            .sortedWith(compareBy({ it[0] }, { it[1] }, { it[4] }))
        check(rows.size >= 100) { "Only ${rows.size} translations found, refusing to overwrite $output" }

        val header = listOf("Territory", "Language", "Language (English)", "Vernacular Title", "English Title", "Home")
        output.writeText((listOf(header) + rows).joinToString("\n", postfix = "\n") { it.joinToString("\t") })
        println("Wrote ${rows.size} translations")
    }
}
