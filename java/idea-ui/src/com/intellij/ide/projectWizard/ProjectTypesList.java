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
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.newProjectWizard.SelectTemplateSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 11/21/12
 */
public class ProjectTypesList implements Disposable {

  private final JBList myList;
  private final CollectionListModel<TemplateItem> myModel;
  private Pair<TemplateItem, Integer> myBestMatch;

  public ProjectTypesList(JBList list, MultiMap<String, ProjectCategory> map) {

    myList = list;
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    new ListSpeedSearch(myList) {
      @Override
      protected String getElementText(Object element) {
        return super.getElementText(element);
      }
    }.setComparator(new SpeedSearchComparator(false));
    List<TemplateItem> items = buildItems(map);
    myModel = new CollectionListModel<TemplateItem>(items);

    myList.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptor() {
      @Nullable
      @Override
      public String getTextFor(Object value) {
        return ((TemplateItem)value).getName();
      }

      @Nullable
      @Override
      public String getTooltipFor(Object value) {
        return null;
      }

      @Nullable
      @Override
      public Icon getIconFor(Object value) {
        return ((TemplateItem)value).getIcon();
      }

      @Override
      public boolean hasSeparatorAboveOf(Object value) {
        TemplateItem item = (TemplateItem)value;
        int index = myModel.getElementIndex(item);
        return index == 0 || !myModel.getElementAt(index - 1).getGroupName().equals(item.getGroupName());
      }

      @Nullable
      @Override
      public String getCaptionAboveOf(Object value) {
        return ((TemplateItem)value).getGroupName();
      }
    }));

    myList.setModel(myModel);
  }

  void installKeyAction(JComponent component) {
    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        InputEvent event = e.getInputEvent();
        if (event instanceof KeyEvent) {
          int row = myList.getSelectedIndex();
           int toSelect;
           switch (((KeyEvent)event).getKeyCode()) {
             case KeyEvent.VK_UP:
               toSelect = row == 0 ? myList.getItemsCount() - 1 : row - 1;
               myList.setSelectedIndex(toSelect);
               myList.ensureIndexIsVisible(toSelect);
               break;
             case KeyEvent.VK_DOWN:
               toSelect = row < myList.getItemsCount() - 1 ? row + 1 : 0;
               myList.setSelectedIndex(toSelect);
               myList.ensureIndexIsVisible(toSelect);
               break;
           }
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_UP, KeyEvent.VK_DOWN), component);
  }

  void resetSelection() {
    if (myList.getSelectedIndex() != -1) return;
    SelectTemplateSettings settings = SelectTemplateSettings.getInstance();
    if (settings.getLastGroup() == null || !setSelectedType(settings.getLastGroup(), settings.getLastTemplate())) {
      myList.setSelectedIndex(0);
    }
  }

  void saveSelection() {
    TemplateItem item = (TemplateItem)myList.getSelectedValue();
    if (item != null) {
      SelectTemplateSettings.getInstance().setLastTemplate(item.getGroupName(), item.getName());
    }
  }

  private List<TemplateItem> buildItems(MultiMap<String, ProjectCategory> map) {
    List<TemplateItem> items = new ArrayList<TemplateItem>();
    List<String> groups = new ArrayList<String>(map.keySet());
    Collections.sort(groups);
    for (String group : groups) {
      for (ProjectCategory template : map.get(group)) {
        TemplateItem templateItem = new TemplateItem(template, group);
        items.add(templateItem);
      }
    }
    return items;
  }

  @Nullable
  public ProjectCategory getSelectedType() {
    Object value = myList.getSelectedValue();
    return value instanceof TemplateItem ? ((TemplateItem)value).myTemplate : null;
  }

  public boolean setSelectedType(@Nullable String group, @Nullable String name) {
    for (int i = 0; i < myList.getModel().getSize(); i++) {
      Object o = myList.getModel().getElementAt(i);
      if (o instanceof TemplateItem && ((TemplateItem)o).myGroup.equals(group) && ((TemplateItem)o).getName().equals(name)) {
        myList.setSelectedIndex(i);
        myList.ensureIndexIsVisible(i);
        return true;
      }
    }

    return false;
  }

  @Override
  public void dispose() {
  }

  static class TemplateItem {

    private final ProjectCategory myTemplate;
    private final String myGroup;

    TemplateItem(ProjectCategory template, String group) {
      myTemplate = template;
      myGroup = group;
    }

    String getName() {
      return myTemplate.getDisplayName();
    }

    public String getGroupName() {
      return myGroup;
    }

    Icon getIcon() {
      return myTemplate.createModuleBuilder().getNodeIcon();
    }

    @Nullable
    String getDescription() {
      return myTemplate.getDescription();
    }

    @Override
    public String toString() {
      return getName() + " " + getGroupName();
    }
  }
}
