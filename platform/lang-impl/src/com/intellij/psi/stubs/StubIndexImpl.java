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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

@State(
  name = "FileBasedIndex",
  roamingType = RoamingType.DISABLED,
  storages = {
  @Storage(
    file = StoragePathMacros.APP_CONFIG + "/stubIndex.xml")
    }
)
public class StubIndexImpl extends StubIndex implements ApplicationComponent, PersistentStateComponent<StubIndexState> {
  private static final AtomicReference<Boolean> ourForcedClean = new AtomicReference<Boolean>(null);
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubIndexImpl");
  private final Map<StubIndexKey<?,?>, MyIndex<?>> myIndices = new THashMap<StubIndexKey<?,?>, MyIndex<?>>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();

  private StubIndexState myPreviouslyRegistered;

  public StubIndexImpl(FileBasedIndex fileBasedIndex /* need this to ensure initialization order*/ ) throws IOException {
    final boolean forceClean = Boolean.TRUE == ourForcedClean.getAndSet(Boolean.FALSE);

    final StubIndexExtension<?, ?>[] extensions = Extensions.getExtensions(StubIndexExtension.EP_NAME);
    boolean needRebuild = false;
    for (StubIndexExtension extension : extensions) {
      //noinspection unchecked
      needRebuild |= registerIndexer(extension, forceClean);
    }
    if (needRebuild) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        requestRebuild();
      }
      else {
        forceRebuild(new Throwable());
      }
    }
    dropUnregisteredIndices();
  }
  
  @Nullable
  public static StubIndexImpl getInstanceOrInvalidate() {
    if (ourForcedClean.compareAndSet(null, Boolean.TRUE)) {
      return null;
    }
    return (StubIndexImpl)getInstance();
  }

  private <K> boolean registerIndexer(@NotNull StubIndexExtension<K, ?> extension, final boolean forceClean) throws IOException {
    final StubIndexKey<K, ?> indexKey = extension.getKey();
    final int version = extension.getVersion();
    myIndexIdToVersionMap.put(indexKey, version);
    final File versionFile = IndexInfrastructure.getVersionFile(indexKey);
    final boolean versionFileExisted = versionFile.exists();
    final File indexRootDir = IndexInfrastructure.getIndexRootDir(indexKey);
    boolean needRebuild = false;
    if (forceClean || IndexInfrastructure.versionDiffers(versionFile, version)) {
      final String[] children = indexRootDir.list();
      // rebuild only if there exists what to rebuild
      needRebuild = !forceClean && (versionFileExisted || children != null && children.length > 0);
      if (needRebuild) {
        LOG.info("Version has changed for stub index " + extension.getKey() + ". The index will be rebuilt.");
      }
      FileUtil.delete(indexRootDir);
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, StubIdList> storage = new MapIndexStorage<K, StubIdList>(
          IndexInfrastructure.getStorageFile(indexKey),
          extension.getKeyDescriptor(),
          new StubIdExternalizer(),
          extension.getCacheSize()
        );
        final MemoryIndexStorage<K, StubIdList> memStorage = new MemoryIndexStorage<K, StubIdList>(storage);
        myIndices.put(indexKey, new MyIndex<K>(memStorage));
        break;
      }
      catch (IOException e) {
        LOG.info(e);
        needRebuild = true;
        FileUtil.delete(indexRootDir);
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
    return needRebuild;
  }

  private static class StubIdExternalizer implements DataExternalizer<StubIdList> {
    @Override
    public void save(final DataOutput out, @NotNull final StubIdList value) throws IOException {
      int size = value.size();
      if (size == 0) {
        DataInputOutputUtil.writeINT(out, Integer.MAX_VALUE);
      }
      else if (size == 1) {
        DataInputOutputUtil.writeINT(out, value.get(0)); // most often case
      }
      else {
        DataInputOutputUtil.writeINT(out, -size);
        for(int i = 0; i < size; ++i) {
          DataInputOutputUtil.writeINT(out, value.get(i));
        }
      }
    }

    @NotNull
    @Override
    public StubIdList read(final DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      if (size == Integer.MAX_VALUE) {
        return new StubIdList();
      }
      else if (size >= 0) {
        return new StubIdList(size);
      }
      else {
        size = -size;
        int[] result = new int[size];
        for(int i = 0; i < size; ++i) {
          result[i] = DataInputOutputUtil.readINT(in);
        }
        return new StubIdList(result, size);
      }
    }
  }

  @NotNull
  @Override
  public <Key, Psi extends PsiElement> Collection<Psi> get(@NotNull final StubIndexKey<Key, Psi> indexKey,
                                                           @NotNull final Key key,
                                                           @NotNull final Project project,
                                                           final GlobalSearchScope scope) {
    final List<Psi> result = new SmartList<Psi>();
    process(indexKey, key, project, scope, new CommonProcessors.CollectProcessor<Psi>(result));
    return result;
  }

  @Override
  public <Key, Psi extends PsiElement> boolean process(@NotNull final StubIndexKey<Key, Psi> indexKey,
                                                       @NotNull final Key key,
                                                       @NotNull final Project project,
                                                       @Nullable final GlobalSearchScope scope,
                                                       @NotNull final Processor<? super Psi> processor) {
    final FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    fileBasedIndex.ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, scope);

    final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
    final PsiManager psiManager = PsiManager.getInstance(project);

    final MyIndex<Key> index = (MyIndex<Key>)myIndices.get(indexKey);

    try {
      try {
        // disable up-to-date check to avoid locks on attempt to acquire index write lock while holding at the same time the readLock for this index
        FileBasedIndexImpl.disableUpToDateCheckForCurrentThread();
        index.getReadLock().lock();
        final ValueContainer<StubIdList> container = index.getData(key);

        final FileBasedIndexImpl.ProjectIndexableFilesFilter projectFilesFilter = fileBasedIndex.projectIndexableFiles(project);

        return container.forEach(new ValueContainer.ContainerAction<StubIdList>() {
          @Override
          public boolean perform(final int id, @NotNull final StubIdList value) {
            if (projectFilesFilter != null && !projectFilesFilter.contains(id)) return true;
            final VirtualFile file = IndexInfrastructure.findFileByIdIfCached(fs, id);
            if (file == null || scope != null && !scope.contains(file)) {
              return true;
            }
            StubTree stubTree = null;

            final PsiFile _psifile = psiManager.findFile(file);
            PsiFileWithStubSupport psiFile = null;

            if (_psifile != null && !(_psifile instanceof PsiPlainTextFile)) {
              if (_psifile instanceof PsiFileWithStubSupport) {
                psiFile = (PsiFileWithStubSupport)_psifile;
                stubTree = psiFile.getStubTree();
                if (stubTree == null && psiFile instanceof PsiFileImpl) {
                  stubTree = ((PsiFileImpl)psiFile).calcStubTree();
                }
              }
            }

            if (stubTree == null && psiFile == null) {
              return true;
            }
            if (stubTree == null) {
              ObjectStubTree objectStubTree = StubTreeLoader.getInstance().readFromVFile(project, file);
              if (!(objectStubTree instanceof ObjectStubTree)) {
                return true;
              }
              stubTree = (StubTree)objectStubTree;
              final List<StubElement<?>> plained = stubTree.getPlainList();
              for (int i = 0, size = value.size(); i < size; i++) {
                final StubElement<?> stub = plained.get(value.get(i));
                final ASTNode tree = psiFile.findTreeForStub(stubTree, stub);

                if (tree != null) {
                  if (tree.getElementType() == stubType(stub)) {
                    Psi psi = (Psi)tree.getPsi();
                    if (!processor.process(psi)) return false;
                  }
                  else {
                    String persistedStubTree = ((PsiFileStubImpl)stubTree.getRoot()).printTree();

                    String stubTreeJustBuilt =
                      ((PsiFileStubImpl)((IStubFileElementType)((PsiFileImpl)psiFile).getContentElementType()).getBuilder()
                        .buildStubTree(psiFile)).printTree();

                    StringBuilder builder = new StringBuilder();
                    builder.append("Oops\n");


                    builder.append("Recorded stub:-----------------------------------\n");
                    builder.append(persistedStubTree);
                    builder.append("\nAST built stub: ------------------------------------\n");
                    builder.append(stubTreeJustBuilt);
                    builder.append("\n");
                    LOG.info(builder.toString());

                    // requestReindex() may want to acquire write lock (for indices not requiring content loading)
                    // thus, because here we are under read lock, need to use invoke later
                    ApplicationManager.getApplication().invokeLater(new Runnable() {
                      @Override
                      public void run() {
                        fileBasedIndex.requestReindex(file);
                      }
                    }, ModalityState.NON_MODAL);
                  }
                }
              }
            }
            else {
              final List<StubElement<?>> plained = stubTree.getPlainList();
              for (int i = 0, size = value.size(); i < size; i++) {
                final int stubTreeIndex = value.get(i);
                if (stubTreeIndex >= plained.size()) {
                  final VirtualFile virtualFile = psiFile.getVirtualFile();
                  StubTree stubTreeFromIndex = (StubTree)StubTreeLoader.getInstance().readFromVFile(project, file);
                  LOG.error(LogMessageEx.createEvent("PSI and index do not match: PSI " + psiFile + ", first stub " + plained.get(0),
                                                     "Please report the problem to JetBrains with the file attached",
                                                     new Attachment(virtualFile != null ? virtualFile.getPath() : "vFile.txt", psiFile.getText()),
                                                     new Attachment("stubTree.txt", ((PsiFileStubImpl)stubTree.getRoot()).printTree()),
                                                     new Attachment("stubTreeFromIndex.txt", stubTreeFromIndex == null ? "null" : ((PsiFileStubImpl)stubTreeFromIndex.getRoot()).printTree())));

                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      fileBasedIndex.requestReindex(file);
                    }
                  }, ModalityState.NON_MODAL);

                  break;
                }
                Psi psi = (Psi)plained.get(stubTreeIndex).getPsi();
                if (!processor.process(psi)) return false;
              }
            }
            return true;
          }
        });
      }
      finally {
        index.getReadLock().unlock();
        FileBasedIndexImpl.enableUpToDateCheckForCurrentThread();
      }
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = FileBasedIndexImpl.getCauseToRebuildIndex(e);
      if (cause != null) {
        forceRebuild(cause);
      }
      else {
        throw e;
      }
    }

    return true;
  }

  private static IElementType stubType(@NotNull final StubElement<?> stub) {
    if (stub instanceof PsiFileStub) {
      return ((PsiFileStub)stub).getType();
    }

    return stub.getStubType();
  }

  private static void forceRebuild(@NotNull Throwable e) {
    LOG.info(e);
    FileBasedIndex.getInstance().scheduleRebuild(StubUpdatingIndex.INDEX_ID, e);
  }

  private static void requestRebuild() {
    FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID);
  }

  @Override
  @NotNull
  public <K> Collection<K> getAllKeys(@NotNull StubIndexKey<K, ?> indexKey, @NotNull Project project) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project));

    final MyIndex<K> index = (MyIndex<K>)myIndices.get(indexKey);
    try {
      return index.getAllKeys();
    }
    catch (StorageException e) {
      forceRebuild(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException || cause instanceof StorageException) {
        forceRebuild(e);
      }
      throw e;
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "Stub.IndexManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    // This index must be disposed only after StubUpdatingIndex is disposed
    // To ensure this, disposing is done explicitly from StubUpdatingIndex by calling dispose() method
    // do not call this method here to avoid double-disposal
  }

  public void dispose() {
    for (UpdatableIndex index : myIndices.values()) {
      index.dispose();
    }
  }

  public void setDataBufferingEnabled(final boolean enabled) {
    for (UpdatableIndex index : myIndices.values()) {
      final IndexStorage indexStorage = ((MapReduceIndex)index).getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
  }

  public void cleanupMemoryStorage() {
    for (UpdatableIndex index : myIndices.values()) {
      final IndexStorage indexStorage = ((MapReduceIndex)index).getStorage();
      index.getWriteLock().lock();
      try {
        ((MemoryIndexStorage)indexStorage).clearMemoryMap();
      }
      finally {
        index.getWriteLock().unlock();
      }
    }
  }


  public void clearAllIndices() {
    for (UpdatableIndex index : myIndices.values()) {
      try {
        index.clear();
      }
      catch (StorageException e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = new HashSet<String>(myPreviouslyRegistered != null? myPreviouslyRegistered.registeredIndices : Collections.<String>emptyList());
    for (ID<?, ?> key : myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }

    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  @NotNull
  @Override
  public StubIndexState getState() {
    return new StubIndexState(myIndices.keySet());
  }

  @Override
  public void loadState(final StubIndexState state) {
    myPreviouslyRegistered = state;
  }

  public final Lock getWriteLock(StubIndexKey indexKey) {
    return myIndices.get(indexKey).getWriteLock();
  }

  public Collection<StubIndexKey> getAllStubIndexKeys() {
    return Collections.<StubIndexKey>unmodifiableCollection(myIndices.keySet());
  }

  public void flush(StubIndexKey key) throws StorageException {
    final MyIndex<?> index = myIndices.get(key);
    index.flush();
  }

  public <K> void updateIndex(@NotNull StubIndexKey key, int fileId, @NotNull final Map<K, StubIdList> oldValues, @NotNull Map<K, StubIdList> newValues) {
    try {
      final MyIndex<K> index = (MyIndex<K>)myIndices.get(key);
      index.updateWithMap(fileId, newValues, new Callable<Collection<K>>() {
        @Override
        public Collection<K> call() throws Exception {
          return oldValues.keySet();
        }
      });
    }
    catch (StorageException e) {
      LOG.info(e);
      requestRebuild();
    }
  }

  private static class MyIndex<K> extends MapReduceIndex<K, StubIdList, Void> {
    public MyIndex(final IndexStorage<K, StubIdList> storage) {
      super(null, null, storage);
    }

    @Override
    public void updateWithMap(final int inputId, @NotNull final Map<K, StubIdList> newData, @NotNull Callable<Collection<K>> oldKeysGetter) throws StorageException {
      super.updateWithMap(inputId, newData, oldKeysGetter);
    }
  }

  public static <Key, Psi extends PsiElement> Collection<Psi> safeGet(@NotNull StubIndexKey<Key, Psi> indexKey,
                                                                      @NotNull Key key,
                                                                      @NotNull final Project project,
                                                                      final GlobalSearchScope scope,
                                                                      @NotNull Class<Psi> requiredClass) {
    Collection<Psi> collection = getInstance().get(indexKey, key, project, scope);
    for (Iterator<Psi> iterator = collection.iterator(); iterator.hasNext(); ) {
      Psi psi = iterator.next();
      if (!requiredClass.isInstance(psi)) {
        iterator.remove();

        VirtualFile faultyContainer = PsiUtilCore.getVirtualFile(psi);
        LOG.error("Invalid stub element type in index: " + faultyContainer + ". found: " + psi);
        if (faultyContainer != null && faultyContainer.isValid()) {
          FileBasedIndex.getInstance().requestReindex(faultyContainer);
        }
      }
    }
    
    return collection;
  }

}
