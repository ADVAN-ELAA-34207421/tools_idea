package org.jetbrains.idea.maven.dom.model.completion;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.maven.dom.MavenDomProjectModelDescription;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;

import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class MavenPomXmlCompletionTagListenerContributor extends CompletionContributor {

  private final Set<String> myHandledTags = ImmutableSet.of("dependency");

  //private final MultiMap<String, TagInsertListener> myListenersMap;
  //{
  //  MultiMap<String, TagInsertListener> listenersMap = new MultiMap<String, TagInsertListener>();
  //  for (TagInsertListener listener : new TagInsertListener[]{}) {
  //
  //  }
  //
  //  myListenersMap = listenersMap;
  //}

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    PsiFile psiFile = parameters.getOriginalFile();
    if (!(psiFile instanceof XmlFile)) return;

    if (!MavenDomUtil.isProjectFile(psiFile)) return;

    DomFileDescription<?> description = DomManager.getDomManager(psiFile.getProject()).getDomFileDescription((XmlFile)psiFile);

    if (!(description instanceof MavenDomProjectModelDescription)) return;

    result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      @Override
      public void consume(CompletionResult r) {
        final LookupElement lookupElement = r.getLookupElement();

        if (myHandledTags.contains(lookupElement.getLookupString())) {
          LookupElement decorator =
            LookupElementDecorator.withInsertHandler(lookupElement, new InsertHandler<LookupElementDecorator<LookupElement>>() {
              @Override
              public void handleInsert(final InsertionContext context, LookupElementDecorator<LookupElement> item) {
                lookupElement.handleInsert(context);

                Object object = lookupElement.getObject();
                if ("dependency".equals(lookupElement.getLookupString()) && object instanceof XmlTag
                    && "maven-4.0.0.xsd".equals(((XmlTag)object).getContainingFile().getName())) {
                  context.commitDocument();

                  PsiElement psiElement = context.getFile().findElementAt(context.getEditor().getCaretModel().getOffset());
                  XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
                  if (xmlTag != null) {
                    DomElement domElement = DomManager.getDomManager(context.getProject()).getDomElement(xmlTag);
                    if (domElement instanceof MavenDomDependency) {
                      context.setLaterRunnable(new Runnable() {
                        @Override
                        public void run() {
                          new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(context.getProject(), context.getEditor());
                        }
                      });
                    }
                  }
                }
              }
            });

          r = r.withLookupElement(decorator);
        }

        result.passResult(r);
      }
    });
  }

  //private static abstract class TagInsertListener {
  //  public abstract String getTagName();
  //
  //  public abstract boolean isApplicable(InsertionContext context);
  //
  //  public abstract void onInsert(InsertionContext context);
  //}

  //private static class DomTagInsertHandler extends TagInsertListener {
  //  private final String myTagName;
  //
  //  private final Class myDomClass;
  //
  //  private DomTagInsertHandler(String tagName, Class domClass) {
  //    myTagName = tagName;
  //    myDomClass = domClass;
  //  }
  //
  //
  //  @Override
  //  public String getTagName() {
  //    return myTagName;
  //  }
  //
  //  @Override
  //  public boolean isApplicable(InsertionContext context) {
  //    context.get
  //    return false;
  //  }
  //
  //  @Override
  //  public void onInsert(InsertionContext context) {
  //
  //  }
  //}
}