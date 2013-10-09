package com.intellij.vcs.log.parser;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitParser {

  private static int nextSeparatorIndex(@NotNull String line, int startIndex) {
    int nextIndex = line.indexOf("|-", startIndex);
    if (nextIndex == -1) {
      throw new IllegalArgumentException("not found separator \"|-\", with startIndex=" + startIndex +
                                         ", in line: " + line);
    }
    return nextIndex;
  }

  /**
   * @param line input format:
   *             ab123|-adada 193 352
   *             123|-             // no parent
   */
  @NotNull
  public static VcsCommit parseCommitParents(@NotNull String line) {
    int separatorIndex = nextSeparatorIndex(line, 0);
    String commitHashStr = line.substring(0, separatorIndex);
    Hash commitHash = createHash(commitHashStr);

    String parentHashStr = line.substring(separatorIndex + 2, line.length());
    String[] parentsHashes = parentHashStr.split("\\s");
    List<Hash> hashes = new ArrayList<Hash>(parentsHashes.length);
    for (String aParentsStr : parentsHashes) {
      if (aParentsStr.length() > 0) {
        hashes.add(createHash(aParentsStr));
      }
    }
    return new SimpleCommit(commitHash, hashes, -1);
  }

  /**
   * @param line 1231423|-adada|-193 adf45
   *             timestamp|-hash commit|-parent hashes
   */
  @NotNull
  public static TimedVcsCommit parseTimestampParentHashes(@NotNull String line) {
    int firstSeparatorIndex = nextSeparatorIndex(line, 0);
    String timestampStr = line.substring(0, firstSeparatorIndex);
    long timestamp;
    try {
      if (timestampStr.isEmpty()) {
        timestamp = 0;
      }
      else {
        timestamp = Long.parseLong(timestampStr);
      }
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("bad timestamp in line: " + line);
    }
    VcsCommit vcsCommit = parseCommitParents(line.substring(firstSeparatorIndex + 2));

    return new SimpleCommit(vcsCommit.getHash(), vcsCommit.getParents(), timestamp);
  }

  @NotNull
  public static List<TimedVcsCommit> log(@NotNull String... commits) {
    return ContainerUtil.map(Arrays.asList(commits), new Function<String, TimedVcsCommit>() {
      @Override
      public TimedVcsCommit fun(String commit) {
        return parseTimestampParentHashes(commit);
      }
    });
  }

  @NotNull
  private static Hash createHash(@NotNull String s) {
    return new SimpleHash(s);
  }

}
