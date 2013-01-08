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
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 29, 2003
 * Time: 9:15:30 PM
 * To change this template use Options | File Templates.
 */
public class PluginInstaller {

  private PluginInstaller() {}

  public static boolean prepareToInstall(List<PluginNode> pluginsToInstall, List<IdeaPluginDescriptor> allPlugins) {
    ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    final List<PluginId> pluginIds = new ArrayList<PluginId>();
    for (PluginNode pluginNode : pluginsToInstall) {
      pluginIds.add(pluginNode.getPluginId());
    }

    boolean result = false;

    for (final PluginNode pluginNode : pluginsToInstall) {
      if (pi != null) pi.setText(pluginNode.getName());

      try {
        result |= prepareToInstall(pluginNode, pluginIds, allPlugins);
      }
      catch (final IOException e) {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            Messages.showErrorDialog(pluginNode.getName() + ": " + e.getMessage(), CommonBundle.message("title.error"));
          }
        });
        return false;
      }
    }
    return result;
  }

  private static boolean prepareToInstall(final PluginNode pluginNode,
                                          final List<PluginId> pluginIds,
                                          List<IdeaPluginDescriptor> allPlugins) throws IOException {
    // check for dependent plugins at first.
    if (pluginNode.getDepends() != null && pluginNode.getDepends().size() > 0) {
      // prepare plugins list for install

      final PluginId[] optionalDependentPluginIds = pluginNode.getOptionalDependentPluginIds();
      final List <PluginNode> depends = new ArrayList <PluginNode> ();
      final List<PluginNode> optionalDeps = new ArrayList<PluginNode>();
      for (int i = 0; i < pluginNode.getDepends().size(); i++) {
        PluginId depPluginId = pluginNode.getDepends().get(i);

        if (PluginManager.isPluginInstalled(depPluginId) || PluginManager.isModuleDependency(depPluginId) ||
            (pluginIds != null && pluginIds.contains(depPluginId))) {
        //  ignore installed or installing plugins
          continue;
        }

        PluginNode depPlugin = new PluginNode(depPluginId);
        depPlugin.setSize("-1");
        depPlugin.setName(depPluginId.getIdString()); //prevent from exceptions

        if (isPluginInRepo(depPluginId, allPlugins)) {
          if (ArrayUtil.indexOf(optionalDependentPluginIds, depPluginId) != -1) {
            optionalDeps.add(depPlugin);
          }
          else {
            depends.add(depPlugin);
          }
        }
      }

      if (depends.size() > 0) { // has something to install prior installing the plugin
        final boolean [] proceed = new boolean[1];
        final StringBuffer buf = new StringBuffer();
        for (PluginNode depend : depends) {
          buf.append(depend.getName()).append(",");
        }
        try {
          GuiUtils.runOrInvokeAndWait(new Runnable() {
            public void run() {
              proceed[0] = Messages.showYesNoDialog(IdeBundle.message("plugin.manager.dependencies.detected.message", depends.size(), buf.substring(0, buf.length() - 1)),
                                                    IdeBundle.message("plugin.manager.dependencies.detected.title"), Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE;
            }
          });
        }
        catch (Exception e) {
          return false;
        }
        if (proceed[0]) {
          if (!prepareToInstall(depends, allPlugins)) {
            return false;
          }
        } else {
          return false;
        }
      }

      if (optionalDeps.size() > 0) {
        final StringBuffer buf = new StringBuffer();
        for (PluginNode depend : optionalDeps) {
          buf.append(depend.getName()).append(",");
        }
        final boolean [] proceed = new boolean[1];
        try {
          GuiUtils.runOrInvokeAndWait(new Runnable() {
            public void run() {
              proceed[0] = Messages.showYesNoDialog(IdeBundle.message("plugin.manager.optional.dependencies.detected.message", optionalDeps.size(),
                                                                      buf.substring(0, buf.length() - 1)), IdeBundle.message("plugin.manager.dependencies.detected.title"),
                                                    Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE;
            }
          });
        }
        catch (Exception e) {
          return false;
        }
        if (proceed[0]) {
          if (!prepareToInstall(optionalDeps, allPlugins)) {
            return false;
          }
        }
      }
    }

    synchronized (PluginManager.lock) {
      PluginDownloader downloader = null;
      final String repositoryName = pluginNode.getRepositoryName();
      if (repositoryName != null) {
        try {
          final ArrayList<PluginDownloader> downloaders = new ArrayList<PluginDownloader>();
          if (!UpdateChecker.checkPluginsHost(repositoryName, downloaders)) {
            return false;
          }
          for (PluginDownloader pluginDownloader : downloaders) {
            if (Comparing.strEqual(pluginDownloader.getPluginId(), pluginNode.getPluginId().getIdString())) {
              downloader = pluginDownloader;
              break;
            }
          }
          if (downloader == null) return false;
        }
        catch (Exception e) {
          return false;
        }
      } else {
        downloader = PluginDownloader.createDownloader(pluginNode);
      }
      if (downloader.prepareToInstall(ProgressManager.getInstance().getProgressIndicator())) {
        downloader.install();
        pluginNode.setStatus(PluginNode.STATUS_DOWNLOADED);
      } else {
        return false;
      }
    }

    return true;
  }

  private static boolean isPluginInRepo(PluginId depPluginId, List<IdeaPluginDescriptor> allPlugins) {
    for (IdeaPluginDescriptor plugin : allPlugins) {
      if (plugin.getPluginId().equals(depPluginId)) {
        return true;
      }
    }
    return false;
  }

  public static void prepareToUninstall (PluginId pluginId) throws IOException {
    synchronized (PluginManager.lock) {
      if (PluginManager.isPluginInstalled(pluginId)) {
        // add command to delete the 'action script' file
        IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);

        StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPath());
        StartupActionScriptManager.addActionCommand(deleteOld);
      }
    }
  }
}
