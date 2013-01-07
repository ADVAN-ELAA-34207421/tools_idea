package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;

public class StringBufferReplaceableWithStringFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringBufferReplaceableByStringInspection());
    myRelativePath = "style/replace_with_string";
    myDefaultHint = InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.quickfix");
  }

  public void testSimpleStringBuffer() { doTest(); }
  public void testStringBuilderAppend() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testStringBufferVariable() { doTest(); }
  public void testStringBufferVariable2() { doTest(); }
  public void testStartsWithPrimitive() { doTest(); }
  public void testPrecedence() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testPrecedence2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testPrecedence3() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testNonString1() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
  public void testNonString2() { doTest(InspectionGadgetsBundle.message("string.builder.replaceable.by.string.quickfix")); }
}
