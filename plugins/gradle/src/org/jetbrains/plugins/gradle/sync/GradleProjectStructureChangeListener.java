package org.jetbrains.plugins.gradle.sync;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;

import java.util.Collection;

/**
 * Defines common interface for clients interested in project structure change events.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 7:04 PM
 */
public interface GradleProjectStructureChangeListener {

  /**
   * Notifies current listener on the newly discovered changes between the gradle and intellij project models.
   * 
   * @param oldChanges      changes between the gradle and intellij project models that had been known prior to the current update
   * @param currentChanges  the most up-to-date changes between the gradle and intellij project models
   */
  void onChanges(@NotNull Collection<GradleProjectStructureChange> oldChanges,
                 @NotNull Collection<GradleProjectStructureChange> currentChanges);
}
