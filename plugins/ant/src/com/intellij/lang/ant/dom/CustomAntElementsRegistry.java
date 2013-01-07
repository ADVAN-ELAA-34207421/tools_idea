/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.ReflectedProject;
import com.intellij.lang.ant.config.impl.AntResourcesClassLoader;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Storage for user-defined tasks and data types
 * parsed from ant files
 * @author Eugene Zhuravlev
 *         Date: Jul 1, 2010
 */
public class CustomAntElementsRegistry {

  public static ThreadLocal<Boolean> ourIsBuildingClasspathForCustomTagLoading = new ThreadLocal<Boolean>() {
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.CustomAntElementsRegistry");
  private static final Key<CustomAntElementsRegistry> REGISTRY_KEY = Key.create("_custom_element_registry_");

  private final Map<XmlName, Class> myCustomElements = new HashMap<XmlName, Class>();
  private final Map<XmlName, String> myErrors = new HashMap<XmlName, String>();
  private final Map<AntDomNamedElement, String> myTypeDefErrors = new HashMap<AntDomNamedElement, String>();
  private final Map<XmlName, AntDomNamedElement> myDeclarations = new HashMap<XmlName, AntDomNamedElement>();
  private final Map<String, ClassLoader> myNamedLoaders = new HashMap<String, ClassLoader>();

  private CustomAntElementsRegistry(final AntDomProject antProject) {
    antProject.accept(new CustomTagDefinitionFinder(antProject));
  }

  public static CustomAntElementsRegistry getInstance(AntDomProject antProject) {
    CustomAntElementsRegistry registry = antProject.getContextAntProject().getUserData(REGISTRY_KEY);
    if (registry == null) {
      registry = new CustomAntElementsRegistry(antProject);
      antProject.putUserData(REGISTRY_KEY, registry);
    }
    return registry;
  }

  @NotNull
  public Set<XmlName> getCompletionVariants(AntDomElement parentElement) {
    if (parentElement instanceof AntDomCustomElement) {
      // this case is already handled in AntDomExtender when defining children
      return Collections.emptySet(); 
    }
    final Set<XmlName> result = new HashSet<XmlName>();
    
    final Pair<AntDomMacroDef, AntDomScriptDef> contextMacroOrScriptDef = getContextMacroOrScriptDef(parentElement);
    final AntDomMacroDef restrictToMacroDef = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getFirst() : null;
    final AntDomScriptDef restrictToScriptDef = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getSecond() : null;
    final boolean parentIsDataType = parentElement.isDataType();

    for (final XmlName xmlName : myCustomElements.keySet()) {
      final AntDomNamedElement declaringElement = myDeclarations.get(xmlName);
      if (declaringElement instanceof AntDomMacrodefElement) {
        if (restrictToMacroDef == null || !restrictToMacroDef.equals(declaringElement.getParentOfType(AntDomMacroDef.class, true))) {
          continue;
        }
      }
      else if (declaringElement instanceof AntDomScriptdefElement) {
        if (restrictToScriptDef == null || !restrictToScriptDef.equals(declaringElement.getParentOfType(AntDomScriptDef.class, true))) {
          continue;
        }
      }

      if (declaringElement != null) {
        if (declaringElement.equals(restrictToMacroDef) || declaringElement.equals(restrictToScriptDef)) {
          continue;
        }
      }

      if (parentIsDataType) {
        if (declaringElement instanceof AntDomMacroDef || declaringElement instanceof AntDomScriptDef || declaringElement instanceof AntDomTaskdef) {
          continue;
        }
        if (declaringElement instanceof AntDomTypeDef) {
          final AntDomTypeDef typedef = (AntDomTypeDef)declaringElement;
          final Class clazz = myCustomElements.get(xmlName);
          if (clazz != null && typedef.isTask(clazz)) {
            continue;                                                           
          }
        }
      }
      
      result.add(xmlName);
    }
    return result;
  }

  @Nullable
  private Pair<AntDomMacroDef, AntDomScriptDef> getContextMacroOrScriptDef(AntDomElement element) {
    final AntDomMacroDef macrodef = element.getParentOfType(AntDomMacroDef.class, false);
    if (macrodef != null) {
      return new Pair<AntDomMacroDef, AntDomScriptDef>(macrodef, null);
    }
    for (AntDomCustomElement custom = element.getParentOfType(AntDomCustomElement.class, false); custom != null; custom = custom.getParentOfType(AntDomCustomElement.class, true)) {
      final AntDomNamedElement declaring = getDeclaringElement(custom.getXmlName());
      if (declaring instanceof AntDomMacroDef) {
        return new Pair<AntDomMacroDef, AntDomScriptDef>((AntDomMacroDef)declaring, null);
      }
      else if (declaring instanceof AntDomScriptDef) {
        return new Pair<AntDomMacroDef, AntDomScriptDef>(null, (AntDomScriptDef)declaring);
      }
    }
    return null;
  }

  @Nullable
  public AntDomElement findDeclaringElement(final AntDomElement parentElement, final XmlName customElementName) {
    final AntDomElement declaration = myDeclarations.get(customElementName);
    if (declaration == null) {
      return null;
    }
    
    if (declaration instanceof AntDomMacrodefElement) {
      final Pair<AntDomMacroDef, AntDomScriptDef> contextMacroOrScriptDef = getContextMacroOrScriptDef(parentElement);
      final AntDomMacroDef macrodefUsed = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getFirst() : null;
      if (macrodefUsed == null || !macrodefUsed.equals(declaration.getParentOfType(AntDomMacroDef.class, true))) {
        return null;
      }
    }
    else if (declaration instanceof AntDomScriptdefElement) {
      final Pair<AntDomMacroDef, AntDomScriptDef> contextMacroOrScriptDef = getContextMacroOrScriptDef(parentElement);
      final AntDomScriptDef scriptDefUsed = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getSecond() : null;
      if (scriptDefUsed == null || !scriptDefUsed.equals(declaration.getParentOfType(AntDomScriptDef.class, true))) {
        return null;
      }
    }
    
    return declaration;
  }

  public AntDomNamedElement getDeclaringElement(XmlName customElementName) {
    return myDeclarations.get(customElementName);
  }
  
  @Nullable
  public Class lookupClass(XmlName xmlName) {
    return myCustomElements.get(xmlName);
  }

  @Nullable
  public String lookupError(XmlName xmlName) {
    return myErrors.get(xmlName);
  }

  public boolean hasTypeLoadingErrors(AntDomTypeDef typedef) {
    final String generalError = myTypeDefErrors.get(typedef);
    if (generalError != null) {
      return true;
    }
    for (Map.Entry<XmlName, AntDomNamedElement> entry : myDeclarations.entrySet()) {
      if (typedef.equals(entry.getValue())) {
        if (myErrors.containsKey(entry.getKey()))  {
          return true;
        }
      }
    }
    return false;
  }

  public List<String> getTypeLoadingErrors(AntDomTypeDef typedef) {
    final String generalError = myTypeDefErrors.get(typedef);
    if (generalError != null) {
      return Collections.singletonList(generalError);
    }
    List<String> errors = null;
    for (Map.Entry<XmlName, AntDomNamedElement> entry : myDeclarations.entrySet()) {
      if (typedef.equals(entry.getValue())) {
        final XmlName xmlName = entry.getKey();
        if (myErrors.containsKey(xmlName))  {
          final String err = myErrors.get(xmlName);
          if (err != null) {
            if (errors == null) {
              errors = new ArrayList<String>();
            }
            errors.add(err);
          }
        }
      }
    }
    return errors == null? Collections.<String>emptyList() : errors;
  }
  
  private void rememberNamedClassLoader(AntDomCustomClasspathComponent typedef, AntDomProject antProject) {
    final String loaderRef = typedef.getLoaderRef().getStringValue();
    if (loaderRef != null) {
      if (!myNamedLoaders.containsKey(loaderRef)) {
        myNamedLoaders.put(loaderRef, createClassLoader(collectUrls(typedef), antProject));
      }
    }
  }

  @Nullable
  private ClassLoader getClassLoader(AntDomCustomClasspathComponent customComponent, AntDomProject antProject) {
    final String loaderRef = customComponent.getLoaderRef().getStringValue();
    if (loaderRef != null && myNamedLoaders.containsKey(loaderRef)) {
      return myNamedLoaders.get(loaderRef);
    }
    return createClassLoader(collectUrls(customComponent), antProject);
  }

  @Nullable
  public static PsiFile loadContentAsFile(PsiFile originalFile, LanguageFileType fileType) {
    final VirtualFile vFile = originalFile.getVirtualFile();
    if (vFile == null) {
      return null;
    }
    try {
      return loadContentAsFile(originalFile.getProject(), vFile.getInputStream(), fileType);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  public static PsiFile loadContentAsFile(Project project, InputStream stream, LanguageFileType fileType) throws IOException {
    final StringBuilder builder = new StringBuilder();
    try {
      int nextByte;
      while ((nextByte = stream.read()) >= 0) {
        builder.append((char)nextByte);
      }
    }
    finally {
      stream.close();
    }
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    return factory.createFileFromText("_ant_dummy__." + fileType.getDefaultExtension(), fileType, builder, LocalTimeCounter.currentTime(), false, false);
  }

  private void registerElement(AntDomNamedElement declaringElement, String customTagName, String nsUri, String classname, ClassLoader loader) {
    Class clazz = null;
    String error = null;
    try {
      clazz = loader.loadClass(classname);
    }
    catch (ClassNotFoundException e) {
      error = "Class not found " + e.getMessage();
      if (error == null) {
        error = "";
      }
      clazz = null;
    }
    catch (NoClassDefFoundError e) {
      error = "Class definition not found " + e.getMessage();
      if (error == null) {
        error = "";
      }
      clazz = null;
    }
    catch (UnsupportedClassVersionError e) {
      error = "Unsupported class version " + e.getMessage();
      if (error == null) {
        error = "";
      }
      clazz = null;
    }
    addCustomDefinition(declaringElement, customTagName, nsUri, clazz, error);
  }

  private void addCustomDefinition(@NotNull AntDomNamedElement declaringTag, String customTagName, String nsUri, Class clazz, String error) {
    final XmlName xmlName = new XmlName(customTagName, nsUri == null? "" : nsUri);
    if (error != null) {
      myErrors.put(xmlName, error);
    }
    myCustomElements.put(xmlName, clazz);
    myDeclarations.put(xmlName, declaringTag);
  }

  private static PsiFile createDummyFile(@NonNls final String name, final LanguageFileType type, final CharSequence str, Project project) {
    return PsiFileFactory.getInstance(project).createFileFromText(name, type, str, LocalTimeCounter.currentTime(), false, false);
  }

  private static boolean isXmlFormat(AntDomTypeDef typedef, @NotNull final String resourceOrFileName) {
    final String format = typedef.getFormat().getStringValue();
    if (format != null) {
      return "xml".equalsIgnoreCase(format);
    }
    return StringUtil.endsWithIgnoreCase(resourceOrFileName, ".xml");
  }

  @Nullable
  public static ClassLoader createClassLoader(final List<URL> urls, final AntDomProject antProject) {
    final ClassLoader parentLoader = antProject.getClassLoader();
    if (urls.size() == 0) {
      return parentLoader;
    }
    return new AntResourcesClassLoader(urls, parentLoader, false, false);
  }

  public static List<URL> collectUrls(AntDomClasspathElement typedef) {
    boolean cleanupNeeded = false;
    if (!ourIsBuildingClasspathForCustomTagLoading.get()) {
      ourIsBuildingClasspathForCustomTagLoading.set(Boolean.TRUE);
      cleanupNeeded = true;
    }

    try {
      final List<URL> urls = new ArrayList<URL>();
      // check classpath attribute
      final List<File> cpFiles = typedef.getClasspath().getValue();
      if (cpFiles != null) {
        for (File file : cpFiles) {
          try {
            urls.add(toLocalURL(file));
          }
          catch (MalformedURLException ignored) {
            LOG.info(ignored);
          }
        }
      }

      final HashSet<AntFilesProvider> processed = new HashSet<AntFilesProvider>();
      final AntDomElement referencedPath = typedef.getClasspathRef().getValue();
      if (referencedPath instanceof AntFilesProvider) {
        for (File cpFile : ((AntFilesProvider)referencedPath).getFiles(processed)) {
          try {
            urls.add(toLocalURL(cpFile));
          }
          catch (MalformedURLException ignored) {
            LOG.info(ignored);
          }
        }
      }
      // check nested elements
      
      for (final Iterator<AntDomElement> it = typedef.getAntChildrenIterator(); it.hasNext();) {
        AntDomElement child = it.next();
        if (child instanceof AntFilesProvider) {
          for (File cpFile : ((AntFilesProvider)child).getFiles(processed)) {
            try {
              urls.add(toLocalURL(cpFile));
            }
            catch (MalformedURLException ignored) {
              LOG.info(ignored);
            }
          }
        }
      }

      return urls;
    }
    finally {
      if (cleanupNeeded) {
        ourIsBuildingClasspathForCustomTagLoading.remove();
      }
    }
  }

  private static URL toLocalURL(final File file) throws MalformedURLException {
    String path = FileUtil.toSystemIndependentName(file.getPath());
    if (!(StringUtil.endsWithIgnoreCase(path, ".jar") || StringUtil.endsWithIgnoreCase(path, ".zip")) && file.isDirectory()) {
      if (!path.endsWith("/")) {
        path = path + "/";
      }
    }
    return new URL("file", "", path);
  }


  private class CustomTagDefinitionFinder extends AntDomRecursiveVisitor {
    private final Set<AntDomElement> myElementsOnThePath = new HashSet<AntDomElement>();
    private final Set<String> processedAntlibs = new HashSet<String>();
    private final AntDomProject myAntProject;

    public CustomTagDefinitionFinder(AntDomProject antProject) {
      myAntProject = antProject;
    }

    public void visitAntDomElement(AntDomElement element) {
      if (element instanceof AntDomCustomElement || myElementsOnThePath.contains(element)) {
        return; // avoid stack overflow
      }
      myElementsOnThePath.add(element);
      try {
        final XmlTag tag = element.getXmlTag();
        if (tag != null) {
          final String[] uris = tag.knownNamespaces();
          for (String uri : uris) {
            if (!processedAntlibs.contains(uri)) {
              processedAntlibs.add(uri);
              final String antLibResource = AntDomAntlib.toAntlibResource(uri);
              if (antLibResource != null) {
                final XmlElement xmlElement = element.getXmlElement();
                if (xmlElement != null) {
                  final ClassLoader loader = myAntProject.getClassLoader();
                  final InputStream stream = loader.getResourceAsStream(antLibResource);
                  if (stream != null) {
                    try {
                      final XmlFile xmlFile = (XmlFile)loadContentAsFile(xmlElement.getProject(), stream, StdFileTypes.XML);
                      if (xmlFile != null) {
                        loadDefinitionsFromAntlib(xmlFile, uri, loader, null, myAntProject);
                      }
                    }
                    catch (IOException e) {
                      LOG.info(e);
                    }
                  }
                }
              }
            }
          }
        }
        super.visitAntDomElement(element);
      }
      finally {
        myElementsOnThePath.remove(element);
      }
    }

    public void visitMacroDef(AntDomMacroDef macrodef) {
      final String customTagName = macrodef.getName().getStringValue();
      if (customTagName != null) {
        final String nsUri = macrodef.getUri().getStringValue();
        addCustomDefinition(macrodef, customTagName, nsUri, null, null);
        for (AntDomMacrodefElement element : macrodef.getMacroElements()) {
          final String customSubTagName = element.getName().getStringValue();
          if (customSubTagName != null) {
            addCustomDefinition(element, customSubTagName, nsUri, null, null);
          }
        }
      }
    }

    public void visitScriptDef(AntDomScriptDef scriptdef) {
      final String customTagName = scriptdef.getName().getStringValue();
      if (customTagName != null) {
        final String nsUri = scriptdef.getUri().getStringValue();
        final ClassLoader classLoader = getClassLoader(scriptdef, myAntProject);
        // register the scriptdef
        addCustomDefinition(scriptdef, customTagName, nsUri, null, null);
        // registering nested elements
        ReflectedProject reflectedProject = null;
        for (AntDomScriptdefElement element : scriptdef.getScriptdefElements()) {
          final String customSubTagName = element.getName().getStringValue();
          if (customSubTagName != null) {
            final String classname = element.getClassname().getStringValue();
            if (classname != null) {
              registerElement(element, customTagName, nsUri, classname, classLoader);
            }
            else {
              Class clazz = null;
              final String typeName = element.getElementType().getStringValue();
              if (typeName != null) {
                clazz = myCustomElements.get(new XmlName(typeName));
                if (clazz == null) {
                  if (reflectedProject == null) { // lazy init
                    reflectedProject = ReflectedProject.getProject(myAntProject.getClassLoader());
                  }
                  final Hashtable<String, Class> coreTasks = reflectedProject.getTaskDefinitions();
                  if (coreTasks != null) {
                    clazz = coreTasks.get(typeName);
                  }
                  if (clazz == null) {
                    final Hashtable<String, Class> coreTypes = reflectedProject.getDataTypeDefinitions();
                    if (coreTypes != null) {
                      clazz = coreTypes.get(typeName);
                    }
                  }
                }
              }
              addCustomDefinition(element, customSubTagName, nsUri, clazz, null);
            }
          }
        }
      }
    }

    public void visitPresetDef(AntDomPresetDef presetdef) {
      final String customTagName = presetdef.getName().getStringValue();
      if (customTagName != null) {
        final String nsUri = presetdef.getUri().getStringValue();
        addCustomDefinition(presetdef, customTagName, nsUri, null, null);
      }
    }

    public void visitTypeDef(AntDomTypeDef typedef) {
      // if loaderRef attribute is specified, make sure the loader is built and stored
      rememberNamedClassLoader(typedef, myAntProject);
      defineCustomElements(typedef, myAntProject);
    }

    public void visitInclude(AntDomInclude includeTag) {
      processInclude(includeTag);
    }

    public void visitImport(AntDomImport importTag) {
      processInclude(importTag);
    }

    private void processInclude(AntDomIncludingDirective directive) {
      final PsiFileSystemItem item = directive.getFile().getValue();
      if (item instanceof PsiFile) {
        final AntDomProject slaveProject = AntSupport.getAntDomProject((PsiFile)item);
        if (slaveProject != null) {
          slaveProject.accept(this);
        }
      }
    }

    private void defineCustomElements(AntDomTypeDef typedef, final AntDomProject antProject) {
      final String uri = typedef.getUri().getStringValue();
      final String customTagName = typedef.getName().getStringValue();
      final String classname = typedef.getClassName().getStringValue();

      if (classname != null && customTagName != null) {
        registerElement(typedef, customTagName, uri, classname, getClassLoader(typedef, antProject));
      }
      else {
        defineCustomElementsFromResources(typedef, uri, antProject, null);
      }
    }

    private void defineCustomElementsFromResources(AntDomTypeDef typedef, final String uri, AntDomProject antProject, ClassLoader loader) {
      final XmlElement xmlElement = antProject.getXmlElement();
      final Project project = xmlElement != null? xmlElement.getProject() : null;
      if (project == null) {
        return;
      }
      XmlFile xmlFile = null;
      PropertiesFile propFile = null;

      final String resource = typedef.getResource().getStringValue();
      if (resource != null) {
        if (loader == null) {
          loader = getClassLoader(typedef, antProject);
        }
        if (loader != null) {
          final InputStream stream = loader.getResourceAsStream(resource);
          if (stream != null) {
            try {
              if (isXmlFormat(typedef, resource)) {
                xmlFile = (XmlFile)loadContentAsFile(project, stream, StdFileTypes.XML);
              }
              else {
                propFile = (PropertiesFile)loadContentAsFile(project, stream, StdFileTypes.PROPERTIES);
              }
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
          else {
            myTypeDefErrors.put(typedef, "Resource \"" + resource + "\" not found in the classpath");
          }
        }
      }
      else {
        final PsiFileSystemItem file = typedef.getFile().getValue();
        if (file instanceof PsiFile) {
          if (isXmlFormat(typedef, file.getName())) {
            xmlFile = file instanceof XmlFile ? (XmlFile)file : (XmlFile)loadContentAsFile((PsiFile)file, StdFileTypes.XML);
          }
          else { // assume properties format
            propFile = file instanceof PropertiesFile ? (PropertiesFile)file : (PropertiesFile)loadContentAsFile((PsiFile)file, StdFileTypes.PROPERTIES);
          }
        }
      }

      if (propFile != null) {
        if (loader == null) { // if not initialized yet
          loader = getClassLoader(typedef, antProject);
        }
        for (final IProperty property : propFile.getProperties()) {
          registerElement(typedef, property.getUnescapedKey(), uri, property.getValue(), loader);
        }
      }

      if (xmlFile != null) {
        if (loader == null) { // if not initialized yet
          loader = getClassLoader(typedef, antProject);
        }
        loadDefinitionsFromAntlib(xmlFile, uri, loader, typedef, antProject);
      }
    }

    private void loadDefinitionsFromAntlib(XmlFile xmlFile, String uri, ClassLoader loader, @Nullable AntDomTypeDef typedef, AntDomProject antProject) {
      final AntDomAntlib antLib = AntSupport.getAntLib(xmlFile);
      if (antLib != null) {
        final List<AntDomTypeDef> defs = new ArrayList<AntDomTypeDef>();
        defs.addAll(antLib.getTaskdefs());
        defs.addAll(antLib.getTypedefs());
        if (!defs.isEmpty()) {
          for (AntDomTypeDef def : defs) {
            final String tagName = def.getName().getStringValue();
            final String className = def.getClassName().getStringValue();
            if (tagName != null && className != null) {
              registerElement(typedef != null? typedef : def, tagName, uri, className, loader);
            }
            else {
              defineCustomElementsFromResources(def, uri, antProject, loader);
            }
          }
        }
      }
    }

  }
}
