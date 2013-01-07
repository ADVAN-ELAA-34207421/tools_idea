package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author nik
 */
public abstract class ArtifactRootDescriptor extends BuildRootDescriptor {
  private static final FileFilter ALL_FILES_FILTER = new FileFilter() {
    @Override
    public boolean accept(File file) {
      return true;
    }
  };
  protected final File myRoot;
  private final SourceFileFilter myFilter;
  private final int myRootIndex;
  private final ArtifactBuildTarget myTarget;
  private final DestinationInfo myDestinationInfo;

  protected ArtifactRootDescriptor(File root,
                                   @NotNull SourceFileFilter filter,
                                   int index,
                                   ArtifactBuildTarget target,
                                   @NotNull DestinationInfo destinationInfo) {
    myRoot = root;
    myFilter = filter;
    myRootIndex = index;
    myTarget = target;
    myDestinationInfo = destinationInfo;
  }

  public final String getArtifactName() {
    return myTarget.getId();
  }

  @Override
  public String toString() {
    return getFullPath();
  }

  protected abstract String getFullPath();

  public void writeConfiguration(PrintWriter out) {
    out.println(getFullPath());
    out.println("->" + myDestinationInfo.getOutputPath());
  }

  public ArtifactBuildTarget getTarget() {
    return myTarget;
  }

  @Override
  public FileFilter createFileFilter(@NotNull final ProjectDescriptor descriptor) {
    return new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        try {
          return myFilter.accept(pathname.getAbsolutePath(), descriptor);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @NotNull
  public final File getRootFile() {
    return myRoot;
  }

  @Override
  public String getRootId() {
    return String.valueOf(myRootIndex);
  }

  public abstract void copyFromRoot(String filePath,
                                    int rootIndex, String outputPath,
                                    CompileContext context, BuildOutputConsumer outputConsumer,
                                    ArtifactOutputToSourceMapping outSrcMapping) throws IOException, ProjectBuildException;

  public SourceFileFilter getFilter() {
    return myFilter;
  }

  public DestinationInfo getDestinationInfo() {
    return myDestinationInfo;
  }

  public int getRootIndex() {
    return myRootIndex;
  }
}
