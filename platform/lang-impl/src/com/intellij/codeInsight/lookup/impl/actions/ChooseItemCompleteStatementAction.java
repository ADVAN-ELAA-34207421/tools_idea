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

package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class ChooseItemCompleteStatementAction extends EditorAction {
  public ChooseItemCompleteStatementAction(){
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_SMART_ENTER);
      ChooseItemAction.getLookup(editor).finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      return lookup != null && lookup.isFocused() && lookup.isAvailableToUser();
    }
  }

}