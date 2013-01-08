package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.util.Alarm;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates functionality of importing gradle module to the intellij project.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 2:49 PM
 */
public class GradleModuleManager {

  /**
   * We can't modify project modules (add/remove) until it's initialised, so, we delay that activity. Current constant
   * holds number of milliseconds to wait between 'after project initialisation' processing attempts.
   */
  private static final int PROJECT_INITIALISATION_DELAY_MS = (int)TimeUnit.SECONDS.toMillis(1);

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  @NotNull private final GradleContentRootManager myContentRootImporter;
  @NotNull private final GradleDependencyManager  myDependencyImporter;

  public GradleModuleManager(@NotNull GradleContentRootManager contentRootImporter,
                             @NotNull GradleDependencyManager dependencyImporter)
  {
    myContentRootImporter = contentRootImporter;
    myDependencyImporter = dependencyImporter;
  }

  public void importModule(@NotNull GradleModule module, @NotNull Project project) {
    importModules(Collections.singleton(module), project, false);
  }

  public void importModules(@NotNull final Collection<? extends GradleModule> modules,
                            @NotNull final Project project,
                            final boolean recursive)
  {
    if (modules.isEmpty()) {
      return;
    }
    if (!project.isInitialized()) {
      myAlarm.addRequest(new ImportModulesTask(project, modules, recursive), PROJECT_INITIALISATION_DELAY_MS);
      return;
    }
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        removeExistingModulesConfigs(modules);
        Application application = ApplicationManager.getApplication();
        final Map<GradleModule, Module> moduleMappings = new HashMap<GradleModule, Module>();
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            final ModuleManager moduleManager = ModuleManager.getInstance(project);
            final GradleProjectEntityChangeListener publisher
              = project.getMessageBus().syncPublisher(GradleProjectEntityChangeListener.TOPIC);
            for (GradleModule module : modules) {
              publisher.onChangeStart(module);
              try {
                importModule(moduleManager, module);
              }
              finally {
                publisher.onChangeEnd(module);
              }
            }
          }

          private void importModule(@NotNull ModuleManager moduleManager, @NotNull GradleModule module) {
            final Module created = moduleManager.newModule(module.getModuleFilePath(), StdModuleTypes.JAVA.getId());

            // Ensure that the dependencies are clear (used to be not clear when manually removing the module and importing it via gradle)
            ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(created);
            final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
            moduleRootModel.inheritSdk();
            RootPolicy<Object> visitor = new RootPolicy<Object>() {
              @Override
              public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
                moduleRootModel.removeOrderEntry(libraryOrderEntry);
                return value;
              }

              @Override
              public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
                moduleRootModel.removeOrderEntry(moduleOrderEntry);
                return value;
              }
            };
            try {
              for (OrderEntry orderEntry : moduleRootModel.getOrderEntries()) {
                orderEntry.accept(visitor, null);
              }
            }
            finally {
              moduleRootModel.commit();
            }
            moduleMappings.put(module, created);
          }
        });
        if (!recursive) {
          return;
        }
        for (GradleModule gradleModule : modules) {
          final Module intellijModule = moduleMappings.get(gradleModule);
          myContentRootImporter.importContentRoots(gradleModule.getContentRoots(), intellijModule);
          myDependencyImporter.importDependencies(gradleModule.getDependencies(), intellijModule);
        }
      }
    });
  }

  private static void removeExistingModulesConfigs(@NotNull Collection<? extends  GradleModule> modules) {
    for (GradleModule module : modules) {
      // Remove existing '*.iml' file if necessary.
      final String moduleFilePath = module.getModuleFilePath();
      File file = new File(moduleFilePath);
      if (file.isFile()) {
        boolean success = file.delete();
        if (!success) {
          GradleLog.LOG.warn("Can't remove existing module file at '" + moduleFilePath + "'");
        }
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void removeModules(@NotNull final Collection<? extends Module> modules) {
    if (modules.isEmpty()) {
      return;
    }
    Project project = modules.iterator().next().getProject();
    GradleUtil.executeProjectChangeAction(project, modules, new Runnable() {
      @Override
      public void run() {
        for (Module module : modules) {
          ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
          String path = module.getModuleFilePath();
          moduleManager.disposeModule(module);
          File file = new File(path);
          if (file.isFile()) {
            boolean success = file.delete();
            if (!success) {
              GradleLog.LOG.warn("Can't remove module file at '" + path + "'");
            }
          }
        }
      }
    });
  }
  
  private class ImportModulesTask implements Runnable {

    private final Project                            myProject;
    private final Collection<? extends GradleModule> myModules;
    private final boolean                            myRecursive;

    ImportModulesTask(@NotNull Project project, @NotNull Collection<? extends GradleModule> modules, boolean recursive) {
      myProject = project;
      myModules = modules;
      myRecursive = recursive;
    }

    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      if (!myProject.isInitialized()) {
        myAlarm.addRequest(
          new ImportModulesTask(myProject, myModules, myRecursive),
          PROJECT_INITIALISATION_DELAY_MS
        );
        return;
      }

      importModules(myModules, myProject, myRecursive);
    }
  }
}
