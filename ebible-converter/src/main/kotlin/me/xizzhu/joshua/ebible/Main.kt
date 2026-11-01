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

package me.xizzhu.joshua.ebible

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = "Usage: ebible-converter <usfm.zip> --short WEB --name \"World English Bible\" --language en_us [--out dist]"

fun main(args: Array<String>) {
    val options = args.drop(1).chunked(2).associate { it[0] to it.getOrNull(1) }
    val source = args.firstOrNull()?.takeUnless { it.startsWith("--") }
    val short = options["--short"]
    val name = options["--name"]
    val language = options["--language"]
    if (source == null || short == null || name == null || language == null) {
        System.err.println(USAGE)
        exitProcess(1)
    }
    val out = File(options["--out"] ?: "dist")
    convert(File(source), TranslationMeta(short, name, language), out)
    println("Wrote ${File(out, "translations/$short.zip")}")
}
