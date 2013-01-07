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

package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.Map;

public class FileEncodingConfigurable implements SearchableConfigurable, OptionalConfigurable, Configurable.NoScroll {
  private static final String SYSTEM_DEFAULT = IdeBundle.message("encoding.name.system.default");
  private final Project myProject;
  private FileTreeTable myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JCheckBox myAutodetectUTFEncodedFilesCheckBox;
  private JCheckBox myTransparentNativeToAsciiCheckBox;
  private JPanel myPropertiesFilesEncodingCombo;
  private Charset mySelectedCharsetForPropertiesFiles;
  private JComboBox myIdeEncodingsCombo;
  private JLabel myTitleLabel;
  private ChooseFileEncodingAction myAction;

  public FileEncodingConfigurable(Project project) {
    myProject = project;
    myTitleLabel.setText(myTitleLabel.getText().replace("$productName", ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Override
  @Nls
  public String getDisplayName() {
    return IdeBundle.message("file.encodings.configurable");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.file.encodings";
  }

  @Override
  @NotNull
  public String getId() {
    return "File.Encoding";
  }

  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myAction = new ChooseFileEncodingAction(null) {
      @Override
      public void update(final AnActionEvent e) {
        getTemplatePresentation().setEnabled(true);
        getTemplatePresentation().setText(mySelectedCharsetForPropertiesFiles == null ? SYSTEM_DEFAULT :
                                          mySelectedCharsetForPropertiesFiles.displayName());
      }

      @Override
      protected void chosen(final VirtualFile virtualFile, final Charset charset) {
        mySelectedCharsetForPropertiesFiles = charset == NO_ENCODING ? null : charset;
        update((AnActionEvent)null);
      }
    };
    myPropertiesFilesEncodingCombo.removeAll();
    Presentation templatePresentation = myAction.getTemplatePresentation();
    myPropertiesFilesEncodingCombo.add(myAction.createCustomComponent(templatePresentation), BorderLayout.CENTER);
    myTreeView = new FileTreeTable(myProject);
    myTreePanel.setViewportView(myTreeView);
    myTreeView.getEmptyText().setText(IdeBundle.message("file.encodings.not.configured"));
    return myPanel;
  }

  @Override
  public boolean isModified() {
    if (isEncodingModified()) return true;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);

    Map<VirtualFile, Charset> editing = myTreeView.getValues();
    Map<VirtualFile, Charset> mapping = EncodingProjectManager.getInstance(myProject).getAllMappings();
    boolean same = editing.equals(mapping)
       && Comparing.equal(encodingManager.getDefaultCharsetForPropertiesFiles(null), mySelectedCharsetForPropertiesFiles)
       && encodingManager.isUseUTFGuessing(null) == myAutodetectUTFEncodedFilesCheckBox.isSelected()
       && encodingManager.isNative2AsciiForPropertiesFiles() == myTransparentNativeToAsciiCheckBox.isSelected()
      ;
    return !same;
  }

  public boolean isEncodingModified() {
    final Object item = myIdeEncodingsCombo.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(item)) {
      return !StringUtil.isEmpty(EncodingManager.getInstance().getDefaultCharsetName());
    }

    return !Comparing.equal(item, EncodingManager.getInstance().getDefaultCharset());
  }

  @Override
  public void apply() throws ConfigurationException {
    Map<VirtualFile,Charset> result = myTreeView.getValues();
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    encodingManager.setMapping(result);
    encodingManager.setDefaultCharsetForPropertiesFiles(null, mySelectedCharsetForPropertiesFiles);
    encodingManager.setNative2AsciiForPropertiesFiles(null, myTransparentNativeToAsciiCheckBox.isSelected());
    encodingManager.setUseUTFGuessing(null, myAutodetectUTFEncodedFilesCheckBox.isSelected());

    final Object item = myIdeEncodingsCombo.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(item)) {
      EncodingManager.getInstance().setDefaultCharsetName("");
    }
    else if (item != null) {
      EncodingManager.getInstance().setDefaultCharsetName(((Charset)item).name());
    }
  }

  @Override
  public void reset() {
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(myProject);
    myTreeView.reset(encodingManager.getAllMappings());
    myAutodetectUTFEncodedFilesCheckBox.setSelected(encodingManager.isUseUTFGuessing(null));
    myTransparentNativeToAsciiCheckBox.setSelected(encodingManager.isNative2AsciiForPropertiesFiles());
    mySelectedCharsetForPropertiesFiles = encodingManager.getDefaultCharsetForPropertiesFiles(null);
    myAction.update((AnActionEvent)null);

    final DefaultComboBoxModel encodingsModel = new DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets());
    encodingsModel.insertElementAt(SYSTEM_DEFAULT, 0);
    myIdeEncodingsCombo.setModel(encodingsModel);

    final String name = EncodingManager.getInstance().getDefaultCharsetName();
    if (StringUtil.isEmpty(name)) {
      myIdeEncodingsCombo.setSelectedItem(SYSTEM_DEFAULT);
    }
    else {
      myIdeEncodingsCombo.setSelectedItem(EncodingManager.getInstance().getDefaultCharset());
    }
 }

  @Override
  public void disposeUIResources() {
    myAction = null;
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    myTreeView.select(virtualFile);
  }

  private void createUIComponents() {
    myTreePanel = ScrollPaneFactory.createScrollPane(new JBTable());
  }

  @Override
  public boolean needDisplay() {
    // TODO[yole] cleaner API
    return !PlatformUtils.isRubyMine();
  }
}
