/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class GenericProgramRunner<Settings extends JDOMExternalizable> implements ProgramRunner<Settings> {

  @Deprecated
  public static final DataKey<RunContentDescriptor> CONTENT_TO_REUSE_DATA_KEY = DataKey.create("contentToReuse");
  @Deprecated @NonNls public static final String CONTENT_TO_REUSE = CONTENT_TO_REUSE_DATA_KEY.getName();

  @Nullable
  public Settings createConfigurationData(final ConfigurationInfoProvider settingsProvider) {
    return null;
  }

  public void checkConfiguration(final RunnerSettings settings, final ConfigurationPerRunnerSettings configurationPerRunnerSettings)
    throws RuntimeConfigurationException {
  }

  public void onProcessStarted(final RunnerSettings settings, final ExecutionResult executionResult) {
  }

  public AnAction[] createActions(final ExecutionResult executionResult) {
    return AnAction.EMPTY_ARRAY;
  }

  @Nullable
  public SettingsEditor<Settings> getSettingsEditor(final Executor executor, final RunConfiguration configuration) {
    return null;
  }

  public void execute(@NotNull final Executor executor, @NotNull final ExecutionEnvironment environment) throws ExecutionException {
    execute(executor, environment, null);
  }

  public void execute(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env, @Nullable final Callback callback)
      throws ExecutionException {

    final Project project = env.getProject();
    if (project == null) {
      return;
    }

    final RunProfileState state = env.getState(executor);
    if (state == null) {
      return;
    }

    RunnerSettings runnerSettings = env.getRunnerSettings();
    if (runnerSettings != null) {
      RunManager.getInstance(project).refreshUsagesList(runnerSettings.getRunProfile());
    }

    ExecutionManager.getInstance(project).startRunProfile(new RunProfileStarter() {
      @Override
      public RunContentDescriptor execute(@NotNull Project project,
                                          @NotNull Executor executor,
                                          @NotNull RunProfileState state,
                                          @Nullable RunContentDescriptor contentToReuse,
                                          @NotNull ExecutionEnvironment env) throws ExecutionException {
        final RunContentDescriptor descriptor = doExecute(project, executor, state, contentToReuse, env);
        if (callback != null) callback.processStarted(descriptor);
        return descriptor;
      }
    }, state, project, executor, env);
  }

  @Nullable
  protected abstract RunContentDescriptor doExecute(final Project project, final Executor executor, final RunProfileState state,
                                        final RunContentDescriptor contentToReuse,
                                        final ExecutionEnvironment env) throws ExecutionException;

}
