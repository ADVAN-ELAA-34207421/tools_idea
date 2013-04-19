/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.model.project.change.user;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Stands for a project change explicitly made by a user (e.g. a 'new module is added' or 'particular library dependency
 * is removed from particular module') etc.
 * <p/>
 * This interface also requires the implementations to be IS-A {@link Comparable}. That is a limitation of IJ serialization 
 * sub-system (see CollectionBinding.getIterable() which wraps any set into a TreeSet).
 * 
 * @author Denis Zhdanov
 * @since 2/18/13 8:03 PM
 */
public interface UserProjectChange<T extends UserProjectChange> extends PersistentStateComponent<T>, Comparable<UserProjectChange<?>> {

  void invite(@NotNull UserProjectChangeVisitor visitor);
}
