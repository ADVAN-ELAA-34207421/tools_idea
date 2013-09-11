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
package com.intellij.ide.actions;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereAction extends AnAction implements CustomComponentAction {
  private final SearchEverywhereAction.MyListRenderer myRenderer;
  private final JPanel myContentPanel;
  SearchTextField field;
  SearchTextField myPopupField;
  private GotoClassModel2 myClassModel;
  private GotoFileModel myFileModel;
  private GotoActionModel myActionModel;
  private String[] myClasses;
  private String[] myFiles;
  private String[] myActions;
  private Component myFocusComponent;
  private JBPopup myPopup;
  private int myLeftWidth;
  private SearchListModel myListModel = new SearchListModel();
  private TitleIndexes myTitleIndexes;
  private Map<String, String> myConfigurables = new HashMap<String, String>();

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private JBList myList = new JBList(myListModel) {
    {
      setOpaque(false);
    }
    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(getTitlePanelBackground());
      g.fillRect(0, 0, myLeftWidth - 1, getHeight());
      g.setColor(getSeparatorColor());
      g.drawLine(myLeftWidth-1, 0, myLeftWidth-1, getHeight());
      super.paintComponent(g);
    }
  };
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private static AtomicBoolean ourShiftCanBeUsed = new AtomicBoolean(false);
  private ArrayList<VirtualFile> myAlreadyAddedFiles = new ArrayList<VirtualFile>();

  static {
    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent event) {
        if (event instanceof KeyEvent) {
          ourShiftCanBeUsed.set((((KeyEvent)event).getKeyCode() != KeyEvent.VK_SHIFT) || event.getID() != KeyEvent.KEY_PRESSED);
        }
        return false;
      }
    }, null);
  }

  private int myTopHitsCount;
  private int myInitialWidth;
  private int myInitialHeight;
  private Balloon myBalloon;
  private JLabel mySearchLabel;

  public SearchEverywhereAction() {
    myContentPanel = new JPanel(new BorderLayout());
    mySearchLabel = new JBLabel(AllIcons.Actions.Find) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK);
      }
    };
    mySearchLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myContentPanel.remove(mySearchLabel);
        field.getTextEditor().setColumns(25);
        myContentPanel.add(field, BorderLayout.CENTER);
        myContentPanel.revalidate();
        myContentPanel.repaint();
        IdeFocusManager.findInstanceByComponent(field.getTextEditor()).requestFocus(field.getTextEditor(), true);
      }
    });

    createSearchField();
    LafManager.getInstance().addLafManagerListener(new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(LafManager source) {
        createSearchField();
      }
    });
    myRenderer = new MyListRenderer();
    myList.setCellRenderer(myRenderer);
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final int i = myList.locationToIndex(e.getPoint());
        if (i != -1) {
          myList.setSelectedIndex(i);
          doNavigate();
        }
      }
    });
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        onFocusLost(field.getTextEditor());
      }
    });

  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!ourShiftCanBeUsed.get() && Registry.is("search.everywhere.enabled"));
  }


  private void createSearchField() {
    field = new MySearchTextField();
    initSearchField(field);
  }

  private void initSearchField(final SearchTextField search) {
    final JTextField editor = search.getTextEditor();
    onFocusLost(editor);
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        final int len = pattern.trim().length();
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (search.getTextEditor().hasFocus()) {
              rebuildList(pattern);
            }
          }
        }, /*Registry.intValue("ide.goto.rebuild.delay")*/ len == 1 ? 400 : len == 2 ? 300 : len == 3 ? 250 : 30);
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        search.setText("");
        search.getTextEditor().setForeground(UIUtil.getLabelForeground());
        myTitleIndexes = new TitleIndexes();
        editor.setColumns(25);
        myFocusComponent = e.getOppositeComponent();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final JComponent parent = (JComponent)editor.getParent();
            parent.revalidate();
            parent.repaint();
          }
        });
        if (myPopup != null && myPopup.isVisible()) {
          myPopup.cancel();
          myPopup = null;
        }
        rebuildList("");
      }

      @Override
      public void focusLost(FocusEvent e) {
        if ( myPopup instanceof AbstractPopup && myPopup.isVisible() && ((AbstractPopup)myPopup).getPopupWindow() == e.getOppositeComponent()) {
          return;
        }
        onFocusLost(editor);
      }
    });

    editor.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE) {
          if (myPopup != null && myPopup.isVisible()) {
            myPopup.cancel();
          }
          IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(editor);
          focusManager.requestDefaultFocus(true);
        }
        else if (keyCode == KeyEvent.VK_ENTER) {
          doNavigate();
        }
      }
    });
  }

  private void onFocusLost(final JTextField editor) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        editor.setColumns(SystemInfo.isMac ? 5 : 8);
        field.getTextEditor().setForeground(UIUtil.getLabelDisabledForeground());
        field.setText(" " + KeymapUtil.getFirstKeyboardShortcutText(SearchEverywhereAction.this));
        if (myCalcThread != null) {
          myCalcThread.cancel();
          myCalcThread = null;
        }
        myAlarm.cancelAllRequests();
        myListModel.clear();
        myContentPanel.remove(field);
        myContentPanel.add(mySearchLabel);

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myContentPanel.revalidate();
            myContentPanel.repaint();
          }
        });
      }
    });
  }

  private SearchTextField getField() {
    return myBalloon != null ? myPopupField : field;
  }

  private void doNavigate() {
    final String pattern = getField().getText();
    final Object value = myList.getSelectedValue();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(field.getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      if (value instanceof PsiElement) {
        NavigationUtil.activateFileWithPsiElement((PsiElement)value, true);
        return;
      }
      else if (isVirtualFile(value)) {
        OpenFileDescriptor navigatable = new OpenFileDescriptor(project, (VirtualFile)value);
        if (navigatable.canNavigate()) {
          navigatable.navigate(true);
          return;
        }
      }
      else {
        focusManager.requestDefaultFocus(true);
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            if (value instanceof BooleanOptionDescription) {
              final BooleanOptionDescription option = (BooleanOptionDescription)value;
              option.setOptionState(!option.isOptionEnabled());
            }
            else {
              GotoActionAction.openOptionOrPerformAction(value, pattern, project, myContextComponent, myActionEvent);
            }
          }
        });
        return;
      }
    }
    finally {
      token.finish();
      onFocusLost(field.getTextEditor());
    }
    focusManager.requestDefaultFocus(true);
  }

  private void rebuildList(final String pattern) {
    if (myCalcThread != null) {
      myCalcThread.cancel();
    }
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));

    assert project != null;
    myRenderer.myProject = project;
    myCalcThread = new CalcThread(project, pattern);
    myCalcThread.start();
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    myActionEvent = e;
    myPopupField = new MySearchTextField();
    myPopupField.setOpaque(true);
    initSearchField(myPopupField);
    myPopupField.getTextEditor().setColumns(25);
    myBalloon = JBPopupFactory.getInstance().createBalloonBuilder(myPopupField)
      .setShowCallout(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(true)
      .setAnimationCycle(0)
      .setDialogMode(false)
      .setBorderColor(new JBColor(new Color(156, 192, 255), new Color(77, 77, 77)))
      .setFillColor(new JBColor(new Color(77, 121, 231), new Color(60, 63, 65)))
      .createBalloon();
    myBalloon.show(JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext()), Balloon.Position.below);

    IdeFocusManager focusManager = IdeFocusManager.getInstance(e.getProject());
    focusManager.requestFocus(myPopupField.getTextEditor(), true);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return myContentPanel;
  }

  private static class MySearchTextField extends SearchTextField implements DataProvider {
    public MySearchTextField() {
      super(false);
      setOpaque(false);
      getTextEditor().setOpaque(false);
    }

    @Override
    protected void showPopup() {
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
        return getTextEditor().getText();
      }
      return null;
    }
  }

  private class MyListRenderer extends ColoredListCellRenderer {
    ColoredListCellRenderer myLocation = new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(myLocationString, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(myLocationIcon);
      }
    };
    private String myLocationString;
    private DefaultPsiElementCellRenderer myPsiRenderer = new DefaultPsiElementCellRenderer();
    private Icon myLocationIcon;
    private Project myProject;
    private JPanel myMainPanel = new JPanel(new BorderLayout());
    private JLabel myTitle = new JLabel();
    private JPanel myLeftPanel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getMinimumSize() {
        return new Dimension(myLeftWidth, super.getMinimumSize().height);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myLeftWidth, super.getPreferredSize().height);
      }
    };

    @Override
    public void clear() {
      super.clear();
      myLocation.clear();
      myLocationString = null;
      myLocationIcon = null;
    }

    public void setLocationString(String locationString) {
      myLocationString = locationString;
    }

    public void setLocationIcon(Icon locationIcon) {
      myLocationIcon = locationIcon;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component cmp;
      PsiFile file;
      myLocationString = null;
      if (value instanceof VirtualFile && myProject != null && (file = PsiManager.getInstance(myProject).findFile((VirtualFile)value)) != null) {
          cmp = new GotoFileCellRenderer(list.getWidth()).getListCellRendererComponent(list, file, index, isSelected, cellHasFocus);
      } else if (value instanceof PsiElement) {
        cmp = myPsiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      } else {
        cmp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
      if (myLocationString != null || value instanceof BooleanOptionDescription) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(cmp, BorderLayout.CENTER);
        final Component rightComponent;
        if (value instanceof BooleanOptionDescription) {
          final OnOffButton button = new OnOffButton();
          button.setSelected(((BooleanOptionDescription)value).isOptionEnabled());
          rightComponent = button;
        }
        else {
          rightComponent = myLocation.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
        panel.add(rightComponent, BorderLayout.EAST);
        cmp = panel;
      }

      cmp.setBackground(isSelected ? UIUtil.getListSelectionBackground() : getRightBackground());
      String title = myTitleIndexes.getTitle(index);
      myTitle.setText(title == null ? "" : title);
      myLeftPanel.removeAll();
      myLeftPanel.setBackground(getTitlePanelBackground());
      myMainPanel.removeAll();
      myLeftPanel.add(myTitle, BorderLayout.EAST);
      myMainPanel.add(myLeftPanel, BorderLayout.WEST);
      myMainPanel.add(cmp, BorderLayout.CENTER);
      return myMainPanel;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setIcon(EmptyIcon.ICON_16);
      AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        if (value instanceof PsiElement) {
          String name = myClassModel.getElementName(value);
          assert name != null;
          append(name);
        }
        else if (isVirtualFile(value)) {
          append(((VirtualFile)value).getName());
        }
        else if (isActionValue(value)) {
          final Map.Entry actionWithParentGroup = value instanceof Map.Entry ? (Map.Entry)value : null;
          final AnAction anAction = actionWithParentGroup == null ? (AnAction)value : (AnAction)actionWithParentGroup.getKey();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          final Icon icon = templatePresentation.getIcon();

          final DataContext dataContext = DataManager.getInstance().getDataContext(myContextComponent);

          final AnActionEvent event = GotoActionModel.updateActionBeforeShow(anAction, dataContext);
          final Presentation presentation = event.getPresentation();

          append(templatePresentation.getText());

          final String groupName = actionWithParentGroup == null ? null : (String)actionWithParentGroup.getValue();
          if (!StringUtil.isEmpty(groupName)) {
            setLocationString(groupName);
          }
          if (icon != null && icon.getIconWidth() <= 16 && icon.getIconHeight() <= 16) {
            setIcon(icon);
          }
        }
        else if (isSetting(value)) {
          String hit = ((OptionDescription)value).getHit();
          if (hit == null) {
            hit = ((OptionDescription)value).getOption();
          }
          hit = StringUtil.unescapeXml(hit);
          if (hit.length() > 60) {
            hit = hit.substring(0, 60) + "...";
          }
          hit = hit.replace("  ", " "); //avoid extra spaces from mnemonics and xml conversion
          String text = hit.trim();
          if (text.endsWith(":")) {
            text = text.substring(0, text.length() - 1);
          }
          append(text);
          final String id = ((OptionDescription)value).getConfigurableId();
          final String name = myConfigurables.get(id);
          if (name != null) {
            setLocationString(name);
          }
        }
      }
      finally {
        token.finish();
      }
    }

    public void recalculateWidth() {
      myLeftWidth = 16;
      ListModel model = myList.getModel();
      myTitle.setIcon(EmptyIcon.ICON_16);
      myTitle.setFont(getTitleFont());
      int index = 0;
      while (index < model.getSize()) {
        Object el = model.getElementAt(index);
        Object prev = index == 0 ? null : model.getElementAt(index - 1);
        String title = myTitleIndexes.getTitle(index);
        if (title != null) {
          myTitle.setText(title);
          myLeftWidth = Math.max(myLeftWidth, myTitle.getPreferredSize().width);
        }
        index++;
      }

      //myLeftWidth += 10;
      myTitle.setForeground(Gray._122);
      myTitle.setAlignmentY(BOTTOM_ALIGNMENT);
      myLeftPanel.setBorder(new CompoundBorder(new CustomLineBorder(getSeparatorColor(), 0, 0, 0, 1), new EmptyBorder(0, 0, 0, 5)));
    }

    private Font getTitleFont() {
      return UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }
  }

  private static JBColor getTitlePanelBackground() {
    return new JBColor(Gray._242, JBColor.background());
  }

  private static Color getRightBackground() {
    return UIUtil.isUnderAquaLookAndFeel() ? Gray._236 : UIUtil.getListBackground();
  }

  private static JBColor getSeparatorColor() {
    return new JBColor(Gray._206, Gray._75);
  }

  private static boolean isActionValue(Object o) {
    return o instanceof Map.Entry || o instanceof AnAction;
  }

  private static boolean isSetting(Object o) {
    return o instanceof OptionDescription;
  }

  private static boolean isVirtualFile(Object o) {
    return o instanceof VirtualFile;
  }

  @SuppressWarnings("SSBasedInspection")
  private class CalcThread implements Runnable {
    private final Project project;
    private final String pattern;
    private ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();

    public CalcThread(Project project, String pattern) {
      this.project = project;
      this.pattern = pattern;
    }

    @Override
    public void run() {
      //noinspection SSBasedInspection
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          myTitleIndexes.clear();
          myListModel.clear();
          myAlreadyAddedFiles.clear();
        }
      });
      if (pattern.trim().length() == 0) {
        buildModelFromRecentFiles();
        updatePopup();
        return;
      }

      checkModelsUpToDate();
      buildTopHit(pattern);
      buildRecentFiles(pattern);
      updatePopup();
      AccessToken readLock = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        buildClasses(pattern);
      } finally {readLock.finish();}
      updatePopup();
      readLock = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        buildFiles(pattern);
      } finally {readLock.finish();}
      buildActionsAndSettings(pattern);
      updatePopup();
    }

    private void buildActionsAndSettings(String pattern) {
      final Set<AnAction> actions = new HashSet<AnAction>();
      final Set<Object> settings = new HashSet<Object>();
      final HashSet<AnAction> toolWindows = new HashSet<AnAction>();

      List<MatchResult> matches = collectResults(pattern, myActions, myActionModel);

      for (MatchResult o : matches) {
        //if (actionsCount > 15) break;
        myProgressIndicator.checkCanceled();
        Object[] objects = myActionModel.getElementsByName(o.elementName, true, pattern);
        for (Object object : objects) {
          myProgressIndicator.checkCanceled();
          if (isSetting(object)) {
            settings.add(object);
          }
          else if (isToolWindowAction(object)) {
            toolWindows.add((AnAction)((Map.Entry)object).getKey());
          }
          else if (isActionValue(object)) {
            actions.add((AnAction)((Map.Entry)object).getKey());
          }
          if (actions.size() + settings.size() + toolWindows.size() > 15) break;
        }
      }

      myProgressIndicator.checkCanceled();

      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (myProgressIndicator.isCanceled()) return;
          if (toolWindows.size() > 0) {
            myTitleIndexes.toolWindows = myListModel.size();
            for (Object toolWindow : toolWindows) {
              myListModel.addElement(toolWindow);
            }
          }
          if (actions.size() > 0) {
            myTitleIndexes.actions = myListModel.size();
            for (Object action : actions) {
              myListModel.addElement(action);
            }
          }
          if (settings.size() > 0) {
            myTitleIndexes.settings = myListModel.size();
            for (Object setting : settings) {
              myListModel.addElement(setting);
            }
          }
        }
      });
    }

    private void buildFiles(String pattern) {
      int filesCounter = 0;
      List<MatchResult> matches = collectResults(pattern, myFiles, myFileModel);
      final List<VirtualFile> files = new ArrayList<VirtualFile>();

      for (MatchResult o : matches) {
        if (filesCounter > 15) break;
        Object[] objects = myFileModel.getElementsByName(o.elementName, false, pattern, myProgressIndicator);
        for (Object object : objects) {
          if (!myListModel.contains(object)) {
            if (object instanceof PsiFile) {
              object = ((PsiFile)object).getVirtualFile();
            }
            if (object instanceof VirtualFile &&
                !myAlreadyAddedFiles.contains((VirtualFile)object) &&
                !((VirtualFile)object).isDirectory()) {
              files.add((VirtualFile)object);
              filesCounter++;
            }
          }
          if (filesCounter > 15) break;
        }
      }

      myProgressIndicator.checkCanceled();

      if (files.size() > 0) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.files = myListModel.size();
              for (Object file : files) {
                myListModel.addElement(file);
              }
            }
          }
        });
      }
    }

    private void buildClasses(String pattern) {
      int clsCounter = 0;
      List<MatchResult> matches = collectResults(pattern, myClasses, myClassModel);
      final List<Object> classes = new ArrayList<Object>();
      for (MatchResult matchResult : matches) {
        if (clsCounter > 15) break;

        Object[] objects = myClassModel.getElementsByName(matchResult.elementName, false, pattern, myProgressIndicator);
        for (Object object : objects) {
          if (!myListModel.contains(object)) {
            if (object instanceof PsiElement) {
              VirtualFile file = PsiUtilCore.getVirtualFile((PsiElement)object);
              if (file != null) {
                if (myAlreadyAddedFiles.contains(file)) {
                  continue;
                }
                else {
                  myAlreadyAddedFiles.add(file);
                }
              }
            }
            classes.add(object);
            clsCounter++;

            if (clsCounter > 15) break;
          }
        }
      }

      myProgressIndicator.checkCanceled();

      if (classes.size() > 0) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.classes = myListModel.size();
              for (Object cls : classes) {
                myListModel.addElement(cls);
              }
            }
          }
        });
      }
    }

    private void buildRecentFiles(String pattern) {
      final MinusculeMatcher matcher = new MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
      final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      for (VirtualFile file : ArrayUtil.reverseArray(EditorHistoryManager.getInstance(project).getFiles())) {
        if (StringUtil.isEmptyOrSpaces(pattern) || matcher.matches(file.getName())) {
          if (!files.contains(file)) {
            files.add(file);
          }
        }
        if (files.size() > 15) break;
      }

      if (files.size() > 0) {
        myAlreadyAddedFiles.addAll(files);

        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.recentFiles = myListModel.size();
              for (Object file : files) {
                myListModel.addElement(file);
              }
            }
          }
        });
      }
    }

    private void buildTopHit(String pattern) {
      final List<Object> elements = new ArrayList<Object>();
      final Consumer<Object> consumer = new Consumer<Object>() {
        @Override
        public void consume(Object o) {
          if (isSetting(o) || isVirtualFile(o) || isActionValue(o) || o instanceof PsiElement) {
            elements.add(o);
          }
        }
      };

      for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
        myProgressIndicator.checkCanceled();
        provider.consumeTopHits(pattern, consumer);
      }
      if (elements.size() > 0) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.topHit = myListModel.size();
              for (Object element : elements) {
                myListModel.addElement(element);
              }
            }
          }
        });
      }
    }

    private void checkModelsUpToDate() {
      if (myClassModel == null) {
        myClassModel = new GotoClassModel2(project);
        myFileModel = new GotoFileModel(project);
        myActionModel = createActionModel();
        myClasses = myClassModel.getNames(false);
        myFiles = myFileModel.getNames(false);
        myActions = myActionModel.getNames(true);
        myConfigurables.clear();
        fillConfigurablesIds(null, new IdeConfigurablesGroup().getConfigurables());
        fillConfigurablesIds(null, new ProjectConfigurablesGroup(project).getConfigurables());
      }
    }

    private void buildModelFromRecentFiles() {
      buildRecentFiles("");
    }

    private GotoActionModel createActionModel() {
      return new GotoActionModel(project, myFocusComponent) {
        @Override
        public boolean matches(@NotNull String name, @NotNull String pattern) {
          final AnAction anAction = ActionManager.getInstance().getAction(name);
          if (anAction == null) return true;
          return NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE).matches(
            anAction.getTemplatePresentation().getText());
        }

        @NotNull
        @Override
        public Object[] getElementsByName(String id, boolean checkBoxState, String pattern) {
          final HashMap<AnAction, String> map = new HashMap<AnAction, String>();
          final AnAction act = myActionManager.getAction(id);
          if (act != null) {
            map.put(act, myActionsMap.get(act));
            if (checkBoxState) {
              final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
              for (AnAction action : map.keySet()) { //do not add already included actions
                ids.remove(getActionId(action));
              }
              if (ids.contains(id)) {
                final AnAction anAction = myActionManager.getAction(id);
                map.put(anAction, null);
              }
            }
          }
          Object[] objects = map.entrySet().toArray(new Map.Entry[map.size()]);
          if (Comparing.strEqual(id, SETTINGS_KEY)) {
            final Set<String> words = myIndex.getProcessedWords(pattern);
            Set<OptionDescription> optionDescriptions = null;
            final String actionManagerName = myActionManager.getComponentName();
            for (String word : words) {
              final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)myIndex).getAcceptableDescriptions(word);
              if (descriptions != null) {
                for (Iterator<OptionDescription> iterator = descriptions.iterator(); iterator.hasNext(); ) {
                  OptionDescription description = iterator.next();
                  if (actionManagerName.equals(description.getPath())) {
                    iterator.remove();
                  }
                }
                if (!descriptions.isEmpty()) {
                  if (optionDescriptions == null) {
                    optionDescriptions = descriptions;
                  }
                  else {
                    optionDescriptions.retainAll(descriptions);
                  }
                }
              }
              else {
                optionDescriptions = null;
                break;
              }
            }
            if (optionDescriptions != null && !optionDescriptions.isEmpty()) {
              Set<String> currentHits = new HashSet<String>();
              for (Iterator<OptionDescription> iterator = optionDescriptions.iterator(); iterator.hasNext(); ) {
                OptionDescription description = iterator.next();
                final String hit = description.getHit();
                if (hit == null || !currentHits.add(hit.trim())) {
                  iterator.remove();
                }
              }
              final Object[] descriptions = optionDescriptions.toArray();
              Arrays.sort(descriptions);
              objects = ArrayUtil.mergeArrays(objects, descriptions);
            }
          }
          return objects;
        }
      };
    }

    @SuppressWarnings("SSBasedInspection")
    private void updatePopup() {
      myProgressIndicator.checkCanceled();
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myProgressIndicator.checkCanceled();
          myListModel.update();
          myList.revalidate();
          myList.repaint();

          myRenderer.recalculateWidth();
          if (myPopup == null || !myPopup.isVisible()) {
            final ActionCallback callback = ListDelegationUtil.installKeyboardDelegation(getField().getTextEditor(), myList);
            final ComponentPopupBuilder builder = JBPopupFactory.getInstance()
              .createComponentPopupBuilder(new JBScrollPane(myList), null);
            myInitialWidth = myInitialHeight = 0;
            myPopup = builder
              .setRequestFocus(false)
              .setCancelKeyEnabled(false)
              .createPopup();
            Disposer.register(myPopup, new Disposable() {
              @Override
              public void dispose() {
                callback.setDone();
                if (myBalloon!= null) {
                  myBalloon.hide();
                  myBalloon = null;
                }
              }
            });
            if (getField() == field) {
              myPopup.showUnderneathOf(field);
            } else {
              myPopup.show(new RelativePoint(getField(), new Point(0, getField().getHeight() + 4)));
            }
            ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
              @Override
              public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                myPopup.cancel();
              }
            }, myPopup);
          }
          else {
            myList.revalidate();
            myList.repaint();
          }
          ListScrollingUtil.ensureSelectionExists(myList);
          if (myList.getModel().getSize() == 0) {
            //rebuildList("");
          }
          else {
            final Dimension size = myList.getPreferredSize();
            Dimension sz = new Dimension(Math.max(getField().getWidth(), size.width), size.height);
            if (sz.width > 800 || sz.height > 800) {
              final int extra = new JBScrollPane().getVerticalScrollBar().getWidth();
              sz = new Dimension(Math.min(800, Math.max(getField().getWidth(), size.width + extra)), Math.min(800, size.height + extra));
              sz.width += 16;
            }
            else {
              sz.height++;
              sz.height++;
              sz.width++;
              sz.width++;
            }
            myPopup.setSize(sz);
            final Point screen = getField().getLocationOnScreen();
            final int x;
            if (getField() == field) {
               x = screen.x + getField().getWidth() - myPopup.getSize().width;
            } else {
              x = screen.x - myLeftWidth - 5;
            }

            myPopup.setLocation(new Point(x, myPopup.getLocationOnScreen().y));
          }
        }
      });
    }

    private List<MatchResult> collectResults(String pattern, String[] names, final ChooseByNameModel model) {
      if (names == null) return Collections.emptyList();
      final String trimmedPattern = pattern.trim();
      if (!pattern.startsWith("*") && pattern.length() > 1) {
        pattern = "*" + pattern;
      }
      final ArrayList<MatchResult> results = new ArrayList<MatchResult>();

      MinusculeMatcher matcher = new MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE) {
        @Override
        public boolean matches(@NotNull String name) {
          if (!(model instanceof GotoActionModel) && trimmedPattern.indexOf(' ') > 0 && name.trim().indexOf(' ') < 0) {
            return false;
          }
          return super.matches(name);
        }
      };
      MatchResult result;

      for (String name : names) {
        //myProgressIndicator.checkCanceled();
        result = null;
        if (model instanceof CustomMatcherModel) {
          try {
            result = ((CustomMatcherModel)model).matches(name, pattern) ? new MatchResult(name, 0, true) : null;
          }
          catch (Exception ignore) {
          }
        }
        else {
          result = matcher.matches(name) ? new MatchResult(name, matcher.matchingDegree(name), matcher.isStartMatch(name)) : null;
        }

        if (result != null) {
          results.add(result);
        }
      }

      Collections.sort(results, new Comparator<MatchResult>() {
        @Override
        public int compare(MatchResult o1, MatchResult o2) {
          return o1.compareTo(o2);
        }
      });
      return results;
    }

    public void cancel() {
      myProgressIndicator.cancel();
    }

    public void start() {
      if (!myProgressIndicator.isCanceled()) {
        ProgressManager.getInstance().runProcess(this, myProgressIndicator);
      }
    }
  }

  private static boolean isToolWindowAction(Object o) {
    return isActionValue(o) && (o instanceof Map.Entry && ((Map.Entry)o).getKey() instanceof ActivateToolWindowAction);
  }

  private void fillConfigurablesIds(String pathToParent, Configurable[] configurables) {
    for (Configurable configurable : configurables) {
      if (configurable instanceof SearchableConfigurable) {
        final String id = ((SearchableConfigurable)configurable).getId();
        String name = configurable.getDisplayName();
        if (pathToParent != null) {
          name = pathToParent + " -> " + name;
        }
        myConfigurables.put(id, name);
        if (configurable instanceof SearchableConfigurable.Parent) {
          fillConfigurablesIds(name, ((SearchableConfigurable.Parent)configurable).getConfigurables());
        }
      }
    }
  }

  static class TitleIndexes {
    int topHit;
    int recentFiles;
    int classes;
    int files;
    int actions;
    int settings;
    int toolWindows;

    final String gotoClassTitle;
    final String gotoFileTitle;
    final String gotoActionTitle;
    final String gotoSettingsTitle;
    final String gotoRecentFilesTitle;
    static final String toolWindowsTitle = "Tool Windows";

    TitleIndexes() {
      String gotoClass = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoClass"));
      gotoClassTitle = StringUtil.isEmpty(gotoClass) ? "Classes" : "Classes (" + gotoClass + ")";
      String gotoFile = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoFile"));
      gotoFileTitle = StringUtil.isEmpty(gotoFile) ? "Files" : "Files (" + gotoFile + ")";
      String gotoAction = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoAction"));
      gotoActionTitle = StringUtil.isEmpty(gotoAction) ? "Actions" : "Actions (" + gotoAction + ")";
      String gotoSettings = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ShowSettings"));
      gotoSettingsTitle = StringUtil.isEmpty(gotoAction) ? "Preferences" : "Preferences (" + gotoSettings + ")";
      String gotoRecentFiles = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("RecentFiles"));
      gotoRecentFilesTitle = StringUtil.isEmpty(gotoRecentFiles) ? "Recent Files" : "Recent Files (" + gotoRecentFiles + ")";
    }

    String getTitle(int index) {
      if (index == topHit) return index == 0 ? "Top Hit" : "Top Hits";
      if (index == recentFiles) return gotoRecentFilesTitle;
      if (index == classes) return gotoClassTitle;
      if (index == files) return gotoFileTitle;
      if (index == toolWindows) return toolWindowsTitle;
      if (index == actions) return gotoActionTitle;
      if (index == settings) return gotoSettingsTitle;
      return null;
    }

    public void clear() {
      topHit = -1;
      recentFiles = -1;
      classes = -1;
      files = -1;
      actions = -1;
      settings = -1;
      toolWindows = -1;
    }
  }

  private static class SearchListModel extends DefaultListModel {
    Vector myDelegate;

    private SearchListModel() {
      super();
      try {
        final Field field = DefaultListModel.class.getDeclaredField("delegate");
        field.setAccessible(true);
        myDelegate = (Vector)field.get(this);
      }
      catch (NoSuchFieldException e) {

      }
      catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void addElement(Object obj) {
      myDelegate.add(obj);
    }

    public void update() {
      fireContentsChanged(this, 0, getSize() - 1);
    }
  }
}
