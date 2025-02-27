/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.test.util

import com.intellij.debugger.impl.OutputChecker
import com.intellij.idea.IdeaLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TargetBackend
import org.junit.Assert
import java.io.File
import kotlin.math.min

internal class KotlinOutputChecker(
    private val testDir: String,
    appPath: String,
    outputPath: String,
    private val useIrBackend: Boolean,
    private val expectedOutputFile: File,
) : OutputChecker({ appPath }, { outputPath }) {
    companion object {
        @JvmStatic
        private val LOG = Logger.getInstance(KotlinOutputChecker::class.java)
    }

    private lateinit var myTestName: String

    // True if the underlying test has already failed, but the failure was ignored.
    var threwException = false

    override fun init(testName: String) {
        super.init(testName)
        this.myTestName = Character.toLowerCase(testName[0]) + testName.substring(1)
    }

    // Copied from the base OutputChecker.checkValid(). Need to intercept call to base preprocessBuffer() method
    override fun checkValid(jdk: Sdk, sortClassPath: Boolean) {
        if (IdeaLogger.ourErrorsOccurred != null) {
            throw IdeaLogger.ourErrorsOccurred
        }

        val actual = buildOutputString().replace("FRAME:(.*):\\d+".toRegex(), "$1:!LINE_NUMBER!")

        val outDir = File(testDir)
        var outFile = expectedOutputFile
        val currentBackend = if (useIrBackend) TargetBackend.JVM_IR else TargetBackend.JVM
        val isIgnored = outFile.exists() && InTextDirectivesUtils.isIgnoredTarget(currentBackend, outFile)

        if (!outFile.exists()) {
            if (SystemInfo.isWindows) {
                val winOut = File(outDir, "$myTestName.win.out")
                if (winOut.exists()) {
                    outFile = winOut
                }
            } else if (SystemInfo.isUnix) {
                val unixOut = File(outDir, "$myTestName.unx.out")
                if (unixOut.exists()) {
                    outFile = unixOut
                }
            }
        }

        if (!outFile.exists()) {
            FileUtil.writeToFile(outFile, actual)
            LOG.error("Test file created ${outFile.path}\n**************** Don't forget to put it into VCS! *******************")
        } else {
            val originalText = FileUtilRt.loadFile(outFile, CharsetToolkit.UTF8)
            val expected = StringUtilRt.convertLineSeparators(originalText).split("\n").filter {
                !it.trim().startsWith(InTextDirectivesUtils.IGNORE_BACKEND_DIRECTIVE_PREFIX)
            }.joinToString("\n")
            if (expected != actual) {
                println("expected:")
                println(originalText)
                println("actual:")
                println(actual)

                val len = min(expected.length, actual.length)
                if (expected.length != actual.length) {
                    println("Text sizes differ: expected " + expected.length + " but actual: " + actual.length)
                }
                if (expected.length > len) {
                    println("Rest from expected text is: \"" + expected.substring(len) + "\"")
                } else if (actual.length > len) {
                    println("Rest from actual text is: \"" + actual.substring(len) + "\"")
                }

                // Ignore test failure if marked as ignored.
                if (isIgnored) return

                Assert.assertEquals(expected, actual)
            } else if (isIgnored && !threwException) {
                // Fail if tests are marked as failing, but actually pass.
                throw AssertionError("Test passes and could be unmuted, remove IGNORE_BACKEND directive from ${outFile.path}")
            }
        }
    }

    private fun buildOutputString(): String {
        // Call base method with reflection
        val m = OutputChecker::class.java.getDeclaredMethod("buildOutputString")!!
        val isAccessible = m.isAccessible

        try {
            m.isAccessible = true
            return m.invoke(this) as String
        } finally {
            m.isAccessible = isAccessible
        }
    }
}
