package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.printmodel.GraphPrintCell;

import java.util.List;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {

  public enum Kind {
    NORMAL,
    PICK,
    FIXUP,
    REWORD,
    APPLIED
  }

  private final GraphPrintCell row;
  private final Kind kind;

  public GraphCommitCell(GraphPrintCell row, Kind kind, String text, List<VcsRef> refsToThisCommit) {
    super(text, refsToThisCommit);
    this.kind = kind;
    this.row = row;
  }

  public GraphPrintCell getPrintCell() {
    return row;
  }


  public Kind getKind() {
    return kind;
  }
}
