/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.group;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementEditorAware;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementRepresentationAware;
import com.intellij.application.options.codeStyle.arrangement.util.ArrangementRuleIndexControl;
import com.intellij.application.options.codeStyle.arrangement.util.InsetsPanel;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 11/13/12 8:10 PM
 */
public class ArrangementGroupingComponent extends JPanel implements ArrangementRepresentationAware, ArrangementEditorAware {

  @NotNull private final ArrangementGroupingType     myGroupingType;
  @NotNull private final ArrangementRuleIndexControl myRowIndexControl;
  @NotNull private final JBCheckBox                  myCheckedBox;
  @NotNull private final JLabel                      myGroupingTypeLabel;

  @Nullable private final JComboBox myOrderTypeBox;

  public ArrangementGroupingComponent(@NotNull final ArrangementNodeDisplayManager displayManager,
                                      @NotNull ArrangementGroupingType groupingType,
                                      @NotNull Collection<ArrangementEntryOrderType> availableOrderTypes)
  {
    myGroupingType = groupingType;

    FontMetrics metrics = getFontMetrics(getFont());
    int maxWidth = 0;
    for (int i = 0; i <= 99; i++) {
      maxWidth = Math.max(metrics.stringWidth(String.valueOf(i)), maxWidth);
    }
    int height = metrics.getHeight() - metrics.getDescent() - metrics.getLeading();
    int diameter = Math.max(maxWidth, height) * 5 / 3;
    myRowIndexControl = new ArrangementRuleIndexControl(diameter, height);

    myCheckedBox = new JBCheckBox();
    myCheckedBox.setOpaque(false);
    myCheckedBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        refreshControl();
      }
    });
    
    myGroupingTypeLabel = new JLabel(displayManager.getDisplayValue(groupingType));
    myGroupingTypeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myCheckedBox.setSelected(!myCheckedBox.isSelected());
      }
    });

    if (availableOrderTypes.isEmpty()) {
      myOrderTypeBox = null;
    }
    else {
      myOrderTypeBox = new JComboBox(availableOrderTypes.toArray());
      myOrderTypeBox.setRenderer(new ListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          return new JLabel(displayManager.getDisplayValue(value));
        }
      });
    }

    init();
  }

  private void init() {
    setLayout(new GridBagLayout());
    add(myRowIndexControl,
        new GridBag().anchor(GridBagConstraints.CENTER)
          .insets(0, ArrangementConstants.HORIZONTAL_PADDING, 0, ArrangementConstants.HORIZONTAL_GAP * 2)
    );
    add(myCheckedBox, new GridBag().anchor(GridBagConstraints.WEST).insets(0, 0, 0, 2));
    add(myGroupingTypeLabel, new GridBag().anchor(GridBagConstraints.WEST).insets(0, 0, 0, ArrangementConstants.HORIZONTAL_GAP));
    if (myOrderTypeBox != null) {
      int minWidth = 0;
      ListCellRenderer renderer = myOrderTypeBox.getRenderer();
      for (int i = 0, max = myOrderTypeBox.getItemCount(); i < max; i++) {
        Component rendererComponent = renderer.getListCellRendererComponent(null, myOrderTypeBox.getItemAt(i), i, false, true);
        minWidth = Math.max(minWidth, rendererComponent.getPreferredSize().width);
      }
      myOrderTypeBox.setPreferredSize(new Dimension(minWidth * 5 / 3, myOrderTypeBox.getPreferredSize().height));
      add(myOrderTypeBox, new GridBag().anchor(GridBagConstraints.WEST));
    }
    add(new JLabel(" "), new GridBag().weightx(1).fillCellHorizontally());
    
    setBackground(UIUtil.getListBackground());
    setBorder(IdeBorderFactory.createEmptyBorder(ArrangementConstants.VERTICAL_GAP));
  }

  @Override
  protected void paintComponent(Graphics g) {
    Dimension size = getSize();
    if (size != null) {
      int baseline = myGroupingTypeLabel.getBaseline(size.width, size.height);
      baseline -= myRowIndexControl.getBounds().y;
      myRowIndexControl.setBaseLine(baseline);
    }
    super.paintComponent(g);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  public boolean isChecked() {
    return myCheckedBox.isSelected();
  }

  public void setChecked(boolean checked) {
    myCheckedBox.setSelected(checked);
    refreshControl();
  }

  private void refreshControl() {
    boolean checked = isChecked();
    myGroupingTypeLabel.setEnabled(checked);
    if (myOrderTypeBox != null) {
      myOrderTypeBox.setEnabled(checked);
    }
  }

  @NotNull
  public ArrangementGroupingType getGroupingType() {
    return myGroupingType;
  }

  public void setOrderType(@NotNull ArrangementEntryOrderType type) {
    if (myOrderTypeBox != null) {
      myOrderTypeBox.setSelectedItem(type);
    }
  }

  public void setRowIndex(int row) {
    myRowIndexControl.setIndex(row);
  }

  public void setHighlight(boolean highlight) {
    setBackground(highlight ? UIUtil.getDecoratedRowColor() : UIUtil.getListBackground());
  }
  
  @Nullable
  public ArrangementEntryOrderType getOrderType() {
    return myOrderTypeBox == null ? null : (ArrangementEntryOrderType)myOrderTypeBox.getSelectedItem();
  }

  @Override
  public String toString() {
    return myGroupingType.toString();
  }
}
