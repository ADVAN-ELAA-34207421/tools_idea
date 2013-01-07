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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationNameValuePairImpl extends GroovyPsiElementImpl implements GrAnnotationNameValuePair, PsiReference {
  public GrAnnotationNameValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotationNameValuePair(this);
  }

  public String toString() {
    return "Annotation member value pair";
  }

  @Nullable
  public String getName() {
    final PsiElement nameId = getNameIdentifierGroovy();
    return nameId != null ? nameId.getText() : null;
  }

  @Override
  public String getLiteralValue() {
    return null;
  }

  @Nullable
  public PsiElement getNameIdentifierGroovy() {
    PsiElement child = getFirstChild();
    if (child == null) return null;

    IElementType type = child.getNode().getElementType();
    if (type == GroovyTokenTypes.mIDENT || type == GroovyTokenTypes.kDEF) return child;

    return null;
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public GrAnnotationMemberValue getValue() {
    return findChildByClass(GrAnnotationMemberValue.class);
  }

  @NotNull
  public PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    GrAnnotationMemberValue value = getValue();
    if (value == null) {
      return (PsiAnnotationMemberValue)add(newValue);
    }
    else {
      return (PsiAnnotationMemberValue)value.replace(newValue);
    }
  }

  public PsiReference getReference() {
    return getNameIdentifierGroovy() == null ? null : this;
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    PsiElement nameId = getNameIdentifierGroovy();
    assert nameId != null;
    return nameId.getTextRange().shiftRight(-getTextRange().getStartOffset());
  }

  @Nullable
  public PsiElement resolve() {
    GrAnnotation anno = getAnnotation();
    if (anno != null) {
      GrCodeReferenceElement ref = anno.getClassReference();
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass && ((PsiClass) resolved).isAnnotationType()) {
        String declaredName = getName();
        String name = declaredName == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : declaredName;
        PsiMethod[] methods = ((PsiClass) resolved).findMethodsByName(name, false);
        return methods.length == 1 ? methods[0] : null;
      }
    }
    return null;
  }

  @Nullable
  private GrAnnotation getAnnotation() {
    PsiElement pParent = getParent().getParent();
    if (pParent instanceof GrAnnotation) return (GrAnnotation) pParent;
    PsiElement ppParent = pParent.getParent();
    return ppParent instanceof GrAnnotation ? (GrAnnotation)ppParent : null;
  }

  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getText());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getNameIdentifierGroovy();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null;
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      assert node != null;
      getNode().replaceChild(node, newNameNode);
    } else {
      PsiElement first = getFirstChild();
      ASTNode anchorBefore = first != null ? first.getNode() : null;
      getNode().addLeaf(GroovyTokenTypes.mASSIGN, "=", anchorBefore);
      getNode().addChild(newNameNode, anchorBefore);
    }

    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NYI");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiMethod && getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  public Object[] getVariants() {
    return GroovyCompletionUtil.getAnnotationCompletionResults(getAnnotation(), PrefixMatcher.ALWAYS_TRUE).toArray();
  }

  public boolean isSoft() {
    return false;
  }
}
