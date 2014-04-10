package com.intellij.vcs.log.graph.mutable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.mutable.elements.MutableNode;
import com.intellij.vcs.log.graph.mutable.elements.MutableNodeRow;
import com.intellij.vcs.log.graph.mutable.elements.UsualEdge;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.graph.elements.Node.NodeType.COMMIT_NODE;
import static com.intellij.vcs.log.graph.elements.Node.NodeType.END_COMMIT_NODE;

/**
 * @author erokhins
 */
public class GraphBuilder {

  private static final Logger LOG = Logger.getInstance(GraphBuilder.class);

  @NotNull
  public static MutableGraph build(@NotNull List<? extends GraphCommit> commitParentses, Collection<VcsRef> allRefs) {
    GraphBuilder builder = new GraphBuilder(allRefs);
    return builder.runBuild(commitParentses);
  }

  // local package
  static void createUsualEdge(@NotNull MutableNode up, @NotNull MutableNode down, @NotNull Branch branch) {
    UsualEdge edge = new UsualEdge(up, down, branch);
    up.getInnerDownEdges().add(edge);
    down.getInnerUpEdges().add(edge);
  }

  private final MutableGraph graph;
  private final Map<Integer, MutableNode> underdoneNodes;
  private MultiMap<Integer, VcsRef> myRefsOfHashes;

  private MutableNodeRow nextRow;

  public GraphBuilder(MutableGraph graph, Map<Integer, MutableNode> underdoneNodes, MutableNodeRow nextRow, Collection<VcsRef> refs) {
    this.graph = graph;
    this.underdoneNodes = underdoneNodes;
    this.nextRow = nextRow;

    myRefsOfHashes = prepareRefsMap(refs);
  }

  @NotNull
  private static MultiMap<Integer, VcsRef> prepareRefsMap(@NotNull Collection<VcsRef> refs) {
    MultiMap<Integer, VcsRef> map = MultiMap.create();
    for (VcsRef ref : refs) {
      map.putValue(ref.getCommitIndex(), ref);
    }
    return map;
  }

  public GraphBuilder(MutableGraph graph, Collection<VcsRef> refs) {
    this(graph, new HashMap<Integer, MutableNode>(), new MutableNodeRow(graph, 0), refs);
  }

  public GraphBuilder(Collection<VcsRef> refs) {
    this(new MutableGraph(), refs);
  }


  @NotNull
  private Collection<VcsRef> findRefForHash(int hash) {
    return myRefsOfHashes.get(hash);
  }

  private MutableNode addCurrentCommitAndFinishRow(int commitHash) {
    MutableNode node = underdoneNodes.remove(commitHash);
    if (node == null) {
      Collection<VcsRef> refs = findRefForHash(commitHash);
      node = createNode(commitHash, createBranch(commitHash, refs));
    }
    node.setType(COMMIT_NODE);
    node.setNodeRow(nextRow);

    nextRow.getInnerNodeList().add(node);
    graph.getAllRows().add(nextRow);
    nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
    return node;
  }

  @NotNull
  protected Branch createBranch(int commitHash, @NotNull Collection<VcsRef> refs) {
    int oneOfHeads;
    if (refs.isEmpty()) {
      // should never happen, but fallback gently.
      LOG.error("Ref should exist for this node. Hash: " + commitHash);
      oneOfHeads = -1;
    }
    else {
      oneOfHeads = refs.iterator().next().getCommitIndex();
    }
    return new Branch(commitHash, refs, oneOfHeads);
  }

  private void addParent(MutableNode node, int parentHash, Branch branch) {
    MutableNode parentNode = underdoneNodes.remove(parentHash);
    if (parentNode == null) {
      parentNode = createNode(parentHash, branch);
    }
    createUsualEdge(node, parentNode, branch);
    underdoneNodes.put(parentHash, parentNode);
  }

  private static MutableNode createNode(int hash, Branch branch) {
    return new MutableNode(branch, hash);
  }

  private void append(@NotNull GraphCommit commit) {
    MutableNode node = addCurrentCommitAndFinishRow(commit.getIndex());

    int[] parents = commit.getParentIndices();
    Branch branch = node.getBranch();
    if (parents.length == 1) {
      addParent(node, parents[0], branch);
    }
    else {
      for (int parentHash : parents) {
        Collection<VcsRef> refs = findRefForHash(node.getCommitIndex());
        addParent(node, parentHash, new Branch(node.getCommitIndex(), parentHash, refs, branch.getOneOfHeads()));
      }
    }
  }


  private void lastActions() {
    Set<Integer> notReadiedCommitHashes = underdoneNodes.keySet();
    for (Integer hash : notReadiedCommitHashes) {
      MutableNode underdoneNode = underdoneNodes.get(hash);
      underdoneNode.setNodeRow(nextRow);
      underdoneNode.setType(END_COMMIT_NODE);
      nextRow.getInnerNodeList().add(underdoneNode);
    }
    if (!nextRow.getInnerNodeList().isEmpty()) {
      graph.getAllRows().add(nextRow);
    }
  }

  // local package
  @NotNull
  public MutableGraph runBuild(@NotNull List<? extends GraphCommit> commitParentses) {
    for (GraphCommit vcsCommit : commitParentses) {
      append(vcsCommit);
    }
    lastActions();
    graph.updateVisibleRows();
    return graph;
  }

}
