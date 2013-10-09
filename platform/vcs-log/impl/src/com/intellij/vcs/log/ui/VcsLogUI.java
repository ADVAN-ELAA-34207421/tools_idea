package com.intellij.vcs.log.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.printmodel.SelectController;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;

/**
 * @author erokhins
 */
public class VcsLogUI {

  private static final Logger LOG = Logger.getInstance(VcsLogUI.class);

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final MainFrame myMainFrame;
  @NotNull private final VcsLogColorManager myColorManager;

  @Nullable private GraphElement prevGraphElement;
  @NotNull  private TableModel myGraphModel;

  public VcsLogUI(@NotNull VcsLogDataHolder logDataHolder, @NotNull Project project, @NotNull VcsLogColorManager manager) {
    myLogDataHolder = logDataHolder;
    myColorManager = manager;
    myMainFrame = new MainFrame(myLogDataHolder, this, project);
    project.getMessageBus().connect(project).subscribe(VcsLogDataHolder.REFRESH_COMPLETED, new Runnable() {
      @Override
      public void run() {
        reloadModel();
        updateUI();
      }
    });
    reloadModel();
    updateUI();
  }

  @NotNull
  public MainFrame getMainFrame() {
    return myMainFrame;
  }

  public void jumpToRow(final int rowIndex) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().jumpToRow(rowIndex);
        click(rowIndex);
      }
    });
  }

  public void reloadModel() {
    myGraphModel = new GraphTableModel(myLogDataHolder);
  }

  public void updateUI() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().setModel(myGraphModel);
        myMainFrame.getGraphTable().setPreferredColumnWidths();
        myMainFrame.getGraphTable().repaint();
        myMainFrame.refresh();
      }
    });
  }

  public void addToSelection(final Hash hash) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        int row = myLogDataHolder.getDataPack().getRowByHash(hash);
        myMainFrame.getGraphTable().getSelectionModel().addSelectionInterval(row, row);
      }
    });
  }

  public void showAll() {
    myLogDataHolder.getDataPack().getGraphModel().getFragmentManager().showAll();
    updateUI();
    jumpToRow(0);
  }

  public void hideAll() {
    myLogDataHolder.getDataPack().getGraphModel().getFragmentManager().hideAll();
    updateUI();
    jumpToRow(0);
  }

  public void setLongEdgeVisibility(boolean visibility) {
    myLogDataHolder.getDataPack().getPrintCellModel().setLongEdgeVisibility(visibility);
    updateUI();
  }

  public boolean areLongEdgesHidden() {
    return myLogDataHolder.getDataPack().getPrintCellModel().areLongEdgesHidden();
  }

  public void over(@Nullable GraphElement graphElement) {
    SelectController selectController = myLogDataHolder.getDataPack().getPrintCellModel().getSelectController();
    FragmentManager fragmentManager = myLogDataHolder.getDataPack().getGraphModel().getFragmentManager();
    if (graphElement == prevGraphElement) {
      return;
    }
    else {
      prevGraphElement = graphElement;
    }
    selectController.deselectAll();
    if (graphElement == null) {
      updateUI();
    }
    else {
      GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
      selectController.select(graphFragment);
      updateUI();
    }
  }

  public void click(@Nullable GraphElement graphElement) {
    SelectController selectController = myLogDataHolder.getDataPack().getPrintCellModel().getSelectController();
    FragmentManager fragmentController = myLogDataHolder.getDataPack().getGraphModel().getFragmentManager();
    selectController.deselectAll();
    if (graphElement == null) {
      return;
    }
    GraphFragment fragment = fragmentController.relateFragment(graphElement);
    if (fragment == null) {
      return;
    }
    UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
    updateUI();
    jumpToRow(updateRequest.from());
  }

  public void click(int rowIndex) {
    DataPack dataPack = myLogDataHolder.getDataPack();
    dataPack.getPrintCellModel().getCommitSelectController().deselectAll();
    Node node = dataPack.getNode(rowIndex);
    if (node != null) {
      FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
      dataPack.getPrintCellModel().getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
    }
    updateUI();
  }

  public void jumpToCommit(final Hash commitHash) {
    int row = myLogDataHolder.getDataPack().getRowByHash(commitHash);
    if (row != -1) {
      jumpToRow(row);
    }
    else if (myLogDataHolder.isFullLogReady()) {
      myLogDataHolder.showFullLog(new Runnable() {
        @Override
        public void run() {
          jumpToCommit(commitHash);
        }
      });
    }
    else {
      LOG.info("No row for hash " + commitHash);
    }
  }

  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }
}
