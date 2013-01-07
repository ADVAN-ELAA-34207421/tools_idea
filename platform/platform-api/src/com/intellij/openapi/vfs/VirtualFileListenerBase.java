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

package com.intellij.openapi.vfs;

@Deprecated
/**
 * use {@link com.intellij.openapi.vfs.VirtualFileAdapter} instead
 */
public class VirtualFileListenerBase implements VirtualFileListener{
  public void propertyChanged(VirtualFilePropertyEvent event) {}
  public void contentsChanged(VirtualFileEvent event) {}
  public void fileCreated(VirtualFileEvent event) {}
  public void fileDeleted(VirtualFileEvent event) {}
  public void fileMoved(VirtualFileMoveEvent event) {}
  public void beforePropertyChange(VirtualFilePropertyEvent event) {}
  public void beforeContentsChange(VirtualFileEvent event) {}
  public void beforeFileDeletion(VirtualFileEvent event) {}
  public void fileCopied(final VirtualFileCopyEvent event) {
    fileCreated(event);
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {}
}
