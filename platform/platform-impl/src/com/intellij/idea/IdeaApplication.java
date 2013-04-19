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
package com.intellij.idea;

import com.intellij.ExtensionPoints;
import com.intellij.Patches;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.CommandLineProcessor;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeRepaintManager;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.Splash;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings({"CallToPrintStackTrace"})
public class IdeaApplication {
  @NonNls public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";

  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private static IdeaApplication ourInstance;

  protected final String[] myArgs;
  private boolean myPerformProjectLoad = true;
  private ApplicationStarter myStarter;

  public IdeaApplication(String[] args) {
    LOG.assertTrue(ourInstance == null);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;

    myArgs = args;
    boolean isInternal = Boolean.valueOf(System.getProperty(IDEA_IS_INTERNAL_PROPERTY)).booleanValue();

    if (Main.isCommandLine(args)) {
      boolean headless = Main.isHeadless(args);
      if (!headless) patchSystem();
      new CommandLineApplication(isInternal, false, headless);
    }
    else {
      patchSystem();

      Splash splash = null;
      if (myArgs.length == 0) {
        myStarter = getStarter();
        if (myStarter instanceof IdeStarter) {
          splash = ((IdeStarter)myStarter).showSplash(myArgs);
        }
      }

      ApplicationManagerEx.createApplication(isInternal, false, false, false, "idea", splash);
    }

    if (myStarter == null) {
      myStarter = getStarter();
    }
    myStarter.premain(args);
  }

  private static void patchSystem() {
    System.setProperty("sun.awt.noerasebackground", "true");

    Toolkit.getDefaultToolkit().getSystemEventQueue().push(IdeEventQueue.getInstance());

    if (Patches.SUN_BUG_ID_6209673) {
      RepaintManager.setCurrentManager(new IdeRepaintManager());
    }

    if (SystemInfo.isXWindow && !SystemProperties.getBooleanProperty("idea.skip.wm.patching", false)) {
      String wmName = X11UiUtil.getWmName();
      LOG.info("WM detected: " + wmName);
      if (wmName != null) {
        X11UiUtil.patchDetectedWm(wmName);
      }
    }

    IconLoader.activate();

    new JFrame().pack(); // this peer will prevent shutting down our application
  }

  protected ApplicationStarter getStarter() {
    if (myArgs.length > 0) {
      PluginManager.getPlugins();

      ExtensionPoint<ApplicationStarter> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER);
      final ApplicationStarter[] starters = point.getExtensions();
      String key = myArgs[0];
      for (ApplicationStarter o : starters) {
        if (Comparing.equal(o.getCommandName(), key)) return o;
      }
    }
    return new IdeStarter();
  }

  public static IdeaApplication getInstance() {
    return ourInstance;
  }

  public void run() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    try {
      app.load(PathManager.getOptionsPath());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (InvalidDataException e) {
      e.printStackTrace();
    }

    myStarter.main(myArgs);
    myStarter = null; //GC it
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initLAF() {
    try {
      Class.forName("com.jgoodies.looks.plastic.PlasticLookAndFeel");

      if (SystemInfo.isWindows) {
        UIManager.installLookAndFeel("JGoodies Windows L&F", "com.jgoodies.looks.windows.WindowsLookAndFeel");
      }

      UIManager.installLookAndFeel("JGoodies Plastic", "com.jgoodies.looks.plastic.PlasticLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic 3D", "com.jgoodies.looks.plastic.Plastic3DLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic XP", "com.jgoodies.looks.plastic.PlasticXPLookAndFeel");
    }
    catch (ClassNotFoundException ignored) { }
  }

  protected class IdeStarter implements ApplicationStarter {
    private Splash mySplash;

    @Override
    public String getCommandName() {
      return null;
    }

    @Override
    public void premain(String[] args) {
      initLAF();
    }

    @Nullable
    private Splash showSplash(String[] args) {
      if (StartupUtil.shouldShowSplash(args)) {
        final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
        final SplashScreen splashScreen = getSplashScreen();
        if (splashScreen == null) {
          mySplash = new Splash(appInfo);
          mySplash.show();
          return mySplash;
        }
        else {
          updateSplashScreen(appInfo, splashScreen);
        }
      }
      return null;
    }

    private void updateSplashScreen(ApplicationInfoEx appInfo, SplashScreen splashScreen) {
      final Graphics2D graphics = splashScreen.createGraphics();
      final Dimension size = splashScreen.getSize();
      if (Splash.showLicenseeInfo(graphics, 0, 0, size.height, appInfo.getSplashTextColor())) {
        splashScreen.update();
      }
    }

    @Nullable
    private SplashScreen getSplashScreen() {
      return SplashScreen.getSplashScreen();
    }

    @Override
    public void main(String[] args) {

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      // app.setupIdeQueue(IdeEventQueue.getInstance());
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();

      try {
        IdeEventQueue.getInstance().setWindowManager(windowManager);

        final Ref<Boolean> willOpenProject = new Ref<Boolean>(Boolean.FALSE);
        final AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
        lifecyclePublisher.appFrameCreated(args, willOpenProject);
        LOG.info("App initialization took " + (System.nanoTime() - PluginManager.startupStart) / 1000000 + " ms");
        PluginManager.dumpPluginClassStatistics();
        if (!willOpenProject.get()) {
          WelcomeFrame.showNow();
          lifecyclePublisher.welcomeScreenDisplayed();
        }
        else {
          windowManager.showFrame();
        }
      }
      catch (PluginException e) {
        Messages.showErrorDialog("Plugin " + e.getPluginId() + " couldn't be loaded, the IDE will now exit.\n" +
                                 "See the full details in the log.\n" +
                                 e.getMessage(), "Plugin Error");
        System.exit(-1);
      }

      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (mySplash != null) {
            mySplash.dispose();
            mySplash = null; // Allow GC collect the splash window
          }
        }
      }, ModalityState.NON_MODAL);

      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myPerformProjectLoad) {
            loadProject();
          }

          final UpdateSettings settings = UpdateSettings.getInstance();
          if (settings != null) {
            final ApplicationInfo appInfo = ApplicationInfo.getInstance();
            if (StringUtil.compareVersionNumbers(settings.LAST_BUILD_CHECKED, appInfo.getBuild().asString()) < 0 ||
                (UpdateChecker.isMyVeryFirstOpening() && UpdateChecker.checkNeeded())) {
              UpdateChecker.setMyVeryFirstOpening(false);
              UpdateChecker.updateAndShowResult();
            }
          }

          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              PluginManager.reportPluginError();
            }
          });
        }
      }, ModalityState.NON_MODAL);
    }

  }

  private void loadProject() {
    Project project = null;
    if (myArgs != null && myArgs.length > 0 && myArgs[0] != null) {
      project = CommandLineProcessor.processExternalCommandLine(Arrays.asList(myArgs), null);
    }

    final MessageBus bus = ApplicationManager.getApplication().getMessageBus();
    bus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(project);
  }

  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void setPerformProjectLoad(boolean performProjectLoad) {
    myPerformProjectLoad = performProjectLoad;
  }
}
