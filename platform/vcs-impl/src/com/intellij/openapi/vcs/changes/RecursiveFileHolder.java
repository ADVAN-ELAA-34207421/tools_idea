
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.MembershipMap;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;

import java.util.*;

/**
 * @author max
 */
public class RecursiveFileHolder<T> extends AbstractIgnoredFilesHolder {
  protected final HolderType myHolderType;
  protected final TreeMap<VirtualFile, T> myMap;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    super(project);
    myMap = new TreeMap<VirtualFile, T>(FilePathComparator.getInstance());
    myHolderType = holderType;
  }

  public void cleanAll() {
    myMap.clear();
  }

  @Override
  protected Collection<VirtualFile> keys() {
    return myMap.keySet();
  }

  @Override
  public void notifyVcsStarted(AbstractVcs scope) {
  }

  public HolderType getType() {
    return myHolderType;
  }

  public void addFile(final VirtualFile file) {
    if (! containsFile(file)) {
      myMap.put(file, null);
    }
  }

  public void removeFile(final VirtualFile file) {
    myMap.remove(file);
  }

  public RecursiveFileHolder copy() {
    final RecursiveFileHolder<T> copyHolder = new RecursiveFileHolder<T>(myProject, myHolderType);
    copyHolder.myMap.putAll(myMap);
    return copyHolder;
  }

  public boolean containsFile(final VirtualFile file) {
    final VirtualFile floor = myMap.floorKey(file);
    if (floor == null) return false;
    final SortedMap<VirtualFile, T> floorMap = myMap.headMap(floor, true);
    for (VirtualFile parent : floorMap.keySet()) {
      if (VfsUtil.isAncestor(parent, file, false)) {
        return true;
      }
    }
    return false;
  }

  public Collection<VirtualFile> values() {
    return myMap.keySet();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RecursiveFileHolder that = (RecursiveFileHolder)o;
    if (myMap.size() != that.myMap.size()) return false;
    final Iterator<Map.Entry<VirtualFile, T>> it1 = myMap.entrySet().iterator();
    final Iterator<Map.Entry<VirtualFile, T>> it2 = that.myMap.entrySet().iterator();

    while (it1.hasNext()) {
      if (! it2.hasNext()) return false;
      Map.Entry<VirtualFile, T> next1 = it1.next();
      Map.Entry<VirtualFile, T> next2 = it2.next();
      if (! Comparing.equal(next1.getKey(), next2.getKey()) || ! Comparing.equal(next1.getValue(), next2.getValue())) return false;
    }

    return true;
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
