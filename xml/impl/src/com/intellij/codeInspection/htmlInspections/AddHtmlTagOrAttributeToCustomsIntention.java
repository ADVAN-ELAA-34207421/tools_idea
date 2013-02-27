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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class AddHtmlTagOrAttributeToCustomsIntention implements IntentionAction {
  private final String myName;
  private final int myType;
  private final String myInspectionName;

  public AddHtmlTagOrAttributeToCustomsIntention(String shortName, String name, int type) {
    myInspectionName = shortName;
    myName = name;
    myType = type;
  }

  @NotNull
  public String getText() {
    if (myType == XmlEntitiesInspection.UNKNOWN_TAG) {
      return XmlBundle.message("add.custom.html.tag", myName);
    }

    if (myType == XmlEntitiesInspection.UNKNOWN_ATTRIBUTE) {
      return XmlBundle.message("add.custom.html.attribute", myName);
    }

    if (myType == XmlEntitiesInspection.NOT_REQUIRED_ATTRIBUTE) {
      return XmlBundle.message("add.optional.html.attribute", myName);
    }

    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return XmlBundle.message("fix.html.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    InspectionProjectProfileManager.getInstance(project).getInspectionProfile().modifyProfile(new Consumer<ModifiableModel>() {
      @Override
      public void consume(ModifiableModel model) {
        XmlEntitiesInspection xmlEntitiesInspection = (XmlEntitiesInspection)model.getUnwrappedTool(myInspectionName, file);
        xmlEntitiesInspection.setAdditionalEntries(myType, appendName(xmlEntitiesInspection.getAdditionalEntries(myType)));
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  private String appendName(String toAppend) {
    if (toAppend.length() > 0) {
      toAppend += "," + myName;
    }
    else {
      toAppend = myName;
    }
    return toAppend;
  }

}
