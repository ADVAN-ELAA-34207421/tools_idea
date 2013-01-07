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
package org.zmlx.hg4idea;

import com.google.common.base.Objects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class HgFileRevision implements VcsFileRevision {

  private final Project project;
  @NotNull private final HgFile hgFile;
  @NotNull private final HgRevisionNumber vcsRevisionNumber;
  private final String branchName;
  private final Date revisionDate;
  private final String author;
  private final String commitMessage;
  private final Set<String> filesModified;
  private final Set<String> filesAdded;
  private final Set<String> filesDeleted;
  private Map<String,String> filesCopied;

  public HgFileRevision(Project project, @NotNull HgFile hgFile, @NotNull HgRevisionNumber vcsRevisionNumber,
    String branchName, Date revisionDate, String author, String commitMessage,
    Set<String> filesModified, Set<String> filesAdded, Set<String> filesDeleted, Map<String, String> filesCopied) {
    this.project = project;
    this.hgFile = hgFile;
    this.vcsRevisionNumber = vcsRevisionNumber;
    this.branchName = branchName;
    this.revisionDate = revisionDate;
    this.author = author;
    this.commitMessage = commitMessage;
    this.filesModified = filesModified;
    this.filesAdded = filesAdded;
    this.filesDeleted = filesDeleted;
    this.filesCopied = filesCopied;
  }

  public HgRevisionNumber getRevisionNumber() {
    return vcsRevisionNumber;
  }

  public String getBranchName() {
    return branchName;
  }

  @Override
  public RepositoryLocation getChangedRepositoryPath() {
    return null;
  }

  public Date getRevisionDate() {
    return revisionDate;
  }

  public String getAuthor() {
    return author;
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public Set<String> getModifiedFiles() {
    return filesModified;
  }

  public Set<String> getAddedFiles() {
    return filesAdded;
  }

  public Set<String> getDeletedFiles() {
    return filesDeleted;
  }

  public Map<String, String> getCopiedFiles() {
    return filesCopied;
  }

  public byte[] loadContent() throws IOException, VcsException {
    try {
      Charset charset = hgFile.toFilePath().getCharset();

      HgFile fileToCat = HgUtil.getFileNameInTargetRevision(project, vcsRevisionNumber, hgFile);
      String result = new HgCatCommand(project).execute(fileToCat, vcsRevisionNumber, charset);
      if (result == null) {
        return new byte[0];
      } else {
        return result.getBytes(charset.name());
      }
    } catch (UnsupportedEncodingException e) {
      throw new VcsException(e);
    }
  }

  public byte[] getContent() throws IOException, VcsException {
    return ContentRevisionCache.getOrLoadAsBytes(project, hgFile.toFilePath(), getRevisionNumber(), HgVcs.getKey(),
                                                 ContentRevisionCache.UniqueType.REPOSITORY_CONTENT,
                                                 new Throwable2Computable<byte[], VcsException, IOException>() {
                                                   @Override
                                                   public byte[] compute() throws VcsException, IOException {
                                                     return loadContent();
                                                   }
                                                 });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HgFileRevision revision = (HgFileRevision)o;

    if (!hgFile.equals(revision.hgFile)) {
      return false;
    }
    if (!vcsRevisionNumber.equals(revision.vcsRevisionNumber)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(hgFile, vcsRevisionNumber);
  }
}
