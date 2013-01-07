package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsDependenciesList;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class JpsEclipseClasspathSerializer extends JpsModuleClasspathSerializer {
  @NonNls public static final String CLASSPATH_STORAGE_ID = "eclipse";
  private static final Logger LOG = Logger.getInstance(JpsEclipseClasspathSerializer.class);

  public JpsEclipseClasspathSerializer() {
    super(CLASSPATH_STORAGE_ID);
  }

  @Override
  public void loadClasspath(@NotNull JpsModule module,
                            @Nullable String classpathDir,
                            @NotNull String baseModulePath,
                            JpsMacroExpander expander,
                            List<String> paths,
                            JpsSdkType<?> projectSdkType) {
    final JpsDependenciesList dependenciesList = module.getDependenciesList();
    dependenciesList.clear();
    try {
      final File classpathFile = new File(classpathDir, EclipseXml.DOT_CLASSPATH_EXT);
      if (!classpathFile.exists()) return; //no classpath file - no compilation
      final Document document = JDOMUtil.loadDocument(classpathFile);
      final JpsEclipseClasspathReader reader = new JpsEclipseClasspathReader(classpathDir, paths, new HashSet<String>());
      reader.readClasspath(module, null, document.getRootElement(), expander);//todo
      final String eml = module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
      final File emlFile = new File(baseModulePath, eml);
      if (emlFile.isFile()) {
        final Document emlDocument = JDOMUtil.loadDocument(emlFile);
        final Element root = emlDocument.getRootElement();
        new JpsIdeaSpecificSettings(expander).readIDEASpecific(root, module, projectSdkType);
      }
    }
    catch (Exception e) {
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }
}
