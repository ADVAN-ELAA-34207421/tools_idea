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
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ClsTypeElementImpl extends ClsElementImpl implements PsiTypeElement {
  static final char VARIANCE_NONE = '\0';
  static final char VARIANCE_EXTENDS = '+';
  static final char VARIANCE_SUPER = '-';
  static final char VARIANCE_INVARIANT = '*';
  @NonNls private static final String VARIANCE_EXTENDS_PREFIX = "? extends ";
  @NonNls private static final String VARIANCE_SUPER_PREFIX = "? super ";

  private final PsiElement myParent;
  private final String myTypeText;
  private volatile ClsElementImpl myChild = null;
  private boolean myChildSet = false;
  private volatile PsiType myCachedType;
  private final char myVariance;

  public ClsTypeElementImpl(@NotNull PsiElement parent, String typeText, char variance) {
    myParent = parent;
    myTypeText = typeText;
    myVariance = variance;
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    loadChild();
    return myChild != null ? new PsiElement[]{myChild} : PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public String getText() {
    final String shortClassName = PsiNameHelper.getShortClassName(myTypeText);
    return decorateTypeText(shortClassName);
  }

  private String decorateTypeText(final String shortClassName) {
    switch (myVariance) {
      case VARIANCE_NONE:
        return shortClassName;
      case VARIANCE_EXTENDS:
        return VARIANCE_EXTENDS_PREFIX + shortClassName;
      case VARIANCE_SUPER:
        return VARIANCE_SUPER_PREFIX + shortClassName;
      case VARIANCE_INVARIANT:
        return "?";
      default:
        assert false : myVariance;
        return null;
    }
  }

  public String getCanonicalText() {
    return decorateTypeText(myTypeText);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(decorateTypeText(myTypeText));
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.TYPE);

    loadChild();

    if (myChild != null) {
      myChild.setMirror(element.getFirstChildNode());
    }
  }

  private void loadChild() {
    if (isPrimitive()) {
      synchronized (LAZY_BUILT_LOCK) {
        myChildSet = true;
      }
      return;
    }

    if (isArray() || isVarArgs()) {
      createComponentTypeChild();
    }
    else {
      createClassReferenceChild();
    }
  }

  private boolean isPrimitive() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(myTypeText) != null;
  }

  private boolean isArray() {
    return myTypeText.endsWith("[]");
  }

  private boolean isVarArgs() {
    return myTypeText.endsWith("...");
  }

  @Override
  @NotNull
  public PsiType getType() {
    if (myCachedType == null) {
      synchronized (LAZY_BUILT_LOCK) {
        if (myCachedType == null) {
          myCachedType = calculateType();
        }
      }
    }
    return myCachedType;
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  @Override
  public PsiAnnotationOwner getOwner(PsiAnnotation annotation) {
    return this; //todo
  }

  @Override
  public PsiType getTypeNoResolve(@NotNull PsiElement context) {
    return getType();
  }

  private PsiType calculateType() {
    PsiType result = JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(myTypeText);
    if (result != null) return result;

    if (isArray()) {
      createComponentTypeChild();
      if (myVariance == VARIANCE_NONE) return ((PsiTypeElement)myChild).getType().createArrayType();
      switch (myVariance) {
        case VARIANCE_EXTENDS:
          return PsiWildcardType.createExtends(getManager(), ((PsiTypeElement)myChild).getType());
        case VARIANCE_SUPER:
          return PsiWildcardType.createSuper(getManager(), ((PsiTypeElement)myChild).getType());
        default:
          assert false : myVariance;
          return null;
      }
    }
    else if (isVarArgs()) {
      createComponentTypeChild();
      return new PsiEllipsisType(((PsiTypeElement)myChild).getType());
    }

    createClassReferenceChild();
    final PsiClassReferenceType psiClassReferenceType;
    if (myVariance != VARIANCE_INVARIANT) {
      psiClassReferenceType = new PsiClassReferenceType((PsiJavaCodeReferenceElement)myChild, null);
    }
    else {
      psiClassReferenceType = null;
    }

    switch (myVariance) {
      case VARIANCE_NONE:
        return psiClassReferenceType;
      case VARIANCE_EXTENDS:
        return PsiWildcardType.createExtends(getManager(), psiClassReferenceType);
      case VARIANCE_SUPER:
        return PsiWildcardType.createSuper(getManager(), psiClassReferenceType);
      case VARIANCE_INVARIANT:
        return PsiWildcardType.createUnbounded(getManager());
      default:
        assert false : myVariance;
        return null;
    }
  }

  private void createClassReferenceChild() {
    synchronized (LAZY_BUILT_LOCK) {
      if (!myChildSet) {
        if (myVariance != VARIANCE_INVARIANT) {
          myChild = new ClsJavaCodeReferenceElementImpl(this, myTypeText);
        }
        myChildSet = true;
      }
    }
  }

  private void createComponentTypeChild() {
    synchronized (LAZY_BUILT_LOCK) {
      if (!myChildSet) {
        if (isArray()) {
          if (myVariance == VARIANCE_NONE) {
            myChild = new ClsTypeElementImpl(this, myTypeText.substring(0, myTypeText.length() - 2), myVariance);
          }
          else {
            myChild = new ClsTypeElementImpl(this, myTypeText, VARIANCE_NONE);
          }
        }
        else if (isVarArgs()) {
          myChild = new ClsTypeElementImpl(this, myTypeText.substring(0, myTypeText.length() - 3), myVariance);
        }
        else {
          assert false : myTypeText;
        }
        myChildSet = true;
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    throw new UnsupportedOperationException();//todo
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException();//todo
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
