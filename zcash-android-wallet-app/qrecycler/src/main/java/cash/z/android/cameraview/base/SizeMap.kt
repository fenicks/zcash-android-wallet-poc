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


import androidx.collection.ArrayMap
import java.util.SortedSet
import java.util.TreeSet

/**
 * A collection class that automatically groups [Size]s by their [AspectRatio]s.
 */
internal class SizeMap {

    private val mRatios = ArrayMap<AspectRatio, SortedSet<Size>>()

    val isEmpty: Boolean
        get() = mRatios.isEmpty()

    /**
     * Add a new [Size] to this collection.
     *
     * @param size The size to add.
     * @return `true` if it is added, `false` if it already exists and is not added.
     */
    fun add(size: Size): Boolean {
        for (ratio in mRatios.keys) {
            if (ratio.matches(size)) {
                val sizes = mRatios[ratio]
                if (sizes?.contains(size) == true) {
                    return false
                } else {
                    if(sizes == null) mRatios[ratio] = TreeSet<Size>().let { it.add(size); it }
                    else sizes.add(size)
                    return true
                }
            }
        }
        // None of the existing ratio matches the provided size; add a new key
        val sizes = TreeSet<Size>()
        sizes.add(size)
        mRatios.put(AspectRatio.of(size.width, size.height), sizes)
        return true
    }

    /**
     * Removes the specified aspect ratio and all sizes associated with it.
     *
     * @param ratio The aspect ratio to be removed.
     */
    fun remove(ratio: AspectRatio) {
        mRatios.remove(ratio)
    }

    fun ratios(): Set<AspectRatio> {
        return mRatios.keys
    }

    fun sizes(ratio: AspectRatio): SortedSet<Size> {
        return mRatios[ratio]!!
    }

    fun clear() {
        mRatios.clear()
    }

}
