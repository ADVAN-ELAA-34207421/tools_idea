package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates functionality of importing gradle module to the intellij project.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 2:49 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ModuleDataService implements ProjectDataService<ModuleData> {

  private static final Logger LOG = Logger.getInstance("#" + ModuleDataService.class.getName());

  /**
   * We can't modify project modules (add/remove) until it's initialised, so, we delay that activity. Current constant
   * holds number of milliseconds to wait between 'after project initialisation' processing attempts.
   */
  private static final int PROJECT_INITIALISATION_DELAY_MS = (int)TimeUnit.SECONDS.toMillis(1);

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  @NotNull private final ProjectStructureHelper       myProjectStructureHelper;

  public ModuleDataService(@NotNull ProjectStructureHelper helper) {
    myProjectStructureHelper = helper;
  }

  @NotNull
  @Override
  public Key<ModuleData> getTargetDataKey() {
    return ProjectKeys.MODULE;
  }

  public void importData(@NotNull final Collection<DataNode<ModuleData>> toImport,
                         @NotNull final Project project,
                         final boolean synchronous)
  {
    if (toImport.isEmpty()) {
      return;
    }
    if (!project.isInitialized()) {
      myAlarm.addRequest(new ImportModulesTask(project, toImport, synchronous), PROJECT_INITIALISATION_DELAY_MS);
      return;
    }
    Runnable task = new Runnable() {
      @Override
      public void run() {
        final Collection<DataNode<ModuleData>> toCreate = filterExistingModules(toImport, project);
        if (toCreate.isEmpty()) {
          return;
        }
        removeExistingModulesConfigs(toCreate, project);
        Application application = ApplicationManager.getApplication();
        final Map<DataNode<ModuleData>, Module> moduleMappings = ContainerUtilRt.newHashMap();
        application.runWriteAction(new Runnable() {
          @Override
          public void run() {
            final ModuleManager moduleManager = ModuleManager.getInstance(project);
            final ProjectEntityChangeListener publisher
              = project.getMessageBus().syncPublisher(ProjectEntityChangeListener.TOPIC);
            for (DataNode<ModuleData> module : toCreate) {
              publisher.onChangeStart(module, module.getData().getOwner());
              try {
                importModule(moduleManager, module);
              }
              finally {
                publisher.onChangeEnd(module, module.getData().getOwner());
              }
            }
          }

          private void importModule(@NotNull ModuleManager moduleManager, @NotNull DataNode<ModuleData> module) {
            ModuleData data = module.getData();
            final Module created = moduleManager.newModule(data.getModuleFilePath(), data.getModuleTypeId());

            // Ensure that the dependencies are clear (used to be not clear when manually removing the module and importing it via gradle)
            ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(created);
            final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
            moduleRootModel.inheritSdk();
            created.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, data.getOwner().toString());
            ProjectData projectData = module.getData(ProjectKeys.PROJECT);
            if (projectData != null) {
              created.setOption(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY, projectData.getLinkedExternalProjectPath());
            }
            
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
      }
    };
    if (synchronous) {
      UIUtil.invokeAndWaitIfNeeded(task);
    }
    else {
      UIUtil.invokeLaterIfNeeded(task);
    }
  }

  @NotNull
  private Collection<DataNode<ModuleData>> filterExistingModules(@NotNull Collection<DataNode<ModuleData>> modules,
                                                                 @NotNull Project project)
  {
    Collection<DataNode<ModuleData>> result = ContainerUtilRt.newArrayList();
    for (DataNode<ModuleData> node : modules) {
      if (myProjectStructureHelper.findIdeModule(node.getData(), project) == null) {
        result.add(node);
      }
    }
    return result;
  }

  private void removeExistingModulesConfigs(@NotNull final Collection<DataNode<ModuleData>> nodes, @NotNull Project project) {
    if (nodes.isEmpty()) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(project, nodes.iterator().next().getData().getOwner(), nodes, true, new Runnable() {
      @Override
      public void run() {
        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        for (DataNode<ModuleData> node : nodes) {
          // Remove existing '*.iml' file if necessary.
          ModuleData data = node.getData();
          VirtualFile file = fileSystem.refreshAndFindFileByPath(data.getModuleFilePath());
          if (file != null) {
            try {
              file.delete(this);
            }
            catch (IOException e) {
              LOG.warn("Can't remove existing module config file at '" + data.getModuleFilePath() + "'");
            }
          }
        }
      }
    });
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<ModuleData>> toRemove, @NotNull Project project, boolean synchronous) {
     Collection<Module> modules = ContainerUtilRt.newArrayList();
    for (DataNode<ModuleData> node : toRemove) {
      Module module = myProjectStructureHelper.findIdeModule(node.getData(), project);
      if (module != null) {
        modules.add(module);
      }
    }
    removeData(modules, synchronous);
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void removeData(@NotNull final Collection<? extends Module> modules, boolean synchronous) {
    if (modules.isEmpty()) {
      return;
    }
    Project project = modules.iterator().next().getProject();
    ExternalSystemApiUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, modules, synchronous, new Runnable() {
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
              LOG.warn("Can't remove module file at '" + path + "'");
            }
          }
        }
      }
    });
  }
  
  private class ImportModulesTask implements Runnable {

    private final Project                          myProject;
    private final Collection<DataNode<ModuleData>> myModules;
    private final boolean                          mySynchronous;

    ImportModulesTask(@NotNull Project project, @NotNull Collection<DataNode<ModuleData>> modules, boolean synchronous) {
      myProject = project;
      myModules = modules;
      mySynchronous = synchronous;
    }

    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      if (!myProject.isInitialized()) {
        myAlarm.addRequest(
          new ImportModulesTask(myProject, myModules, mySynchronous),
          PROJECT_INITIALISATION_DELAY_MS
        );
        return;
      }

      importData(myModules, myProject, mySynchronous);
    }
  }
}
