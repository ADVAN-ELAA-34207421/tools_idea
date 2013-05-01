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

/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:43:17 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Set;

public class InspectionManagerEx extends InspectionManagerBase {
  private GlobalInspectionContextImpl myGlobalInspectionContext = null;
  @NonNls private String myCurrentProfileName;
  private final NotNullLazyValue<ContentManager> myContentManager;
  private final Set<GlobalInspectionContextImpl> myRunningContexts = new HashSet<GlobalInspectionContextImpl>();

  public InspectionManagerEx(final Project project) {
    super(project);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
          return ContentFactory.SERVICE.getInstance().createContentManager(new TabbedPaneContentUI(), true, project);
        }
      };
    }
    else {
      myContentManager = new NotNullLazyValue<ContentManager>() {
        @NotNull
        @Override
        protected ContentManager compute() {
          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
          ToolWindow toolWindow =
            toolWindowManager.registerToolWindow(ToolWindowId.INSPECTION, true, ToolWindowAnchor.BOTTOM, project);
          ContentManager contentManager = toolWindow.getContentManager();
          toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowInspection);
          new ContentManagerWatcher(toolWindow, contentManager);
          return contentManager;
        }
      };
    }
  }


  @NotNull
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   @Nullable final HintAction hintAction,
                                                   boolean onTheFly,
                                                   final LocalQuickFix... fixes) {
    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction, onTheFly);
  }

  public GlobalInspectionContextImpl createNewGlobalContext(boolean reuse) {
    if (reuse) {
      if (myGlobalInspectionContext == null) {
        myGlobalInspectionContext = new GlobalInspectionContextImpl(getProject(), myContentManager);
      }
      myRunningContexts.add(myGlobalInspectionContext);
      return myGlobalInspectionContext;
    }
    final GlobalInspectionContextImpl inspectionContext = new GlobalInspectionContextImpl(getProject(), myContentManager);
    myRunningContexts.add(inspectionContext);
    return inspectionContext;
  }

  public void setProfile(final String name) {
    myCurrentProfileName = name;
  }

  public String getCurrentProfile() {
    if (myCurrentProfileName == null) {
      final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(getProject());
      myCurrentProfileName = profileManager.getProjectProfile();
      if (myCurrentProfileName == null) {
        myCurrentProfileName = InspectionProfileManager.getInstance().getRootProfile().getName();
      }
    }
    return myCurrentProfileName;
  }

  public void closeRunningContext(GlobalInspectionContextImpl globalInspectionContext){
    myRunningContexts.remove(globalInspectionContext);
  }

  public Set<GlobalInspectionContextImpl> getRunningContexts() {
    return myRunningContexts;
  }

  public static boolean inspectionResultSuppressed(@NotNull PsiElement place, LocalInspectionTool tool) {
    if (tool instanceof CustomSuppressableInspectionTool) {
      return ((CustomSuppressableInspectionTool)tool).isSuppressedFor(place);
    }
    String alternativeId;
    String id;

    return isSuppressed(place, id = tool.getID()) ||
           (alternativeId = tool.getAlternativeID()) != null &&
           !alternativeId.equals(id) &&
           isSuppressed(place, alternativeId);
  }

  public static boolean canRunInspections(final Project project, final boolean online) {
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      if (!factory.isProjectConfiguredToRunInspections(project, online)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isSuppressed(@NotNull PsiElement psiElement, String id) {
    if (id == null) return false;
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      if (!factory.isToCheckMember(psiElement, id)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Deprecated
  public ProblemDescriptor createProblemDescriptor(@NotNull final PsiElement psiElement,
                                                   @NotNull final String descriptionTemplate,
                                                   @NotNull final ProblemHighlightType highlightType,
                                                   @Nullable final HintAction hintAction,
                                                   final LocalQuickFix... fixes) {

    return new ProblemDescriptorImpl(psiElement, psiElement, descriptionTemplate, fixes, highlightType, false, null, hintAction, true);
  }

  @TestOnly
  public NotNullLazyValue<ContentManager> getContentManager() {
    return myContentManager;
  }
}
