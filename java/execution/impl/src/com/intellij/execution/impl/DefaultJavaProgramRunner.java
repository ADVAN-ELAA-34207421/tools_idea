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
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.*;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author spleaner
 */
public class DefaultJavaProgramRunner extends JavaPatchableProgramRunner {
  @Override
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) &&
           profile instanceof ModuleRunProfile &&
           !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction);
  }

  @Override
  public void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException {
    runCustomPatchers(javaParameters, settings, Executor.EXECUTOR_EXTENSION_NAME.findExtension(DefaultRunExecutor.class));
  }

  @Override
  protected RunContentDescriptor doExecute(final Project project, final Executor executor, final RunProfileState state, final RunContentDescriptor contentToReuse,
                                           final ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    boolean shouldAddDefaultActions = true;
    if (state instanceof JavaCommandLine) {
      final JavaParameters parameters = ((JavaCommandLine)state).getJavaParameters();
      patch(parameters, state.getRunnerSettings(), true);
      final ProcessProxy proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine)state);
      executionResult = state.execute(executor, this);
      if (proxy != null && executionResult != null) {
        proxy.attach(executionResult.getProcessHandler());
      }
      if (state instanceof JavaCommandLineState && !((JavaCommandLineState)state).shouldAddJavaProgramRunnerActions()) {
        shouldAddDefaultActions = false;
      }
    }
    else {
      executionResult = state.execute(executor, this);
    }

    if (executionResult == null) {
      return null;
    }

    onProcessStarted(env.getRunnerSettings(), executionResult);

    final RunContentBuilder contentBuilder = new RunContentBuilder(project, this, executor, executionResult, env);
    Disposer.register(project, contentBuilder);
    if (shouldAddDefaultActions) {
      addDefaultActions(contentBuilder);
    }

    RunContentDescriptor runContent = contentBuilder.showRunContent(contentToReuse);

    AnAction[] actions = createActions(contentBuilder.getExecutionResult());

    for (AnAction action : actions) {
      contentBuilder.addAction(action);
    }

    return runContent;
  }

  protected static void addDefaultActions(final RunContentBuilder contentBuilder) {
    final ExecutionResult executionResult = contentBuilder.getExecutionResult();
    final ExecutionConsole executionConsole = executionResult.getExecutionConsole();
    final JComponent consoleComponent = executionConsole != null ? executionConsole.getComponent() : null;
    final ControlBreakAction controlBreakAction = new ControlBreakAction(contentBuilder.getProcessHandler());
    if (consoleComponent != null) {
      controlBreakAction.registerCustomShortcutSet(controlBreakAction.getShortcutSet(), consoleComponent);
      final ProcessHandler processHandler = executionResult.getProcessHandler();
      assert processHandler != null : executionResult;
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          processHandler.removeProcessListener(this);
          controlBreakAction.unregisterCustomShortcutSet(consoleComponent);
        }
      });
    }
    contentBuilder.addAction(controlBreakAction);
    contentBuilder.addAction(new SoftExitAction(contentBuilder.getProcessHandler()));
  }


  private abstract static class LauncherBasedAction extends AnAction {
    protected final ProcessHandler myProcessHandler;

    protected LauncherBasedAction(String text, String description, Icon icon, ProcessHandler processHandler) {
      super(text, description, icon);
      myProcessHandler = processHandler;
    }

    @Override
    public void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      if (!isVisible()) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }
      presentation.setVisible(true);
      presentation.setEnabled(!myProcessHandler.isProcessTerminated());
    }

    protected boolean isVisible() {
      return ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler) != null;
    }
  }

  protected static class ControlBreakAction extends LauncherBasedAction {
    public ControlBreakAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.dump.threads.action.name"), null, AllIcons.Actions.Dump,
            processHandler);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_CANCEL, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean isVisible() {
      return super.isVisible() && ProcessProxyFactory.getInstance().isBreakGenLibraryAvailable();
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      if (proxy != null) {
        proxy.sendBreak();
      }
    }
  }

  protected static class SoftExitAction extends LauncherBasedAction {
    public SoftExitAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.exit.action.name"), null, AllIcons.Actions.Exit, processHandler);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      if (proxy != null) {
        proxy.sendStop();
      }
    }
  }

  @Override
  @NotNull
  public String getRunnerId() {
    return "Run";
  }
}
