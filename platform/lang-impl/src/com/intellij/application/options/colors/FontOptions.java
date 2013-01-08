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

package com.intellij.application.options.colors;

import com.intellij.application.options.OptionsConstants;
import com.intellij.application.options.SelectFontDialog;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FontOptions extends JPanel implements OptionsPanel{
  private final ColorAndFontOptions myOptions;

  private JTextField myEditorFontSizeField;

  private JTextField myLineSpacingField;
  private JTextField myFontNameField;

  private static ArrayList<String> myFontNames;
  private static ArrayList<String> myMonospacedFontNames;
  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);
  private boolean myIsInSchemeChange = false;
  private String myTitle;

  public FontOptions(ColorAndFontOptions options) {
    this(options, ApplicationBundle.message("group.editor.font"));
  }

  protected FontOptions(ColorAndFontOptions options, final String title) {
    super(new BorderLayout());
    myOptions = options;
    myTitle = title;

    JPanel schemesGroup = new JPanel(new BorderLayout());

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createEditorFontPanel(), BorderLayout.NORTH);
    schemesGroup.add(panel, BorderLayout.CENTER);
    add(schemesGroup, BorderLayout.CENTER);
  }

  @Override
  public void updateOptionsList() {
    myIsInSchemeChange = true;

    myLineSpacingField.setText(Float.toString(getLineSpacing()));
    myEditorFontSizeField.setText(Integer.toString(getCurrentFontSize()));
    myFontNameField.setText(getCurrentFontName());

    boolean enabled = !ColorAndFontOptions.isReadOnly(myOptions.getSelectedScheme());
    myLineSpacingField.setEnabled(enabled);
    myEditorFontSizeField.setEditable(enabled);
    myFontNameField.setEnabled(enabled);

    myIsInSchemeChange = false;

  }

  protected String getCurrentFontName() {
    return getCurrentScheme().getEditorFontName();
  }

  protected void setCurrentFontName(String fontName) {
    getCurrentScheme().setEditorFontName(fontName);
  }


  protected int getCurrentFontSize() {
    return getCurrentScheme().getEditorFontSize();
  }

  protected void setCurrentFontSize(int fontSize) {
    getCurrentScheme().setEditorFontSize(fontSize);
  }

  protected float getLineSpacing() {
    return getCurrentScheme().getLineSpacing();
  }

  protected void setCurrentLineSpacing(float lineSpacing) {
    getCurrentScheme().setLineSpacing(lineSpacing);
  }

  @Override
  @Nullable
  public Runnable showOption(final String option) {
    return null;
  }

  @Override
  public void applyChangesToScheme() {
  }

  @Override
  public void selectOption(final String typeToSelect) {
  }

  protected EditorColorsScheme getCurrentScheme() {
    return myOptions.getSelectedScheme();
  }

  private JPanel createEditorFontPanel() {
    JPanel editorFontPanel = new JPanel();
    editorFontPanel.setBorder(IdeBorderFactory.createTitledBorder(myTitle, false));
    editorFontPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 1;

    gbConstraints.insets = new Insets(0, 0, 0, 0);
    gbConstraints.gridwidth = 1;
    editorFontPanel.add(new JLabel(ApplicationBundle.message("label.font.name")), gbConstraints);

    myFontNameField = new MyTextField(20);
    myFontNameField.setEditable(false);
    myFontNameField.setFocusable(false);

    gbConstraints.gridx = 1;
    gbConstraints.insets = new Insets(0, 0, 0, 2);
    editorFontPanel.add(myFontNameField, gbConstraints);

    JButton myFontNameButton = new FixedSizeButton(myFontNameField);
    gbConstraints.gridx = 2;
    gbConstraints.insets = new Insets(0, 0, 0, 8);
    editorFontPanel.add(myFontNameButton, gbConstraints);

    gbConstraints.gridx = 3;
    gbConstraints.insets = new Insets(0, 0, 0, 0);
    editorFontPanel.add(new JLabel(ApplicationBundle.message("editbox.font.size")), gbConstraints);
    gbConstraints.gridx = 4;
    gbConstraints.insets = new Insets(0, 0, 0, 8);
    myEditorFontSizeField = new MyTextField(4);
    gbConstraints.gridx = 5;
    editorFontPanel.add(myEditorFontSizeField, gbConstraints);
    gbConstraints.insets = new Insets(0, 0, 0, 0);
    gbConstraints.gridx = 6;
    editorFontPanel.add(new JLabel(ApplicationBundle.message("editbox.line.spacing")), gbConstraints);
    gbConstraints.insets = new Insets(0, 0, 0, 0);
    gbConstraints.gridx = 7;
    myLineSpacingField = new MyTextField(4);
    editorFontPanel.add(myLineSpacingField, gbConstraints);
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 8;
    editorFontPanel.add(new TailPanel(), gbConstraints);

    myFontNameButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        EditorColorsScheme current = getCurrentScheme();
        if (ColorAndFontOptions.isReadOnly(current) || ColorSettingsUtil.isSharedScheme(current)) {
          showReadOnlyMessage(FontOptions.this, ColorSettingsUtil.isSharedScheme(current));
          return;
        }

        selectFont();
      }
    });

    myEditorFontSizeField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange) return;
        int fontSize = OptionsConstants.DEFAULT_EDITOR_FONT_SIZE;
        try {
          fontSize = Integer.parseInt(myEditorFontSizeField.getText());
        }
        catch (NumberFormatException e) {
          // OK, ignore
        }
        finally {
          if (fontSize < 1) fontSize = 1;
          if (fontSize > OptionsConstants.MAX_EDITOR_FONT_SIZE) fontSize = OptionsConstants.MAX_EDITOR_FONT_SIZE;

          setCurrentFontSize(fontSize);
          updateDescription(true);
        }
      }
    });

    myLineSpacingField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        if (myIsInSchemeChange) return;
        float lineSpacing = 1;
        try {
          lineSpacing = Float.parseFloat(myLineSpacingField.getText());
        }
        catch (NumberFormatException e) {
          // OK, ignore
        }
        finally {
          if (lineSpacing <= 0) lineSpacing = 1;
          if (lineSpacing > 30) lineSpacing = 30;
          if (getLineSpacing() != lineSpacing) {
            setCurrentLineSpacing(lineSpacing);
          }
          updateDescription(true);
        }
      }
    });

    return editorFontPanel;
  }

  private void selectFont() {
    initFontTables();

    ArrayList<String> fontNames = new ArrayList<String>(myFontNames);
    ArrayList<String> monospacedFontNames = new ArrayList<String>(myMonospacedFontNames);
    String initialFontName = myFontNameField.getText();
    if (!fontNames.contains(EditorSettingsExternalizable.DEFAULT_FONT_NAME)) {
      fontNames.add(0, EditorSettingsExternalizable.DEFAULT_FONT_NAME);
    }
    if (!fontNames.contains(initialFontName)) {
      fontNames.add(0, initialFontName);
    }
    SelectFontDialog selectFontDialog = new SelectFontDialog(this, fontNames, initialFontName, monospacedFontNames);
    selectFontDialog.show();
    if (!selectFontDialog.isOK()) {
      return;
    }
    String fontName = selectFontDialog.getFontName();
    if (fontName != null) {
      myFontNameField.setText(fontName);
      setCurrentFontName(fontName);
      updateDescription(true);
    }
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  private void initFontTables() {
    if (myFontNames == null) {
      myFontNames = new ArrayList<String>();
      myMonospacedFontNames = new ArrayList<String>();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new InitFontsRunnable(), ApplicationBundle.message("progress.analyzing.fonts"), false, null);
    }
  }

  public boolean updateDescription(boolean modified) {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    if (modified && (ColorAndFontOptions.isReadOnly(scheme) || ColorSettingsUtil.isSharedScheme(scheme))) {
      showReadOnlyMessage(this, ColorSettingsUtil.isSharedScheme(scheme));
      return false;
    }

    myDispatcher.getMulticaster().fontChanged();

    return true;
  }

  public static void showReadOnlyMessage(JComponent parent, final boolean sharedScheme) {
    if (!sharedScheme) {
      Messages.showMessageDialog(
          parent,
          ApplicationBundle.message("error.readonly.scheme.cannot.be.modified"),
          ApplicationBundle.message("title.cannot.modify.readonly.scheme"),
          Messages.getInformationIcon()
      );
    }
    else {
      Messages.showMessageDialog(
          parent,
          ApplicationBundle.message("error.shared.scheme.cannot.be.modified"),
          ApplicationBundle.message("title.cannot.modify.readonly.scheme"),
          Messages.getInformationIcon()
      );
    }
  }

  private class InitFontsRunnable implements Runnable {
    @Override
    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

      GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
      String[] fontNames = graphicsEnvironment.getAvailableFontFamilyNames();
      for (final String fontName : fontNames) {
        //noinspection HardCodedStringLiteral
        if (fontName.endsWith(".bold") || fontName.endsWith(".italic")) {
          continue;
        }
        try {
          Font plainFont = new Font(fontName, Font.PLAIN, OptionsConstants.DEFAULT_EDITOR_FONT_SIZE);
          if (plainFont.canDisplay('W')) {
            Font boldFont = plainFont.deriveFont(Font.BOLD);
            if (progress != null) {
              progress.setText(ApplicationBundle.message("progress.analysing.font", fontName));
            }
            FontMetrics plainMetrics = getFontMetrics(plainFont);
            FontMetrics boldMetrics = getFontMetrics(boldFont);
            if (plainMetrics.getDescent() < 0 ||
                boldMetrics.getDescent() < 0 ||
                plainMetrics.getAscent() < 0 ||
                boldMetrics.getAscent() < 0) {
              continue;
            }
            int plainL = plainMetrics.charWidth('l');
            int boldL = boldMetrics.charWidth('l');
            int plainW = plainMetrics.charWidth('W');
            int boldW = boldMetrics.charWidth('W');
            int plainSpace = plainMetrics.charWidth(' ');
            int boldSpace = boldMetrics.charWidth(' ');
            if (plainL <= 0 || boldL <= 0 || plainW <= 0 || boldW <= 0 || plainSpace <= 0 || boldSpace <= 0) {
              continue;
            }
            myFontNames.add(fontName);
            if (plainL == plainW && plainL == boldL && plainW == boldW && plainSpace == boldSpace) {
              myMonospacedFontNames.add(fontName);
            }
          }
        }
        catch (Throwable e) {
          // JRE has problems working with the font. Just skip.
        }
      }
    }
  }

  private static class MyTextField extends JTextField {
    private MyTextField(int size) {
      super(size);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }
  }

  @Override
  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public JPanel getPanel() {
    return this;
  }

  @Override
  public Set<String> processListOptions() {
    return new HashSet<String>();
  }
}
