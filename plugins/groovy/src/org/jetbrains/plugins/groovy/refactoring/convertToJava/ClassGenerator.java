/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.jetbrains.plugins.groovy.refactoring.convertToJava.TypeWriter.writeType;

/**
 * @author Maxim.Medvedev
 */
public class ClassGenerator {

  private ClassNameProvider classNameProvider;
  private ClassItemGenerator classItemGenerator;

  public ClassGenerator(ClassNameProvider classNameProvider, ClassItemGenerator classItemGenerator) {
    this.classNameProvider = classNameProvider;
    this.classItemGenerator = classItemGenerator;
  }

  private void writePackageStatement(StringBuilder text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      ModifierListGenerator.writeModifiers(text, packageDefinition.getAnnotationList(), ModifierListGenerator.JAVA_MODIFIERS,
                                           classItemGenerator.generateAnnotations());
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(';');
      text.append('\n');
      text.append('\n');
    }
  }

  public void writeTypeDefinition(StringBuilder text, @NotNull final PsiClass typeDefinition, boolean toplevel, boolean insertPackageSmst) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    final GroovyFile containingFile = (GroovyFile)typeDefinition.getContainingFile();
    if (insertPackageSmst) {
      writePackageStatement(text, containingFile.getPackageDefinition());
    }

    boolean isEnum = typeDefinition.isEnum();
    boolean isAnnotationType = typeDefinition.isAnnotationType();
    boolean isInterface = typeDefinition.isInterface();

    ModifierListGenerator.writeClassModifiers(text, typeDefinition.getModifierList(), typeDefinition.isInterface(), toplevel,
                                              classItemGenerator.generateAnnotations());

    if (isAnnotationType) {
      text.append('@');
    }

    if (isInterface) {
      text.append("interface");
    }
    else if (isEnum) {
      text.append("enum");
    }
    else {
      text.append("class");
    }

    text.append(' ').append(typeDefinition.getName());

    GenerationUtil.writeTypeParameters(text, typeDefinition, classNameProvider);

    text.append(' ');

    if (isScript) {
      text.append("extends ").append(GroovyCommonClassNames.GROOVY_LANG_SCRIPT).append(' ');
    }
    else if (!isEnum && !isAnnotationType) {
      classItemGenerator.writeExtendsList(text, typeDefinition);
      classItemGenerator.writeImplementsList(text, typeDefinition);
    }

    text.append("{\n");

    writeMembers(text, typeDefinition);
    text.append('}');
  }

  public void writeMembers(StringBuilder text, PsiClass typeDefinition) {
    if (typeDefinition instanceof GrEnumTypeDefinition) {
      final GrEnumConstant[] enumConstants = ((GrEnumTypeDefinition)typeDefinition).getEnumConstants();
      for (GrEnumConstant constant : enumConstants) {
        classItemGenerator.writeEnumConstant(text, constant);
        text.append(',');
      }
      if (enumConstants.length > 0) {
        //text.removeFromTheEnd(1).append(";\n");
        text.delete(text.length() - 1, text.length());
      }
      text.append(";\n");
    }

    writeAllMethods(text, classItemGenerator.collectMethods(typeDefinition), typeDefinition);

    if (typeDefinition instanceof GrTypeDefinition) {
      for (GrMembersDeclaration declaration : ((GrTypeDefinition)typeDefinition).getMemberDeclarations()) {
        if (declaration instanceof GrVariableDeclaration) {
          classItemGenerator.writeVariableDeclarations(text, (GrVariableDeclaration)declaration);
        }
      }
      for (PsiClass inner : typeDefinition.getInnerClasses()) {
        writeTypeDefinition(text, inner, false, false);
        text.append('\n');
      }
    }

    classItemGenerator.writePostponed(text, typeDefinition);
  }

  private void writeImplementsList(StringBuilder text, PsiClass typeDefinition, boolean isInterface) {
    final Collection<PsiClassType> implementsTypes = new LinkedHashSet<PsiClassType>();
    Collections.addAll(implementsTypes, typeDefinition.getImplementsListTypes());
  /*for (PsiClass aClass : collectDelegateTypes(typeDefinition)) {
      if (aClass.isInterface()) {
        implementsTypes.add(JavaPsiFacade.getElementFactory(myProject).createType(aClass));
      } else {
        Collections.addAll(implementsTypes, aClass.getImplementsListTypes());
      }
    }*/

    if (implementsTypes.isEmpty()) return;

    text.append(isInterface ? "extends " : "implements ");
    for (PsiClassType implementsType : implementsTypes) {
      writeType(text, implementsType, typeDefinition, classNameProvider);
      text.append(", ");
    }
    if (implementsTypes.size() > 0) text.delete(text.length()-2, text.length());
    //if (implementsTypes.size() > 0) text.removeFromTheEnd(2);
    text.append(' ');
  }

  private void writeExtendsList(StringBuilder text, PsiClass typeDefinition) {
    final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

    if (extendsClassesTypes.length > 0) {

      text.append("extends ");
      writeType(text, extendsClassesTypes[0], typeDefinition, classNameProvider);
      text.append(' ');
    }
  }


  private void writeAllMethods(StringBuilder text, Collection<PsiMethod> methods, PsiClass aClass) {
    for (PsiMethod method : methods) {
      if (!shouldBeGenerated(method)) continue;

      if (method.isConstructor()) {
        classItemGenerator.writeConstructor(text, method, aClass.isEnum());
      }
      else {
        classItemGenerator.writeMethod(text, method);
        text.append('\n');
      }
    }
  }


  private static boolean shouldBeGenerated(PsiMethod method) {
    for (PsiMethod psiMethod : method.findSuperMethods()) {
      if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final PsiType type = method.getReturnType();
        final PsiType superType = psiMethod.getReturnType();
        if (type != null && superType != null && !superType.isAssignableFrom(type)) {
          return false;
        }
      }
    }
    return true;
  }


  private static int getOptionalParameterCount(GrMethod method) {
    final GrParameter[] parameters = method.getParameterList().getParameters();
    int count = 0;
    for (GrParameter parameter : parameters) {
      if (parameter.isOptional()) count++;
    }
    return count;
  }
}
