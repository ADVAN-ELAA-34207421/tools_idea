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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightLocalVariable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrExpressionImpl implements GrAssignmentExpression {

  private static final Function<GrAssignmentExpressionImpl, PsiType> TYPE_CALCULATOR =
    new NullableFunction<GrAssignmentExpressionImpl, PsiType>() {
      @Override
      public PsiType fun(GrAssignmentExpressionImpl assignment) {
        final GroovyResolveResult[] results = assignment.multiResolve(false);

        if (results.length == 0) {
          final GrExpression rValue = assignment.getRValue();
          return rValue == null ? null : rValue.getType();
        }


        PsiType returnType = null;
        final PsiManager manager = assignment.getManager();
        for (GroovyResolveResult result : results) {
          final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(result, assignment, new PsiType[]{getRValueType(assignment)});
          returnType = TypesUtil.getLeastUpperBoundNullable(returnType, substituted, manager);
        }
        return returnType;
      }
    };
  private static final Key<CachedValue<GrLightLocalVariable>> LOCAL_VAR_KEY = Key.create("local var");

  public GrAssignmentExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  @NotNull
  public GrExpression getLValue() {
    return findExpressionChild(this);
  }

  @Nullable
  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  public IElementType getOperationToken() {
    return getOpToken().getNode().getElementType();
  }

  public PsiElement getOpToken() {
    return findNotNullChildByType(TokenSets.ASSIGN_OP_SET);
  }

  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent != null) {
      return true;
    }

    GrExpression lValue = getLValue();
    if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression)lValue).getQualifier() == null) {
      String refName = processor instanceof ResolverProcessor ? ((ResolverProcessor)processor).getName() : null;
      if (isDeclarationAssignment((GrReferenceExpression)lValue, refName)) {

        GrVariable var = getOrCreateLocalVar();
        if (!processor.execute(var, ResolveState.initial())) {
          return false;
        }

        //if (!processor.execute(lValue, ResolveState.initial())) return false;
      }
    }

    return true;
  }

  private GrVariable getOrCreateLocalVar() {
    GrExpression lValue = getLValue();
    CachedValue<GrLightLocalVariable> local = lValue.getUserData(LOCAL_VAR_KEY);
    if (local == null) {
      local = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<GrLightLocalVariable>() {
        @Nullable
        @Override
        public Result<GrLightLocalVariable> compute() {
          return Result.create(new GrLightLocalVariable(GrAssignmentExpressionImpl.this), PsiModificationTracker.MODIFICATION_COUNT);
        }
      });
      lValue.putUserData(LOCAL_VAR_KEY, local);
    }
    return local.getValue();
  }

  private static boolean isDeclarationAssignment(@NotNull GrReferenceExpression lRefExpr, @Nullable String nameHint) {
    if (nameHint == null || nameHint.equals(lRefExpr.getName())) {
      final PsiElement target = lRefExpr.resolve(); //this is NOT quadratic since the next statement will prevent from further processing declarations upstream
      if (!(target instanceof PsiVariable || target instanceof GrAccessorMethod)) {
        return true;
      }
    }
    return false;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement token = getOpToken();
    assert token != null;
    final int offset = token.getStartOffsetInParent();
    return new TextRange(offset, offset + token.getTextLength());
  }

  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("assignment expression cannot be renamed");
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("assignment expression cannot be bound to anything");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    final IElementType operationToken = getOperationToken();
    if (operationToken == GroovyTokenTypes.mASSIGN) return null;

    return this;
  }

  private static final ResolveCache.PolyVariantResolver<GrAssignmentExpressionImpl> RESOLVER =
    new ResolveCache.PolyVariantResolver<GrAssignmentExpressionImpl>() {
      @NotNull
      @Override
      public GroovyResolveResult[] resolve(@NotNull GrAssignmentExpressionImpl assignmentExpression, boolean incompleteCode) {
        final IElementType opType = assignmentExpression.getOperationToken();
        if (opType == null || opType == GroovyTokenTypes.mASSIGN) return GroovyResolveResult.EMPTY_ARRAY;

        final GrExpression lValue = assignmentExpression.getLValue();
        final PsiType lType;
        if (!(lValue instanceof GrIndexProperty)) {
          lType = lValue.getType();
        }
        else {
          /*
          now we have something like map[i] += 2. It equals to map.putAt(i, map.getAt(i).plus(2))
          by default map[i] resolves to putAt, but we need getAt(). so this hack is for it =)
           */
          lType = ((GrExpression)lValue.copy()).getType();
        }
        if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;

        PsiType rType = getRValueType(assignmentExpression);

        final IElementType operatorToken = TokenSets.ASSIGNMENTS_TO_OPERATORS.get(opType);
        return TypesUtil.getOverloadedOperatorCandidates(lType, operatorToken, assignmentExpression, new PsiType[]{rType});
      }
    };

  @Nullable
  private static PsiType getRValueType(GrAssignmentExpressionImpl assignmentExpression) {
    final GrExpression rightOperand = assignmentExpression.getRValue();
    return rightOperand == null ? null : rightOperand.getType();
  }
}
