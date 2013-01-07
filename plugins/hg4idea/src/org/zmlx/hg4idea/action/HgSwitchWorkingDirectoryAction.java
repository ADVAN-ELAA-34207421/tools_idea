// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.ui.HgSwitchDialog;

import java.util.Collection;

public class HgSwitchWorkingDirectoryAction extends HgAbstractGlobalAction {

  protected HgGlobalCommandBuilder getHgGlobalCommandBuilder(final Project project) {
    return new HgGlobalCommandBuilder() {
      public HgGlobalCommand build(Collection<VirtualFile> repos) {
        HgSwitchDialog dialog = new HgSwitchDialog(project);
        dialog.setRoots(repos);
        dialog.show();
        if (dialog.isOK()) {
          return buildCommand(dialog, project);
        }
        return null;
      }
    };
  }

  private HgGlobalCommand buildCommand(final HgSwitchDialog dialog, final Project project) {
    return new HgGlobalCommand() {
      public VirtualFile getRepo() {
        return dialog.getRepository();
      }

      public void execute() {
        final HgUpdateCommand command = new HgUpdateCommand(project, dialog.getRepository());
        command.setClean(dialog.isRemoveLocalChanges());
        if (dialog.isRevisionSelected()) {
          command.setRevision(dialog.getRevision());
        }
        if (dialog.isBranchSelected()) {
          command.setBranch(dialog.getBranch().getName());
        }
        if (dialog.isTagSelected()) {
          command.setRevision(dialog.getTag().getName());
        }
        FileDocumentManager.getInstance().saveAllDocuments();
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            HgCommandResult result = command.execute();
            new HgCommandResultNotifier(project).process(result, null, null);
            project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
          }
        });
      }
    };
  }

}
