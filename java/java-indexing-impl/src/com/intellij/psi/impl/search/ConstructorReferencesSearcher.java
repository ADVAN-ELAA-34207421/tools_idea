package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ConstructorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public ConstructorReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
    final PsiElement element = p.getElementToSearch();
    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      new ConstructorReferencesSearchHelper(element.getManager())
        .processConstructorReferences(consumer, (PsiMethod)p.getElementToSearch(), p.getScope(), p.isIgnoreAccessScope(), true, p.getOptimizer());
    }
  }
}
