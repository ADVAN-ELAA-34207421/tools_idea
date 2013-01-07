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

package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  private final InspectionToolWrapper myTool;
  private final Class myQuickfixClass;

  public CleanupInspectionIntention(final InspectionToolWrapper tool, Class quickFixClass) {
    myTool = tool;
    myQuickfixClass = quickFixClass;
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", myTool.getDisplayName());
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return;
    final List<CommonProblemDescriptor> descriptions =
      ProgressManager.getInstance().runProcess(new Computable<List<CommonProblemDescriptor>>() {
        @Override
        public List<CommonProblemDescriptor> compute() {
          return InspectionRunningUtil.runInspectionOnFile(file, myTool);
        }
      }, new EmptyProgressIndicator());

    Collections.sort(descriptions, new Comparator<CommonProblemDescriptor>() {
      public int compare(final CommonProblemDescriptor o1, final CommonProblemDescriptor o2) {
        final ProblemDescriptorImpl d1 = (ProblemDescriptorImpl)o1;
        final ProblemDescriptorImpl d2 = (ProblemDescriptorImpl)o2;
        return d2.getTextRange().getStartOffset() - d1.getTextRange().getStartOffset();
      }
    });
    for (CommonProblemDescriptor descriptor : descriptions) {
      final QuickFix[] fixes = descriptor.getFixes();
      if (fixes != null && fixes.length > 0) {
        for (QuickFix<CommonProblemDescriptor> fix : fixes) {
          if (fix != null && fix.getClass().isAssignableFrom(myQuickfixClass)) {
            final PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
            if (element != null && element.isValid()) {
              fix.applyFix(project, descriptor);
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            }
            break;
          }
        }
      }
    }
  }

  


  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myQuickfixClass != null && myQuickfixClass != EmptyIntentionAction.class && !(myTool instanceof LocalInspectionToolWrapper &&
                                                                                         ((LocalInspectionToolWrapper)myTool).isUnfair());
  }

  public boolean startInWriteAction() {
    return true;
  }
}
