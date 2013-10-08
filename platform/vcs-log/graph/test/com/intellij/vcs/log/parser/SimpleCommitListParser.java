package com.intellij.vcs.log.parser;

import com.intellij.vcs.log.VcsCommit;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleCommitListParser {
  public static List<VcsCommit> parseCommitList(@NotNull String input) {
    SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(input));
    try {
      return parser.readAllCommits();
    }
    catch (IOException e) {
      throw new IllegalStateException();
    }
  }

  private final BufferedReader bufferedReader;

  public SimpleCommitListParser(StringReader bufferedReader) {
    this.bufferedReader = new BufferedReader(bufferedReader);
  }

  public List<VcsCommit> readAllCommits() throws IOException {
    String line;
    List<VcsCommit> vcsCommitParentses = new ArrayList<VcsCommit>();
    while ((line = bufferedReader.readLine()) != null) {
      vcsCommitParentses.add(CommitParser.parseCommitParents(line));
    }
    return vcsCommitParentses;
  }

}
