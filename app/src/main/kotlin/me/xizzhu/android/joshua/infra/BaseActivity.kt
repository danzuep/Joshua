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

package me.xizzhu.android.joshua.infra

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.xizzhu.android.joshua.Navigator
import co.touchlab.kermit.Logger
import javax.inject.Inject

abstract class BaseActivity<VB : ViewBinding, ViewAction, ViewState, VM : BaseViewModel<ViewAction, ViewState>> : AppCompatActivity() {
    protected val tag: String = javaClass.simpleName

    @Inject
    internal lateinit var navigator: Navigator

    protected abstract val viewModel: VM

    protected abstract val viewBinding: VB

    protected abstract fun initializeView()

    protected abstract fun onViewActionEmitted(viewAction: ViewAction)

    protected abstract fun onViewStateUpdated(viewState: ViewState)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.withTag(tag).i { "onCreate()" }

        setContentView(viewBinding.root)
        initializeView()

        viewModel.viewAction().onEach(::onViewActionEmitted).launchIn(lifecycleScope)
        viewModel.viewState().onEach(::onViewStateUpdated).launchIn(lifecycleScope)
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        Logger.withTag(tag).i { "onStart()" }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        Logger.withTag(tag).i { "onResume()" }
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        Logger.withTag(tag).i { "onPause()" }
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        Logger.withTag(tag).i { "onStop()" }
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Logger.withTag(tag).i { "onSaveInstanceState()" }
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        Logger.withTag(tag).i { "onDestroy()" }
    }

    @CallSuper
    override fun onLowMemory() {
        super.onLowMemory()
        Logger.withTag(tag).i { "onLowMemory()" }
    }

    @CallSuper
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.withTag(tag).i { "onTrimMemory(): level - $level" }
    }
}
