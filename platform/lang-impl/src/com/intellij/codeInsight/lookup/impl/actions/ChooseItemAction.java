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
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

public abstract class ChooseItemAction extends EditorAction {
  public ChooseItemAction(Handler handler){
    super(handler);
  }

  @NotNull
  static LookupImpl getLookup(Editor editor) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null) {
      throw new AssertionError("The last lookup disposed at: " + LookupImpl.getLastLookupDisposeTrace() + "\n-----------------------\n");
    }
    return lookup;
  }

  protected static class Handler extends EditorActionHandler {
    final boolean focusedOnly;

    Handler(boolean focusedOnly) {
      this.focusedOnly = focusedOnly;
    }

    @Override
    public void execute(@NotNull final Editor editor, final DataContext dataContext) {
      LookupImpl lookup = getLookup(editor);
      if (!lookup.isFocused()) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CONTROL_ENTER);
      }

      lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }


    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup == null) return false;
      if (!lookup.isAvailableToUser()) return false;
      if (focusedOnly && !lookup.isFocused()) return false;
      if (ChooseItemReplaceAction.hasTemplatePrefix(lookup, TemplateSettings.ENTER_CHAR)) return false;
      return true;
    }
  }

  public static class Always extends ChooseItemAction {
    public Always() {
      super(new Handler(false));
    }
  }
  public static class FocusedOnly extends ChooseItemAction {
    public FocusedOnly() {
      super(new Handler(true));
    }
  }

}
