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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureHandler;
import org.jetbrains.plugins.groovy.refactoring.extract.method.GroovyExtractMethodHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;

/**
 * @author ilyas
 */
public class GroovyRefactoringSupportProvider extends RefactoringSupportProvider {

  public static final GroovyRefactoringSupportProvider INSTANCE = new GroovyRefactoringSupportProvider();

  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof GrTypeDefinition ||
           element instanceof GrField ||
           element instanceof GrMethod;
  }

  /**
   * @return handler for introducing local variables in Groovy
   */
  @Nullable
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new GrIntroduceVariableHandler();
  }

  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() {
    return new GroovyExtractMethodHandler();
  }

  @Override
  public ChangeSignatureHandler getChangeSignatureHandler() {
    return new GrChangeSignatureHandler();
  }

  @Override
  public boolean isInplaceRenameAvailable(PsiElement element, PsiElement context) {
    return false;
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new GrIntroduceFieldHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new GrIntroduceParameterHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new GrIntroduceConstantHandler();
  }
}
