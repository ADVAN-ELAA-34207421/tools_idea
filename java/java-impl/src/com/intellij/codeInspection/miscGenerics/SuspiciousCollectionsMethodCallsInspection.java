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
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class SuspiciousCollectionsMethodCallsInspection extends BaseLocalInspectionTool {
  public boolean REPORT_CONVERTIBLE_METHOD_CALLS = true;

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("report.suspicious.but.possibly.correct.method.calls"), this, "REPORT_CONVERTIBLE_METHOD_CALLS");
  }

  private static void setupPatternMethods(PsiManager manager,
                                          GlobalSearchScope searchScope,
                                          List<PsiMethod> patternMethods,
                                          IntArrayList indices) {
    final PsiClass collectionClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, searchScope);
    PsiType[] javaLangObject = {PsiType.getJavaLangObject(manager, searchScope)};
    MethodSignature removeSignature = MethodSignatureUtil.createMethodSignature("remove", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    if (collectionClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(collectionClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, indices);
      MethodSignature containsSignature = MethodSignatureUtil.createMethodSignature("contains", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod contains = MethodSignatureUtil.findMethodBySignature(collectionClass, containsSignature, false);
      addMethod(contains, 0, patternMethods, indices);
    }

    final PsiClass listClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_UTIL_LIST, searchScope);
    if (listClass != null) {
      MethodSignature indexofSignature = MethodSignatureUtil.createMethodSignature("indexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod indexof = MethodSignatureUtil.findMethodBySignature(listClass, indexofSignature, false);
      addMethod(indexof, 0, patternMethods, indices);
      MethodSignature lastindexofSignature = MethodSignatureUtil.createMethodSignature("lastIndexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod lastindexof = MethodSignatureUtil.findMethodBySignature(listClass, lastindexofSignature, false);
      addMethod(lastindexof, 0, patternMethods, indices);
    }

    final PsiClass mapClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_UTIL_MAP, searchScope);
    if (mapClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(mapClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, indices);
      MethodSignature getSignature = MethodSignatureUtil.createMethodSignature("get", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod get = MethodSignatureUtil.findMethodBySignature(mapClass, getSignature, false);
      addMethod(get, 0, patternMethods, indices);
      MethodSignature containsKeySignature = MethodSignatureUtil.createMethodSignature("containsKey", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsKey = MethodSignatureUtil.findMethodBySignature(mapClass, containsKeySignature, false);
      addMethod(containsKey, 0, patternMethods, indices);
      MethodSignature containsValueSignature = MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(mapClass, containsValueSignature, false);
      addMethod(containsValue, 1, patternMethods, indices);
    }
  }

  private static void addMethod(final PsiMethod patternMethod, int typeParamIndex, List<PsiMethod> patternMethods, IntArrayList indices) {
    if (patternMethod != null) {
      patternMethods.add(patternMethod);
      indices.add(typeParamIndex);
    }
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final List<PsiMethod> patternMethods = new ArrayList<PsiMethod>();
    final IntArrayList indices = new IntArrayList();
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);
        final String message = getSuspiciousMethodCallMessage(methodCall, REPORT_CONVERTIBLE_METHOD_CALLS, patternMethods, indices
        );
        if (message != null) {
          holder.registerProblem(methodCall.getArgumentList().getExpressions()[0], message);
        }
      }
    };
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.suspicious.collections.method.calls.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SuspiciousMethodCalls";
  }

  @Nullable
  private static String getSuspiciousMethodCallMessage(final PsiMethodCallExpression methodCall,
                                                       final boolean reportConvertibleMethodCalls, final List<PsiMethod> patternMethods,
                                                       final IntArrayList indices) {
    final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    if (args.length != 1) return null;

    PsiType argType = args[0].getType();
    final String plainMessage = getSuspiciousMethodCallMessage(methodCall, argType, reportConvertibleMethodCalls, patternMethods, indices);
    if (plainMessage != null) {
      final PsiType dfaType = GuessManager.getInstance(methodCall.getProject()).getControlFlowExpressionType(args[0]);
      if (dfaType != null && getSuspiciousMethodCallMessage(methodCall, dfaType, reportConvertibleMethodCalls, patternMethods, indices) == null) {
        return null;
      }
    }

    return plainMessage;
  }

  @Nullable
  public static String getSuspiciousMethodCallMessage(PsiMethodCallExpression methodCall,
                                                      PsiType argType, boolean reportConvertibleMethodCalls, List<PsiMethod> patternMethods,
                                                      IntArrayList indices) {
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) return null;
    if (argType instanceof PsiPrimitiveType) {
      argType = ((PsiPrimitiveType)argType).getBoxedType(methodCall);
    }

    if (!(argType instanceof PsiClassType)) return null;

    final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    PsiMethod calleeMethod = (PsiMethod)resolveResult.getElement();
    if (calleeMethod == null) return null;
    PsiMethod contextMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (patternMethods) {
      if (patternMethods.isEmpty()) {
        setupPatternMethods(methodCall.getManager(), methodCall.getResolveScope(), patternMethods, indices);
      }
    }

    for (int i = 0; i < patternMethods.size(); i++) {
      PsiMethod patternMethod = patternMethods.get(i);
      if (!patternMethod.getName().equals(methodExpression.getReferenceName())) continue;
      int index = indices.get(i);

      //we are in collections method implementation
      if (contextMethod != null && isInheritorOrSelf(contextMethod, patternMethod)) return null;

      final PsiClass calleeClass = calleeMethod.getContainingClass();
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiClass patternClass = patternMethod.getContainingClass();
      assert patternClass != null;
      assert calleeClass != null;
      substitutor = TypeConversionUtil.getClassSubstitutor(patternClass, calleeClass, substitutor);
      if (substitutor == null) continue;

      if (!patternMethod.getSignature(substitutor).equals(calleeMethod.getSignature(PsiSubstitutor.EMPTY))) continue;

      PsiTypeParameter[] typeParameters = patternClass.getTypeParameters();
      if (typeParameters.length <= index) return null;
      final PsiTypeParameter typeParameter = typeParameters[index];
      PsiType typeParamMapping = substitutor.substitute(typeParameter);
      if (typeParamMapping == null) return null;
      String message = null;
      if (typeParamMapping instanceof PsiCapturedWildcardType) {
        typeParamMapping = ((PsiCapturedWildcardType)typeParamMapping).getWildcard();
      }
      if (!typeParamMapping.isAssignableFrom(argType)) {
        if (typeParamMapping.isConvertibleFrom(argType)) {
          if (reportConvertibleMethodCalls) {
            message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor1",
                                                PsiFormatUtil.formatMethod(calleeMethod, substitutor,
                                                                           PsiFormatUtilBase.SHOW_NAME |
                                                                           PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                                           PsiFormatUtilBase.SHOW_TYPE));
          }
        }
        else {
          PsiType qualifierType = qualifier.getType();
          if (qualifierType != null) {
            message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                                PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                                PsiFormatUtil.formatType(argType, 0, PsiSubstitutor.EMPTY));
          }
        }
      }
      return message;
    }
    return null;
  }

  private static boolean isInheritorOrSelf(PsiMethod inheritorCandidate, PsiMethod base) {
    PsiClass aClass = inheritorCandidate.getContainingClass();
    PsiClass bClass = base.getContainingClass();
    if (aClass == null || bClass == null) return false;
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
    return substitutor != null &&
           MethodSignatureUtil.findMethodBySignature(bClass, inheritorCandidate.getSignature(substitutor), false) == base;
  }
}
