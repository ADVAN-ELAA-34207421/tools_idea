/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Dec 7, 2004
 */
public interface InspectionProfile extends Profile {

  HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element);

  /**
   * If you need to modify tool's settings, please use {@link #modifyProfile(com.intellij.util.Consumer)}
   */
  InspectionProfileEntry getInspectionTool(@NotNull String shortName, @NotNull PsiElement element);

  @Nullable
  InspectionProfileEntry getInspectionTool(@NotNull String shortName);

  /** Returns (unwrapped) inspection */
  InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element);

  void modifyProfile(Consumer<ModifiableModel> modelConsumer);

  /**
   * @param element context element
   * @return all (both enabled and disabled) tools
   */
  @NotNull
  InspectionProfileEntry[] getInspectionTools(@Nullable PsiElement element);

  void cleanup(Project project);

  /**
   * @see #modifyProfile(com.intellij.util.Consumer)
   */
  @NotNull
  ModifiableModel getModifiableModel();

  boolean isToolEnabled(HighlightDisplayKey key, PsiElement element);

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isExecutable();

  boolean isEditable();  

  @NotNull
  String getDisplayName();

  void scopesChanged();
}
