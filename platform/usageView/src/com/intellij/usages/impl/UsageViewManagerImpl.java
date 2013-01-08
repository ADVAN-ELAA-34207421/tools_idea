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
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.SearchInBackgroundOption;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.content.Content;
import com.intellij.usageView.*;
import com.intellij.usages.*;
import com.intellij.usages.UsageViewManager;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.ui.RangeBlinker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author max
 */
public class UsageViewManagerImpl extends UsageViewManager {
  private final Project myProject;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");
  
  public UsageViewManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public UsageView createUsageView(@NotNull UsageTarget[] targets,
                                   @NotNull Usage[] usages,
                                   @NotNull UsageViewPresentation presentation,
                                   Factory<UsageSearcher> usageSearcherFactory) {
    UsageViewImpl usageView = new UsageViewImpl(myProject, presentation, targets, usageSearcherFactory);
    appendUsages(usages, usageView);
    usageView.setSearchInProgress(false);
    return usageView;
  }

  @Override
  @NotNull
  public UsageView showUsages(@NotNull UsageTarget[] searchedFor,
                              @NotNull Usage[] foundUsages,
                              @NotNull UsageViewPresentation presentation,
                              Factory<UsageSearcher> factory) {
    UsageView usageView = createUsageView(searchedFor, foundUsages, presentation, factory);
    addContent((UsageViewImpl)usageView, presentation);
    showToolWindow(true);
    return usageView;
  }

  @Override
  @NotNull
  public UsageView showUsages(@NotNull UsageTarget[] searchedFor, @NotNull Usage[] foundUsages, @NotNull UsageViewPresentation presentation) {
    return showUsages(searchedFor, foundUsages, presentation, null);
  }

