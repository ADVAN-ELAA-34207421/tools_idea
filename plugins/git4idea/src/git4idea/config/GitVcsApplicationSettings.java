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
package git4idea.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * The application wide settings for the git
 */
@State(
  name = "Git.Application.Settings",
  storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/vcs.xml")})
public class GitVcsApplicationSettings implements PersistentStateComponent<GitVcsApplicationSettings.State> {

  @NonNls private static final String[] DEFAULT_WINDOWS_PATHS = { "C:\\Program Files\\Git\\bin",
                                                                  "C:\\Program Files (x86)\\Git\\bin",
                                                                  "C:\\cygwin\\bin" };
  @NonNls private static final String[] DEFAULT_UNIX_PATHS = { "/usr/local/bin",
                                                               "/usr/bin",
                                                               "/opt/local/bin",
                                                               "/opt/bin",
                                                               "/usr/local/git/bin" };
  @NonNls private static final String[] DEFAULT_WINDOWS_GITS = { "git.cmd", "git.exe" };
  @NonNls private static final String DEFAULT_UNIX_GIT = "git";
  
  private State myState = new State();

  /**
   * Kinds of SSH executable to be used with the git
   */
  public enum SshExecutable {
    IDEA_SSH,
    NATIVE_SSH,
  }

  public static class State {
    public String myPathToGit = null;
    public SshExecutable SSH_EXECUTABLE = null;
  }

  public static GitVcsApplicationSettings getInstance() {
    return ServiceManager.getService(GitVcsApplicationSettings.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  /**
   * @return the default executable name depending on the platform
   */
  @NotNull
  public String defaultGit() {
    if (myState.myPathToGit == null) {
      String[] paths;
      String[] programVariants;
      if (SystemInfo.isWindows) {
        programVariants = DEFAULT_WINDOWS_GITS;
        paths = DEFAULT_WINDOWS_PATHS;
      }
      else {
        programVariants = new String[] { DEFAULT_UNIX_GIT };
        paths = DEFAULT_UNIX_PATHS;
      }

      for (String p : paths) {
        for (String program : programVariants) {
          File f = new File(p, program);
          if (f.exists()) {
            myState.myPathToGit = f.getAbsolutePath();
            break;
          }
        }
      }
      if (myState.myPathToGit == null) { // otherwise, take the first variant and hope it's in $PATH
        myState.myPathToGit = programVariants[0];
      }
    }
    return myState.myPathToGit;
  }

  @NotNull
  public String getPathToGit() {
    return myState.myPathToGit == null ? defaultGit() : myState.myPathToGit;
  }

  public void setPathToGit(String pathToGit) {
    myState.myPathToGit = pathToGit;
  }

  public void setIdeaSsh(@NotNull SshExecutable executable) {
    myState.SSH_EXECUTABLE = executable;
  }

  @Nullable
  SshExecutable getIdeaSsh() {
    return myState.SSH_EXECUTABLE;
  }

}
