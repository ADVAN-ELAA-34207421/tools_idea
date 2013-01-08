/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

/**
 * @author ilyas
 */
public class GrStringImpl extends GrAbstractLiteral implements GrString {

  public GrStringImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Compound Gstring";
  }

  public PsiType getType() {
    return getTypeByFQName(findChildByClass(GrStringInjection.class) != null ? GROOVY_LANG_GSTRING : JAVA_LANG_STRING);
  }

  public boolean isPlainString() {
    return !getText().startsWith("\"\"\"");
  }

  @Override
  public GrStringInjection[] getInjections() {
    return findChildrenByClass(GrStringInjection.class);
  }

  @Override
  public String[] getTextParts() {
    List<PsiElement> parts = findChildrenByType(GroovyTokenTypes.mGSTRING_CONTENT);

    String[] result = new String[parts.size()];
    int i = 0;
    for (PsiElement part : parts) {
      result[i++] = part.getText();
    }
    return result;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitGStringExpression(this);
  }

  public Object getValue() {
    if (findChildByClass(GrStringInjection.class) != null) return null;

    final PsiElement fchild = getFirstChild();
    if (fchild == null) return null;

    final PsiElement content = fchild.getNextSibling();
    if (content == null || content.getNode().getElementType() != GroovyTokenTypes.mGSTRING_CONTENT) return null;

    final String text = content.getText();
    StringBuilder chars = new StringBuilder(text.length());
    boolean result = GrStringUtil.parseStringCharacters(text, chars, null);
    return result ? chars.toString() : null;
  }
}