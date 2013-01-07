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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrReferenceListElementType;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */

public class ReferenceElement implements GroovyElementTypes {
  public static final String DUMMY_IDENTIFIER = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED; //inserted by completion

  public static IElementType parseReferenceList(PsiBuilder builder,
                                                final IElementType startElement,
                                                final GrReferenceListElementType<?> clauseType) {
    PsiBuilder.Marker isMarker = builder.mark();

    if (!ParserUtils.getToken(builder, startElement)) {
      isMarker.rollbackTo();
      return NONE;
    }

    ParserUtils.getToken(builder, mNLS);

    if (parseReferenceElement(builder)== fail) {
      isMarker.rollbackTo();
      return WRONGWAY;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (parseReferenceElement(builder) == fail) {
        isMarker.rollbackTo();
        return WRONGWAY;
      }
    }

    ParserUtils.getToken(builder, mNLS);
    isMarker.done(clauseType);
    return clauseType;
  }

  public enum ReferenceElementResult {
    mayBeType, mustBeType, fail
  }

  public static ReferenceElementResult parseForImport(PsiBuilder builder) {
    return parse(builder, false, false, true, false, false);
  }

  public static ReferenceElementResult parseForPackage(PsiBuilder builder) {
    return parse(builder, false, false, false, false, false);
  }

  
  //it doesn't important first letter of identifier of ThrowClause, of Annotation, of new Expresion, of implements, extends, superclass clauses
  public static ReferenceElementResult parseReferenceElement(PsiBuilder builder) {
    return parseReferenceElement(builder, false, true);
  }

  public static ReferenceElementResult parseReferenceElement(PsiBuilder builder, boolean isUpperCase, final boolean expressionPossible) {
    return parse(builder, isUpperCase, true, false, false, expressionPossible);
  }

  public static ReferenceElementResult parse(PsiBuilder builder,
                                             boolean checkUpperCase,
                                             boolean parseTypeArgs,
                                             boolean forImport,
                                             final boolean allowDiamond,
                                             boolean expressionPossible) {
    PsiBuilder.Marker internalTypeMarker = builder.mark();

    String lastIdentifier = builder.getTokenText();

    if (!ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS)) {
      internalTypeMarker.rollbackTo();
      return fail;
    }

    boolean hasTypeArguments = false;
    if (parseTypeArgs) {
      hasTypeArguments = TypeArguments.parseTypeArguments(builder, expressionPossible, allowDiamond);
    }

    internalTypeMarker.done(REFERENCE_ELEMENT);
    internalTypeMarker = internalTypeMarker.precede();

    while (mDOT.equals(builder.getTokenType())) {

      if ((ParserUtils.lookAhead(builder, mDOT, mSTAR) ||
          ParserUtils.lookAhead(builder, mDOT, mNLS, mSTAR)) &&
          forImport) {
        internalTypeMarker.drop();
        return mayBeType;
      }

      ParserUtils.getToken(builder, mDOT);

      if (forImport) {
        ParserUtils.getToken(builder, mNLS);
      }

      lastIdentifier = builder.getTokenText();

      if (!ParserUtils.getToken(builder, TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS)) {
        internalTypeMarker.rollbackTo();
        return fail;
      }

      if (parseTypeArgs) {
        hasTypeArguments = TypeArguments.parseTypeArguments(builder, expressionPossible, allowDiamond) || hasTypeArguments;
      }

      internalTypeMarker.done(REFERENCE_ELEMENT);
      internalTypeMarker = internalTypeMarker.precede();
    }

    char firstChar;
    if (lastIdentifier != null) firstChar = lastIdentifier.charAt(0);
    else return fail;

    if (checkUpperCase && (!Character.isUpperCase(firstChar) || DUMMY_IDENTIFIER.equals(lastIdentifier))) { //hack to make completion work
      internalTypeMarker.rollbackTo();
      return fail;
    }

    //    internalTypeMarker.done(TYPE_ELEMENT);
    internalTypeMarker.drop();
    return hasTypeArguments ? mustBeType : mayBeType;
  }

}