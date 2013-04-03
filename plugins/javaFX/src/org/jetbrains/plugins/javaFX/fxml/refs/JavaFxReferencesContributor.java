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
package org.jetbrains.plugins.javaFX.fxml.refs;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.plugins.javaFX.JavaFxFileReferenceProvider;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;

/**
 * User: anna
 * Date: 2/22/13
 */
public class JavaFxReferencesContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(literalExpression().and(new FilterPattern(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)context;
        PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(literalExpression, PsiMethodCallExpression.class);
        if (callExpression != null && "getResource".equals(callExpression.getMethodExpression().getReferenceName())) {
          final PsiCallExpression superCall = PsiTreeUtil.getParentOfType(callExpression, PsiCallExpression.class, true);
          if (superCall instanceof PsiMethodCallExpression) {
            final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)superCall).getMethodExpression();
            if ("load".equals(methodExpression.getReferenceName())) {
              final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
              PsiClass psiClass = null;
              if (qualifierExpression instanceof PsiReferenceExpression) {
                final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
                if (resolve instanceof PsiClass) {
                  psiClass = (PsiClass)resolve;
                }
              } else if (qualifierExpression != null) {
                psiClass = PsiUtil.resolveClassInType(qualifierExpression.getType());
              }
              if (psiClass != null && JavaFxCommonClassNames.JAVAFX_FXML_FXMLLOADER.equals(psiClass.getQualifiedName())) {
                return true;
              }
            }
          } else if (superCall instanceof PsiNewExpression) {
            final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)superCall).getClassOrAnonymousClassReference();
            if (reference != null) {
              final PsiElement resolve = reference.resolve();
              if (resolve instanceof PsiClass && JavaFxCommonClassNames.JAVAFX_FXML_FXMLLOADER.equals(((PsiClass)resolve).getQualifiedName())) {
                return true;
              }
            }
          }
        }
        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), new JavaFxFileReferenceProvider());
  }
}
