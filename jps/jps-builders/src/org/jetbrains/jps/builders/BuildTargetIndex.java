package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.incremental.CompileContext;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public interface BuildTargetIndex extends BuildTargetRegistry {

  List<BuildTargetChunk> getSortedTargetChunks(@NotNull CompileContext context);

  @Deprecated
  Set<BuildTarget<?>> getDependenciesRecursively(@NotNull BuildTarget<?> target, @NotNull CompileContext context);

  @NotNull
  Collection<BuildTarget<?>> getDependencies(@NotNull BuildTarget<?> target, @NotNull CompileContext context);
}
