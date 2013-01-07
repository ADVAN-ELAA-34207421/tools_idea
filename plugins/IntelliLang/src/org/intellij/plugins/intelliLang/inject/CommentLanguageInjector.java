package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public class CommentLanguageInjector implements MultiHostInjector {

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiLanguageInjectionHost.class);
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;
    final ElementManipulator<PsiLanguageInjectionHost> manipulator = ElementManipulators.getManipulator(host);
    if (manipulator == null) return;
    TextRange rangeInElement = manipulator.getRangeInElement(host);
    if (rangeInElement.isEmpty()) return;
    BaseInjection injection = InjectorUtils.findCommentInjection(context, "comment", Ref.<PsiComment>create());
    if (injection == null) return;
    InjectedLanguage injectedLanguage = InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
    Language language = injectedLanguage != null ? injectedLanguage.getLanguage() : null;
    if (language != null) {
      Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> info =
        Trinity.create(host, injectedLanguage, rangeInElement);
      InjectorUtils.registerInjection(language, Collections.singletonList(info), context.getContainingFile(), registrar);
    }
  }
}
