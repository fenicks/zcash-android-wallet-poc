/*
 * Copyright (C) 2019 Electric Coin Company
 *
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file has been modified by Electric Coin Company to translate it into Kotlin and add support for Firebase vision.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cash.z.android.cameraview.base

import android.view.Surface
import android.view.SurfaceHolder
import android.view.View


/**
 * Encapsulates all the operations related to camera preview in a backward-compatible manner.
 */
internal abstract class PreviewImpl {

    private var mCallback: Callback? = null

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    internal abstract val surface: Surface

    internal abstract val view: View

    internal abstract val outputClass: Class<*>

    internal abstract val isReady: Boolean

    val surfaceHolder: SurfaceHolder?
        get() = null

    open val surfaceTexture: Any?
        get() = null

    internal interface Callback {
        fun onSurfaceChanged()
    }

    fun setCallback(callback: Callback) {
        mCallback = callback
    }

    internal abstract fun setDisplayOrientation(displayOrientation: Int)

    protected fun dispatchSurfaceChanged() {
        mCallback!!.onSurfaceChanged()
    }

    open fun setBufferSize(width: Int, height: Int) {}

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

}
