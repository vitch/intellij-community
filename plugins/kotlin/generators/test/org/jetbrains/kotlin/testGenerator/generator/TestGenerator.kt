package org.jetbrains.kotlin.testGenerator.generator

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.test.*
import org.jetbrains.kotlin.testGenerator.model.*
import org.junit.runner.RunWith
import java.io.File
import java.util.*

object TestGenerator {
    private val commonImports = importsListOf(
        TestDataPath::class,
        JUnit3RunnerWithInners::class,
        KotlinTestUtils::class,
        TestMetadata::class,
        TestRoot::class,
        RunWith::class
    )

    fun write(workspace: TWorkspace) {
        for (group in workspace.groups) {
            for (suite in group.suites) {
                write(suite, group)
            }
        }
    }

    private fun write(suite: TSuite, group: TGroup) {
        val packageName = suite.generatedClassName.substringBeforeLast('.')
        val rootModelName = suite.generatedClassName.substringAfterLast('.')

        val content = buildCode {
            appendCopyrightComment()
            newLine()

            appendLine("package $packageName;")
            newLine()

            appendImports(getImports(suite))
            appendGeneratedComment()
            appendAnnotation(TAnnotation<SuppressWarnings>("all"))
            appendAnnotation(TAnnotation<TestRoot>(group.modulePath))
            appendAnnotation(TAnnotation<TestDataPath>("\$CONTENT_ROOT"))

            val singleModel = suite.models.singleOrNull()
            if (singleModel != null) {
                append(SuiteElement.create(group, suite, singleModel, rootModelName, isNested = false))
            } else {
                appendAnnotation(TAnnotation<RunWith>(JUnit3RunnerWithInners::class.java))
                appendBlock("public abstract class $rootModelName extends ${suite.abstractTestClass.simpleName}") {
                    val children = suite.models
                        .map { SuiteElement.create(group, suite, it, it.testClassName, isNested = true) }
                    appendList(children, separator = "\n\n")
                }
            }
            newLine()
        }

        val filePath = suite.generatedClassName.replace('.', '/') + ".java"
        val file = File(group.testSourcesRoot, filePath)
        write(file, postProcessContent(content))
    }

    private fun write(file: File, content: String) {
        val oldContent = file.takeIf { it.isFile }?.readText() ?: ""

        if (normalizeContent(content) != normalizeContent(oldContent)) {
            file.writeText(content)
            val path = file.toRelativeStringSystemIndependent(KotlinRoot.DIR)
            println("Updated $path")
        }
    }

    private fun normalizeContent(content: String): String = content.replace(Regex("\\R"), "\n")

    private fun getImports(suite: TSuite): List<String> {
        val imports = (commonImports + suite.imports).toMutableList()

        if (suite.models.any { it.targetBackend != TargetBackend.ANY }) {
            imports += importsListOf(TargetBackend::class)
        }

        val superPackageName = suite.abstractTestClass.`package`.name
        val selfPackageName = suite.generatedClassName.substringBeforeLast('.')
        if (superPackageName != selfPackageName) {
            imports += importsListOf(suite.abstractTestClass.kotlin)
        }

        return imports
    }

    private fun postProcessContent(text: String): String {
        return text.lineSequence()
            .map { it.trimEnd() }
            .joinToString(System.getProperty("line.separator"))
    }

    private fun Code.appendImports(imports: List<String>) {
        if (imports.isNotEmpty()) {
            imports.forEach { appendLine("import $it;") }
            newLine()
        }
    }

    private fun Code.appendCopyrightComment() {
        val year = GregorianCalendar()[Calendar.YEAR]
        appendMultilineComment("""
            Copyright 2010-$year JetBrains s.r.o. and Kotlin Programming Language contributors.
            Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
        """.trimIndent())
    }

    private fun Code.appendGeneratedComment() {
        appendMultilineComment("""
            This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}.
            DO NOT MODIFY MANUALLY.
        """.trimIndent())
    }
}