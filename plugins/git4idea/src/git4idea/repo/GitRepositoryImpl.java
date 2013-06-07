/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.branch.GitBranchesCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Kirill Likhodedov
 */
public class GitRepositoryImpl extends RepositoryImpl implements GitRepository, Disposable {

  @NotNull private final GitPlatformFacade myPlatformFacade;
  @NotNull private final GitRepositoryReader myReader;
  @NotNull private final VirtualFile myGitDir;
  @Nullable private final GitUntrackedFilesHolder myUntrackedFilesHolder;

  @Nullable private volatile GitLocalBranch myCurrentBranch;
  @NotNull private volatile GitBranchesCollection myBranches = GitBranchesCollection.EMPTY;
  @NotNull private volatile Collection<GitRemote> myRemotes = Collections.emptyList();
  @NotNull private volatile Collection<GitBranchTrackInfo> myBranchTrackInfos;

  /**
   * Get the GitRepository instance from the {@link GitRepositoryManager}.
   * If you need to have an instance of GitRepository for a repository outside the project, use
   * {@link #getLightInstance(VirtualFile, Project, git4idea.GitPlatformFacade, Disposable)}
   */
  @SuppressWarnings("ConstantConditions")
  protected GitRepositoryImpl(@NotNull VirtualFile rootDir, @NotNull GitPlatformFacade facade, @NotNull Project project,
                              @NotNull Disposable parentDisposable, final boolean light) {
    super(project, rootDir, parentDisposable);
    myPlatformFacade = facade;
    myGitDir = GitUtil.findGitDir(rootDir);
    assert myGitDir != null : ".git directory wasn't found under " + rootDir.getPresentableUrl();
    myReader = new GitRepositoryReader(VfsUtilCore.virtualToIoFile(myGitDir));
    if (!light) {
      myUntrackedFilesHolder = new GitUntrackedFilesHolder(this);
      Disposer.register(this, myUntrackedFilesHolder);
    }
    else {
      myUntrackedFilesHolder = null;
    }
    update();
  }

  /**
   * Returns the temporary light instance of GitRepository.
   * It lacks functionality of auto-updating GitRepository on Git internal files change, and also stored a stub instance of
   * {@link GitUntrackedFilesHolder}.
   */
  @NotNull
  public static GitRepository getLightInstance(@NotNull VirtualFile root, @NotNull Project project, @NotNull GitPlatformFacade facade,
                                               @NotNull Disposable parentDisposable) {
    return new GitRepositoryImpl(root, facade, project, parentDisposable, true);
  }

  /**
   * Returns the full-functional instance of GitRepository - with UntrackedFilesHolder and GitRepositoryUpdater.
   * This is used for repositories registered in project, and should be optained via {@link GitRepositoryManager}.
   */
  @NotNull
  public static GitRepository getFullInstance(@NotNull VirtualFile root, @NotNull Project project, @NotNull GitPlatformFacade facade,
                                              @NotNull Disposable parentDisposable) {
    GitRepositoryImpl repository = new GitRepositoryImpl(root, facade, project, parentDisposable, false);
    repository.myUntrackedFilesHolder.setupVfsListener(project);  //myUntrackedFilesHolder cannot be null because it is not light instance
    repository.setupUpdater();
    return repository;
  }

  private void setupUpdater() {
    GitRepositoryUpdater updater = new GitRepositoryUpdater(this);
    Disposer.register(this, updater);
  }


  @NotNull
  @Override
  public VirtualFile getGitDir() {
    return myGitDir;
  }

  @Override
  @NotNull
  public GitUntrackedFilesHolder getUntrackedFilesHolder() {
    if (myUntrackedFilesHolder == null) {
      throw new IllegalStateException("Using untracked files holder with light git repository instance " + this);
    }
    return myUntrackedFilesHolder;
  }

  @Override
  @Nullable
  public GitLocalBranch getCurrentBranch() {
    return myCurrentBranch;
  }

  /**
   * @return local and remote branches in this repository.
   */
  @Override
  @NotNull
  public GitBranchesCollection getBranches() {
    return new GitBranchesCollection(myBranches);
  }

  @Override
  @NotNull
  public Collection<GitRemote> getRemotes() {
    return myRemotes;
  }

  @Override
  @NotNull
  public Collection<GitBranchTrackInfo> getBranchTrackInfos() {
    return myBranchTrackInfos;
  }

  @Override
  public boolean isRebaseInProgress() {
    return getState() == State.REBASING;
  }

  @Override
  public boolean isOnBranch() {
    return getState() != State.DETACHED && getState() != State.REBASING;
  }

  @Override
  public boolean isFresh() {
    return getCurrentRevision() == null;
  }

  @Override
  public void update() {
    File configFile = new File(VfsUtilCore.virtualToIoFile(myGitDir), "config");
    GitConfig config = GitConfig.read(myPlatformFacade, configFile);
    myRemotes = config.parseRemotes();
    readRepository(myRemotes);
    myBranchTrackInfos = config.parseTrackInfos(myBranches.getLocalBranches(), myBranches.getRemoteBranches());
    if (!Disposer.isDisposed(getProject()) && getMessageBus() != null) {
      getMessageBus().syncPublisher(GIT_REPO_CHANGE).repositoryChanged(this);
    }
  }

  private void readRepository(@NotNull Collection<GitRemote> remotes) {
    myState = myReader.readState();
    myCurrentRevision = myReader.readCurrentRevision();
    myCurrentBranch = myReader.readCurrentBranch();
    myBranches = myReader.readBranches(remotes);
  }

  @Override
  public String toLogString() {
    return String.format("GitRepository{myCurrentBranch=%s, myCurrentRevision='%s', myState=%s, myRootDir=%s}",
                         myCurrentBranch, myCurrentRevision, myState, getRoot());
  }
}
