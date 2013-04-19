package org.jetbrains.plugins.gradle.config;

import com.intellij.application.options.colors.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.psi.codeStyle.DisplayPriority;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 1/19/12 11:32 AM
 */
public class GradleColorAndFontPanelFactory implements ColorAndFontPanelFactoryEx {

  @NotNull
  @Override
  public NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
    SchemesPanel schemesPanel = new SchemesPanel(options);
    final ColorAndFontDescriptionPanel descriptionPanel = new ColorAndFontDescriptionPanel();
    final OptionsPanel optionsPanel = new OptionsPanelImpl(descriptionPanel, options, schemesPanel, ExternalSystemBundle
      .message("gradle.name"));
    GradleColorAndFontPreviewPanel previewPanel = new GradleColorAndFontPreviewPanel(options);
    return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, ExternalSystemBundle.message("gradle.name"), null, null);
  }

  @NotNull
  @Override
  public String getPanelDisplayName() {
    return ExternalSystemBundle.message("gradle.name");
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.LANGUAGE_SETTINGS;
  }
}
