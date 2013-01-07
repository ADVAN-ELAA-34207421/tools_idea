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
package com.intellij.refactoring.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertyUtils {
    private PropertyUtils() {
    }

    public static PsiMethod findSetterForField(PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final Project project = field.getProject();
        final String propertyName = PropertyUtil.suggestPropertyName(project, field);
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        return PropertyUtil.findPropertySetter(containingClass, propertyName, isStatic, true);
    }

    public static PsiMethod findGetterForField(PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final Project project = field.getProject();
        final String propertyName = PropertyUtil.suggestPropertyName(project, field);
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        return PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, true);
    }

  /**
   * If the name of the method looks like a getter and the body consists of a single return statement,
   * returns the returned expression. Otherwise, returns null.
   *
   * @param method the method to check
   * @return the return value, or null if it doesn't match the condotions.
   */
  @Nullable
  public static PsiExpression getGetterReturnExpression(PsiMethod method) {
    return method != null && hasGetterSignature(method) ? getSingleReturnValue(method) : null;
  }

  private static boolean hasGetterSignature(@NotNull PsiMethod method) {
    return PropertyUtil.isSimplePropertyGetter(method) && !method.hasModifierProperty(PsiModifier.SYNCHRONIZED);
  }

  @Nullable
  public static PsiExpression getSingleReturnValue(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return null;
    }
    final PsiStatement[] statements = body.getStatements();
    final PsiStatement statement = statements.length != 1 ? null : statements[0];
    return statement instanceof PsiReturnStatement ? ((PsiReturnStatement)statement).getReturnValue() : null;
  }

  @Nullable
  public static PsiField getFieldOfGetter(PsiMethod method) {
    PsiField field = getSimplyReturnedField(method, getGetterReturnExpression(method));
    if (field != null) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null && field.getType().equalsToText(returnType.getCanonicalText())) {
        return field;
      }
    }
    return null;
  }

  @Nullable
  public static PsiField getSimplyReturnedField(PsiMethod method, @Nullable PsiExpression value) {
    if (!(value instanceof PsiReferenceExpression)) {
      return null;
    }

    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    if (hasSubstantialQualifier(reference)) {
      return null;
    }

    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiField)) {
      return null;
    }

    final PsiField field = (PsiField)referent;
    return InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), field.getContainingClass(), true) ? field : null;
  }

  private static boolean hasSubstantialQualifier(PsiReferenceExpression reference) {
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier == null) return false;

    if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
      return false;
    }

    if (qualifier instanceof PsiReferenceExpression) {
      return !(((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass);
    }
    return true;
  }

  public static boolean isSimpleGetter(PsiMethod method) {
    return getFieldOfGetter(method) != null;
  }

  @Nullable
  public static PsiField getFieldOfSetter(PsiMethod method) {
    if (method == null) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 1) {
      return null;
    }
    @NonNls final String name = method.getName();
    if (!name.startsWith("set")) {
      return null;
    }
    if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      return null;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return null;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) {
      return null;
    }
    final PsiStatement statement = statements[0];
    if (!(statement instanceof PsiExpressionStatement)) {
      return null;
    }
    final PsiExpressionStatement possibleAssignmentStatement = (PsiExpressionStatement)statement;
    final PsiExpression possibleAssignment = possibleAssignmentStatement.getExpression();
    if (!(possibleAssignment instanceof PsiAssignmentExpression)) {
      return null;
    }
    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)possibleAssignment;
    if (!JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      return null;
    }
    final PsiExpression lhs = assignment.getLExpression();
    if (!(lhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiClass)) {
        return null;
      }
    }
    else if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
      return null;
    }
    final PsiElement referent = reference.resolve();
    if (referent == null) {
      return null;
    }
    if (!(referent instanceof PsiField)) {
      return null;
    }
    final PsiField field = (PsiField)referent;
    final PsiClass fieldContainingClass = field.getContainingClass();
    final PsiClass methodContainingClass = method.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(methodContainingClass, fieldContainingClass, true)) {
      return null;
    }
    final PsiExpression rhs = assignment.getRExpression();
    if (!(rhs instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression rReference = (PsiReferenceExpression)rhs;
    final PsiExpression rQualifier = rReference.getQualifierExpression();
    if (rQualifier != null) {
      return null;
    }
    final PsiElement rReferent = rReference.resolve();
    if (rReferent == null) {
      return null;
    }
    if (!(rReferent instanceof PsiParameter)) {
      return null;
    }
    final PsiType fieldType = field.getType();
    final PsiType parameterType = ((PsiVariable)rReferent).getType();
    if (fieldType.equalsToText(parameterType.getCanonicalText())) {
      return field;
    }
    else {
      return null;
    }
  }

  public static boolean isSimpleSetter(PsiMethod method) {
    return getFieldOfSetter(method) != null;
  }

  @Nullable
  public static PsiMethod getReversePropertyMethod(PsiMethod propertyMethod) {
    if (propertyMethod == null) {
      return null;
    }
    final PsiClass aClass = propertyMethod.getContainingClass();
    if (aClass == null) {
      return null;
    }
    final String methodName = propertyMethod.getName();
    final String prefix;
    if (methodName.startsWith("get")) {
      prefix = "get";
    }
    else if (methodName.startsWith("is")) {
      prefix = "is";
    }
    else if (methodName.startsWith("set")) {
      prefix = "set";
    }
    else {
      return null;
    }
    final String name = methodName.substring(prefix.length());
    final PsiField field;
    if (prefix.equals("set")) {
      field = getFieldOfSetter(propertyMethod);
    }
    else {
      field = getFieldOfGetter(propertyMethod);
    }
    if (field == null) {
      return null;
    }
    if (prefix.equals("set")) {
      final PsiMethod result = findPropertyMethod(aClass, "get", name, field);
      if (result != null) {
        return result;
      }
      return findPropertyMethod(aClass, "is", name, field);
    }
    else {
      return findPropertyMethod(aClass, "set", name, field);
    }
  }

  private static PsiMethod findPropertyMethod(@NotNull PsiClass aClass, @NotNull String prefix, @NotNull String propertyName, @NotNull PsiField field1) {
    final PsiMethod[] methods = aClass.findMethodsByName(prefix + propertyName, true);
    for (PsiMethod method : methods) {
      final PsiField field2;
      if (prefix.equals("set")) {
        field2 = getFieldOfSetter(method);
      }
      else {
        field2 = getFieldOfGetter(method);
      }
      if (field1.equals(field2)) {
        return method;
      }
    }
    return null;
  }
}
