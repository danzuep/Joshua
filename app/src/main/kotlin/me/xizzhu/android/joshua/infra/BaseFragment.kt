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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import me.xizzhu.android.joshua.Navigator
import co.touchlab.kermit.Logger
import javax.inject.Inject
import kotlinx.coroutines.launch

abstract class BaseFragment<VB : ViewBinding, ViewAction, ViewState, VM : BaseViewModel<ViewAction, ViewState>> : Fragment() {
    protected val loggingTag: String = javaClass.simpleName

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
        Logger.withTag(loggingTag).i { "onCreate()" }
    }

    @CallSuper
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Logger.withTag(loggingTag).i { "onCreateView()" }
        return viewBinding.root
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.withTag(loggingTag).i { "onViewCreated()" }

        initializeView()

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewAction().collect(::onViewActionEmitted)
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState().collect(::onViewStateUpdated)
            }
        }
    }

    @CallSuper
    override fun onStart() {
        super.onStart()
        Logger.withTag(loggingTag).i { "onStart()" }
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        Logger.withTag(loggingTag).i { "onResume()" }
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        Logger.withTag(loggingTag).i { "onPause()" }
    }

    @CallSuper
    override fun onStop() {
        super.onStop()
        Logger.withTag(loggingTag).i { "onStop()" }
    }

    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Logger.withTag(loggingTag).i { "onSaveInstanceState()" }
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        Logger.withTag(loggingTag).i { "onDestroy()" }
    }
}
