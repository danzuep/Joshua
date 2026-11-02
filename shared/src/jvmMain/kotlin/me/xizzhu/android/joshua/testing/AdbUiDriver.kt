/*
 * Copyright (C) 2026 Xizhi Zhu
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

package me.xizzhu.android.joshua.testing

import java.io.File
import java.util.concurrent.TimeUnit

/** Drives a device or emulator through `adb` and `uiautomator`. */
class AdbUiDriver(private val uiDumpFile: File = File("ui.xml")) : UiDriver {
    private fun adb(vararg args: String): String = try {
        val process = ProcessBuilder(listOf("adb") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.MINUTES)
        output
    } catch (e: Exception) {
        println("adb ${args.joinToString(" ")} failed: $e")
        ""
    }

    override fun launch(component: String, intExtras: Map<String, Int>) {
        val extras = intExtras.flatMap { (key, value) -> listOf("--ei", key, value.toString()) }
        adb("shell", "am", "start", "-W", "-n", component, *extras.toTypedArray())
    }

    override fun dump(): UiHierarchy {
        uiDumpFile.delete()
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        adb("pull", "/sdcard/ui.xml", uiDumpFile.path)
        return UiHierarchy(if (uiDumpFile.exists()) uiDumpFile.readText() else "")
    }

    override fun tap(x: Int, y: Int) {
        adb("shell", "input", "tap", x.toString(), y.toString())
    }

    override fun longPress(x: Int, y: Int) {
        adb("shell", "input", "swipe", x.toString(), y.toString(), x.toString(), y.toString(), "800")
    }

    // `input text` treats %s as a space.
    override fun typeText(text: String) {
        adb("shell", "input", "text", text.replace(" ", "%s"))
    }

    override fun press(key: Key) {
        adb("shell", "input", "keyevent", if (key == Key.ENTER) "KEYCODE_ENTER" else "KEYCODE_BACK")
    }

    override fun resumedActivity(): String = adb("shell", "dumpsys", "activity", "activities").lineSequence()
        .firstOrNull { "ResumedActivity" in it }.orEmpty()

    override fun pause(millis: Long) {
        Thread.sleep(millis)
    }
}
