/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo

abstract class ForeignUsagesRenameProcessor {
    companion object {
        private val EP_NAME = ExtensionPointName<ForeignUsagesRenameProcessor>("org.jetbrains.kotlin.foreignUsagesRenameProcessor")

        @JvmStatic
        fun processAll(element: PsiElement, newName: String, usages: Array<UsageInfo>, fallbackHandler: (UsageInfo) -> Unit) {
            val usagesByLanguage = usages.groupBy { it.element?.language }
            for ((language, languageInfos) in usagesByLanguage) {
                if (language != null && EP_NAME.extensions.any { it.process(element, newName, language, languageInfos) }) {
                    continue
                }
                languageInfos.forEach(fallbackHandler)
            }
        }

        @JvmStatic
        fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
            EP_NAME.extensions.forEach { it.prepare(element, newName, allRenames, scope) }
        }
    }

    abstract fun process(element: PsiElement, newName: String, language: Language, allUsages: Collection<UsageInfo>): Boolean
    abstract fun prepare(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope)
}