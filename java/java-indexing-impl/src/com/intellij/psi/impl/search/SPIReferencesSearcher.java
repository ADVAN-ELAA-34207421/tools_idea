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
package com.intellij.psi.impl.search;

import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class SPIReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
    final PsiElement element = p.getElementToSearch();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final SearchScope scope = p.getEffectiveSearchScope();
    if (!(scope instanceof GlobalSearchScope)) return;
    final PsiClass aClass = (PsiClass)element;
    final String jvmClassName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return ClassUtil.getJVMClassName(aClass);
      }
    });
    if (jvmClassName == null) return;
    final PsiFile[] files = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
      @Override
      public PsiFile[] compute() {
        return FilenameIndex.getFilesByName(aClass.getProject(), jvmClassName, (GlobalSearchScope)scope);
      }
    });
    for (PsiFile file : files) {
      if (file.getLanguage() == SPILanguage.INSTANCE) {
        final PsiReference reference = file.getReference();
        if (reference != null) {
          consumer.process(reference);
        }
      }
    }
  }
}
