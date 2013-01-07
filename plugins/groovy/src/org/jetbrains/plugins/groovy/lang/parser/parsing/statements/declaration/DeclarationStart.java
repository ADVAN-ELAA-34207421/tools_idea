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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/**
 * DeclarationStart ::= "def"
 * | modifier
 * | @IDENT
 * | (upperCaseIdent | builtInType | QulifiedTypeName)  {LBRACK balacedTokens RBRACK} IDENT
 */

public class DeclarationStart implements GroovyElementTypes {
  /*
   * @deprecated
   */

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker declStartMarker = builder.mark();

    if (Declaration.parse(builder, false, parser)) {
      declStartMarker.rollbackTo();
      return true;
    } else {
      declStartMarker.rollbackTo();
      return false;
    }
  }

}