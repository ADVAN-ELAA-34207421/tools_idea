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
package com.jetbrains.python.psi;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public enum LanguageLevel {
  PYTHON24(24, false, true, false, false),
  PYTHON25(25, false, true, false, false),
  PYTHON26(26, true, true, false, false),
  PYTHON27(27, true, true, true, false),
  PYTHON30(30, true, false, false, true),
  PYTHON31(31, true, false, true, true),
  PYTHON32(32, true, false, true, true),
  PYTHON33(33, true, false, true, true);

  public static LanguageLevel FORCE_LANGUAGE_LEVEL = null;

  public static LanguageLevel getDefault() {
    return PYTHON26;
  }

  private final int myVersion;

  private final boolean myHasWithStatement;
  private final boolean myHasPrintStatement;
  private final boolean mySupportsSetLiterals;
  private final boolean myIsPy3K;

  LanguageLevel(int version, boolean hasWithStatement, boolean hasPrintStatement, boolean supportsSetLiterals, boolean isPy3K) {
    myVersion = version;
    myHasWithStatement = hasWithStatement;
    myHasPrintStatement = hasPrintStatement;
    mySupportsSetLiterals = supportsSetLiterals;
    myIsPy3K = isPy3K;
  }

  /**
   * @return an int where major and minor version are represented decimally: "version 2.5" is 25.
   */
  public int getVersion() {
    return myVersion;
  }

  public boolean hasWithStatement() {
    return myHasWithStatement;
  }

  public boolean hasPrintStatement() {
    return myHasPrintStatement;
  }

  public boolean supportsSetLiterals() {
    return mySupportsSetLiterals;
  }

  public boolean isPy3K() {
    return myIsPy3K;
  }

  public boolean isOlderThan(@NotNull LanguageLevel other) {
    return myVersion < other.myVersion;
  }

  public boolean isAtLeast(@NotNull LanguageLevel other) {
    return myVersion >= other.myVersion;
  }

  public static LanguageLevel fromPythonVersion(@NotNull String pythonVersion) {
    if (pythonVersion.startsWith("2.7")) {
      return PYTHON27;
    }
    if (pythonVersion.startsWith("2.6")) {
      return PYTHON26;
    }
    if (pythonVersion.startsWith("2.5")) {
      return PYTHON25;
    }
    if (pythonVersion.startsWith("3")) {
      if (pythonVersion.startsWith("3.0")) {
        return PYTHON30;
      }
      if (pythonVersion.startsWith("3.1")) {
        return PYTHON31;
      }
      if (pythonVersion.startsWith("3.2")) {
        return PYTHON32;
      }
      return PYTHON33;
    }
    return PYTHON24;
  }

  public static final Key<LanguageLevel> KEY = new Key<LanguageLevel>("python.language.level");

  @NotNull
  public static LanguageLevel forFile(@NotNull VirtualFile virtualFile) {
    if (virtualFile instanceof VirtualFileWindow)
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();

    // Most of the cases should be handled by this one, PyLanguageLevelPusher pushes folders only
    final VirtualFile folder = virtualFile.getParent();
    if (folder != null) {
      final LanguageLevel level = folder.getUserData(KEY);
      if (level != null) return level;
    }
    else {
      // However this allows us to setup language level per file manually
      // in case when it is LightVirtualFile
      final LanguageLevel level = virtualFile.getUserData(KEY);
      if (level != null) return level;

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final LanguageLevel languageLevel = FORCE_LANGUAGE_LEVEL;
        if (languageLevel != null) {
          return languageLevel;
        }
      }
    }

    return getDefault();
  }

  @NotNull
  public static LanguageLevel forElement(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      return ((PyFile) containingFile).getLanguageLevel();
    }
    return getDefault();
  }

  @Override
  public String toString() {
    return myVersion / 10 + "." + myVersion % 10;
  }
}
