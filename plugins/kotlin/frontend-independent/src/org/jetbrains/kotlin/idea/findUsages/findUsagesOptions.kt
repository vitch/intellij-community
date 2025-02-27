/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.findUsages.JavaClassFindUsagesOptions
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions
import com.intellij.find.findUsages.JavaVariableFindUsagesOptions
import com.intellij.openapi.project.Project

interface KotlinMemberFindUsagesOptions {
    var searchExpected: Boolean
}

class KotlinClassFindUsagesOptions(project: Project) : KotlinMemberFindUsagesOptions, JavaClassFindUsagesOptions(project) {
    override var searchExpected: Boolean = true

    var searchConstructorUsages: Boolean = true

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && other is KotlinClassFindUsagesOptions && other.searchConstructorUsages == searchConstructorUsages
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + if (searchConstructorUsages) 1 else 0
    }
}

interface KotlinCallableFindUsagesOptions : KotlinMemberFindUsagesOptions {
    var searchOverrides: Boolean

    fun toJavaOptions(project: Project): FindUsagesOptions?
}

class KotlinFunctionFindUsagesOptions(project: Project) : KotlinCallableFindUsagesOptions, JavaMethodFindUsagesOptions(project) {
    override var searchExpected: Boolean = true

    override var searchOverrides: Boolean
        get() = isOverridingMethods
        set(value) {
            isOverridingMethods = value
        }

    override fun toJavaOptions(project: Project): FindUsagesOptions? {
        val javaOptions = JavaMethodFindUsagesOptions(project)
        javaOptions.fastTrack = fastTrack
        javaOptions.isCheckDeepInheritance = isCheckDeepInheritance
        javaOptions.isImplementingMethods = isImplementingMethods
        javaOptions.isIncludeInherited = isIncludeInherited
        javaOptions.isIncludeOverloadUsages = isIncludeOverloadUsages
        javaOptions.isOverridingMethods = isOverridingMethods
        javaOptions.isSearchForTextOccurrences = isSearchForTextOccurrences
        javaOptions.isSkipImportStatements = isSkipImportStatements
        javaOptions.isSearchForBaseMethod = isSearchForBaseMethod
        javaOptions.isUsages = isUsages
        javaOptions.searchScope = searchScope

        return javaOptions
    }
}

class KotlinPropertyFindUsagesOptions(
    project: Project
) : KotlinCallableFindUsagesOptions, JavaVariableFindUsagesOptions(project) {
    override var searchExpected: Boolean = true
    var isReadWriteAccess: Boolean = true
    override var searchOverrides: Boolean = false

    override fun toJavaOptions(project: Project): JavaVariableFindUsagesOptions {
        val javaOptions = JavaVariableFindUsagesOptions(project)
        javaOptions.fastTrack = fastTrack
        javaOptions.isSearchForTextOccurrences = isSearchForTextOccurrences
        javaOptions.isSkipImportStatements = isSkipImportStatements
        javaOptions.isReadAccess = isReadAccess
        javaOptions.isWriteAccess = isWriteAccess
        javaOptions.isUsages = isUsages
        javaOptions.searchScope = searchScope
        javaOptions.isSearchForAccessors = isSearchForAccessors
        javaOptions.isSearchInOverridingMethods = isSearchInOverridingMethods
        javaOptions.isSearchForBaseAccessors = isSearchForBaseAccessors
        return javaOptions
    }
}