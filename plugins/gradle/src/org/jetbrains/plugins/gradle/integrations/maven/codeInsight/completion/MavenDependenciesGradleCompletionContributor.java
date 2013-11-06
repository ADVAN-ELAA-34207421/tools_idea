/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult;
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.plugins.gradle.codeInsight.AbstractGradleCompletionContributor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Vladislav.Soroka
 * @since 10/31/13
 */
public class MavenDependenciesGradleCompletionContributor extends AbstractGradleCompletionContributor {
  private static final String GROUP_LABEL = "group";
  private static final String NAME_LABEL = "name";
  private static final String VERSION_LABEL = "version";

  private static final ElementPattern<PsiElement> DEPENDENCIES_CALL_PATTERN = psiElement()
    .inside(true, psiElement(GrMethodCallExpression.class).with(new PatternCondition<GrMethodCallExpression>("withInvokedExpressionText") {
      @Override
      public boolean accepts(@NotNull GrMethodCallExpression expression, ProcessingContext context) {
        GrExpression grExpression = expression.getInvokedExpression();
        return grExpression != null && "dependencies".equals(grExpression.getText());
      }
    }));

  private static final ElementPattern<PsiElement> IN_BASIC_DEPENDENCY_NOTATION = psiElement()
    .and(GRADLE_FILE_PATTERN)
    .withParent(GrLiteral.class)
    .withSuperParent(2, psiElement(GrCommandArgumentList.class))
    .and(DEPENDENCIES_CALL_PATTERN);

  private static final ElementPattern<PsiElement> IN_MAP_DEPENDENCY_NOTATION = psiElement()
    .and(GRADLE_FILE_PATTERN)
    .withParent(GrLiteral.class)
    .withSuperParent(2, psiElement(GrNamedArgument.class))
    .and(DEPENDENCIES_CALL_PATTERN);


  public MavenDependenciesGradleCompletionContributor() {
    // group:name:version notation
    // e.g.:
    //    compile 'junit:junit:4.11'
    extend(CompletionType.BASIC, IN_BASIC_DEPENDENCY_NOTATION, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters params,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        result.stopHere();

        String searchText = CompletionUtil.findReferenceOrAlphanumericPrefix(params);
        MavenArtifactSearcher searcher = new MavenArtifactSearcher();
        List<MavenArtifactSearchResult> searchResults = searcher.search(params.getPosition().getProject(), searchText, 100);
        for (MavenArtifactSearchResult searchResult : searchResults) {
          for (MavenArtifactInfo artifactInfo : searchResult.versions) {
            final StringBuilder buf = new StringBuilder();
            MavenId.append(buf, artifactInfo.getGroupId());
            MavenId.append(buf, artifactInfo.getArtifactId());
            MavenId.append(buf, artifactInfo.getVersion());

            LookupElement builder = LookupElementBuilder.create(buf.toString()).withIcon(AllIcons.Nodes.PpLib);
            result.addElement(builder);
          }
        }
      }
    });

    // map-style notation:
    // e.g.:
    //    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'
    extend(CompletionType.BASIC, IN_MAP_DEPENDENCY_NOTATION, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters params,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        result.stopHere();

        final PsiElement parent = params.getPosition().getParent().getParent();
        if (!(parent instanceof GrNamedArgument) || !(parent.getParent() instanceof GrCommandArgumentList)) return;

        final GrNamedArgument namedArgument = (GrNamedArgument)parent;
        if (GROUP_LABEL.equals(namedArgument.getLabelName())) {
          MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(namedArgument.getProject());
          for (String groupId : m.getGroupIds()) {
            LookupElement builder = LookupElementBuilder.create(groupId).withIcon(AllIcons.Nodes.PpLib);
            result.addElement(builder);
          }
        }
        else if (NAME_LABEL.equals(namedArgument.getLabelName())) {
          String groupId = findNamedArgumentValue((GrCommandArgumentList)namedArgument.getParent(), GROUP_LABEL);
          if (groupId == null) return;

          MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(namedArgument.getProject());
          for (String artifactId : m.getArtifactIds(groupId)) {
            LookupElement builder = LookupElementBuilder.create(artifactId).withIcon(AllIcons.Nodes.PpLib);
            result.addElement(builder);
          }
        }
        else if (VERSION_LABEL.equals(namedArgument.getLabelName())) {
          GrCommandArgumentList argumentList = (GrCommandArgumentList)namedArgument.getParent();

          String groupId = findNamedArgumentValue(argumentList, GROUP_LABEL);
          if (groupId == null) return;

          String artifactId = findNamedArgumentValue(argumentList, NAME_LABEL);
          if (artifactId == null) return;

          MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(namedArgument.getProject());
          for (String version : m.getVersions(groupId, artifactId)) {
            LookupElement builder = LookupElementBuilder.create(version).withIcon(AllIcons.Nodes.PpLib);
            result.addElement(builder);
          }
        }
      }
    });
  }
}
