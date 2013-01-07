/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;

/**
 * @author Dmitry Avdeev
 */
public class CloseTaskAction extends BaseTaskAction {

  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    assert project != null;
    LocalTask task = TaskManager.getManager(project).getActiveTask();
    CloseTaskDialog dialog = new CloseTaskDialog(project, task);
    dialog.show();
    if (dialog.isOK()) {
    }
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (event.getPresentation().isEnabled()) {
      Presentation presentation = event.getPresentation();
      Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
      presentation.setEnabled(project != null && !TaskManager.getManager(project).getActiveTask().isDefault());
    }
  }
}