  private void addContent(@NotNull UsageViewImpl usageView, @NotNull UsageViewPresentation presentation) {
    Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).addContent(
      presentation.getTabText(),
      presentation.getTabName(),
      presentation.getToolwindowTitle(),
      true,
      usageView.getComponent(),
      presentation.isOpenInNewTab(),
      true
    );
    usageView.setContent(content);
    content.putUserData(USAGE_VIEW_KEY, usageView);
  }

  @Override
  public UsageView searchAndShowUsages(@NotNull final UsageTarget[] searchFor,
                                       @NotNull final Factory<UsageSearcher> searcherFactory,
                                       final boolean showPanelIfOnlyOneUsage,
                                       final boolean showNotFoundMessage,
                                       @NotNull final UsageViewPresentation presentation,
                                       @Nullable final UsageViewStateListener listener) {
    final AtomicReference<UsageViewImpl> usageView = new AtomicReference<UsageViewImpl>();

    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    Task task = new Task.Backgroundable(myProject, getProgressTitle(presentation), true, new SearchInBackgroundOption()) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, processPresentation, listener).run();
      }

      @Override
      public DumbModeAction getDumbModeAction() {
        return DumbModeAction.CANCEL;
      }

      @Override
      @Nullable
      public NotificationInfo getNotificationInfo() {
        String notification = usageView.get() != null ? usageView.get().getUsagesCount() + " Usage(s) Found" : "No Usages Found";
        return new NotificationInfo("Find Usages", "Find Usages Finished", notification);
      }
    };
    ProgressManager.getInstance().run(task);
    return usageView.get();
  }

  @Override
  public void searchAndShowUsages(@NotNull UsageTarget[] searchFor,
                                  @NotNull Factory<UsageSearcher> searcherFactory,
                                  @NotNull FindUsagesProcessPresentation processPresentation,
                                  @NotNull UsageViewPresentation presentation,
                                  @Nullable UsageViewStateListener listener) {
    final AtomicReference<UsageViewImpl> usageView = new AtomicReference<UsageViewImpl>();
    final SearchForUsagesRunnable runnable = new SearchForUsagesRunnable(usageView, presentation, searchFor, searcherFactory, processPresentation, listener);
    final Factory<ProgressIndicator> progressIndicatorFactory = processPresentation.getProgressIndicatorFactory();

    final ProgressIndicator progressIndicator = progressIndicatorFactory != null ? progressIndicatorFactory.create() : null;

    final AtomicBoolean findUsagesStartedShown = new AtomicBoolean();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          ProgressManager.getInstance().runProcess(new Runnable() {
            @Override
            public void run() {
              runnable.searchUsages(findUsagesStartedShown);
            }
          }, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          //ignore
        }
        finally {
          runnable.endSearchForUsages(findUsagesStartedShown);
        }
      }
    });
  }

  @Override
  public UsageView getSelectedUsageView() {
    final Content content = com.intellij.usageView.UsageViewManager.getInstance(myProject).getSelectedContent();
    if (content != null) {
      return content.getUserData(USAGE_VIEW_KEY);
    }

    return null;
  }

  @NotNull
  public static String getProgressTitle(@NotNull UsageViewPresentation presentation) {
    final String scopeText = presentation.getScopeText();
    if (scopeText == null) {
      return UsageViewBundle.message("progress.searching.for", StringUtil.capitalize(presentation.getUsagesString()));
    }
    return UsageViewBundle.message("progress.searching.for.in", StringUtil.capitalize(presentation.getUsagesString()), scopeText);
  }

  private void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  private static void appendUsages(@NotNull final Usage[] foundUsages, @NotNull final UsageViewImpl usageView) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (Usage foundUsage : foundUsages) {
          usageView.appendUsage(foundUsage);
        }
      }
    });
  }


  public void showTooManyUsagesWarning(final ProgressIndicator indicator,
                                       @NotNull final CountDownLatch waitWhileUserClick,
                                       final int usageCount,
                                       final UsageViewImpl usageView) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (usageView != null && usageView.searchHasBeenCancelled() || indicator != null && indicator.isCanceled()) return;
        String message = UsageViewBundle.message("find.excessive.usage.count.prompt", usageCount);
        UsageLimitUtil.Result ret = UsageLimitUtil.showTooManyUsagesWarning(myProject, message);
        if (ret == UsageLimitUtil.Result.ABORT && usageView != null) {
          usageView.setCurrentSearchCancelled(true);
          if (indicator != null) indicator.cancel();
        }
        waitWhileUserClick.countDown();
      }
    });
  }

  private class SearchForUsagesRunnable implements Runnable {
    private final AtomicInteger myUsageCountWithoutDefinition = new AtomicInteger(0);
    private final AtomicReference<Usage> myFirstUsage = new AtomicReference<Usage>();
    private final AtomicReference<UsageViewImpl> myUsageViewRef;
    private final UsageViewPresentation myPresentation;
    private final UsageTarget[] mySearchFor;
    private final Factory<UsageSearcher> mySearcherFactory;
    private final FindUsagesProcessPresentation myProcessPresentation;
    private final UsageViewStateListener myListener;
    private volatile boolean mySearchHasBeenCancelled;

    private SearchForUsagesRunnable(@NotNull AtomicReference<UsageViewImpl> usageView,
                                    @NotNull UsageViewPresentation presentation,
                                    @NotNull UsageTarget[] searchFor,
                                    @NotNull Factory<UsageSearcher> searcherFactory,
                                    @NotNull FindUsagesProcessPresentation processPresentation,
                                    @Nullable UsageViewStateListener listener) {
      myUsageViewRef = usageView;
      myPresentation = presentation;
      mySearchFor = searchFor;
      mySearcherFactory = searcherFactory;
      myProcessPresentation = processPresentation;
      myListener = listener;
      mySearchHasBeenCancelled = false;
    }

    private UsageViewImpl getUsageView() {
      UsageViewImpl usageView = myUsageViewRef.get();
      if (usageView != null) return usageView;
      int usageCount = myUsageCountWithoutDefinition.get();
      if (usageCount >= 2 || usageCount == 1 && myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        usageView = new MyUsageViewImpl();
        if (myUsageViewRef.compareAndSet(null, usageView)) {
          openView(usageView);
          Usage firstUsage = myFirstUsage.get();
          if (firstUsage != null) {
            usageView.appendUsage(firstUsage);
          }
        }
        else {
          Disposer.dispose(usageView);
        }
        return myUsageViewRef.get();
      }
      return null;
    }

    private void openView(@NotNull final UsageViewImpl usageView) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          addContent(usageView, myPresentation);
          if (myListener != null) {
            myListener.usageViewCreated(usageView);
          }
          showToolWindow(false);
        }
      });
    }

    @Override
    public void run() {
      AtomicBoolean findUsagesStartedShown = new AtomicBoolean();
      searchUsages(findUsagesStartedShown);
      endSearchForUsages(findUsagesStartedShown);
    }

    private void searchUsages(@NotNull final AtomicBoolean findStartedBalloonShown) {
      Alarm findUsagesStartedBalloon = new Alarm();
      findUsagesStartedBalloon.addRequest(new Runnable() {
        @Override
        public void run() {
          notifyByFindBalloon("Find Usages in progress...", null);
          findStartedBalloonShown.set(true);
        }
      }, 300, ModalityState.NON_MODAL);
      UsageSearcher usageSearcher = mySearcherFactory.create();
      final AtomicInteger tooManyUsages = new AtomicInteger();
      // 0: ok, 1:warning dialog shown; 2:user closed dialog
      final CountDownLatch waitWhileUserClick = new CountDownLatch(1);
      usageSearcher.generate(new Processor<Usage>() {
        @Override
        public boolean process(final Usage usage) {
          final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (searchHasBeenCancelled() || indicator != null && indicator.isCanceled()) return false;
          if (tooManyUsages.get() == 1) {
            try {
              waitWhileUserClick.await(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException ignored) {
            }
          }

          boolean incrementCounter = !isSelfUsage(usage, mySearchFor);

          if (incrementCounter) {
            final int usageCount = myUsageCountWithoutDefinition.incrementAndGet();
            if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
              myFirstUsage.compareAndSet(null, usage);
            }

            final UsageViewImpl usageView = getUsageView();

            if (usageCount > UsageLimitUtil.USAGES_LIMIT && tooManyUsages.get() == 0 && tooManyUsages.compareAndSet(0, 1)) {
              showTooManyUsagesWarning(indicator, waitWhileUserClick, myUsageCountWithoutDefinition.get(), usageView);
            }
            
            if (usageView != null) {
              usageView.appendUsage(usage);
            }
          }
          return indicator == null || !indicator.isCanceled();
        }
      });
      if (getUsageView() != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            showToolWindow(true);
          }
        }, myProject.getDisposed());
      }
      Disposer.dispose(findUsagesStartedBalloon);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (findStartedBalloonShown.get()) {
            Balloon balloon = ToolWindowManager.getInstance(myProject).getToolWindowBalloon(ToolWindowId.FIND);
            if (balloon != null) {
              balloon.hide();
            }
          }
        }
      }, myProject.getDisposed());
    }

    public void setCurrentSearchCancelled(boolean cancelled) {
      mySearchHasBeenCancelled = cancelled;
    }

    public boolean searchHasBeenCancelled() {
      return mySearchHasBeenCancelled;
    }

    private void endSearchForUsages(@NotNull final AtomicBoolean findStartedBalloonShown) {
      assert !ApplicationManager.getApplication().isDispatchThread() : Thread.currentThread();
      int usageCount = myUsageCountWithoutDefinition.get();
      if (usageCount == 0 && myProcessPresentation.isShowNotFoundMessage()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final List<Action> notFoundActions = myProcessPresentation.getNotFoundActions();
              final String message = UsageViewBundle.message("dialog.no.usages.found.in",
                                                             StringUtil.decapitalize(myPresentation.getUsagesString()),
                                                             myPresentation.getScopeText());

              if (notFoundActions == null || notFoundActions.isEmpty()) {
                notifyByFindBalloon("<html>"+message+".<br>" + createOptionsHtml() + "</html>", createGotToOptionsListener(mySearchFor));
                findStartedBalloonShown.set(false);
              }
              else {
                List<String> titles = new ArrayList<String>(notFoundActions.size() + 1);
                titles.add(UsageViewBundle.message("dialog.button.ok"));
                for (Action action : notFoundActions) {
                  Object value = action.getValue(FindUsagesProcessPresentation.NAME_WITH_MNEMONIC_KEY);
                  if (value == null) value = action.getValue(Action.NAME);

                  titles.add((String)value);
                }

                int option = Messages.showDialog(myProject, message, UsageViewBundle.message("dialog.title.information"),
                                                 ArrayUtil.toStringArray(titles), 0, Messages.getInformationIcon());

                if (option > 0) {
                  notFoundActions.get(option - 1).actionPerformed(new ActionEvent(this, 0, titles.get(option)));
                }
              }
            }
          }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
      else if (usageCount == 1 && !myProcessPresentation.isShowPanelIfOnlyOneUsage()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Usage usage = myFirstUsage.get();
            if (usage.canNavigate()) {
              usage.navigate(true);
              flashUsageScriptaculously(usage);
            }
            notifyByFindBalloon("<html>Only one usage found.<br>" + createOptionsHtml() + "</html>", createGotToOptionsListener(mySearchFor));
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
      else {
        final UsageViewImpl usageView = myUsageViewRef.get();
        if (usageView != null) {
          usageView.drainQueuedUsageNodes();
          usageView.setSearchInProgress(false);
        }
      }

      if (myListener != null) {
        myListener.findingUsagesFinished(myUsageViewRef.get());
      }
    }

    private class MyUsageViewImpl extends UsageViewImpl {
      public MyUsageViewImpl() {
        super(myProject, SearchForUsagesRunnable.this.myPresentation, mySearchFor, mySearcherFactory);
      }

      @Override
      public void close() {
        setCurrentSearchCancelled(true);
        super.close();
      }

      @Override
      public boolean searchHasBeenCancelled() {
        return SearchForUsagesRunnable.this.searchHasBeenCancelled();
      }

      @Override
      public void setCurrentSearchCancelled(boolean cancelled) {
        SearchForUsagesRunnable.this.setCurrentSearchCancelled(cancelled);
      }
    }
  }

  private void notifyByFindBalloon(@NotNull String text, HyperlinkListener listener) {
    com.intellij.usageView.UsageViewManager.getInstance(myProject); // in case tool window not registered
    ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.FIND, MessageType.INFO, text, AllIcons.Actions.Find, listener);
  }

  @NotNull
  private HyperlinkListener createGotToOptionsListener(@NotNull final UsageTarget[] targets) {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        FindManager.getInstance(myProject).showSettingsAndFindUsages(targets);
      }
    };
  }

  @NotNull
  private static String createOptionsHtml() {
    String shortcutText = "";
    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
    if (shortcut != null) {
      shortcutText = "&nbsp;(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return "<a href='xxx'>Find Options...</a>" + shortcutText;
  }

  private static void flashUsageScriptaculously(@NotNull final Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) {
      return;
    }
    UsageInfo2UsageAdapter usageInfo = (UsageInfo2UsageAdapter)usage;

    Editor editor = usageInfo.openTextEditor(true);
    if (editor == null) return;
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);

    RangeBlinker rangeBlinker = new RangeBlinker(editor, attributes, 6);
    List<Segment> segments = new ArrayList<Segment>();
    CommonProcessors.CollectProcessor<Segment> processor = new CommonProcessors.CollectProcessor<Segment>(segments);
    usageInfo.processRangeMarkers(processor);
    rangeBlinker.resetMarkers(segments);
    rangeBlinker.startBlinking();
  }
}
