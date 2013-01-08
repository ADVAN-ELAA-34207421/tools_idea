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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgHeadsCommand;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class HgIntegrateDialog implements Configurable {

  private final Project project;

  private JRadioButton revisionOption;
  private JTextField revisionTxt;
  private JRadioButton branchOption;
  private JRadioButton tagOption;
  private JComboBox branchSelector;
  private JComboBox tagSelector;
  private JPanel contentPanel;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;
  private JRadioButton otherHeadRadioButton;
  private JLabel otherHeadLabel;

  private HgRevisionNumber otherHead;

  public HgIntegrateDialog(Project project, Collection<FilePath> roots) {
    this.project = project;
    hgRepositorySelectorComponent.setRoots(pathsToFiles(roots));
    hgRepositorySelectorComponent.setTitle("Select repository to integrate");
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    ChangeListener changeListener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateOptions();
      }
    };
    branchOption.addChangeListener(changeListener);
    tagOption.addChangeListener(changeListener);
    revisionOption.addChangeListener(changeListener);
    otherHeadRadioButton.addChangeListener(changeListener);

    updateRepository();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  public HgTagBranch getBranch() {
    return branchOption.isSelected() ? (HgTagBranch) branchSelector.getSelectedItem() : null;
  }

  public HgTagBranch getTag() {
    return tagOption.isSelected() ? (HgTagBranch) tagSelector.getSelectedItem() : null;
  }

  public String getRevision() {
    return revisionOption.isSelected() ? revisionTxt.getText() : null;
  }

  public HgRevisionNumber getOtherHead() {
    return otherHeadRadioButton.isSelected() ? otherHead : null;
  }

  @Nls
  public String getDisplayName() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return contentPanel;
  }

  public boolean isModified() {
    return true;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  private void updateRepository() {
    VirtualFile repo = getRepository();
    loadBranches(repo);
    loadTags(repo);
    loadHeads(repo);
  }

  private void updateOptions() {
    revisionTxt.setEnabled(revisionOption.isSelected());
    branchSelector.setEnabled(branchOption.isSelected());
    tagSelector.setEnabled(tagOption.isSelected());
  }

  private void loadBranches(VirtualFile root) {
    new HgTagBranchCommand(project, root).listBranches(new Consumer<List<HgTagBranch>>() {
      @Override
      public void consume(final List<HgTagBranch> branches) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            branchSelector.setModel(new DefaultComboBoxModel(branches.toArray()));
          }
        });
      }
    });
  }

  private void loadTags(VirtualFile root) {
    new HgTagBranchCommand(project, root).listTags(new Consumer<List<HgTagBranch>>() {
      @Override
      public void consume(final List<HgTagBranch> tags) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            tagSelector.setModel(new DefaultComboBoxModel(tags.toArray()));
          }
        });
      }
    });
  }

  private void loadHeads(final VirtualFile root) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final List<HgRevisionNumber> heads = new HgHeadsCommand(project, root).execute();
        if (heads.size() != 2) {
          disableOtherHeadsChoice();
          return;
        }

        HgRevisionNumber currentParent = new HgWorkingCopyRevisionsCommand(project).identify(root).getFirst();
        for (Iterator<HgRevisionNumber> it = heads.iterator() ; it.hasNext(); ) {
          final HgRevisionNumber rev = it.next();
          if (rev.getRevisionNumber().equals(currentParent.getRevisionNumber())) {
            it.remove();
          }
        }

        if (heads.size() == 1) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              otherHeadRadioButton.setVisible(true);
              otherHeadLabel.setVisible(true);
              otherHead = heads.get(0);
              otherHeadLabel.setText(HgVcsMessages.message("hg4idea.integrate.other.head", otherHead.asString()));
            }
          });
        } else {
          //apparently we are not at one of the heads
          disableOtherHeadsChoice();
        }
      }
    });
  }

  private void disableOtherHeadsChoice() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        otherHeadLabel.setVisible(false);
        otherHeadRadioButton.setVisible(false);
      }
    });
  }

  private List<VirtualFile> pathsToFiles(Collection<FilePath> paths) {
    List<VirtualFile> files = new LinkedList<VirtualFile>();
    for (FilePath path : paths) {
      files.add(path.getVirtualFile());
    }
    return files;
  }

  public void disposeUIResources() {
  }

}
