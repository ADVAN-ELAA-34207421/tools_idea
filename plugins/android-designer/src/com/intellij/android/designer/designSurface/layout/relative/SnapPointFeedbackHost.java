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
package com.intellij.android.designer.designSurface.layout.relative;

import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SnapPointFeedbackHost extends JComponent {
  public static final BasicStroke STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 1}, 0);
  public static final Color COLOR = new Color(60, 139, 186);
  public static final int EXPAND_SIZE = 10;
  public static final String KEY = "SnapPointFeedbackHost";

  public static SimpleTextAttributes SNAP_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, COLOR);

  private final List<Rectangle> myLines = new ArrayList<Rectangle>();
  private final List<Rectangle> myArrows = new ArrayList<Rectangle>();

  public void addHorizontalLine(int x, int y, int length) {
    myLines.add(new Rectangle(x, y, length, -1));
  }

  public void addVerticalLine(int x, int y, int length) {
    myLines.add(new Rectangle(x, y, -1, length));
  }

  public void addHorizontalArrow(int x, int y, int length) {
    myArrows.add(new Rectangle(x, y, length, -1));
  }

  public void addVerticalArrow(int x, int y, int length) {
    myArrows.add(new Rectangle(x, y, -1, length));
  }

  public void clearAll() {
    myLines.clear();
    myArrows.clear();
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x - EXPAND_SIZE, y - EXPAND_SIZE, width + 2 * EXPAND_SIZE, height + 2 * EXPAND_SIZE);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D)g;
    Stroke stroke = g2d.getStroke();

    g.setColor(COLOR);
    g2d.setStroke(STROKE);

    Rectangle bounds = getBounds();

    for (Rectangle line : myLines) {
      int x = line.x - bounds.x;
      int y = line.y - bounds.y;

      if (line.width == -1) {
        g.drawLine(x, y, x, y + line.height);
      }
      else {
        g.drawLine(x, y, x + line.width, y);
      }
    }

    g.setColor(Color.orange);
    g2d.setStroke(stroke);

    for (Rectangle line : myArrows) {
      int x = line.x - bounds.x;
      int y = line.y - bounds.y;

      if (line.width == -1) {
        paintArrowVertical(g, x, y, x, y + line.height);
      }
      else {
        paintArrowHorizontal(g, x, y, x + line.width, y);
      }
    }
  }

  public static void paintArrowHorizontal(Graphics g, int x1, int y1, int x2, int y2) {
    g.drawLine(x1, y1, x2, y2);
    g.drawLine(x1 + 5, y1 - 5, x1, y1);
    g.drawLine(x1 + 5, y1 + 5, x1, y1);
    g.drawLine(x2 - 5, y2 - 5, x2, y2);
    g.drawLine(x2 - 5, y2 + 5, x2, y2);
  }

  public static void paintArrowVertical(Graphics g, int x1, int y1, int x2, int y2) {
    g.drawLine(x1, y1, x2, y2);
    g.drawLine(x1 - 5, y1 + 5, x1, y1);
    g.drawLine(x1 + 5, y1 + 5, x1, y1);
    g.drawLine(x2 - 5, y2 - 5, x2, y2);
    g.drawLine(x2 + 5, y2 - 5, x2, y2);
  }
}