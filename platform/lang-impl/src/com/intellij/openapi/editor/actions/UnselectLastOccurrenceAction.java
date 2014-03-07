/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.Nullable;

public class UnselectLastOccurrenceAction extends EditorAction {
  protected UnselectLastOccurrenceAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return super.isEnabled(editor, dataContext) && editor.getCaretModel().supportsMultipleCarets();
    }

    @Override
    public void execute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (editor.getCaretModel().getAllCarets().size() > 1) {
        editor.getCaretModel().removeCaret(editor.getCaretModel().getPrimaryCaret());
      }
      else {
        editor.getSelectionModel().removeSelection();
      }
      SelectNextOccurrenceAction.Handler.getAndResetNotFoundStatus(editor);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }
}