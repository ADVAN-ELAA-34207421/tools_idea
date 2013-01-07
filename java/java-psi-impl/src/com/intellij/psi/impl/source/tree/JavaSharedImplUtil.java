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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JavaSharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.JavaSharedImplUtil");

  private JavaSharedImplUtil() { }

  public static PsiType getType(@NotNull PsiTypeElement typeElement, @NotNull PsiElement anchor, @NotNull PsiElement context) {
    int cStyleArrayCount = countBrackets(anchor);
    PsiType type;
    if (typeElement instanceof PsiTypeElementImpl) {
      type = ((PsiTypeElementImpl)typeElement).getDetachedType(context);
    }
    else {
      type = typeElement.getType();
    }
    for (int i = 0; i < cStyleArrayCount; i++) {
      type = type.createArrayType();
    }
    return type;
  }

  public static PsiType getTypeNoResolve(@NotNull PsiTypeElement typeElement, @NotNull PsiElement anchor, @NotNull PsiElement context) {
    int cStyleArrayCount = countBrackets(anchor);
    PsiType type = typeElement.getTypeNoResolve(context);
    for (int i = 0; i < cStyleArrayCount; i++) {
      type = type.createArrayType();
    }
    return type;
  }

  private static int countBrackets(PsiElement anchor) {
    int cStyleArrayCount = 0;
    ASTNode name = SourceTreeToPsiMap.psiToTreeNotNull(anchor);
    for (ASTNode child = name.getTreeNext(); child != null; child = child.getTreeNext()) {
      IElementType i = child.getElementType();
      if (i == JavaTokenType.LBRACKET) {
        cStyleArrayCount++;
      }
      else if (i != JavaTokenType.RBRACKET && !ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(i)) {
        break;
      }
    }
    return cStyleArrayCount;
  }

  public static void normalizeBrackets(PsiVariable variable) {
    CompositeElement variableElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(variable);

    PsiTypeElement typeElement = variable.getTypeElement();
    PsiIdentifier nameElement = variable.getNameIdentifier();
    LOG.assertTrue(typeElement != null && nameElement != null);

    ASTNode type = typeElement.getNode();
    ASTNode name = nameElement.getNode();

    ASTNode firstBracket = null;
    ASTNode lastBracket = null;
    int arrayCount = 0;
    ASTNode element = name;
    while (element != null) {
      element = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
      if (element == null || element.getElementType() != JavaTokenType.LBRACKET) break;
      if (firstBracket == null) firstBracket = element;
      lastBracket = element;
      arrayCount++;

      element = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
      if (element == null || element.getElementType() != JavaTokenType.RBRACKET) break;
      lastBracket = element;
    }

    if (firstBracket != null) {
      element = firstBracket;
      while (true) {
        ASTNode next = element.getTreeNext();
        variableElement.removeChild(element);
        if (element == lastBracket) break;
        element = next;
      }

      CompositeElement newType = (CompositeElement)type.clone();
      for (int i = 0; i < arrayCount; i++) {
        CompositeElement newType1 = ASTFactory.composite(JavaElementType.TYPE);
        newType1.rawAddChildren(newType);

        newType1.rawAddChildren(ASTFactory.leaf(JavaTokenType.LBRACKET, "["));
        newType1.rawAddChildren(ASTFactory.leaf(JavaTokenType.RBRACKET, "]"));
        newType = newType1;
        newType.acceptTree(new GeneratedMarkerVisitor());
      }
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      variableElement.replaceChild(type, newType);
    }
  }

  public static void setInitializer(PsiVariable variable, final PsiExpression initializer) throws IncorrectOperationException {
    PsiExpression oldInitializer = variable.getInitializer();
    if (oldInitializer != null) {
      oldInitializer.delete();
    }
    if (initializer == null) {
      return;
    }
    CompositeElement variableElement = (CompositeElement)variable.getNode();
    ASTNode eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    if (eq == null) {
      final CharTable charTable = SharedImplUtil.findCharTableByTree(variableElement);
      eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=", 0, 1, charTable, variable.getManager());
      PsiElement element = variable.getNameIdentifier();
      final ASTNode node = PsiImplUtil.skipWhitespaceCommentsAndTokens(element.getNode().getTreeNext(),
                                                                       TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET));
      variableElement.addInternal((TreeElement)eq, eq, node, Boolean.TRUE);
      eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    }
    variable.addAfter(initializer, eq.getPsi());
  }
}
