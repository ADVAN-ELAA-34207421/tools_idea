package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/15/12 11:32 AM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class LibraryDataService implements ProjectDataService<LibraryData, Library> {

  private static final Logger LOG = Logger.getInstance("#" + LibraryDataService.class.getName());
  @NotNull public static final NotNullFunction<String, File> PATH_TO_FILE = new NotNullFunction<String, File>() {
    @NotNull
    @Override
    public File fun(String path) {
      return new File(path);
    }
  };

  @NotNull private final PlatformFacade                myPlatformFacade;
  @NotNull private final ProjectStructureHelper        myProjectStructureHelper;
  @NotNull private final ExternalLibraryPathTypeMapper myLibraryPathTypeMapper;

  public LibraryDataService(@NotNull PlatformFacade platformFacade,
                            @NotNull ProjectStructureHelper helper,
                            @NotNull ExternalLibraryPathTypeMapper mapper)
  {
    myPlatformFacade = platformFacade;
    myProjectStructureHelper = helper;
    myLibraryPathTypeMapper = mapper;
  }

  @NotNull
  @Override
  public Key<LibraryData> getTargetDataKey() {
    return ProjectKeys.LIBRARY;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<LibraryData>> toImport, @NotNull Project project, boolean synchronous) {
    for (DataNode<LibraryData> dataNode : toImport) {
      importLibrary(dataNode.getData(), project, synchronous);
    }
  }

  public void importLibrary(@NotNull final LibraryData toImport, @NotNull final Project project, boolean synchronous) {
    Map<OrderRootType, Collection<File>> libraryFiles = ContainerUtilRt.newHashMap();
    for (LibraryPathType pathType : LibraryPathType.values()) {
      final Set<String> paths = toImport.getPaths(pathType);
      if (paths.isEmpty()) {
        continue;
      }
      libraryFiles.put(myLibraryPathTypeMapper.map(pathType), ContainerUtil.map(paths, PATH_TO_FILE));
    }

    Library library = myProjectStructureHelper.findIdeLibrary(toImport, project);
    if (library != null) {
      syncPaths(toImport, library, synchronous);
      return;
    }
    importLibrary(toImport.getName(), libraryFiles, project, synchronous);
  }

  public void importLibrary(@NotNull final String libraryName,
                            @NotNull final Map<OrderRootType, Collection<File>> libraryFiles,
                            @NotNull final Project project,
                            boolean synchronous)
  {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        // Is assumed to be called from the EDT.
        final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        final LibraryTable.ModifiableModel projectLibraryModel = libraryTable.getModifiableModel();
        final Library intellijLibrary;
        try {
          intellijLibrary = projectLibraryModel.createLibrary(libraryName);
        }
        finally {
          projectLibraryModel.commit();
        }
        final Library.ModifiableModel libraryModel = intellijLibrary.getModifiableModel();
        try {
          registerPaths(libraryFiles, libraryModel, libraryName);
        }
        finally {
          libraryModel.commit();
        }
      }
    });
  }

  private static void registerPaths(@NotNull final Map<OrderRootType, Collection<File>> libraryFiles,
                                    @NotNull Library.ModifiableModel model,
                                    @NotNull String libraryName)
  {
    for (Map.Entry<OrderRootType, Collection<File>> entry : libraryFiles.entrySet()) {
      for (File file : entry.getValue()) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          if (entry.getKey() == OrderRootType.CLASSES) {
            LOG.warn(
              String.format("Can't find %s of the library '%s' at path '%s'", entry.getKey(), libraryName, file.getAbsolutePath())
            );
          }
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, entry.getKey());
        }
        else {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
          if (jarRoot == null) {
            LOG.warn(String.format(
              "Can't parse contents of the jar file at path '%s' for the library '%s''", file.getAbsolutePath(), libraryName
            ));
            continue;
          }
          model.addRoot(jarRoot, entry.getKey());
        }
      }
    }
  }

  public void removeData(@NotNull final Collection<? extends Library> libraries, @NotNull final Project project, boolean synchronous) {
    if (libraries.isEmpty()) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
        final LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
        try {
          for (Library library : libraries) {
            String libraryName = library.getName();
            if (libraryName != null) {
              Library libraryToRemove = model.getLibraryByName(libraryName);
              if (libraryToRemove != null) {
                model.removeLibrary(libraryToRemove);
              }
            }
          }
        }
        finally {
          model.commit();
        }
      }
    });
  }

  public void syncPaths(@NotNull final LibraryData externalLibrary, @NotNull final Library ideLibrary, boolean synchronous) {
    final Set<String> toRemove = ContainerUtilRt.newHashSet();
    final Set<String> toAdd = ContainerUtilRt.newHashSet(externalLibrary.getPaths(LibraryPathType.BINARY));
    for (VirtualFile ideFile : ideLibrary.getFiles(OrderRootType.CLASSES)) {
      String idePath = ExternalSystemApiUtil.getLocalFileSystemPath(ideFile);
      if (!toAdd.remove(idePath)) {
        toRemove.add(idePath);
      }
    }
    if (toRemove.isEmpty() && toAdd.isEmpty()) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        Library.ModifiableModel model = ideLibrary.getModifiableModel();
        try {
          for (String path : toRemove) {
            model.removeRoot(path, OrderRootType.CLASSES);
          }
          Map<OrderRootType, Collection<File>> roots = ContainerUtilRt.newHashMap();
          roots.put(OrderRootType.CLASSES, ContainerUtil.map(toAdd, PATH_TO_FILE));
          registerPaths(roots, model, externalLibrary.getName());
        }
        finally {
          model.commit();
        }
      }
    });
  }
}
