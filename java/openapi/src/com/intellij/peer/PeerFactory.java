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
package com.intellij.peer;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileChooser.FileSystemTreeFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.ui.UIHelper;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.errorView.ErrorViewFactory;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcServer;

import java.net.InetAddress;

public abstract class PeerFactory {
  public static PeerFactory getInstance() {
    return ServiceManager.getService(PeerFactory.class);
  }

  @Deprecated
  public abstract FileStatusFactory getFileStatusFactory();

  @Deprecated
  public abstract DialogWrapperPeerFactory getDialogWrapperPeerFactory();

  @Deprecated
  public abstract PackageSetFactory getPackageSetFactory();

  public abstract UIHelper getUIHelper();

  @Deprecated
  public abstract ErrorViewFactory getErrorViewFactory();

  @Deprecated
  public abstract ContentFactory getContentFactory();

  @Deprecated
  public abstract FileSystemTreeFactory getFileSystemTreeFactory();

  @Deprecated
  public abstract DiffRequestFactory getDiffRequestFactory();

  @Deprecated
  public abstract VcsContextFactory getVcsContextFactory();

  @Deprecated
  public abstract PsiBuilder createBuilder(ASTNode tree, Language lang, CharSequence seq, final Project project);

  @Deprecated
  public abstract PsiBuilder createBuilder(ASTNode tree, Lexer lexer, Language lang, CharSequence seq, final Project project);

  public abstract XmlRpcServer createRpcServer();

  public abstract WebServer createWebServer(int port, InetAddress addr, XmlRpcServer xmlrpc);

  @Deprecated
  public abstract EditorHighlighter createEditorHighlighter(SyntaxHighlighter syntaxHighlighter, EditorColorsScheme colors);

  public abstract Sdk createProjectJdk(String name, final String version, final String homePath, SdkType sdkType);

  public abstract ModuleConfigurationEditor createModuleConfigurationEditor(String moduleName, ModuleConfigurationState state);
}