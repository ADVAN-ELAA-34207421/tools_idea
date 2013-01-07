/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import com.intellij.xdebugger.XDebuggerBundle;
import icons.AndroidIcons;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.logcat.AndroidLogcatToolWindowFactory;
import org.jetbrains.android.logcat.AndroidLogcatView;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.List;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public class AndroidDebugRunner extends DefaultProgramRunner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidDebugRunner");

  public static final Key<AndroidSessionInfo> ANDROID_SESSION_INFO = new Key<AndroidSessionInfo>("ANDROID_SESSION_INFO");
  private static final Object myReaderLock = new Object();

  private static final Object myDebugLock = new Object();
  @NonNls private static final String ANDROID_DEBUG_SELECTED_TAB_PROPERTY = "ANDROID_DEBUG_SELECTED_TAB_";
  public static final String ANDROID_LOGCAT_CONTENT_ID = "Android Logcat";

  private static void tryToCloseOldSessions(final Executor executor, Project project) {
    final ExecutionManager manager = ExecutionManager.getInstance(project);
    ProcessHandler[] processes = manager.getRunningProcesses();
    for (ProcessHandler process : processes) {
      final AndroidSessionInfo info = process.getUserData(ANDROID_SESSION_INFO);
      if (info != null) {
        process.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(ProcessEvent event) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                manager.getContentManager().removeRunContent(executor, info.getDescriptor());
              }
            });
          }
        });
        process.detachProcess();
      }
    }
  }

  @Override
  protected RunContentDescriptor doExecute(final Project project,
                                           final Executor executor,
                                           final RunProfileState state,
                                           final RunContentDescriptor contentToReuse,
                                           final ExecutionEnvironment environment) throws ExecutionException {
    assert state instanceof AndroidRunningState;
    final AndroidRunningState runningState = (AndroidRunningState)state;
    final RunContentDescriptor[] descriptor = {null};

    runningState.addListener(new AndroidRunningStateListener() {
      @Override
      public void executionFailed() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (descriptor[0] != null) {
              showNotification(project, executor, descriptor[0], "error", false, NotificationType.ERROR);
            }
          }
        });
      }
    });
    descriptor[0] = doExec(project, executor, runningState, contentToReuse, environment);
    return descriptor[0];
  }

  private RunContentDescriptor doExec(Project project,
                                      Executor executor,
                                      AndroidRunningState state,
                                      RunContentDescriptor contentToReuse,
                                      ExecutionEnvironment environment) throws ExecutionException {
    if (DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
      final RunContentDescriptor descriptor = super.doExecute(project, executor, state, contentToReuse, environment);

      if (descriptor != null) {
        setActivateToolWindowWhenAddedProperty(project, executor, descriptor, "running");
      }
      return descriptor;
    }

    final RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof AndroidTestRunConfiguration) {
      String targetPackage = getTargetPackage((AndroidTestRunConfiguration)runProfile, state);
      if (targetPackage == null) {
        throw new ExecutionException(AndroidBundle.message("target.package.not.specified.error"));
      }
      state.setTargetPackageName(targetPackage);
    }
    state.setDebugMode(true);
    RunContentDescriptor runDescriptor;
    synchronized (myDebugLock) {
      MyDebugLauncher launcher = new MyDebugLauncher(project, executor, state, environment);
      state.setDebugLauncher(launcher);

      final RunContentDescriptor descriptor = embedToExistingSession(project, executor, state);
      runDescriptor = descriptor != null ? descriptor : super.doExecute(project, executor, state, contentToReuse, environment);
      launcher.setRunDescriptor(runDescriptor);
      if (descriptor != null) {
        return null;
      }
    }
    if (runDescriptor == null) {
      return null;
    }
    tryToCloseOldSessions(executor, project);
    final ProcessHandler handler = state.getProcessHandler();
    handler.putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(
      runDescriptor, state, executor.getId()));
    state.setRestarter(runDescriptor.getRestarter());
    setActivateToolWindowWhenAddedProperty(project, executor, runDescriptor, "running");
    return runDescriptor;
  }

  private static void setActivateToolWindowWhenAddedProperty(Project project,
                                                             Executor executor,
                                                             RunContentDescriptor descriptor,
                                                             String status) {
    final boolean activateToolWindow = shouldActivateExecWindow(project);
    descriptor.setActivateToolWindowWhenAdded(activateToolWindow);

    if (!activateToolWindow) {
      showNotification(project, executor, descriptor, status, false, NotificationType.INFORMATION);
    }
  }

  private static boolean shouldActivateExecWindow(Project project) {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AndroidLogcatToolWindowFactory.TOOL_WINDOW_ID);
    return toolWindow == null || !toolWindow.isVisible();
  }

  @Nullable
  private static Pair<ProcessHandler, AndroidSessionInfo> findOldSession(Project project,
                                                                         Executor executor,
                                                                         AndroidRunConfigurationBase configuration) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      final AndroidSessionInfo info = handler.getUserData(ANDROID_SESSION_INFO);

      if (info != null &&
          info.getState().getConfiguration().equals(configuration) &&
          executor.getId().equals(info.getExecutorId())) {
        return Pair.create(handler, info);
      }
    }
    return null;
  }

  private static RunContentDescriptor embedToExistingSession(final Project project,
                                                             final Executor executor,
                                                             final AndroidRunningState state) throws ExecutionException {
    final Pair<ProcessHandler, AndroidSessionInfo> pair = findOldSession(project, executor, state.getConfiguration());
    final AndroidSessionInfo oldSessionInfo = pair != null ? pair.getSecond() : null;
    final ProcessHandler oldProcessHandler = pair != null ? pair.getFirst() : null;

    if (oldSessionInfo == null || oldProcessHandler == null) {
      return null;
    }
    final AndroidExecutionState oldState = oldSessionInfo.getState();
    final IDevice[] oldDevices = oldState.getDevices();
    final ConsoleView oldConsole = oldState.getConsoleView();

    if (oldDevices == null ||
        oldConsole == null ||
        oldDevices.length == 0 ||
        oldDevices.length > 1) {
      return null;
    }
    final Ref<List<IDevice>> devicesRef = Ref.create();

    final boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        devicesRef.set(state.getAllCompatibleDevices());
      }
    }, "Scanning available devices", false, project);

    if (!result) {
      return null;
    }
    final List<IDevice> devices = devicesRef.get();

    if (devices.size() == 0 ||
        devices.size() > 1 ||
        devices.get(0) != oldDevices[0]) {
      return null;
    }
    oldProcessHandler.detachProcess();
    state.setTargetDevices(devices.toArray(new IDevice[devices.size()]));
    state.setConsole(oldConsole);
    final RunContentDescriptor oldDescriptor = oldSessionInfo.getDescriptor();
    final DefaultDebugProcessHandler newProcessHandler = new DefaultDebugProcessHandler();
    oldDescriptor.setProcessHandler(newProcessHandler);
    state.setProcessHandler(newProcessHandler);
    newProcessHandler.startNotify();
    oldConsole.attachToProcess(newProcessHandler);
    AndroidProcessText.attach(newProcessHandler);
    newProcessHandler.notifyTextAvailable("The session was restarted\n", STDOUT);

    showNotification(project, executor, oldDescriptor, "running", false, NotificationType.INFORMATION);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        state.start();
      }
    });
    return oldDescriptor;
  }

  private static void showNotification(final Project project,
                                       final Executor executor,
                                       final RunContentDescriptor descriptor,
                                       final String status,
                                       final boolean notifySelectedContent,
                                       final NotificationType type) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final String sessionName = descriptor.getDisplayName();
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(executor.getToolWindowId());
        final Content content = descriptor.getAttachedContent();
        final String notificationMessage;
        if (content != null && content.isSelected() && toolWindow.isVisible()) {
          if (!notifySelectedContent) {
            return;
          }
          notificationMessage = "Session '" + sessionName + "': " + status;
        }
        else {
          notificationMessage = "Session <a href=''>'" + sessionName + "'</a>: " + status;
        }

        NotificationGroup.toolWindowGroup("Android Session Restarted", executor.getToolWindowId(), true)
          .createNotification("", notificationMessage,
                              type, new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                final RunContentManager contentManager = ExecutionManager.getInstance(project).getContentManager();

                for (RunContentDescriptor d : contentManager.getAllDescriptors()) {
                  if (d.equals(descriptor)) {
                    final Content content = d.getAttachedContent();
                    content.getManager().setSelectedContent(content);
                    toolWindow.activate(null, true, true);
                    break;
                  }
                }
              }
            }
          }).notify(project);
      }
    });
  }

  @Nullable
  private static String getTargetPackage(AndroidTestRunConfiguration configuration, AndroidRunningState state) {
    Manifest manifest = state.getFacet().getManifest();
    assert manifest != null;
    for (Instrumentation instrumentation : manifest.getInstrumentations()) {
      PsiClass c = instrumentation.getInstrumentationClass().getValue();
      String runner = configuration.INSTRUMENTATION_RUNNER_CLASS;
      if (c != null && (runner.length() == 0 || runner.equals(c.getQualifiedName()))) {
        String targetPackage = instrumentation.getTargetPackage().getValue();
        if (targetPackage != null) {
          return targetPackage;
        }
      }
    }
    return null;
  }

  private static class AndroidDebugState implements RemoteState, AndroidExecutionState {
    private final Project myProject;
    private final RemoteConnection myConnection;
    private final RunnerSettings myRunnerSettings;
    private final ConfigurationPerRunnerSettings myConfigurationSettings;
    private final AndroidRunningState myState;
    private final IDevice myDevice;

    private volatile ConsoleView myConsoleView;

    public AndroidDebugState(Project project,
                             RemoteConnection connection,
                             RunnerSettings runnerSettings,
                             ConfigurationPerRunnerSettings configurationSettings,
                             AndroidRunningState state,
                             IDevice device) {
      myProject = project;
      myConnection = connection;
      myRunnerSettings = runnerSettings;
      myConfigurationSettings = configurationSettings;
      myState = state;
      myDevice = device;
    }

    public RunnerSettings getRunnerSettings() {
      return myRunnerSettings;
    }

    public ConfigurationPerRunnerSettings getConfigurationSettings() {
      return myConfigurationSettings;
    }

    public ExecutionResult execute(final Executor executor, @NotNull final ProgramRunner runner) throws ExecutionException {
      RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
      myState.setProcessHandler(process);
      myConsoleView = myState.getConfiguration().attachConsole(myState, executor);
      final MyLogcatExecutionConsole console = new MyLogcatExecutionConsole(myProject, myDevice, process, myConsoleView,
                                                                            myState.getConfiguration().getType().getId());
      return new DefaultExecutionResult(console, process);
    }

    public RemoteConnection getRemoteConnection() {
      return myConnection;
    }

    @Override
    public IDevice[] getDevices() {
      return new IDevice[]{myDevice};
    }

    @Nullable
    @Override
    public ConsoleView getConsoleView() {
      return myConsoleView;
    }

    @NotNull
    @Override
    public AndroidRunConfigurationBase getConfiguration() {
      return myState.getConfiguration();
    }
  }

  @NotNull
  public String getRunnerId() {
    return "AndroidDebugRunner";
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) || DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) &&
           profile instanceof AndroidRunConfigurationBase;
  }

  private static class MyLogcatExecutionConsole implements ExecutionConsoleEx, ObservableConsoleView {
    private final Project myProject;
    private final AndroidLogcatView myToolWindowView;
    private final ConsoleView myConsoleView;
    private final String myConfigurationId;

    private MyLogcatExecutionConsole(Project project,
                                     IDevice device,
                                     RemoteDebugProcessHandler process,
                                     ConsoleView consoleView,
                                     String configurationId) {
      myProject = project;
      myConsoleView = consoleView;
      myConfigurationId = configurationId;
      myToolWindowView = new AndroidLogcatView(project, device, true) {
        @Override
        protected boolean isActive() {
          final DebuggerSessionTab sessionTab = DebuggerPanelsManager.getInstance(myProject).getSessionTab();
          if (sessionTab == null) {
            return false;
          }
          final Content content = sessionTab.getUi().findContent(ANDROID_LOGCAT_CONTENT_ID);
          return content != null && content.isSelected();
        }
      };
      Disposer.register(this, myToolWindowView);
      myToolWindowView.getLogConsole().attachStopLogConsoleTrackingListener(process);
    }

    @Override
    public void buildUi(final RunnerLayoutUi layoutUi) {
      final Content consoleContent = layoutUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, getComponent(),
                                                            XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                                            AllIcons.Debugger.Console, getPreferredFocusableComponent());

      consoleContent.setCloseable(false);
      layoutUi.addContent(consoleContent, 1, PlaceInGrid.bottom, false);

      // todo: provide other icon
      final Content logcatContent = layoutUi.createContent(ANDROID_LOGCAT_CONTENT_ID, myToolWindowView.getContentPanel(), "Logcat",
                                                         AndroidIcons.Android, getPreferredFocusableComponent());
      logcatContent.setCloseable(false);
      logcatContent.setSearchComponent(myToolWindowView.createSearchComponent(myProject));
      layoutUi.addContent(logcatContent, 2, PlaceInGrid.bottom, false);
      final String selectedTabProperty = ANDROID_DEBUG_SELECTED_TAB_PROPERTY + myConfigurationId;

        final String tabName = PropertiesComponent.getInstance().getValue(selectedTabProperty);
      Content selectedContent = logcatContent;

      if (tabName != null) {
        for (Content content : layoutUi.getContents()) {
          if (tabName.equals(content.getDisplayName())) {
            selectedContent = content;
          }
        }
      }
      layoutUi.getContentManager().setSelectedContent(selectedContent);

      layoutUi.addListener(new ContentManagerAdapter() {
        public void selectionChanged(final ContentManagerEvent event) {
          final Content content = event.getContent();

          if (content.isSelected()) {
            PropertiesComponent.getInstance().setValue(selectedTabProperty, content.getDisplayName());
          }
          myToolWindowView.activate();
        }
      }, myToolWindowView);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myToolWindowView.activate();
        }
      });
    }

    @Nullable
    @Override
    public String getExecutionConsoleId() {
      return "ANDROID_LOGCAT";
    }

    @Override
    public JComponent getComponent() {
      return myConsoleView.getComponent();
    }

    @Override
    public JComponent getPreferredFocusableComponent() {
      return myConsoleView.getPreferredFocusableComponent();
    }

    @Override
    public void dispose() {
    }

    @Override
    public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
      if (myConsoleView instanceof ObservableConsoleView) {
        ((ObservableConsoleView)myConsoleView).addChangeListener(listener, parent);
      }
    }
  }

  private class MyDebugLauncher implements DebugLauncher {
    private final Project myProject;
    private final Executor myExecutor;
    private final AndroidRunningState myRunningState;
    private final ExecutionEnvironment myEnvironment;
    private RunContentDescriptor myRunDescriptor;

    public MyDebugLauncher(Project project,
                           Executor executor,
                           AndroidRunningState state,
                           ExecutionEnvironment environment) {
      myProject = project;
      myExecutor = executor;
      myRunningState = state;
      myEnvironment = environment;
    }

    public void setRunDescriptor(RunContentDescriptor runDescriptor) {
      myRunDescriptor = runDescriptor;
    }

    public void launchDebug(final IDevice device, final String debugPort) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
        public void run() {
          final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(myProject);
          AndroidDebugState st =
            new AndroidDebugState(myProject, new RemoteConnection(true, "localhost", debugPort, false), myEnvironment.getRunnerSettings(),
                                  myEnvironment.getConfigurationSettings(), myRunningState, device);
          RunContentDescriptor debugDescriptor = null;
          final ProcessHandler processHandler = myRunningState.getProcessHandler();
          try {
            synchronized (myDebugLock) {
              assert myRunDescriptor != null;
              debugDescriptor = manager
                .attachVirtualMachine(myExecutor, AndroidDebugRunner.this, myEnvironment, st, myRunDescriptor, st.getRemoteConnection(),
                                      false);
            }
          }
          catch (ExecutionException e) {
            processHandler.notifyTextAvailable("ExecutionException: " + e.getMessage() + '.', STDERR);
          }
          ProcessHandler newProcessHandler = debugDescriptor != null ? debugDescriptor.getProcessHandler() : null;
          if (debugDescriptor == null || newProcessHandler == null) {
            processHandler.notifyTextAvailable("Can't start debugging.", STDERR);
            processHandler.destroyProcess();
            return;
          }
          processHandler.detachProcess();
          final AndroidProcessText oldText = AndroidProcessText.get(processHandler);
          if (oldText != null) {
            oldText.printTo(newProcessHandler);
          }
          AndroidProcessText.attach(newProcessHandler);

          myRunningState.getProcessHandler().putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(
            debugDescriptor, st, myExecutor.getId()));

          final DebuggerSessionTab sessionTab = manager.getSessionTab();
          assert sessionTab != null;
          sessionTab.setEnvironment(myEnvironment);

          RunProfile profile = myEnvironment.getRunProfile();
          assert profile instanceof AndroidRunConfigurationBase;
          RunContentManager runContentManager = ExecutionManager.getInstance(myProject).getContentManager();

          setActivateToolWindowWhenAddedProperty(myProject, myExecutor, debugDescriptor, "debugger connected");

          runContentManager.showRunContent(myExecutor, debugDescriptor);
          newProcessHandler.startNotify();
        }
      });
    }
  }
}
