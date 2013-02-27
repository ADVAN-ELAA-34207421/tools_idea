/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author max
 */
public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  public static final VirtualFileSystemEntry[] EMPTY_ARRAY = new VirtualFileSystemEntry[0];

  protected static final PersistentFS ourPersistence = PersistentFS.getInstance();

  private static final Key<String> SYMLINK_TARGET = Key.create("local.vfs.symlink.target");

  private static final int DIRTY_FLAG = 0x0100;
  private static final int IS_SYMLINK_FLAG = 0x0200;
  private static final int HAS_SYMLINK_FLAG = 0x0400;
  private static final int IS_SPECIAL_FLAG = 0x0800;
  private static final int INT_FLAGS_MASK = 0xff00;

  private static final String EMPTY = "";
  @NonNls private static final String[] WELL_KNOWN_SUFFIXES = {
    "$1.class", "$2.class", "Test.java", "List.java", "tion.java", ".class",
    ".java", ".html", ".txt", ".xml", ".php", ".gif", ".svn", ".css", ".js"
  };
  private static final byte[][] WELL_KNOWN_SUFFIXES_BYTES;
  private static final int[] WELL_KNOWN_SUFFIXES_LENGTH;
  private static final int SUFFIX_BITS = 4;
  private static final int SUFFIX_MASK = (1 << SUFFIX_BITS) - 1;
  private static final int SUFFIX_SHIFT = Short.SIZE - SUFFIX_BITS;

  static {
    WELL_KNOWN_SUFFIXES_BYTES = new byte[WELL_KNOWN_SUFFIXES.length][];
    WELL_KNOWN_SUFFIXES_LENGTH = new int[WELL_KNOWN_SUFFIXES.length];
    for (int i = 0; i < WELL_KNOWN_SUFFIXES.length; i++) {
      String suffix = WELL_KNOWN_SUFFIXES[i];
      WELL_KNOWN_SUFFIXES_BYTES[i] = suffix.getBytes(CharsetToolkit.UTF8_CHARSET);
      WELL_KNOWN_SUFFIXES_LENGTH[i] = suffix.length();
    }

    assert 1 << SUFFIX_BITS == WELL_KNOWN_SUFFIXES.length + 1;
  }

  /** Either a String or byte[]. Possibly should be concatenated with one of the entries in the {@link #WELL_KNOWN_SUFFIXES}. */
  private volatile Object myName;
  private volatile VirtualDirectoryImpl myParent;
  /** Also, high four bits are used as an index into the {@link #WELL_KNOWN_SUFFIXES} array. */
  private volatile short myFlags = 0;
  private volatile int myId;

  public VirtualFileSystemEntry(@NotNull String name, VirtualDirectoryImpl parent, int id, @PersistentFS.Attributes int attributes) {
    myParent = parent;
    myId = id;

    storeName(name);

    if (parent != null && parent != VirtualDirectoryImpl.NULL_VIRTUAL_FILE) {
      setFlagInt(IS_SYMLINK_FLAG, PersistentFS.isSymLink(attributes));
      setFlagInt(IS_SPECIAL_FLAG, PersistentFS.isSpecialFile(attributes));
      updateLinkStatus();
    }
  }

  private void storeName(@NotNull String name) {
    myFlags &= (1<<SUFFIX_SHIFT)-1;
    for (int i = 0; i < WELL_KNOWN_SUFFIXES.length; i++) {
      String suffix = WELL_KNOWN_SUFFIXES[i];
      if (name.endsWith(suffix)) {
        name = StringUtil.trimEnd(name, suffix);
        int mask = (i+1) << SUFFIX_SHIFT;
        myFlags |= mask;
        break;
      }
    }

    myName = encodeName(name.replace('\\', '/'));  // note: on Unix-style FS names may contain backslashes
  }

  private void updateLinkStatus() {
    boolean isSymLink = isSymLink();
    if (isSymLink) {
      String target = myParent.getFileSystem().resolveSymLink(this);
      putUserData(SYMLINK_TARGET, target != null ? FileUtil.toSystemIndependentName(target) : target);
    }
    setFlagInt(HAS_SYMLINK_FLAG, isSymLink || ((VirtualFileSystemEntry)myParent).getFlagInt(HAS_SYMLINK_FLAG));
  }

  @NotNull
  private static Object encodeName(@NotNull String name) {
    int length = name.length();
    if (length == 0) return ArrayUtil.EMPTY_BYTE_ARRAY;

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      char c = name.charAt(i);
      if (!IOUtil.isAscii(c)) {
        return name;
      }
      bytes[i] = (byte)c;
    }
    return bytes;
  }

  @NotNull
  private String getEncodedSuffix() {
    int index = getSuffixIndex();
    if (index == 0) return EMPTY;
    return WELL_KNOWN_SUFFIXES[index-1];
  }
  @NotNull
  private byte[] getEncodedSuffixBytes() {
    int index = getSuffixIndex();
    if (index == 0) return ArrayUtil.EMPTY_BYTE_ARRAY;
    return WELL_KNOWN_SUFFIXES_BYTES[index-1];
  }
  private int getEncodedSuffixLength() {
    int index = getSuffixIndex();
    if (index == 0) return 0;
    return WELL_KNOWN_SUFFIXES_LENGTH[index-1];
  }

  private int getSuffixIndex() {
    return (myFlags >> SUFFIX_SHIFT) & SUFFIX_MASK;
  }

  @Override
  @NotNull
  public String getName() {
    Object name = rawName();
    String suffix = getEncodedSuffix();
    if (name instanceof String) {
      //noinspection StringEquality
      return suffix == EMPTY ? (String)name : name + suffix;
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    char[] chars = new char[length + suffix.length()];
    for (int i = 0; i < length; i++) {
      chars[i] = (char)bytes[i];
    }
    copyString(chars, length, suffix);
    return StringFactory.createShared(chars);
  }

  int compareNameTo(@NotNull String name, boolean ignoreCase) {
    Object rawName = rawName();
    if (rawName instanceof String) {
      String thisName = getName();
      return compareNames(thisName, name, ignoreCase);
    }

    byte[] bytes = (byte[])rawName;
    int suffixLength = getEncodedSuffixLength();
    int bytesLength = bytes.length;
    int d = bytesLength + suffixLength - name.length();
    if (d != 0) return d;
    d = compare(bytes, 0, name, 0, bytesLength, ignoreCase);
    if (d != 0) return d;
    byte[] suffix = getEncodedSuffixBytes();
    d = compare(suffix, 0, name, bytesLength, suffixLength, ignoreCase);
    return d;
  }

  static int compareNames(@NotNull String name1, @NotNull String name2, boolean ignoreCase) {
    int d = name1.length() - name2.length();
    if (d != 0) return d;
    for (int i=0; i<name1.length(); i++) {
      // com.intellij.openapi.util.text.StringUtil.compare(String,String,boolean) inconsistent
      d = StringUtil.compare(name1.charAt(i), name2.charAt(i), ignoreCase);
      if (d != 0) return d;
    }
    return 0;
  }


  private static int compare(@NotNull byte[] name1, int offset1, @NotNull String name2, int offset2, int len, boolean ignoreCase) {
    for (int i1 = offset1, i2=offset2; i1 < offset1 + len; i1++, i2++) {
      char c1 = (char)name1[i1];
      char c2 = name2.charAt(i2);
      int d = StringUtil.compare(c1, c2, ignoreCase);
      if (d != 0) return d;
    }
    return 0;
  }

  protected Object rawName() {
    return myName;
  }

  @Override
  public VirtualFileSystemEntry getParent() {
    return myParent;
  }

  @Override
  public boolean isDirty() {
    return (myFlags & DIRTY_FLAG) != 0;
  }

  @Override
  public boolean getFlag(int mask) {
    assert (mask & INT_FLAGS_MASK) == 0 : "Mask '" + Integer.toBinaryString(mask) + "' is in reserved range.";
    return getFlagInt(mask);
  }

  private boolean getFlagInt(int mask) {
    return (myFlags & mask) != 0;
  }

  @Override
  public void setFlag(int mask, boolean value) {
    assert (mask & INT_FLAGS_MASK) == 0 : "Mask '" + Integer.toBinaryString(mask) + "' is in reserved range.";
    setFlagInt(mask, value);
  }

  private void setFlagInt(int mask, boolean value) {
    if (value) {
      myFlags |= mask;
    }
    else {
      myFlags &= ~mask;
    }
  }

  @Override
  public void markClean() {
    setFlagInt(DIRTY_FLAG, false);
  }

  @Override
  public void markDirty() {
    if (!isDirty()) {
      markDirtyInternal();
      VirtualDirectoryImpl parent = myParent;
      if (parent != null) parent.markDirty();
    }
  }

  protected void markDirtyInternal() {
    setFlagInt(DIRTY_FLAG, true);
  }

  @Override
  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((NewVirtualFile)file).markDirtyRecursively();
    }
  }

  protected char[] appendPathOnFileSystem(int pathLength, int[] position) {
    Object o = rawName();
    String suffix = getEncodedSuffix();
    int rawNameLength = o instanceof String ? ((String)o).length() : ((byte[])o).length;
    int nameLength = rawNameLength + suffix.length();
    boolean appendSlash = SystemInfo.isWindows && myParent == null && suffix.isEmpty() && rawNameLength == 2 &&
                          (o instanceof String ? ((String)o).charAt(1) : (char)((byte[])o)[1]) == ':';

    char[] chars;
    if (myParent != null) {
      chars = myParent.appendPathOnFileSystem(pathLength + 1 + nameLength, position);
      if (position[0] > 0 && chars[position[0] - 1] != '/') {
        chars[position[0]++] = '/';
      }
    }
    else {
      int rootPathLength = pathLength + nameLength;
      if (appendSlash) ++rootPathLength;
      chars = new char[rootPathLength];
    }

    if (o instanceof String) {
      position[0] = copyString(chars, position[0], (String)o);
    }
    else {
      byte[] bytes = (byte[])o;
      int pos = position[0];
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, len = bytes.length; i < len; i++) {
        chars[pos++] = (char)bytes[i];
      }
      position[0] = pos;
    }

    if (appendSlash) {
      chars[position[0]++] = '/';
    }
    else {
      position[0] = copyString(chars, position[0], suffix);
    }

    return chars;
  }

  private static int copyString(@NotNull char[] chars, int pos, @NotNull String s) {
    int length = s.length();
    s.getChars(0, length, chars, pos);
    return pos + length;
  }

  @Override
  @NotNull
  public String getUrl() {
    String protocol = getFileSystem().getProtocol();
    int prefixLen = protocol.length() + "://".length();
    int[] pos = {prefixLen};
    char[] chars = appendPathOnFileSystem(prefixLen, pos);
    copyString(chars, copyString(chars, 0, protocol), "://");
    return chars.length == pos[0] ? StringFactory.createShared(chars) : new String(chars, 0, pos[0]);
  }

  @Override
  @NotNull
  public String getPath() {
    int[] pos = {0};
    char[] chars = appendPathOnFileSystem(0, pos);
    return chars.length == pos[0] ? StringFactory.createShared(chars) : new String(chars, 0, pos[0]);
  }

  @Override
  public void delete(final Object requestor) throws IOException {
    ourPersistence.deleteFile(requestor, this);
  }

  @Override
  public void rename(final Object requestor, @NotNull @NonNls final String newName) throws IOException {
    if (getName().equals(newName)) return;
    if (!isValidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

    ourPersistence.renameFile(requestor, this, newName);
  }

  @Override
  @NotNull
  public VirtualFile createChildData(final Object requestor, @NotNull final String name) throws IOException {
    validateName(name);
    return ourPersistence.createChildFile(requestor, this, name);
  }

  @Override
  public boolean isWritable() {
    return ourPersistence.isWritable(this);
  }

  @Override
  public void setWritable(boolean writable) throws IOException {
    ourPersistence.setWritable(this, writable);
  }

  @Override
  public long getTimeStamp() {
    return ourPersistence.getTimeStamp(this);
  }

  @Override
  public void setTimeStamp(final long time) throws IOException {
    ourPersistence.setTimeStamp(this, time);
  }

  @Override
  public long getLength() {
    return ourPersistence.getLength(this);
  }

  @Override
  public VirtualFile copy(final Object requestor, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VfsBundle.message("file.copy.target.must.be.directory"));
    }

    return EncodingRegistry.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return ourPersistence.copyFile(requestor, VirtualFileSystemEntry.this, newParent, copyName);
      }
    });
  }

  @Override
  public void move(final Object requestor, @NotNull final VirtualFile newParent) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        ourPersistence.moveFile(requestor, VirtualFileSystemEntry.this, newParent);
        return VirtualFileSystemEntry.this;
      }
    });
  }

  @Override
  public int getId() {
    return myId;
  }

  @Override
  public int hashCode() {
    int id = myId;
    return id >= 0 ? id : -id;
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, final String name) throws IOException {
    validateName(name);
    return ourPersistence.createChildDirectory(requestor, this, name);
  }

  private static void validateName(String name) throws IOException {
    if (name == null || name.isEmpty()) throw new IOException("File name cannot be empty");
    if (name.indexOf('/') >= 0 || name.indexOf(File.separatorChar) >= 0) {
      throw new IOException("File name cannot contain file path separators: '" + name + "'");
    }
  }

  @Override
  public boolean exists() {
    return ourPersistence.exists(this);
  }

  @Override
  public boolean isValid() {
    return exists();
  }

  public String toString() {
    return getUrl();
  }

  public void setNewName(@NotNull final String newName) {
    if (newName.isEmpty()) {
      throw new IllegalArgumentException("Name of the virtual file cannot be set to empty string");
    }

    myParent.removeChild(this);
    storeName(newName);
    myParent.addChild(this);
  }

  public void setParent(@NotNull final VirtualFile newParent) {
    myParent.removeChild(this);
    myParent = (VirtualDirectoryImpl)newParent;
    myParent.addChild(this);
    updateLinkStatus();
  }

  @Override
  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  public void invalidate() {
    myId = -Math.abs(myId);
  }

  @Override
  public Charset getCharset() {
    return isCharsetSet() ? super.getCharset() : computeCharset();
  }

  private Charset computeCharset() {
    Charset charset;
    if (isDirectory()) {
      Charset configured = EncodingManager.getInstance().getEncoding(this, true);
      charset = configured == null ? Charset.defaultCharset() : configured;
      setCharset(charset);
    }
    else if (SingleRootFileViewProvider.isTooLargeForContentLoading(this)) {
      charset = super.getCharset();
    }
    else {
      try {
        final byte[] content;
        try {
          content = contentsToByteArray();
        }
        catch (FileNotFoundException e) {
          // file has already been deleted on disk
          return super.getCharset();
        }
        charset = LoadTextUtil.detectCharsetAndSetBOM(this, content);
      }
      catch (FileTooBigException e) {
        return super.getCharset();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return charset;
  }

  @Override
  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS && !isDirectory()) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.isEmpty() ? getName() : nameWithoutExtension;
    }
    return getName();
  }

  @Override
  public boolean isSymLink() {
    return getFlagInt(IS_SYMLINK_FLAG);
  }

  @Override
  public boolean isSpecialFile() {
    return getFlagInt(IS_SPECIAL_FLAG);
  }

  @Override
  public String getCanonicalPath() {
    if (getFlagInt(HAS_SYMLINK_FLAG)) {
      if (isSymLink()) {
        return getUserData(SYMLINK_TARGET);
      }
      VirtualDirectoryImpl parent = myParent;
      if (parent != null) {
        return parent.getCanonicalPath() + "/" + getName();
      }
      return getName();
    }
    return getPath();
  }

  @Override
  public NewVirtualFile getCanonicalFile() {
    if (getFlagInt(HAS_SYMLINK_FLAG)) {
      final String path = getCanonicalPath();
      return path != null ? (NewVirtualFile)getFileSystem().findFileByPath(path) : null;
    }
    return this;
  }
}
