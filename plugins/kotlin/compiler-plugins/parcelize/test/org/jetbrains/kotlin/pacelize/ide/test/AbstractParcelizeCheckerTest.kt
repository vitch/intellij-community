/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.pacelize.ide.test

import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest

abstract class AbstractParcelizeCheckerTest : AbstractKotlinHighlightVisitorTest() {
    override fun setUp() {
        super.setUp()
        addParcelizeLibraries(module)
    }

    override fun tearDown() {
        removeParcelizeLibraries(module)
        super.tearDown()
    }
}