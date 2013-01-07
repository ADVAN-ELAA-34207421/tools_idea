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
package git4idea.cherrypick

import com.intellij.notification.NotificationType

import git4idea.history.browser.GitCommit
import org.junit.Before
import org.junit.Test

import static git4idea.test.MockGit.*
import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import static git4idea.test.MockGit.OperationName.GET_UNMERGED_FILES
import static junit.framework.Assert.assertTrue
import git4idea.history.browser.GitCherryPicker

/**
 * Cherry-pick of one or multiple commits with "commit at once" option enabled.
 *
 * @author Kirill Likhodedov
 */
class GitAutoCommittingCherryPickTest extends GitCherryPickTest {

  public static final String EMPTY_CHERRY_PICK = """
# On branch master
# Your branch is ahead of 'origin/master' by 11 commits.
#
# Untracked files:
#   (use "git add <file>..." to include in what will be committed)
#
#\t.idea/
#\tlocal_staged.patch
#\tout/
nothing added to commit but untracked files present (use "git add" to track)
The previous cherry-pick is now empty, possibly due to conflict resolution.
If you wish to commit it anyway, use:

    git commit --allow-empty

Otherwise, please use 'git reset'
"""

  @Before
  void setUp() {
    super.setUp()
    myCherryPicker = new GitCherryPicker(myProject, myGit, myPlatformFacade, true)
  }

