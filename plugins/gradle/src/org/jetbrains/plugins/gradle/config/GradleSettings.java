/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds shared project-level gradle-related settings (should be kept at the '*.ipr' or under '.idea').
 * 
 * @author peter
 */
@State(
    name = "GradleSettings",
    storages = {
      @Storage(file = StoragePathMacros.PROJECT_FILE),
      @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gradle.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GradleSettings implements PersistentStateComponent<GradleSettings> {

  /** Holds information about 'expand/collapse' status of the 'sync project structure tree' nodes. */
  //private final AtomicReference<Set<GradleProjectStructureChange>>             myAcceptedChanges
  //  = new AtomicReference<Set<GradleProjectStructureChange>>();
  
  private String myLinkedProjectPath;
  private String myGradleHome;

  @Override
  public GradleSettings getState() {
    return this;
  }

  @Override
  public void loadState(GradleSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public static GradleSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setGradleHome(@Nullable String gradleHome) { // Necessary for the serialization.
    myGradleHome = gradleHome;
  }

  public static void applyGradleHome(@Nullable String newPath, @NotNull Project project) {
    final GradleSettings settings = getInstance(project);
    final String oldPath = settings.myGradleHome;
    settings.myGradleHome = newPath;
    project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC).onGradleHomeChange(oldPath, newPath);
  }

  @Nullable
  public String getLinkedProjectPath() {
    return myLinkedProjectPath;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setLinkedProjectPath(@Nullable String linkedProjectPath) { // Necessary for the serialization.
    myLinkedProjectPath = linkedProjectPath;
  }

  public static void applyLinkedProjectPath(@Nullable String path, @NotNull Project project) {
    final GradleSettings settings = getInstance(project);
    final String oldPath = settings.myLinkedProjectPath;
    settings.myLinkedProjectPath = path;
    project.getMessageBus().syncPublisher(GradleConfigNotifier.TOPIC).onLinkedProjectPathChange(oldPath, path);
  }

  @Override
  public String toString() {
    return "home: " + myGradleHome + ", path: " + myLinkedProjectPath;
  }
}