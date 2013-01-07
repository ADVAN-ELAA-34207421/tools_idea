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
package com.intellij.execution.configurations;

import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.execution.RunManager
 *
 * If debugger is provided by plugin, RunConfiguration should also implement RunConfigurationWithSuppressedDefaultDebugAction
 * Otherwise (in case of disabled plugin) debug action may be enabled in UI but with no reaction
 * @see RunConfigurationWithSuppressedDefaultDebugAction
 */
public interface RunConfiguration extends RunProfile, JDOMExternalizable, Cloneable {
  ConfigurationFactory getFactory();

  void setName(String name);

  SettingsEditor<? extends RunConfiguration> getConfigurationEditor();

  Project getProject();

  @NotNull
  ConfigurationType getType();

  @Nullable
  JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider);

  @Nullable
  SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(ProgramRunner runner);

  RunConfiguration clone();

  int getUniqueID();
}
