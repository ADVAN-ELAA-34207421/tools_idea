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

package com.intellij.codeInsight;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ReflectionCache;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class CodeInsightUtilBase extends FileModificationService {
  private CodeInsightUtilBase() {
  }

  public static <T extends PsiElement> T findElementInRange(@NotNull PsiFile file,
                                                             int startOffset,
                                                             int endOffset,
                                                             @NotNull Class<T> klass,
                                                             @NotNull Language language) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, language);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    }
    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    final T element =
      ReflectionCache.isAssignable(klass, commonParent.getClass())
      ? (T)commonParent : PsiTreeUtil.getParentOfType(commonParent, klass);
    if (element == null || element.getTextRange().getStartOffset() != startOffset || element.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return element;
  }

  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@NotNull T element) {
    return forcePsiPostprocessAndRestoreElement(element, false);
  }

  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@NotNull T element,
                                                                              boolean useFileLanguage) {
    final PsiFile psiFile = element.getContainingFile();
    final Document document = psiFile.getViewProvider().getDocument();
    //if (document == null) return element;
    final Language language = useFileLanguage ? psiFile.getLanguage() : PsiUtilBase.getDialect(element);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    final RangeMarker rangeMarker = document.createRangeMarker(element.getTextRange());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);

    T elementInRange = findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                          (Class<? extends T>)element.getClass(),
                                          language);
    rangeMarker.dispose();
    return elementInRange;
  }

  @Override
  public boolean prepareFileForWrite(@Nullable final PsiFile psiFile) {
    if (psiFile == null) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    final Project project = psiFile.getProject();

    final Editor editor =
      psiFile.isWritable() ? null : FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
    if (!ReadonlyStatusHandler.ensureFilesWritable(project, file)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (editor != null && editor.getComponent().isDisplayable()) {
            HintManager.getInstance()
              .showErrorHint(editor, CodeInsightBundle.message("error.hint.file.is.readonly", file.getPresentableUrl()));
          }
        }
      });

      return false;
    }

    return true;
  }

  @Override
  public boolean preparePsiElementForWrite(@Nullable PsiElement element) {
    PsiFile file = element == null ? null : element.getContainingFile();
    return prepareFileForWrite(file);
  }

  @Override
  public boolean preparePsiElementsForWrite(@NotNull PsiElement... elements) {
    return preparePsiElementsForWrite(Arrays.asList(elements));
  }

  @Override
  public boolean preparePsiElementsForWrite(@NotNull Collection<? extends PsiElement> elements) {
    if (elements.isEmpty()) return true;
    Set<VirtualFile> files = new THashSet<VirtualFile>();
    Project project = null;
    for (PsiElement element : elements) {
      PsiFile file = element.getContainingFile();
      if (file == null) continue;
      project = file.getProject();
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) continue;
      files.add(virtualFile);
    }
    if (!files.isEmpty()) {
      VirtualFile[] virtualFiles = VfsUtilCore.toVirtualFileArray(files);
      ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(virtualFiles);
      return !status.hasReadonlyFiles();
    }
    return true;
  }

  // returns true on success
  public static boolean prepareEditorForWrite(@NotNull Editor editor) {
    if (!editor.isViewer()) return true;
    JComponent component = HintUtil.createInformationLabel("This view is read-only");
    final LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false);
    return false;
  }
}
