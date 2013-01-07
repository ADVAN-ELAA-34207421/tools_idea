package org.jetbrains.jps.incremental;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
* @author Eugene Zhuravlev
*         Date: 11/16/12
*/
class ChunkBuildOutputConsumerImpl implements ModuleLevelBuilder.OutputConsumer {
  private final CompileContext myContext;
  private Map<BuildTarget<?>, BuildOutputConsumerImpl> myTarget2Consumer = new THashMap<BuildTarget<?>, BuildOutputConsumerImpl>();
  private Map<String, CompiledClass> myClasses = new THashMap<String, CompiledClass>();
  private Map<BuildTarget<?>, Collection<CompiledClass>> myTargetToClassesMap = new THashMap<BuildTarget<?>, Collection<CompiledClass>>();

  public ChunkBuildOutputConsumerImpl(CompileContext context) {
    myContext = context;
  }

  @Override
  public Collection<CompiledClass> getTargetCompiledClasses(BuildTarget<?> target) {
    final Collection<CompiledClass> classes = myTargetToClassesMap.get(target);
    if (classes != null) {
      return Collections.unmodifiableCollection(classes);
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Map<String, CompiledClass> getCompiledClasses() {
    return Collections.unmodifiableMap(myClasses);
  }

  @Override
  @Nullable
  public BinaryContent lookupClassBytes(String className) {
    final CompiledClass object = myClasses.get(className);
    return object != null ? object.getContent() : null;
  }

  @Override
  public void registerCompiledClass(BuildTarget<?> target, CompiledClass compiled) throws IOException {
    if (compiled.getClassName() != null) {
      myClasses.put(compiled.getClassName(), compiled);
      Collection<CompiledClass> classes = myTargetToClassesMap.get(target);
      if (classes == null) {
        classes = new ArrayList<CompiledClass>();
        myTargetToClassesMap.put(target, classes);
      }
      classes.add(compiled);
    }
    registerOutputFile(target, compiled.getOutputFile(), Collections.<String>singleton(compiled.getSourceFile().getPath()));
  }

  @Override
  public void registerOutputFile(BuildTarget<?> target, File outputFile, Collection<String> sourcePaths) throws IOException {
    BuildOutputConsumerImpl consumer = myTarget2Consumer.get(target);
    if (consumer == null) {
      consumer = new BuildOutputConsumerImpl(target, myContext);
      myTarget2Consumer.put(target, consumer);
    }
    consumer.registerOutputFile(outputFile, sourcePaths);
  }

  public void fireFileGeneratedEvents() {
    for (BuildOutputConsumerImpl consumer : myTarget2Consumer.values()) {
      consumer.fireFileGeneratedEvent();
    }
  }

  public void clear() {
    myTarget2Consumer.clear();
    myClasses.clear();
    myTargetToClassesMap.clear();
  }
}
