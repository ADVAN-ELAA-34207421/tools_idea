package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureNodeFilter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 3:48 PM
 */
public abstract class AbstractGradleSyncTreeFilterAction extends ToggleAction {

  @NotNull private final ExternalProjectStructureNodeFilter myFilter;
  @NotNull private final TextAttributesKey                  myAttributesKey;

  private Color   myColor;
  private boolean myIconChanged;

  protected AbstractGradleSyncTreeFilterAction(@NotNull AttributesDescriptor descriptor) {
    myFilter = createFilter(descriptor.getKey());
    myAttributesKey = descriptor.getKey();
    getTemplatePresentation().setText(descriptor.getDisplayName());
    updateIcon(EditorColorsManager.getInstance().getGlobalScheme());
    EditorColorsManager.getInstance().addEditorColorsListener(new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        updateIcon(scheme);
      }
    });
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    final ExternalProjectStructureTreeModel model = GradleUtil.getProjectStructureTreeModel(e.getDataContext());
    if (model == null) {
      return false;
    }

    return model.hasFilter(myFilter);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final ExternalProjectStructureTreeModel treeModel = GradleUtil.getProjectStructureTreeModel(e.getDataContext());
    if (treeModel == null) {
      return;
    }
    if (state) {
      treeModel.addFilter(myFilter);
    }
    else {
      treeModel.removeFilter(myFilter);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null || StringUtil.isEmpty(GradleSettings.getInstance(project).getLinkedExternalProjectPath())) {
      e.getPresentation().setEnabled(false);
      return;
    }
    else {
      e.getPresentation().setEnabled(true);
    }
    
    if (myIconChanged) {
      e.getPresentation().setIcon(getTemplatePresentation().getIcon());
      myIconChanged = false;
    }
  }

  public static ExternalProjectStructureNodeFilter createFilter(@NotNull TextAttributesKey key) {
    return new MyFilter(key);
  }
  
  private void updateIcon(@Nullable EditorColorsScheme scheme) {
    if (scheme == null) {
      return;
    }
    final Color color = scheme.getAttributes(myAttributesKey).getForegroundColor();
    if (color != null && !color.equals(myColor)) {
      getTemplatePresentation().setIcon(new ColorIcon(new JLabel("").getFont().getSize(), color));
      myColor = color;
      myIconChanged = true;
    }
  }
  
  private static class MyFilter implements ExternalProjectStructureNodeFilter {

    @NotNull private final TextAttributesKey myKey;

    MyFilter(@NotNull TextAttributesKey key) {
      myKey = key;
    }

    @Override
    public boolean isVisible(@NotNull ProjectStructureNode<?> node) {
      return myKey.equals(node.getDescriptor().getAttributes());
    }
  }
}
