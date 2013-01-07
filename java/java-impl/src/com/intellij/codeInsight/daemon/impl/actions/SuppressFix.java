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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class SuppressFix extends SuppressIntentionAction {
  private String myID;
  private String myAlternativeID;
  private String myText;

  public SuppressFix(HighlightDisplayKey key) {
    this(key.getID());
    myAlternativeID = HighlightDisplayKey.getAlternativeID(key);
  }

  public SuppressFix(String ID) {
    myID = ID;
  }

  @Override
  @NotNull
  public String getText() {
    return myText == null ? "Suppress for member" : myText;
  }

  @Nullable
  protected PsiDocCommentOwner getContainer(final PsiElement context) {
    if (context == null || !context.getManager().isInProject(context)) {
      return null;
    }
    final PsiFile containingFile = context.getContainingFile();
    if (containingFile == null) {
      // for PsiDirectory
      return null;
    }
    if (!containingFile.getLanguage().isKindOf(StdLanguages.JAVA) || context instanceof PsiFile) {
      return null;
    }
    PsiElement container = context;
    while (container instanceof PsiAnonymousClass || !(container instanceof PsiDocCommentOwner) || container instanceof PsiTypeParameter) {
      container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
      if (container == null) return null;
    }
    return (PsiDocCommentOwner)container;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("suppress.inspection.family");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement context) {
    PsiDocCommentOwner container = getContainer(context);
    boolean isValid = container != null && !(container instanceof JspHolderMethod);
    if (!isValid) {
      return false;
    }
    myText = container instanceof PsiClass
            ? InspectionsBundle.message("suppress.inspection.class")
            : container instanceof PsiMethod ? InspectionsBundle.message("suppress.inspection.method") : InspectionsBundle.message("suppress.inspection.field");
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    PsiDocCommentOwner container = getContainer(element);
    assert container != null;
    if (!CodeInsightUtilBase.preparePsiElementForWrite(container)) return;
    if (use15Suppressions(container)) {
      final PsiModifierList modifierList = container.getModifierList();
      if (modifierList != null) {
        addSuppressAnnotation(project, editor, container, container, getID(container));
      }
    }
    else {
      PsiDocComment docComment = container.getDocComment();
      PsiManager manager = PsiManager.getInstance(project);
      if (docComment == null) {
        String commentText = "/** @" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + getID(container) + "*/";
        docComment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocCommentFromText(commentText);
        PsiElement firstChild = container.getFirstChild();
        container.addBefore(docComment, firstChild);
      }
      else {
        PsiDocTag noInspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (noInspectionTag != null) {
          String tagText = noInspectionTag.getText() + ", " + getID(container);
          noInspectionTag.replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText));
        }
        else {
          String tagText = "@" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + getID(container);
          docComment.add(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText));
        }
      }
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  public static void addSuppressAnnotation(final Project project,
                                           final Editor editor,
                                           final PsiElement container,
                                           final PsiModifierListOwner modifierOwner,
                                           final String id) throws IncorrectOperationException {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierOwner, SuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    final PsiAnnotation newAnnotation = createNewAnnotation(project, editor, container, annotation, id);
    if (newAnnotation != null) {
      if (annotation != null && annotation.isPhysical()) {
        annotation.replace(newAnnotation);
      }
      else {
        final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        new AddAnnotationFix(SuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME, modifierOwner, attributes).invoke(project, editor, container.getContainingFile());
      }
    }
  }

  private static PsiAnnotation createNewAnnotation(final Project project,
                                                   final Editor editor,
                                                   final PsiElement container,
                                                   @Nullable final PsiAnnotation annotation,
                                                   final String id) {

    if (annotation != null) {
      final String currentSuppressedId = "\"" + id + "\"";
      if (!annotation.getText().contains("{")) {
        final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        if (attributes.length == 1) {
          final String suppressedWarnings = attributes[0].getText();
          if (suppressedWarnings.contains(currentSuppressedId)) return null;
          return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(
              "@" + SuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "({" + suppressedWarnings + ", " + currentSuppressedId + "})", container);

        }
      }
      else {
        final int curlyBraceIndex = annotation.getText().lastIndexOf("}");
        if (curlyBraceIndex > 0) {
          final String oldSuppressWarning = annotation.getText().substring(0, curlyBraceIndex);
          if (oldSuppressWarning.contains(currentSuppressedId)) return null;
          return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(
            oldSuppressWarning + ", " + currentSuppressedId + "})", container);
        }
        else if (!ApplicationManager.getApplication().isUnitTestMode() && editor != null) {
          Messages.showErrorDialog(editor.getComponent(),
                                   InspectionsBundle.message("suppress.inspection.annotation.syntax.error", annotation.getText()));
        }
      }
    }
    else {
      return JavaPsiFacade.getInstance(project).getElementFactory()
        .createAnnotationFromText("@" + SuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(\"" + id + "\")", container);
    }
    return null;
  }

  protected boolean use15Suppressions(final PsiDocCommentOwner container) {
    return SuppressManager.getInstance().canHave15Suppressions(container) &&
           !SuppressManager.getInstance().alreadyHas14Suppressions(container);
  }

  private String getID(PsiElement place) {
    String id = getID(place, myAlternativeID);
    return id != null ? id : myID;
  }

  @Nullable
  static String getID(PsiElement place, String alternativeID) {
    if (alternativeID != null) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(place);
      if (module != null) {
        if (!ClasspathStorage.getStorageType(module).equals(ClasspathStorage.DEFAULT_STORAGE)) {
          return alternativeID;
        }
      }
    }

    return null;
  }
}
