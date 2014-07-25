/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class DebuggerSettingsPanelProvider {
  public int getPriority() {
    return 0;
  }

  @NotNull
  public Collection<? extends Configurable> getConfigurables() {
    return Collections.emptyList();
  }

  public void apply() {
  }

  @Nullable
  @Deprecated
  public Configurable getRootConfigurable() {
    return null;
  }

  @NotNull
  public Collection<? extends Configurable> getConfigurable(@NotNull XDebuggerSettings.Category category) {
    return Collections.emptyList();
  }

  /**
   * General settings of category were applied
   */
  public void generalApplied(@NotNull XDebuggerSettings.Category category) {
  }
}
