/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class PsiFieldStubImpl extends StubBase<PsiField> implements PsiFieldStub {
  private final StringRef myName;
  private final TypeInfo myType;
  private final StringRef myInitializer;
  private final byte myFlags;

  private static final int ENUM_CONST = 0x01;
  private static final int DEPRECATED = 0x02;
  private static final int DEPRECATED_ANNOTATION = 0x04;

  public PsiFieldStubImpl(final StubElement parent, final String name, @NotNull TypeInfo type, @Nullable String initializer, final byte flags) {
    this(parent, StringRef.fromString(name), type, StringRef.fromString(initializer), flags);
  }

  public PsiFieldStubImpl(final StubElement parent, final StringRef name, @NotNull TypeInfo type, final StringRef initializer, final byte flags) {
    super(parent, isEnumConst(flags) ? JavaStubElementTypes.ENUM_CONSTANT : JavaStubElementTypes.FIELD);

    myName = name;
    myType = type;
    myInitializer = initializer;
    myFlags = flags;
  }

  @Override
  @NotNull
  public TypeInfo getType(boolean doResolve) {
    if (!doResolve) return myType;

    return addApplicableTypeAnnotationsFromChildModifierList(this, myType);
  }

  public static TypeInfo addApplicableTypeAnnotationsFromChildModifierList(StubBase<?> aThis, TypeInfo type) {
    PsiModifierListStub modifierList = (PsiModifierListStub)aThis.findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    if (modifierList == null) return type;
    TypeInfo typeInfo = new TypeInfo(type);
    for (StubElement child: modifierList.getChildrenStubs()){
      if (!(child instanceof PsiAnnotationStub)) continue;
      PsiAnnotationStub annotationStub = (PsiAnnotationStub)child;
      PsiAnnotationImpl annotation = (PsiAnnotationImpl)annotationStub.getPsiElement();
      if (PsiAnnotationImpl.isAnnotationApplicableTo(annotation, true, "TYPE_USE")) {
        typeInfo.addAnnotation(annotationStub);
      }
    }
    return typeInfo;
  }

  @Override
  public String getInitializerText() {
    return StringRef.toString(myInitializer);
  }

  public byte getFlags() {
    return myFlags;
  }

  @Override
  public boolean isEnumConstant() {
    return isEnumConst(myFlags);
  }

  private static boolean isEnumConst(final byte flags) {
    return (flags & ENUM_CONST) != 0;
  }

  @Override
  public boolean isDeprecated() {
    return (myFlags & DEPRECATED) != 0;
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return (myFlags & DEPRECATED_ANNOTATION) != 0;
  }

  @Override
  public String getName() {
    return StringRef.toString(myName);
  }

  public static byte packFlags(boolean isEnumConst, boolean isDeprecated, boolean hasDeprecatedAnnotation) {
    byte flags = 0;
    if (isEnumConst) flags |= ENUM_CONST;
    if (isDeprecated) flags |= DEPRECATED;
    if (hasDeprecatedAnnotation) flags |= DEPRECATED_ANNOTATION;
    return flags;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiFieldStub[");

    if (isDeprecated() || hasDeprecatedAnnotation()) {
      builder.append("deprecated ");
    }

    if (isEnumConstant()) {
      builder.append("enumconst ");
    }

    TypeInfo type = getType(false); // this can be called from low-level code and we don't want resolve to mess with indexing
    builder.append(getName()).append(':').append(TypeInfo.createTypeText(type));

    if (myInitializer != null) {
      builder.append('=').append(myInitializer);
    }

    builder.append("]");
    return builder.toString();
  }
}
