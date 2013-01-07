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
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class AbstractConvertContext extends ConvertContext {

  public static ConvertContext createConvertContext(final DomElement domElement) {
    return new AbstractConvertContext() {
      @NotNull
      public DomElement getInvocationElement() {
        return domElement;
      }
    };
  }

  public final XmlTag getTag() {
    return getInvocationElement().getXmlTag();
  }

  @Nullable
  public XmlElement getXmlElement() {
    return getInvocationElement().getXmlElement();
  }

  @NotNull
  public final XmlFile getFile() {
    return DomUtil.getFile(getInvocationElement());
  }

  public Module getModule() {
    final DomFileElement<DomElement> fileElement = DomUtil.getFileElement(getInvocationElement());
    if (fileElement == null) {
      final XmlElement xmlElement = getInvocationElement().getXmlElement();
      return xmlElement == null? null : ModuleUtil.findModuleForPsiElement(xmlElement);
    }
    return fileElement.getRootElement().getModule();
  }

  public PsiManager getPsiManager() {
    return getFile().getManager();
  }

}
