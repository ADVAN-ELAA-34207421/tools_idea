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
package com.intellij.codeInsight.template.zencoding.tokens;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlFile;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class TemplateToken extends ZenCodingToken {
  private final String myKey;
  private TemplateImpl myTemplate;
  private final List<Pair<String, String>> myAttribute2Value;
  private XmlFile myFile;

  public TemplateToken(String key, List<Pair<String, String>> attribute2value) {
    myKey = key;
    myAttribute2Value = attribute2value;
  }

  public List<Pair<String, String>> getAttribute2Value() {
    return myAttribute2Value;
  }

  public XmlFile getFile() {
    return myFile;
  }

  public void setFile(XmlFile file) {
    myFile = file;
  }

   public String getKey() {
    return myKey;
  }

  public void setTemplate(TemplateImpl template) {
    myTemplate = template;
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  public String toString() {
    return "TEMPLATE";
  }
}
