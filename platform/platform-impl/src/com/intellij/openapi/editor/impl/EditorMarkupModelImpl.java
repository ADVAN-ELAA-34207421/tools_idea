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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:56:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class EditorMarkupModelImpl extends MarkupModelImpl implements EditorMarkupModel {
  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);
  private static final Icon ERRORS_FOUND_ICON = AllIcons.General.ErrorsFound;
  private static final int ERROR_ICON_WIDTH = ERRORS_FOUND_ICON.getIconWidth();
  private static final int ERROR_ICON_HEIGHT = ERRORS_FOUND_ICON.getIconHeight();
  private static final int PREFERRED_WIDTH = ERROR_ICON_WIDTH + 3;
  private final EditorImpl myEditor;
  // null renderer means we should not show traffic light icon
  private ErrorStripeRenderer myErrorStripeRenderer;
  private final List<ErrorStripeListener> myErrorMarkerListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean dimensionsAreValid;
  private int myEditorScrollbarTop = -1;
  private int myEditorTargetHeight = -1;
  private int myEditorSourceHeight = -1;
  private ProperTextRange myDirtyYPositions;
  private static final ProperTextRange WHOLE_DOCUMENT = new ProperTextRange(0, 0);

  @NotNull private ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private static int myMinMarkHeight = 3;
  private static int ourPreviewLines = 5;// Actually preview has ourPreviewLines * 2 + 1 lines (above + below + current one)
  private LightweightHint myEditorPreviewHint = null;

  EditorMarkupModelImpl(@NotNull EditorImpl editor) {
    super(editor.getDocument());
    myEditor = editor;
  }

  private int offsetToLine(int offset, Document document) {
    if (offset < 0) {
      return 0;
    }
    if (offset > document.getTextLength()) {
      return document.getLineCount();
    }
    return myEditor.offsetToVisualLine(offset);
  }

  public void repaintVerticalScrollBar() {
    myEditor.getVerticalScrollBar().repaint();
  }

  private static int getMinHeight() {
    return myMinMarkHeight;
  }

  void recalcEditorDimensions() {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    int scrollBarHeight = scrollBar.getSize().height;

    myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight()/* + 1*/;
    int editorScrollbarBottom = scrollBar.getIncScrollButtonHeight();
    myEditorTargetHeight = scrollBarHeight - myEditorScrollbarTop - editorScrollbarBottom;
    myEditorSourceHeight = myEditor.getPreferredHeight();

    dimensionsAreValid = scrollBarHeight != 0;
  }

  public void repaintTrafficLightIcon() {
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.myErrorStripeButton.repaint();
      errorPanel.repaintTrafficTooltip();
    }
  }

  private static class PositionedStripe {
    private final Color color;
    private final int yStart;
    private int yEnd;
    private final boolean thin;
    private final int layer;

    private PositionedStripe(Color color, int yStart, int yEnd, boolean thin, int layer) {
      this.color = color;
      this.yStart = yStart;
      this.yEnd = yEnd;
      this.thin = thin;
      this.layer = layer;
    }
  }

  private boolean showToolTipByMouseMove(final MouseEvent e) {
    MouseEvent me = e;

    final int line = getLineByEvent(e);
    Rectangle area = myEditor.getScrollingModel().getVisibleArea();
    //int realY = (int)(((float)e.getY() / e.getComponent().getHeight()) * myEditor.getContentComponent().getHeight());
    int realY = myEditor.getLineHeight() * line;
    boolean isVisible = area.contains(area.x, realY);//area.y < realY && area.y + area.height > realY;

    TooltipRenderer bigRenderer;
    if (!ApplicationManager.getApplication().isInternal() || isVisible) {
      final Set<RangeHighlighter> highlighters = new THashSet<RangeHighlighter>();
      getNearestHighlighters(this, me.getY(), highlighters);
      getNearestHighlighters((MarkupModelEx)DocumentMarkupModel.forDocument(myEditor.getDocument(), getEditor().getProject(), true), me.getY(), highlighters);

      int minDelta = Integer.MAX_VALUE;
      int y = e.getY();

      for (RangeHighlighter each : highlighters) {
        ProperTextRange range = offsetsToYPositions(each.getStartOffset(), each.getEndOffset());
        int eachStartY = range.getStartOffset();
        int eachEndY = range.getEndOffset();
        int eachY = eachStartY + (eachEndY - eachStartY) / 2;
        if (Math.abs(e.getY() - eachY) < minDelta) {
          y = eachY;
        }
      }


      me = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), e.getX(), y + 1, e.getClickCount(),
                          e.isPopupTrigger());

      if (highlighters.isEmpty()) return false;
      bigRenderer = myTooltipRendererProvider.calcTooltipRenderer(highlighters);
    } else {
      final List<RangeHighlighterEx> highlighters = new ArrayList<RangeHighlighterEx>();
      collectRangeHighlighters(this, line, highlighters);
      collectRangeHighlighters((MarkupModelEx)DocumentMarkupModel.forDocument(myEditor.getDocument(), getEditor().getProject(), true), line,
                               highlighters);
      bigRenderer = new EditorFragmentRenderer(line, highlighters, e.getX());
    }

    if (bigRenderer != null) {
      showTooltip(me, bigRenderer, new HintHint(me).setAwtTooltip(true).setPreferredPosition(Balloon.Position.atLeft));
      return true;
    }
    return false;
  }

  private int getLineByEvent(MouseEvent e) {
    int line = myEditor.offsetToLogicalLine(yPositionToOffset(e.getY(), true));
    int foldingLineDecrement = 0;
    FoldRegion[] regions = myEditor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : regions) {
      if (region.isExpanded()) continue;
      int startFoldingLine = myEditor.offsetToLogicalLine(region.getStartOffset());
      int endFoldingLine = myEditor.offsetToLogicalLine(region.getEndOffset());
      if (startFoldingLine <= line) {
        foldingLineDecrement += (endFoldingLine - startFoldingLine);
      }
    }
    return Math.min(myEditor.getVisibleLineCount(), Math.max(0, line - foldingLineDecrement));
  }

  private void collectRangeHighlighters(MarkupModelEx markupModel, final int currentLine, final Collection<RangeHighlighterEx> highlighters) {
    int startOffset = myEditor.getDocument().getLineStartOffset(Math.max(0, currentLine - ourPreviewLines));
    int endOffset = myEditor.getDocument().getLineEndOffset(Math.min(myEditor.getDocument().getLineCount() -1 , currentLine +
                                                                                                                ourPreviewLines));
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          int startLine = offsetToLine(highlighter.getStartOffset(), myEditor.getDocument());
          int endLine = offsetToLine(highlighter.getStartOffset(), myEditor.getDocument());
          if (startLine <= currentLine + ourPreviewLines && endLine >= currentLine - ourPreviewLines) {
            highlighters.add(highlighter);
          }
        }
        return true;
      }
    });
  }

  @Nullable
  private RangeHighlighter getNearestRangeHighlighter(final MouseEvent e) {
    List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    getNearestHighlighters(this, e.getY(), highlighters);
    getNearestHighlighters((MarkupModelEx)DocumentMarkupModel.forDocument(myEditor.getDocument(), myEditor.getProject(), true), e.getY(),
                           highlighters);
    RangeHighlighter nearestMarker = null;
    int yPos = 0;
    for (RangeHighlighter highlighter : highlighters) {
      final int newYPos = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset()).getStartOffset();

      if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
        nearestMarker = highlighter;
        yPos = newYPos;
      }
    }
    return nearestMarker;
  }

  private void getNearestHighlighters(MarkupModelEx markupModel,
                                      final int y,
                                      final Collection<RangeHighlighter> nearest) {
    int startOffset = yPositionToOffset(y - getMinHeight(), true);
    int endOffset = yPositionToOffset(y + getMinHeight(), false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
          if (y >= range.getStartOffset() - getMinHeight() * 2 &&
              y <= range.getEndOffset() + getMinHeight() * 2) {
            nearest.add(highlighter);
          }
        }
        return true;
      }
    });
  }

  private void doClick(final MouseEvent e) {
    RangeHighlighter marker = getNearestRangeHighlighter(e);
    int offset;
    if (marker == null) {
      if (myEditorPreviewHint != null) {
        offset = myEditor.getDocument().getLineEndOffset(getLineByEvent(e));
      } else {
        return;
      }
    } else {
      offset = marker.getStartOffset();
    }

    final Document doc = myEditor.getDocument();
    if (doc.getLineCount() > 0) {
      // Necessary to expand folded block even if navigating just before one
      // Very useful when navigating to first unused import statement.
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));
      myEditor.getCaretModel().moveToOffset(lineEnd);
    }

    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getSelectionModel().removeSelection();
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    scrollingModel.disableAnimation();
    if (myEditorPreviewHint != null) {
      JComponent c = myEditorPreviewHint.getComponent();
      int relativePopupOffset = SwingUtilities.convertPoint(c, c.getLocation(), myEditor.getScrollPane()).y;
      relativePopupOffset += myEditor.getLineHeight() * ourPreviewLines;
      scrollingModel.scrollToCaret(ScrollType.CENTER);
      Point caretLocation = myEditor.visualPositionToXY(myEditor.getCaretModel().getVisualPosition()).getLocation();
      caretLocation = SwingUtilities.convertPoint(myEditor.getContentComponent(), caretLocation, myEditor.getScrollPane());
      scrollingModel.scrollVertically(scrollingModel.getVerticalScrollOffset() - (relativePopupOffset - caretLocation.y));
    }
    else {
      scrollingModel.scrollToCaret(ScrollType.CENTER);
    }
    scrollingModel.enableAnimation();
    if (marker != null) {
      fireErrorMarkerClicked(marker, e);
    }
  }

  @Override
  public void setErrorStripeVisible(boolean val) {
    if (val) {
      myEditor.getVerticalScrollBar().setPersistentUI(new MyErrorPanel());
    }
    else {
      myEditor.getVerticalScrollBar().setPersistentUI(ButtonlessScrollBarUI.createNormal());
    }
  }

  @Nullable
  private MyErrorPanel getErrorPanel() {
    ScrollBarUI ui = myEditor.getVerticalScrollBar().getUI();
    return ui instanceof MyErrorPanel ? (MyErrorPanel)ui : null;
  }

  @Override
  public void setErrorPanelPopupHandler(@NotNull PopupHandler handler) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.setPopupHandler(handler);
    }
  }

  @Override
  public void setErrorStripTooltipRendererProvider(@NotNull final ErrorStripTooltipRendererProvider provider) {
    myTooltipRendererProvider = provider;
  }

  @Override
  @NotNull
  public ErrorStripTooltipRendererProvider getErrorStripTooltipRendererProvider() {
    return myTooltipRendererProvider;
  }

  @Override
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public void setErrorStripeRenderer(ErrorStripeRenderer renderer) {
    assertIsDispatchThread();
    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }
    myErrorStripeRenderer = renderer;
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();

    myEditor.getVerticalScrollBar()
      .updateUI(); // re-create increase/decrease buttons, in case of not-null renderer it will show traffic light icon
    repaintVerticalScrollBar();
  }

  private void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }

  @Override
  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  @Override
  public void dispose() {

    final MyErrorPanel panel = getErrorPanel();
    if (panel != null) {
      panel.uninstallListeners();
    }

    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }
    myErrorStripeRenderer = null;
    super.dispose();
  }

  // startOffset == -1 || endOffset == -1 means whole document
  void repaint(int startOffset, int endOffset) {
    ProperTextRange range = offsetsToYPositions(startOffset, endOffset);
    markDirtied(range);
    if (startOffset == -1 || endOffset == -1) {
      myDirtyYPositions = WHOLE_DOCUMENT;
    }

    myEditor.getVerticalScrollBar().repaint(0, range.getStartOffset(), PREFERRED_WIDTH, range.getLength() + getMinHeight());
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT;
  }

  private static final Dimension STRIPE_BUTTON_PREFERRED_SIZE = new Dimension(PREFERRED_WIDTH, ERROR_ICON_HEIGHT + 4);

  private class ErrorStripeButton extends JButton {
    private ErrorStripeButton() {
      setFocusable(false);
    }

    @Override
    public void paint(Graphics g) {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      final Rectangle bounds = getBounds();
      try {
        if (UISettings.getInstance().PRESENTATION_MODE) {
          g.setColor(getEditor().getColorsScheme().getDefaultBackground());
          g.fillRect(0, 0, bounds.width, bounds.height);

          if (myErrorStripeRenderer != null) {
            myErrorStripeRenderer.paint(this, g, new Rectangle(2, 0, 10, 7));
          }
        } else {

          g.setColor(ButtonlessScrollBarUI.getTrackBackground());
          g.fillRect(0, 0, bounds.width, bounds.height);

          g.setColor(ButtonlessScrollBarUI.getTrackBorderColor());
          g.drawLine(0, 0, 0, bounds.height);

          if (myErrorStripeRenderer != null) {
            myErrorStripeRenderer.paint(this, g, new Rectangle(5, 2, ERROR_ICON_WIDTH, ERROR_ICON_HEIGHT));
          }
        }
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return UISettings.getInstance().PRESENTATION_MODE ? new Dimension(10,7) : STRIPE_BUTTON_PREFERRED_SIZE;
    }
  }

  private class MyErrorPanel extends ButtonlessScrollBarUI implements MouseMotionListener, MouseListener {
    private PopupHandler myHandler;
    private JButton myErrorStripeButton;
    private BufferedImage myCachedTrack;

    @Override
    protected JButton createDecreaseButton(int orientation) {
      myErrorStripeButton = myErrorStripeRenderer == null ? super.createDecreaseButton(orientation) : new ErrorStripeButton();
      return myErrorStripeButton;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      scrollbar.addMouseMotionListener(this);
      scrollbar.addMouseListener(this);
      myErrorStripeButton.addMouseMotionListener(this);
      myErrorStripeButton.addMouseListener(this);
    }

    @Override
    protected void uninstallListeners() {
      scrollbar.removeMouseMotionListener(this);
      scrollbar.removeMouseListener(this);
      myErrorStripeButton.removeMouseMotionListener(this);
      myErrorStripeButton.removeMouseListener(this);
      super.uninstallListeners();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
      if (UISettings.getInstance().PRESENTATION_MODE) {
        super.paintThumb(g, c, thumbBounds);
        return;
      }
      int shift = isMirrored() ? -9 : 9;
      g.translate(shift, 0);
      super.paintThumb(g, c, thumbBounds);
      g.translate(-shift, 0);
    }

    @Override
    protected int adjustThumbWidth(int width) {
      if (UISettings.getInstance().PRESENTATION_MODE) return super.adjustThumbWidth(width);
      return width - 2;
    }

    @Override
    protected int getThickness() {
      if (UISettings.getInstance().PRESENTATION_MODE) return super.getThickness();
      return super.getThickness() + 7;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
      if (UISettings.getInstance().PRESENTATION_MODE) {
        g.setColor(getEditor().getColorsScheme().getDefaultBackground());
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        return;
      }
      Rectangle clip = g.getClipBounds().intersection(bounds);
      if (clip.height == 0) return;

      Rectangle componentBounds = c.getBounds();
      ProperTextRange docRange = ProperTextRange.create(0, (int)componentBounds.getHeight());
      if (myCachedTrack == null || myCachedTrack.getHeight() != componentBounds.getHeight()) {
        myCachedTrack = UIUtil.createImage(componentBounds.width, componentBounds.height, BufferedImage.TYPE_INT_ARGB);
        myDirtyYPositions = docRange;
        paintTrackBasement(myCachedTrack.getGraphics(), new Rectangle(0, 0, componentBounds.width, componentBounds.height));
      }
      if (myDirtyYPositions == WHOLE_DOCUMENT) {
        myDirtyYPositions = docRange;
      }
      if (myDirtyYPositions != null) {
        final Graphics2D imageGraphics = myCachedTrack.createGraphics();

        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

        try {
          myDirtyYPositions = myDirtyYPositions.intersection(docRange);
          if (myDirtyYPositions == null) myDirtyYPositions = docRange;
          repaint(imageGraphics, componentBounds.width, ERROR_ICON_WIDTH - 1, myDirtyYPositions);
          myDirtyYPositions = null;
        }
        finally {
          ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
        }
      }

      UIUtil.drawImage(g, myCachedTrack, null, 0, 0);
    }

    private void paintTrackBasement(Graphics g, Rectangle bounds) {
      if (UISettings.getInstance().PRESENTATION_MODE) {
        return;
      }

      g.setColor(ButtonlessScrollBarUI.getTrackBackground());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height + 1);

      g.setColor(ButtonlessScrollBarUI.getTrackBorderColor());
      int border = isMirrored() ? bounds.x + bounds.width - 1 : bounds.x;
      g.drawLine(border, bounds.y, border, bounds.y + bounds.height + 1);
    }

    @Override
    protected Color adjustColor(Color c) {
      if (UIUtil.isUnderDarcula()) {
        return c;
      }
      return ColorUtil.withAlpha(ColorUtil.shift(super.adjustColor(c), 0.9), 0.85);
    }

    private void repaint(final Graphics g, int gutterWidth, final int stripeWidth, ProperTextRange yrange) {
      final Rectangle clip = new Rectangle(0, yrange.getStartOffset(), gutterWidth, yrange.getLength() + getMinHeight());
      paintTrackBasement(g, clip);

      Document document = myEditor.getDocument();
      int startOffset = yPositionToOffset(clip.y - getMinHeight(), true);
      int endOffset = yPositionToOffset(clip.y + clip.height, false);

      drawMarkup(g, stripeWidth, startOffset, endOffset, EditorMarkupModelImpl.this);
      drawMarkup(g, stripeWidth, startOffset, endOffset,
                 (MarkupModelEx)DocumentMarkupModel.forDocument(document, myEditor.getProject(), true));
    }

    private void drawMarkup(final Graphics g, final int width, int startOffset, int endOffset, MarkupModelEx markup) {
      final Queue<PositionedStripe> thinEnds = new PriorityQueue<PositionedStripe>(5, new Comparator<PositionedStripe>() {
        @Override
        public int compare(PositionedStripe o1, PositionedStripe o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      final Queue<PositionedStripe> wideEnds = new PriorityQueue<PositionedStripe>(5, new Comparator<PositionedStripe>() {
        @Override
        public int compare(PositionedStripe o1, PositionedStripe o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      // sorted by layer
      final List<PositionedStripe> thinStripes = new ArrayList<PositionedStripe>();
      final List<PositionedStripe> wideStripes = new ArrayList<PositionedStripe>();
      final int[] thinYStart = new int[1];  // in range 0..yStart all spots are drawn
      final int[] wideYStart = new int[1];  // in range 0..yStart all spots are drawn

      markup.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
        @Override
        public boolean process(RangeHighlighterEx highlighter) {
          Color color = highlighter.getErrorStripeMarkColor();
          if (color == null) return true;
          boolean isThin = highlighter.isThinErrorStripeMark();
          int[] yStart = isThin ? thinYStart : wideYStart;
          List<PositionedStripe> stripes = isThin ? thinStripes : wideStripes;
          Queue<PositionedStripe> ends = isThin ? thinEnds : wideEnds;

          ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
          final int ys = range.getStartOffset();
          int ye = range.getEndOffset();
          if (ye - ys < getMinHeight()) ye = ys + getMinHeight();

          yStart[0] = drawStripesEndingBefore(ys, ends, stripes, g, width, yStart[0]);

          final int layer = highlighter.getLayer();

          PositionedStripe stripe = null;
          int i;
          for (i = 0; i < stripes.size(); i++) {
            PositionedStripe s = stripes.get(i);
            if (s.layer == layer) {
              stripe = s;
              break;
            }
            if (s.layer < layer) {
              break;
            }
          }
          if (stripe == null) {
            // started new stripe, draw previous above
            if (yStart[0] != ys) {
              if (!stripes.isEmpty()) {
                PositionedStripe top = stripes.get(0);
                drawSpot(g, width, top.thin, yStart[0], ys, top.color, true, true);
              }
              yStart[0] = ys;
            }
            stripe = new PositionedStripe(color, ys, ye, isThin, layer);
            stripes.add(i, stripe);
            ends.offer(stripe);
          }
          else {
            // key changed, reinsert into queue
            if (stripe.yEnd != ye) {
              ends.remove(stripe);
              stripe.yEnd = ye;
              ends.offer(stripe);
            }
          }

          return true;
        }
      });

      drawStripesEndingBefore(Integer.MAX_VALUE, thinEnds, thinStripes, g, width, thinYStart[0]);
      drawStripesEndingBefore(Integer.MAX_VALUE, wideEnds, wideStripes, g, width, wideYStart[0]);
    }

    private int drawStripesEndingBefore(int ys,
                                        Queue<PositionedStripe> ends,
                                        List<PositionedStripe> stripes,
                                        Graphics g, int width, int yStart) {
      while (!ends.isEmpty()) {
        PositionedStripe endingStripe = ends.peek();
        if (endingStripe.yEnd > ys) break;
        ends.remove();

        // check whether endingStripe got obscured in the range yStart..endingStripe.yEnd
        int i = stripes.indexOf(endingStripe);
        stripes.remove(i);
        if (i == 0) {
          // visible
          drawSpot(g, width, endingStripe.thin, yStart, endingStripe.yEnd, endingStripe.color, true, true);
          yStart = endingStripe.yEnd;
        }
      }
      return yStart;
    }

    private void drawSpot(Graphics g,
                          int width,
                          boolean thinErrorStripeMark,
                          int yStart,
                          int yEnd,
                          Color color,
                          boolean drawTopDecoration,
                          boolean drawBottomDecoration) {
      int x = isMirrored() ? 3 : 5;
      int paintWidth = width;
      if (thinErrorStripeMark) {
        paintWidth /= 2;
        paintWidth += 1;
        x = isMirrored() ? width + 2 : 0;
      }
      if (color == null) return;
      g.setColor(color);
      g.fillRect(x + 1, yStart, paintWidth - 2, yEnd - yStart + 1);

      Color brighter = color.brighter();
      g.setColor(brighter);
      //left decoration
      UIUtil.drawLine(g, x, yStart, x, yEnd/* - 1*/);
      if (drawTopDecoration) {
        //top decoration
        UIUtil.drawLine(g, x + 1, yStart, x + paintWidth - 2, yStart);
      }
      Color darker = ColorUtil.shift(color, 0.75);

      g.setColor(darker);
      if (drawBottomDecoration) {
        // bottom decoration
        UIUtil.drawLine(g, x + 1, yEnd/* - 1*/, x + paintWidth - 2, yEnd/* - 1*/);   // large bottom to let overwrite by hl below
      }
      //right decoration
      UIUtil.drawLine(g, x + paintWidth - 2, yStart, x + paintWidth - 2, yEnd/* - 1*/);
    }

    // mouse events
    @Override
    public void mouseClicked(final MouseEvent e) {
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), new Runnable() {
        @Override
        public void run() {
          doMouseClicked(e);
        }
      },
                                                    EditorBundle.message("move.caret.command.name"),
                                                    DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT,
                                                    getDocument()
      );
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    private int getWidth() {
      return scrollbar.getWidth();
    }

    private void doMouseClicked(MouseEvent e) {
      myEditor.getContentComponent().requestFocus();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }
      if (e.getX() > 0 && e.getX() <= getWidth()) {
        doClick(e);
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        showTrafficLightTooltip(e);
        return;
      }

      if (e.getX() > 0 && e.getX() <= getWidth() && showToolTipByMouseMove(e)) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips(e, false);

      if (scrollbar.getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    private TrafficTooltipRenderer myTrafficTooltipRenderer;

    private void showTrafficLightTooltip(MouseEvent e) {
      if (myTrafficTooltipRenderer == null) {
        myTrafficTooltipRenderer = myTooltipRendererProvider.createTrafficTooltipRenderer(new Runnable() {
          @Override
          public void run() {
            myTrafficTooltipRenderer = null;
          }
        }, myEditor);
      }
      showTooltip(e, myTrafficTooltipRenderer, new HintHint(e).setAwtTooltip(true).setMayCenterPosition(true).setContentActive(false)
        .setPreferredPosition(Balloon.Position.atLeft));
    }

    private void repaintTrafficTooltip() {
      if (myTrafficTooltipRenderer != null) {
        myTrafficTooltipRenderer.repaintTooltipWindow();
      }
    }

    private void cancelMyToolTips(final MouseEvent e, boolean checkIfShouldSurvive) {
      if (myEditorPreviewHint != null) {
        myEditorPreviewHint.hide();
        myEditorPreviewHint = null;
      }
      final TooltipController tooltipController = TooltipController.getInstance();
      if (!checkIfShouldSurvive || !tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP, e, true);
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    private void setPopupHandler(@NotNull PopupHandler handler) {
      if (myHandler != null) {
        scrollbar.removeMouseListener(myHandler);
        myErrorStripeButton.removeMouseListener(myHandler);
      }

      myHandler = handler;
      scrollbar.addMouseListener(handler);
      myErrorStripeButton.addMouseListener(myHandler);
    }
  }

  private void showTooltip(MouseEvent e, final TooltipRenderer tooltipObject, @NotNull HintHint hintHint) {
    TooltipController tooltipController = TooltipController.getInstance();
    tooltipController.showTooltipByMouseMove(myEditor, new RelativePoint(e), tooltipObject,
                                             myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                             ERROR_STRIPE_TOOLTIP_GROUP, hintHint);
  }

  private void fireErrorMarkerClicked(RangeHighlighter marker, MouseEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(getEditor(), e, marker);
    for (ErrorStripeListener listener : myErrorMarkerListeners) {
      listener.errorMarkerClicked(event);
    }
  }

  @Override
  public void addErrorMarkerListener(@NotNull final ErrorStripeListener listener, @NotNull Disposable parent) {
    ContainerUtil.add(listener, myErrorMarkerListeners, parent);
  }

  public void markDirtied(@NotNull ProperTextRange yPositions) {
    int start = Math.max(0, yPositions.getStartOffset() - myEditor.getLineHeight());
    int end = myEditorScrollbarTop + myEditorTargetHeight == 0 ? yPositions.getEndOffset() + myEditor.getLineHeight()
                                                               : Math
                .min(myEditorScrollbarTop + myEditorTargetHeight, yPositions.getEndOffset() + myEditor.getLineHeight());
    ProperTextRange adj = new ProperTextRange(start, Math.max(end, start));

    myDirtyYPositions = myDirtyYPositions == null ? adj : myDirtyYPositions.union(adj);

    myEditorScrollbarTop = 0;
    myEditorSourceHeight = 0;
    myEditorTargetHeight = 0;
    dimensionsAreValid = false;
  }

  @Override
  public void setMinMarkHeight(final int minMarkHeight) {
    myMinMarkHeight = minMarkHeight;
  }

  @Override
  public boolean isErrorStripeVisible() {
    return getErrorPanel() != null;
  }

  private static class BasicTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final Collection<RangeHighlighter> highlighters) {
      LineTooltipRenderer bigRenderer = null;
      //do not show same tooltip twice
      Set<String> tooltips = null;

      for (RangeHighlighter highlighter : highlighters) {
        final Object tooltipObject = highlighter.getErrorStripeTooltip();
        if (tooltipObject == null) continue;

        final String text = tooltipObject.toString();
        if (tooltips == null) {
          tooltips = new THashSet<String>();
        }
        if (tooltips.add(text)) {
          if (bigRenderer == null) {
            bigRenderer = new LineTooltipRenderer(text, new Object[]{highlighters});
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }

      return bigRenderer;
    }

    @NotNull
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
      return new LineTooltipRenderer(text, new Object[]{text});
    }

    @NotNull
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
      return new LineTooltipRenderer(text, width, new Object[]{text});
    }

    @NotNull
    @Override
    public TrafficTooltipRenderer createTrafficTooltipRenderer(@NotNull final Runnable onHide, @NotNull Editor editor) {
      return new TrafficTooltipRenderer() {
        @Override
        public void repaintTooltipWindow() {
        }

        @Override
        public LightweightHint show(@NotNull Editor editor,
                                    @NotNull Point p,
                                    boolean alignToRight,
                                    @NotNull TooltipGroup group,
                                    @NotNull HintHint hintHint) {
          JLabel label = new JLabel("WTF");
          return new LightweightHint(label) {
            @Override
            public void hide() {
              super.hide();
              onHide.run();
            }
          };
        }
      };
    }
  }

  @NotNull
  private ProperTextRange offsetsToYPositions(int start, int end) {
    if (!dimensionsAreValid) {
      recalcEditorDimensions();
    }
    Document document = myEditor.getDocument();
    int startLineNumber = end == -1 ? 0 : offsetToLine(start, document);
    int startY;
    int lineCount;
    if (myEditorSourceHeight < myEditorTargetHeight) {
      lineCount = 0;
      startY = myEditorScrollbarTop + startLineNumber * myEditor.getLineHeight();
    }
    else {
      lineCount = myEditorSourceHeight / myEditor.getLineHeight();
      startY = myEditorScrollbarTop + (int)((float)startLineNumber / lineCount * myEditorTargetHeight);
    }

    int endY;
    int endLineNumber = offsetToLine(end, document);
    if (end == -1 || start == -1) {
      endY = Math.min(myEditorSourceHeight, myEditorTargetHeight);
    }
    else if (start == end || offsetToLine(start, document) == endLineNumber) {
      endY = startY; // both offsets are on the same line, no need to recalc Y position
    }
    else {
      if (myEditorSourceHeight < myEditorTargetHeight) {
        endY = myEditorScrollbarTop + endLineNumber * myEditor.getLineHeight();
      }
      else {
        endY = myEditorScrollbarTop + (int)((float)endLineNumber / lineCount * myEditorTargetHeight);
      }
    }
    if (endY < startY) endY = startY;
    return new ProperTextRange(startY, endY);
  }

  private int yPositionToOffset(int y, boolean beginLine) {
    if (!dimensionsAreValid) {
      recalcEditorDimensions();
    }
    final int safeY = Math.max(0, y - myEditorScrollbarTop);
    VisualPosition visual;
    if (myEditorSourceHeight < myEditorTargetHeight) {
      visual = myEditor.xyToVisualPosition(new Point(0, safeY));
    }
    else {
      float fraction = Math.max(0, Math.min(1, safeY / (float)myEditorTargetHeight));
      final int lineCount = myEditorSourceHeight / myEditor.getLineHeight();
      visual = new VisualPosition((int)(fraction * lineCount), 0);
    }
    int line = myEditor.visualToLogicalPosition(visual).line;
    Document document = myEditor.getDocument();
    if (line < 0) return 0;
    if (line >= document.getLineCount()) return document.getTextLength();

    final FoldingModelEx foldingModel = myEditor.getFoldingModel();
    if (beginLine) {
      final int offset = document.getLineStartOffset(line);
      final FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.min(offset, startCollapsed.getStartOffset()) : offset;
    }
    else {
      final int offset = document.getLineEndOffset(line);
      final FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.max(offset, startCollapsed.getEndOffset()) : offset;
    }
  }
  private class EditorFragmentRenderer implements TooltipRenderer {
    private int myLine;
    private Collection<RangeHighlighterEx> myHighlighters;
    private int myMouseX;

    private EditorFragmentRenderer(int currentLine, Collection<RangeHighlighterEx> rangeHighlighters, int mouseX) {
      myLine = currentLine;
      myHighlighters = rangeHighlighters;
      myMouseX = mouseX;
    }

    @Override
    public LightweightHint show(@NotNull final Editor editor,
                                @NotNull Point p,
                                boolean alignToRight,
                                @NotNull TooltipGroup group,
                                @NotNull HintHint hintInfo) {
      final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
      final JPanel editorFragmentPreviewPanel = new JPanel() {
        private static final int R = 6;

        private BufferedImage myImage = null;

        @Override
        public Dimension getPreferredSize() {
          int width = 0;
          if (editor instanceof EditorEx) {
            width = ((EditorEx)editor).getGutterComponentEx().getWidth();
          }
          width += Math.min(editor.getScrollingModel().getVisibleArea().width, editor.getContentComponent().getWidth());
          return new Dimension(width - 6, editor.getLineHeight() * (ourPreviewLines * 2 + 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
          if (myImage == null) {
            Dimension size = getPreferredSize();
            myImage = UIUtil.createImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
            Graphics graphics = myImage.getGraphics();
            int width = 0;
            Graphics2D g2d = (Graphics2D)graphics;
            UISettings.setupAntialiasing(graphics);
            int lineShift = -myEditor.getLineHeight() * (myLine - ourPreviewLines);//first fragment line offset
            int popupStartOffset = myEditor.getDocument().getLineStartOffset(Math.max(0, myLine - ourPreviewLines));
            int popupEndOffset = myEditor.getDocument().getLineEndOffset(Math.min(myEditor.getDocument().getLineCount() - 1, myLine +
                                                                                                                             ourPreviewLines));
            List<RangeHighlighterEx> exs = new ArrayList<RangeHighlighterEx>();
            for (RangeHighlighterEx rangeHighlighter : myHighlighters) {
              if (rangeHighlighter.getEndOffset()> popupStartOffset && rangeHighlighter.getStartOffset() < popupEndOffset) {
                exs.add(rangeHighlighter);
              }
            }
            g2d.setTransform(AffineTransform.getTranslateInstance(5 - myMouseX, lineShift));
            if (editor instanceof EditorEx) {
              EditorGutterComponentEx gutterComponentEx = ((EditorEx)editor).getGutterComponentEx();
              width = gutterComponentEx.getWidth();
              graphics.setClip(0, 0, width, gutterComponentEx.getHeight());
              gutterComponentEx.paint(graphics);
            }
            JComponent contentComponent = editor.getContentComponent();
            graphics.setClip(width, 0, contentComponent.getWidth(), contentComponent.getHeight());
            g2d.setTransform(AffineTransform.getTranslateInstance(width + 5 - myMouseX, lineShift));
            contentComponent.paint(graphics);
            Collections.sort(exs, new Comparator<RangeHighlighterEx>() {
              public int compare(RangeHighlighterEx ex1, RangeHighlighterEx ex2) {
                LogicalPosition startPos1 = myEditor.offsetToLogicalPosition(ex1.getAffectedAreaStartOffset());
                LogicalPosition startPos2 = myEditor.offsetToLogicalPosition(ex2.getAffectedAreaStartOffset());
                if (startPos1.line != startPos2.line) return 0;
                return startPos1.column - startPos2.column;
              }
            });
            Map<Integer, Integer> rightEdges = new HashMap<Integer, Integer>();
            for (RangeHighlighterEx ex : exs) {
              int hStartOffset = ex.getAffectedAreaStartOffset();
              int hEndOffset = ex.getAffectedAreaEndOffset();
              Object tooltip = ex.getErrorStripeTooltip();
              if (tooltip == null) continue;
              String s = String.valueOf(tooltip);
              if (s.isEmpty()) continue;

              LogicalPosition logicalPosition = editor.offsetToLogicalPosition(hStartOffset);
              Point placeToShow = editor.logicalPositionToXY(logicalPosition);
              placeToShow.y -= (0 - editor.getLineHeight() * 3 / 2);

              int w = graphics.getFontMetrics().stringWidth(s);
              int a = graphics.getFontMetrics().getAscent();
              int h = editor.getLineHeight();

              Integer rightEdge = rightEdges.get(logicalPosition.line);
              if (rightEdge == null) rightEdge = 0;
              placeToShow.x = Math.max(placeToShow.x, rightEdge);
              rightEdge  = Math.max(rightEdge, placeToShow.x + w + 3 * R);
              rightEdges.put(logicalPosition.line, rightEdge);

              GraphicsUtil.setupAAPainting(graphics);
              graphics.setColor(MessageType.WARNING.getPopupBackground());
              graphics.fillRoundRect(placeToShow.x - R, placeToShow.y - a, w + 2 * R, h, R, R);
              graphics.setColor(new JBColor(JBColor.GRAY, Gray._200));
              graphics.drawRoundRect(placeToShow.x - R, placeToShow.y - a, w + 2 * R, h, R, R);
              graphics.setColor(JBColor.foreground());
              graphics.drawString(s, placeToShow.x, placeToShow.y + 2);
            }

          }
          g.drawImage(myImage, 0, 0, this);
        }
      };
      LightweightHint hint = new LightweightHint(editorFragmentPreviewPanel);
      hintInfo.setShowImmediately(true);
      hintManager.showEditorHint(hint, editor, hintInfo.getOriginalPoint(), HintManager.HIDE_BY_ANY_KEY |
                                                                           HintManager.HIDE_BY_TEXT_CHANGE |
                                                                           HintManager.HIDE_BY_MOUSEOVER |
                                                                           HintManager.HIDE_BY_ESCAPE |
                                                                           HintManager.HIDE_BY_LOOKUP_ITEM_CHANGE |
                                                                           HintManager.HIDE_BY_OTHER_HINT |
                                                                           HintManager.HIDE_BY_SCROLLING, 0, false, hintInfo);
      myEditorPreviewHint = hint;
      return hint;
    }
  }
}
