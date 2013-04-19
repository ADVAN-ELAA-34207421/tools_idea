package com.intellij.openapi.externalSystem.service;

import com.intellij.CommonBundle;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.wrapper.ExternalSystemFacadeWrapper;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.IntegrationKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiBundle;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Entry point to work with remote {@link RemoteExternalSystemFacade}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 1:08 PM
 */
public class ExternalSystemFacadeManager {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemFacadeManager.class.getName());

  private static final String MAIN_CLASS_NAME = ExternalSystemFacadeImpl.class.getName();

  private static final int REMOTE_FAIL_RECOVERY_ATTEMPTS_NUMBER = 3;

  private final ConcurrentMap<IntegrationKey, RemoteExternalSystemFacade> myFacadeWrappers = ContainerUtil.newConcurrentMap();

  private final Map<IntegrationKey, Pair<RemoteExternalSystemFacade, ExternalSystemExecutionSettings>> myRemoteFacades
    = ContainerUtil.newConcurrentMap();

  @NotNull private final Lock myLock = new ReentrantLock();

  private final AtomicReference<RemoteExternalSystemProgressNotificationManager> myExportedNotificationManager
    = new AtomicReference<RemoteExternalSystemProgressNotificationManager>();

  @NotNull private final ExternalSystemProgressNotificationManagerImpl                    myProgressManager;
  @NotNull private final ExternalSystemSettingsManager                                    mySettingsManager;
  @NotNull private final RemoteProcessSupport<Object, RemoteExternalSystemFacade, String> mySupport;

  @NotNull private final ThreadLocal<ProjectSystemId> myTargetExternalSystemId = new ThreadLocal<ProjectSystemId>();

  public ExternalSystemFacadeManager(@NotNull ExternalSystemSettingsManager settingsManager,
                                     @NotNull ExternalSystemProgressNotificationManager notificationManager)
  {
    mySettingsManager = settingsManager;
    myProgressManager = (ExternalSystemProgressNotificationManagerImpl)notificationManager;
    mySupport = new RemoteProcessSupport<Object, RemoteExternalSystemFacade, String>(RemoteExternalSystemFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(Object o) {
        return RemoteExternalSystemFacade.class.getName();
      }

      @Override
      protected RunProfileState getRunProfileState(Object o, String configuration, Executor executor) throws ExecutionException {
        return createRunProfileState();
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        shutdown(false);
      }
    });
  }

  @NotNull
  private static Project findProject(@NotNull IntegrationKey key) {
    final ProjectManager projectManager = ProjectManager.getInstance();
    for (Project project : projectManager.getOpenProjects()) {
      if (key.getIdeProjectName().equals(project.getName()) && key.getIdeProjectLocationHash().equals(project.getLocationHash())) {
        return project;
      }
    }
    return projectManager.getDefaultProject();
  }

  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {

        final SimpleJavaParameters params = new SimpleJavaParameters();
        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));

        params.setWorkingDirectory(PathManager.getBinPath());
        final List<String> classPath = ContainerUtilRt.newArrayList();

        // IDE jars.
        classPath.addAll(PathManager.getUtilClassPath());
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(LanguageLevel.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(PsiBundle.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Alarm.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(DependencyScope.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(JavaSdkVersion.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(ExtensionPointName.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(OpenProjectFileChooserDescriptor.class), classPath);

        // External system module jars
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(getClass()), classPath);
        ExternalSystemUtil.addBundle(params.getClassPath(), "messages.CommonBundle", CommonBundle.class);
        params.getClassPath().addAll(classPath);

        params.setMainClass(MAIN_CLASS_NAME);
        params.getVMParametersList().addParametersString("-Djava.awt.headless=true -Xmx512m");

        // It may take a while for gradle api to resolve external dependencies. Default RMI timeout
        // is 15 seconds (http://download.oracle.com/javase/6/docs/technotes/guides/rmi/sunrmiproperties.html#connectionTimeout),
        // we don't want to get EOFException because of that.
        params.getVMParametersList().addParametersString(
          "-Dsun.rmi.transport.connectionTimeout=" + String.valueOf(TimeUnit.HOURS.toMillis(1))
        );
        //params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009");

        ProjectSystemId externalSystemId = myTargetExternalSystemId.get();
        if (externalSystemId != null) {
          ExternalSystemManager<?, ?, ?, ?> manager = ExternalSystemUtil.getManager(externalSystemId);
          if (manager != null) {
            params.getProgramParametersList().add(manager.getProjectResolverClass().getName());
            params.getProgramParametersList().add(manager.getBuildManagerClass().getName());
            manager.enhanceParameters(params);
          }
        }

        return params;
      }

      @NotNull
      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      @NotNull
      protected OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        Sdk sdk = params.getJdk();
        if (sdk == null) {
          throw new ExecutionException("No sdk is defined. Params: " + params);
        } 

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(
          ((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk),
          params,
          false
        );
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
          @Override
          public Charset getCharset() {
            return commandLine.getCharset();
          }
        };
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }
    };
  }

  public synchronized void shutdown(boolean wait) {
    mySupport.stopAll(wait);
  }
  
  /**
   * @return              gradle api facade to use
   * @throws Exception    in case of inability to return the facade
   */
  @NotNull
  public RemoteExternalSystemFacade getFacade(@Nullable Project project, @NotNull ProjectSystemId externalSystemId) throws Exception {
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    IntegrationKey key = new IntegrationKey(project, externalSystemId);
    final RemoteExternalSystemFacade facade = myFacadeWrappers.get(key);
    if (facade == null) {
      final RemoteExternalSystemFacade newFacade = (RemoteExternalSystemFacade)Proxy.newProxyInstance(
        ExternalSystemFacadeManager.class.getClassLoader(), new Class[]{RemoteExternalSystemFacade.class}, new MyHandler(key)
      );
      myFacadeWrappers.putIfAbsent(key, newFacade);
    }
    return myFacadeWrappers.get(key);
  }

  public Object doInvoke(@NotNull IntegrationKey key, @NotNull Project project, Method method, Object[] args, int invocationNumber)
    throws Throwable
  {
    RemoteExternalSystemFacade facade = doGetFacade(key, project);
    try {
      return method.invoke(facade, args);
    }
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof RemoteException && invocationNumber > 0) {
        Thread.sleep(1000);
        return doInvoke(key, project, method, args, invocationNumber - 1);
      }
      else {
        throw e;
      }
    }
  }

  @SuppressWarnings("ConstantConditions")
  @NotNull
  private RemoteExternalSystemFacade doGetFacade(@NotNull IntegrationKey key, @NotNull Project project) throws Exception {
    ExternalSystemManager manager = ExternalSystemUtil.getManager(key.getExternalSystemId());
    if (project.isDisposed() || manager == null || !manager.isReady(project)) {
      return RemoteExternalSystemFacade.NULL_OBJECT;
    }
    Pair<RemoteExternalSystemFacade, ExternalSystemExecutionSettings> pair = myRemoteFacades.get(key);
    if (pair != null && prepare(project, key, pair)) {
      return pair.first;
    }
    
    myLock.lock();
    try {
      pair = myRemoteFacades.get(key);
      if (pair != null && prepare(project, key, pair)) {
        return pair.first;
      }
      if (pair != null) {
        mySupport.stopAll(true);
        myFacadeWrappers.clear();
        myRemoteFacades.clear();
      }
      return doCreateFacade(key, project);
    }
    finally {
      myLock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private RemoteExternalSystemFacade doCreateFacade(@NotNull IntegrationKey key, @NotNull Project project) throws Exception {
    myTargetExternalSystemId.set(key.getExternalSystemId());
    final RemoteExternalSystemFacade facade = mySupport.acquire(this, project.getName());
    myTargetExternalSystemId.set(null);
    if (facade == null) {
      throw new IllegalStateException("Can't obtain facade to working with gradle api at the remote process. Project: " + project);
    }
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        mySupport.stopAll(true);
        myFacadeWrappers.clear();
        myRemoteFacades.clear();
      }
    });
    final RemoteExternalSystemFacade result = new ExternalSystemFacadeWrapper(facade, myProgressManager);
    ExternalSystemExecutionSettings settings = mySettingsManager.getExecutionSettings(project, key.getExternalSystemId());
    Pair<RemoteExternalSystemFacade, ExternalSystemExecutionSettings> newPair = Pair.create(result, settings);
    myRemoteFacades.put(key, newPair);
    result.applySettings(newPair.second);
    RemoteExternalSystemProgressNotificationManager exported = myExportedNotificationManager.get();
    if (exported == null) {
      try {
        exported = (RemoteExternalSystemProgressNotificationManager)UnicastRemoteObject.exportObject(myProgressManager, 0);
        myExportedNotificationManager.set(exported);
      }
      catch (RemoteException e) {
        exported = myExportedNotificationManager.get();
      }
    }
    if (exported == null) {
      LOG.warn("Can't export progress manager");
    }
    else {
      result.applyProgressManager(exported);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private boolean prepare(@NotNull Project project,
                          @NotNull IntegrationKey key,
                          @NotNull Pair<RemoteExternalSystemFacade, ExternalSystemExecutionSettings> pair)
  {
    // Check if remote process is alive.
    try {
      pair.first.getResolver();

      ExternalSystemExecutionSettings currentSettings = mySettingsManager.getExecutionSettings(project, key.getExternalSystemId());
      if (!currentSettings.equals(pair.second)) {
        pair.first.applySettings(currentSettings);
        myRemoteFacades.put(key, Pair.create(pair.first, currentSettings));
      }
      return true;
    }
    catch (RemoteException e) {
      return false;
    }
  }

  public boolean isTaskActive(@NotNull ExternalSystemTaskId id) {
    Map<IntegrationKey, Pair<RemoteExternalSystemFacade, ExternalSystemExecutionSettings>> copy
      = ContainerUtilRt.newHashMap(myRemoteFacades);
    for (Map.Entry<IntegrationKey, Pair<RemoteExternalSystemFacade, ExternalSystemExecutionSettings>> entry : copy.entrySet()) {
      try {
        if (entry.getValue().first.isTaskInProgress(id)) {
          return true;
        }
      }
      catch (RemoteException e) {
        myLock.lock();
        try {
          myRemoteFacades.remove(entry.getKey());
          myFacadeWrappers.remove(entry.getKey());
        }
        finally {
          myLock.unlock();
        }
      }
    }
    return false;
  }
  
  private class MyHandler implements InvocationHandler {
    
    @NotNull private final IntegrationKey myKey;

    MyHandler(@NotNull IntegrationKey key) {
      myKey = key;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return doInvoke(myKey, findProject(myKey), method, args, REMOTE_FAIL_RECOVERY_ATTEMPTS_NUMBER);
    }
  }
}
