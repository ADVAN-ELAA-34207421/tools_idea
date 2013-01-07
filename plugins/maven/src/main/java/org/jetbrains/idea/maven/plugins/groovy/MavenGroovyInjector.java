/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy;

import org.jetbrains.idea.maven.utils.MavenPluginConfigurationLanguageInjector;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyInjector extends MavenPluginConfigurationLanguageInjector {
  public MavenGroovyInjector() {
    super("source", Arrays.asList("org.codehaus.groovy.maven", "org.codehaus.gmaven"), "gmaven-plugin", GroovyFileType.GROOVY_LANGUAGE);
  }

}
