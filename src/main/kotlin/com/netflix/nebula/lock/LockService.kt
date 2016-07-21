/*
 * Copyright 2016-2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.nebula.lock

import com.netflix.nebula.lock.groovy.GroovyLockAstVisitor
import com.netflix.nebula.lock.groovy.GroovyLockWriter
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.gradle.api.Project

class LockService(val project: Project, val locksInEffect: List<Locked>) {
    val groovyLockWriter = GroovyLockWriter()

    fun undoLocks() {
        locksInEffect.forEach { lock ->
            val conf = project.configurations.find { it.dependencies.any { it == lock.locked } }
            conf?.resolutionStrategy?.eachDependency { details ->
                val requested = details.requested
                val original = lock.original
                if (requested.group == original.group && requested.name == original.name) {
                    details.useTarget("${original.group}:${original.name}:${original.version}")
                }
            }
        }
    }

    fun updateLocks(overrides: Map<ConfigurationModuleIdentifier, String> = emptyMap()) {
        undoLocks()

        project.configurations.all {
            it.resolutionStrategy.apply {
                cacheDynamicVersionsFor(0, "seconds")
                cacheChangingModulesFor(0, "seconds")
            }
        }

        arrayOf(project, project.rootProject).toSet().forEach { p ->
            when {
                p.buildFile.name.endsWith("gradle") -> updateLockGroovy(p, overrides)
                p.buildFile.name.endsWith("kts") -> updateLockKotlin(p, overrides)
                else -> { /* do nothing */ }
            }
        }
    }

    fun updateLockGroovy(p: Project, overrides: Map<ConfigurationModuleIdentifier, String>) {
        val ast = AstBuilder().buildFromString(p.buildFile.readText())
        val stmt = ast.find { it is BlockStatement }
        if(stmt is BlockStatement) {
            val visitor = GroovyLockAstVisitor(p, overrides)
            visitor.visitBlockStatement(stmt)
            groovyLockWriter.updateLocks(p, visitor.updates)
        }
    }

    fun updateLockKotlin(p: Project, overrides: Map<ConfigurationModuleIdentifier, String>) {
        // TODO implement me
    }
}