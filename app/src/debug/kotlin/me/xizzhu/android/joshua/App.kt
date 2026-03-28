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

package me.xizzhu.android.joshua

import android.app.Application
import android.os.StrictMode
import androidx.annotation.CallSuper
import co.touchlab.kermit.Logger

abstract class BaseApp : Application() {
    @CallSuper
    override fun onCreate() {
        super.onCreate()
        StrictMode.enableDefaults()
        setupGlobalLogger()
    }
}

fun setupGlobalLogger() {
    // Configure the global logger instance
    Logger.setLogWriters(co.touchlab.kermit.platformLogWriter())
    
    // Set an optional default tag for the global logger
    Logger.withTag("Joshua")
}