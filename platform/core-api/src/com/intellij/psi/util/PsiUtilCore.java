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
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author yole
 */
public class PsiUtilCore {
  @SuppressWarnings("ConstantConditions")
  public static final PsiElement NULL_PSI_ELEMENT = new PsiElement() {
    @Override
    @NotNull
    public Project getProject() {
      throw new PsiInvalidElementAccessException(this);
    }

    @Override
    @NotNull
    public Language getLanguage() {
      throw new IllegalAccessError(this.toString());
    }

    @Override
    public PsiManager getManager() {
      return null;
    }

    @Override
    @NotNull
    public PsiElement[] getChildren() {
      return new PsiElement[0];
    }

    @Override
    public PsiElement getParent() {
      return null;
    }

    @Override
    @Nullable
    public PsiElement getFirstChild() {
      return null;
    }

    @Override
    @Nullable
    public PsiElement getLastChild() {
      return null;
    }

    @Override
    @Nullable
    public PsiElement getNextSibling() {
      return null;
    }

    @Override
    @Nullable
    public PsiElement getPrevSibling() {
      return null;
    }

    @Override
    public PsiFile getContainingFile() {
      throw new PsiInvalidElementAccessException(this);
    }

    @Override
    public TextRange getTextRange() {
      return null;
    }

    @Override
    public int getStartOffsetInParent() {
      return 0;
    }

    @Override
    public int getTextLength() {
      return 0;
    }

    @Override
    public PsiElement findElementAt(int offset) {
      return null;
    }

    @Override
    @Nullable
    public PsiReference findReferenceAt(int offset) {
      return null;
    }

    @Override
    public int getTextOffset() {
      return 0;
    }

    @Override
    public String getText() {
      return null;
    }

    @Override
    @NotNull
    public char[] textToCharArray() {
      return new char[0];
    }

    @Override
    public PsiElement getNavigationElement() {
      return null;
    }

    @Override
    public PsiElement getOriginalElement() {
      return null;
    }

    @Override
    public boolean textMatches(@NotNull CharSequence text) {
      return false;
    }

    @Override
    public boolean textMatches(@NotNull PsiElement element) {
      return false;
    }

    @Override
    public boolean textContains(char c) {
      return false;
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {

    }

    @Override
    public void acceptChildren(@NotNull PsiElementVisitor visitor) {

    }

    @Override
    public PsiElement copy() {
      return null;
    }

    @Override
    public PsiElement add(@NotNull PsiElement element) {
      return null;
    }

    @Override
    public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) {
      return null;
    }

    @Override
    public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) {
      return null;
    }

    @Override
    public void checkAdd(@NotNull PsiElement element) {

    }

    @Override
    public PsiElement addRange(PsiElement first, PsiElement last) {
      return null;
    }

    @Override
    public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) {
      return null;
    }

    @Override
    public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) {
      return null;
    }

    @Override
    public void delete() {

    }

    @Override
    public void checkDelete() {

    }

    @Override
    public void deleteChildRange(PsiElement first, PsiElement last) {

    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) {
      return null;
    }

    @Override
    public boolean isValid() {
      return false;
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @Override
    @Nullable
    public PsiReference getReference() {
      return null;
    }

    @Override
    @NotNull
    public PsiReference[] getReferences() {
      return new PsiReference[0];
    }

    @Override
    public <T> T getCopyableUserData(Key<T> key) {
      return null;
    }

    @Override
    public <T> void putCopyableUserData(Key<T> key, T value) {

    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       PsiElement lastParent,
                                       @NotNull PsiElement place) {
      return false;
    }

    @Override
    public PsiElement getContext() {
      return null;
    }

    @Override
    public boolean isPhysical() {
      return false;
    }

    @Override
    @NotNull
    public GlobalSearchScope getResolveScope() {
      return GlobalSearchScope.EMPTY_SCOPE;
    }

    @Override
    @NotNull
    public SearchScope getUseScope() {
      return GlobalSearchScope.EMPTY_SCOPE;
    }

    @Override
    public ASTNode getNode() {
      return null;
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, T value) {

    }

    @Override
    public Icon getIcon(int flags) {
      return null;
    }

    @Override
    public boolean isEquivalentTo(final PsiElement another) {
      return this == another;
    }

    @Override
    public String toString() {
      return "NULL_PSI_ELEMENT";
    }
  };

  @NotNull
  public static PsiElement[] toPsiElementArray(@NotNull Collection<? extends PsiElement> collection) {
    if (collection.isEmpty()) return PsiElement.EMPTY_ARRAY;
    //noinspection SSBasedInspection
    return collection.toArray(new PsiElement[collection.size()]);
  }

  public static Language getNotAnyLanguage(ASTNode node) {
    if (node == null) return Language.ANY;

    final Language lang = node.getElementType().getLanguage();
    return lang == Language.ANY ? getNotAnyLanguage(node.getTreeParent()) : lang;
  }

  @Nullable
  public static VirtualFile getVirtualFile(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) {
      return null;
    }

    if (element instanceof PsiFileSystemItem) {
      return ((PsiFileSystemItem)element).getVirtualFile();
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return null;
    }

    return containingFile.getVirtualFile();
  }

  public static int compareElementsByPosition(final PsiElement element1, final PsiElement element2) {
    if (element1 != null && element2 != null) {
      final PsiFile psiFile1 = element1.getContainingFile();
      final PsiFile psiFile2 = element2.getContainingFile();
      if (Comparing.equal(psiFile1, psiFile2)){
        final TextRange textRange1 = element1.getTextRange();
        final TextRange textRange2 = element2.getTextRange();
        if (textRange1 != null && textRange2 != null) {
          return textRange1.getStartOffset() - textRange2.getStartOffset();
        }
      } else if (psiFile1 != null && psiFile2 != null){
        final String name1 = psiFile1.getName();
        final String name2 = psiFile2.getName();
        return name1.compareToIgnoreCase(name2);
      }
    }
    return 0;
  }

  public static boolean hasErrorElementChild(PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  @NotNull
  public static PsiElement getElementAtOffset(@NotNull PsiFile file, int offset) {
    PsiElement elt = file.findElementAt(offset);
    if (elt == null && offset > 0) {
      elt = file.findElementAt(offset - 1);
    }
    if (elt == null) {
      return file;
    }
    return elt;
  }

  @Nullable
  public static PsiFile getTemplateLanguageFile(final PsiElement element) {
    if (element == null) return null;
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @NotNull
  public static PsiFile[] toPsiFileArray(@NotNull Collection<? extends PsiFile> collection) {
    if (collection.isEmpty()) return PsiFile.EMPTY_ARRAY;
    return collection.toArray(new PsiFile[collection.size()]);
  }

  /**
   * @return name for element using element structure info
   */
  @Nullable
  public static String getName(PsiElement element) {
    String name = null;
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null) {
        name = data.getName(element);
      }
    }
    if (name == null && element instanceof PsiNamedElement) {
      name = ((PsiNamedElement) element).getName();
    }
    return name;
  }

  public static String getQualifiedNameAfterRename(String qName, String newName) {
    if (qName == null) return newName;
    int index = qName.lastIndexOf('.');
    return index < 0 ? newName : qName.substring(0, index + 1) + newName;
  }
}