  @Test
  void "clean tree, no conflicts, then commit & notify, no new changelists"() {
    GitCommit commit = commit()

    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit))
    invokeCherryPick(commit)

    assertHeadCommit(commit)
    assertOnlyDefaultChangelist()
    assertNotificationShown("Cherry-pick successful", commitDetails(commit), NotificationType.INFORMATION)
  }

  @Test
  void "dirty tree, conflicting with commit, then show error"() {
    myGit.registerOperationExecutors(new SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK))

    def commit = commit()
    invokeCherryPick(commit)

    assertNotCherryPicked()
    assertOnlyDefaultChangelist()
    assertNotificationShown("Cherry-pick failed",
                            """
                            ${commitDetails(commit)}<br/>
                            Your local changes would be overwritten by cherry-pick.<br/>
                            Commit your changes or stash them to proceed.
                            """,
                            NotificationType.ERROR)
  }

  @Test
  void "untracked files, conflicting with commit, then show error"() {
    myGit.registerOperationExecutors(new SimpleErrorOperationExecutor(CHERRY_PICK, UNTRACKED_FILES_OVERWRITTEN_BY_CHERRY_PICK))

    def commit = commit()
    invokeCherryPick(commit)

    assertNotCherryPicked()
    assertOnlyDefaultChangelist()
    assertNotificationShown("Cherry-pick error",
                            """
                            ${commitDetails(commit)}<br/>
                            Some untracked working tree files would be overwritten by cherry-pick.<br/>
                            Please move, remove or add them before you can cherry-pick. <a href='view'>View them</a>
                            """,
                            NotificationType.ERROR)
  }

  @Test
  void "conflict, merge dialog, not all merged, then new & active changelist, notification"() {
    prepareConflict()
    myGit.registerOperationExecutors(new SimpleSuccessOperationExecutor(GET_UNMERGED_FILES, UNMERGED_FILE))
    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertChangeLists([DEFAULT, newCommitMessage(commit)], newCommitMessage(commit))
    assertNotificationShown "Cherry-picked with conflicts",
                            """
                            ${commitDetails(commit)}<br/>
                            Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>
                            """,
                            NotificationType.WARNING
  }

  @Test
  void "conflict, merge completed, then commit dialog"() {
    prepareConflict()
    GitCommit commit = commit()

    OKCommitDialogHandler handler = new OKCommitDialogHandler(myRepository)
    myVcsHelper.registerHandler(handler)

    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
  }

  @Test
  void "conflict, merge finished, commit succeeded, no new changelists"() {
    prepareConflict()

    OKCommitDialogHandler handler = new OKCommitDialogHandler(myRepository)
    myVcsHelper.registerHandler(handler)

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertHeadCommit(commit)
    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
    assertOnlyDefaultChangelist()
  }

  @Test
  void "conflict, merge ok, commit cancelled, then new & active changelist"() {
    prepareConflict()

    CancelCommitDialogHandler handler = new CancelCommitDialogHandler()
    myVcsHelper.registerHandler(handler)

    GitCommit commit = commit()
    invokeCherryPick(commit)
    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
    assertChangeLists([DEFAULT, newCommitMessage(commit)], newCommitMessage(commit))
  }

  @Test
  void "2 commits, no problems, then commit all & notify"() {
    GitCommit commit1 = commit("First commit to cherry-pick")
    GitCommit commit2 = commit("Second commit to cherry-pick")
    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1),
                                     new SuccessfulCherryPickExecutor(myRepository, commit2))

    invokeCherryPick([commit1, commit2])
    assertLastCommits commit2, commit1
    assertNotificationShown("Cherry-pick successful", notificationContent(commit1, commit2), NotificationType.INFORMATION)
  }

  @Test
  void "3 commits, 2nd cherry-pick conflicts with local, then 1st success, 2nd stop & error"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1),
                                     new SimpleErrorOperationExecutor(CHERRY_PICK, LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK),
                                     new SuccessfulCherryPickExecutor(myRepository, commit3))

    invokeCherryPick([commit1, commit2, commit3])

    assertHeadCommit(commit1)
    assertNotificationShown("Cherry-pick failed",
                            """
                            ${commitDetails(commit2)}<br/>
                            Your local changes would be overwritten by cherry-pick.<br/>
                            Commit your changes or stash them to proceed.
                            <hr/>
                            However cherry-pick succeeded for the following commit:<br/>
                            ${commitDetails(commit1)}
                            """, NotificationType.ERROR)
  }

  @Test
  void "3 commits, 2nd conflicts with committed, then 1st success, on 2nd show merge dialog"() {
    GitCommit commit1 = commit("First")
    GitCommit commit2 = commit("Second")
    GitCommit commit3 = commit("Third")

    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1))
    prepareConflict()
    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit3))

    OKCommitDialogHandler handler = new OKCommitDialogHandler(myRepository)
    myVcsHelper.registerHandler(handler)
    invokeCherryPick([commit1, commit2, commit3])

    assertMergeDialogShown()
    assertTrue "Commit dialog was not shown", handler.wasCommitDialogShown()
    assertLastCommits commit3, commit2, commit1
  }

//  @Test
  void "Notify if changes have already been applied"() {
    // Inspired by IDEA-73548
    myGit.registerOperationExecutors(new SimpleErrorOperationExecutor(CHERRY_PICK, EMPTY_CHERRY_PICK))

    GitCommit commit = commit()
    invokeCherryPick(commit)

    assertNotCherryPicked()
    assertNotificationShown("Nothing to cherry-pick", "All changes from ${commitDetails(commit)} have already been applied",
                            NotificationType.WARNING)
  }

//  @Test
  void "1st successful, 2nd empty (all applied), then compound notification"() {
    // Inspired by IDEA-73548
    GitCommit commit1 = commit()
    GitCommit commit2 = commit()
    myGit.registerOperationExecutors(new SuccessfulCherryPickExecutor(myRepository, commit1),
                                     new SimpleErrorOperationExecutor(CHERRY_PICK, EMPTY_CHERRY_PICK))

    invokeCherryPick([ commit1, commit2 ])

    assertHeadCommit(commit1)
    assertNotificationShown("Cherry-picked with problems",
"""Successfully cherry-picked ${commitDetails(commit1)}<br/>
Not cherry-picked ${commitDetails(commit2)} - all changes have already been applied""", NotificationType.WARNING)
  }

}
