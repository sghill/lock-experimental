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

package com.netflix.nebula.lock.groovy

import com.netflix.nebula.lock.Locked
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

class GroovyLockExtensions {
    /**
     * Because somehow project is sticky inside the lock closure, even if we remove the metaClass on Dependency.
     * This is only really a problem during test execution, but the solution doesn't harm normal operation.
     */
    static Project cachedProject
    static List<Locked> cachedLocksInEffect

    static void enhanceDependencySyntax(Project project, List<Locked> locksInEffect) {
        cachedProject = project
        cachedLocksInEffect = locksInEffect

        Dependency.metaClass.lock = { lockedVersion ->
            if(delegate instanceof ExternalModuleDependency && !cachedProject.hasProperty('dependencyLock.ignore')) {
                ExternalModuleDependency dep = delegate

                def containingConf = cachedProject.configurations.find { it.dependencies.any { it.is(dep) } }
                containingConf.dependencies.remove(dep)

                def locked = new DefaultExternalModuleDependency(dep.group, dep.name, lockedVersion?.toString(), dep.configuration)
                locked.setChanging(dep.changing)
                locked.setForce(dep.force)

                containingConf.dependencies.add(locked)

                cachedLocksInEffect.add(new Locked(locked, dep))
                containingConf.resolutionStrategy.eachDependency { details ->
                    ModuleVersionSelector requested = details.requested
                    if (requested.group == dep.group && requested.name == dep.name) {
                        details.useTarget group: requested.group, name: requested.name, version: lockedVersion
                    }
                }

            }

            return this
        }
    }
}
