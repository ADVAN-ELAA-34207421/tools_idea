/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testIntegration.intention;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.impl.BaseMoveInitializerToMethodAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.testIntegration.TestIntegrationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author cdr
 */
public class MoveInitializerToSetUpMethodAction extends BaseMoveInitializerToMethodAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.set.up");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return super.isAvailable(project, editor, element) && TestIntegrationUtils.isTest(element);
  }

  @NotNull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Arrays.asList(PsiModifier.STATIC, PsiModifier.FINAL);
  }

  @NotNull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@NotNull Project project, @NotNull Editor editor, PsiFile file, @NotNull PsiClass aClass) {
    final PsiMethod setUpMethod = TestFrameworks.getInstance().findOrCreateSetUpMethod(aClass);
    return setUpMethod == null ? Collections.<PsiMethod>emptyList() : Arrays.asList(setUpMethod);
  }
}
