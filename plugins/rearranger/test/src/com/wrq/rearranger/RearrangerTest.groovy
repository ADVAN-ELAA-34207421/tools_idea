/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger;


import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.wrq.rearranger.settings.CommentRule
import com.wrq.rearranger.settings.RearrangerSettings
import com.wrq.rearranger.settings.RelatedMethodsSettings
import com.wrq.rearranger.settings.attributeGroups.GetterSetterDefinition
import com.wrq.rearranger.settings.attributeGroups.InterfaceAttributes
import com.wrq.rearranger.settings.attributeGroups.RegexUtil
import com.wrq.rearranger.util.CommentRuleBuilder
import com.wrq.rearranger.util.SettingsConfigurationBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import com.wrq.rearranger.util.java.*

/** JUnit tests for the rearranger plugin. */
class RearrangerTest extends LightCodeInsightFixtureTestCase {

  private RearrangerSettings           mySettings
  private SettingsConfigurationBuilder settings
  private JavaClassRuleBuilder         classRule
  private JavaInterfaceRuleBuilder     interfaceRule
  private JavaInnerClassRuleBuilder    innerClassRule
  private JavaFieldRuleBuilder         fieldRule
  private JavaMethodRuleBuilder        methodRule
  private CommentRuleBuilder           commentRule
  private JavaSpacingRule              spacingRule

  @Override
  protected String getBasePath() {
    return "/plugins/rearranger/test/testData/com/wrq/rearranger";
  }

  protected final void setUp() throws Exception {
    super.setUp();
    
    mySettings = new RearrangerSettings();
//        rs.setAskBeforeRearranging(true); // uncomment for debugging file structure popup
    mySettings.showFields = true
    mySettings.showParameterNames = true
    mySettings.showParameterTypes = true
    mySettings.showRules = true
    mySettings.rearrangeInnerClasses = true

    prepareBuilders(mySettings)
  }

  private void prepareBuilders(RearrangerSettings mySettings) {
    settings = new SettingsConfigurationBuilder(settings: mySettings)
    classRule = new JavaClassRuleBuilder(settings: mySettings)
    interfaceRule = new JavaInterfaceRuleBuilder(settings: mySettings)
    innerClassRule = new JavaInnerClassRuleBuilder(settings: mySettings)
    fieldRule = new JavaFieldRuleBuilder(settings: mySettings)
    methodRule = new JavaMethodRuleBuilder(settings: mySettings)
    commentRule = new CommentRuleBuilder(settings: mySettings)
    spacingRule = new JavaSpacingRule(settings: mySettings)
  }

  public final void testNoRearrangement() throws Exception {
    doTest('RearrangementTest', 'NoRearrangementResult1')
  }

  public final void testPublicFieldRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult2') {
      fieldRule.modifier PsiModifier.PUBLIC
  } }

  public final void testNotPublicFieldRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult3') {
      fieldRule.modifier PsiModifier.PUBLIC, invert: true 
  } }

  public final void testConstructorRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult4') {
      methodRule.create {
        modifier([ PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL ])
        target( MethodType.CONSTRUCTOR )
  } } }

  public final void testClassRearrangement() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult5') {
      classRule.create {
        modifier( PsiModifier.PACKAGE_LOCAL )
  } } }

  public final void testPSFRearrangement() throws Exception {
    doTest('RearrangementTest2', 'RearrangementResult6') {
      fieldRule.create {
        modifier([ PsiModifier.FINAL, PsiModifier.STATIC ])
  } } }

  public final void testAnonClassInit() throws Exception {
    doTest('RearrangementTest7', 'RearrangementResult7') {
      fieldRule.create {
        initializer( InitializerType.ANONYMOUS_CLASS )
  } } }

  public final void testNameMatch() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult8') {
      fieldRule.create { name('.*5') }
      methodRule.create { name('.*2') }
  } }

  public final void testStaticInitializer() throws Exception {
    doTest('RearrangementTest8', 'RearrangementResult8A') {
      methodRule.create { modifier( PsiModifier.STATIC ) }
  } }

  public final void testAlphabetizingGSMethods() throws Exception {
    mySettings.keepGettersSettersTogether = false
    doTest('RearrangementTest', 'RearrangementResult9') {
      methodRule.create {
        target([ MethodType.GETTER_OR_SETTER, MethodType.OTHER ])
        'sort by'( SortOption.NAME )
  } } }

  public final void testSimpleComment() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult10') {
      fieldRule.create { modifier( PsiModifier.PUBLIC) }
      commentRule.create {
        comment( '// simple comment **********', condition: CommentRule.EMIT_IF_ITEMS_MATCH_PRECEDING_RULE )
  } } }

  /**
   * Delete old comment and insert (identical) new one.  This tests proper identification and deletion of old
   * comments.
   *
   * @throws Exception test exception
   */
  public final void testReplayComment() throws Exception {
    doTest('RearrangementResult10', 'RearrangementResult10') {
      fieldRule.create { modifier( PsiModifier.PUBLIC) }
      commentRule.create {
        comment( '// simple comment **********', condition: CommentRule.EMIT_IF_ITEMS_MATCH_PRECEDING_RULE )
  } } }

  public final void testMultipleRuleCommentMatch() throws Exception {
    doTest('RearrangementTest11', 'RearrangementResult11') {
      commentRule.create { comment('// FIELDS:', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false) }
      commentRule.create { comment('// FINAL FIELDS:', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false,
                                   'subsequent rules to match': 1) }
      fieldRule.create { modifier(PsiModifier.FINAL) }
      commentRule.create { comment('// NON-FINAL FIELDS:', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true,
                                   'subsequent rules to match': 1) }
      fieldRule.create { modifier(PsiModifier.FINAL, invert: true) }
  } }

  public final void testOpsBlockingQueueExample() throws Exception {
    testOpsBlockingQueueExampleWorker(false, "OpsBlockingQueue", false, "OpsBlockingQueue");
  }

  public final void testOpsBlockingQueueExampleWithGlobalPattern() throws Exception {
    testOpsBlockingQueueExampleWorker(true, "OpsBlockingQueue", false, "OpsBlockingQueue");
  }

  public final void testOpsBlockingQueueExampleWithIndentedComments() throws Exception {
    testOpsBlockingQueueExampleWorker(false, "OpsBlockingQueueIndented", true, "OpsBlockingQueueIndentedResult");
  }

  private void testOpsBlockingQueueExampleWorker(boolean doGlobalPattern,
                                                 String srcFilename,
                                                 boolean doublePublicMethods,
                                                 String compareFilename)
    throws Exception
  {
    // submitted by Joe Martinez.
    doTest(srcFilename, compareFilename) {
      commentRule.create {
        comment('//**************************************        PUBLIC STATIC FIELDS         *************************************',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false, 'all preceding': true,
                'subsequent rules to match': 2, 'preceding rules to match': 1)
      }
      fieldRule.create {
        modifier([ PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL ])
        'sort by'( SortOption.NAME )
      }
      fieldRule.create {
        modifier([ PsiModifier.PUBLIC, PsiModifier.STATIC ])
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//**************************************        PUBLIC FIELDS          *****************************************',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true, 'all preceding': true,
                'subsequent rules to match': 1, 'preceding rules to match': 1)
      }
      fieldRule.create {
        modifier( PsiModifier.PUBLIC )
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//***********************************       PROTECTED/PACKAGE FIELDS        **************************************',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false, 'all preceding': true,
                'subsequent rules to match': 3, 'preceding rules to match': 1)
      }
      fieldRule.create {
        modifier([ PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.STATIC, PsiModifier.FINAL ])
        'sort by'( SortOption.NAME )
      }
      fieldRule.create {
        modifier([ PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.STATIC ])
        'sort by'( SortOption.NAME )
      }
      fieldRule.create {
        modifier([ PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL ])
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//**************************************        PRIVATE FIELDS          *****************************************',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false, 'all preceding': true,
                'subsequent rules to match': 1, 'preceding rules to match': 1)
      }
      fieldRule.create {
        modifier( PsiModifier.PRIVATE )
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//**************************************        CONSTRUCTORS              ************************************* ',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false, 'all preceding': true,
                'subsequent rules to match': 2, 'preceding rules to match': 1)
      }
      methodRule.create {
        modifier( PsiModifier.PUBLIC )
        target( MethodType.CONSTRUCTOR )
      }
      methodRule.create { target( MethodType.CONSTRUCTOR ) }
      commentRule.create {
        comment('//***********************************        GETTERS AND SETTERS              ********************************** ',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': false, 'all preceding': true,
                'subsequent rules to match': 2, 'preceding rules to match': 1)
      }
      methodRule.create {
        modifier( PsiModifier.PUBLIC )
        target( MethodType.GETTER_OR_SETTER )
        'sort by'( SortOption.NAME )
      }
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'sort by'( SortOption.NAME )
      }
      def text = '//**************************************        PUBLIC METHODS              ************************************* '
      if (doublePublicMethods) {
        text += "\n// PUBLIC METHODS LINE 2";
      }
      commentRule.create {
        comment(text, condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true, 'all preceding': true,
                'subsequent rules to match': 1, 'preceding rules to match': 1)
      }
      methodRule.create {
        modifier( PsiModifier.PUBLIC )
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//*********************************     PACKAGE/PROTECTED METHODS              ******************************** ',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true, 'all preceding': true,
                'subsequent rules to match': 1, 'preceding rules to match': 1)
      }
      methodRule.create {
        modifier([ PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL ])
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//**************************************        PRIVATE METHODS              *************************************',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true, 'all preceding': true,
                'subsequent rules to match': 1, 'preceding rules to match': 1)
      }
      methodRule.create {
        modifier( PsiModifier.PRIVATE )
        'sort by'( SortOption.NAME )
      }
      commentRule.create {
        comment('//**************************************        INNER CLASSES              ************************************* ',
                condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true, 'all preceding': true,
                'subsequent rules to match': 1, 'preceding rules to match': 1)
      }
      innerClassRule.create { 'sort by'(SortOption.NAME ) }
      
      mySettings.extractedMethodsSettings.moveExtractedMethods = false
      if (doGlobalPattern) {
        mySettings.globalCommentPattern = "//\\*{20,45}[A-Z /]*\\*{20,45}\n"
      }
    }
  }

  public final void testReturnTypeMatch() throws Exception {
    doTest('RearrangementTest12', 'RearrangementResult12') {
      methodRule.create { 'return type'( 'void' ) }
      fieldRule.create  { type( 'int' ) }
      methodRule.create { 'return type'( '.*je.*' ) }
      methodRule.create { 'return type'( /Integer\[\]/) }
      methodRule.create { 'return type'( 'int' ) }
  } }

  public final void testRelatedMethodsDepthOriginal() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13DO') {
      settings.'extracted methods'( 'depth-first order': true, order: RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER )
  } }

  public final void testRelatedMethodsDepthAlphabetical() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13DA') {
      settings.'extracted methods'( 'depth-first order': true, order: RelatedMethodsSettings.ALPHABETICAL_ORDER )
  } }

  public final void testRelatedMethodsDepthInvocation() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13DI') {
      settings.'extracted methods'( 'depth-first order': true, order: RelatedMethodsSettings.INVOCATION_ORDER )
  } }

  public final void testRelatedMethodsBreadthOriginal() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13BO') {
      settings.'extracted methods'( 'depth-first order': false, order: RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER )
  } }

  public final void testRelatedMethodsBreadthAlphabetical() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13BA') {
      settings.'extracted methods'( 'depth-first order': false, order: RelatedMethodsSettings.ALPHABETICAL_ORDER)
  } }

  public final void testRelatedMethodsBreadthInvocation() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13BI') {
      settings.'extracted methods'( 'depth-first order': false, order: RelatedMethodsSettings.INVOCATION_ORDER)
  } }

  private void doTestEmitComments(args) {
    doTest(args.initial?: 'RearrangementTest13', args.expected) {
      settings.'extracted methods'( 'depth-first order': args.depthFirst, order: args.orderType, commentType: args.commentType )

      def precedingCommentRule = new CommentRule()
      precedingCommentRule.commentText = '''\
// Preceding comment: TL=%TL%
// MN=%MN%
// AM=%AM%
// Level %LV%'''
      mySettings.extractedMethodsSettings.precedingComment = precedingCommentRule

      def trailingCommentRule = new CommentRule()
      trailingCommentRule.commentText = '''\
// Trailing comment: TL=%TL%
// MN=%MN%
// AM=%AM%
// Level %LV%'''
      mySettings.extractedMethodsSettings.trailingComment = trailingCommentRule
  } }
  
  public final void testEmitTLCommentsRelatedMethodsBreadthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13BITLC',
      depthFirst: false,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_TOP_LEVEL
    )
  }

  public final void testEmitEMCommentsRelatedMethodsBreadthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13BIEMC',
      depthFirst: false,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_EACH_METHOD
    )
  }

  public final void testEmitELCommentsRelatedMethodsBreadthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13BIELC',
      depthFirst: false,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL
    )
  }

  public final void testEmitNFCommentsRelatedMethodsBreadthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13BINFC',
      depthFirst: false,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_NEW_FAMILY
    )
  }

  public final void testEmitTLCommentsRelatedMethodsDepthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13DITLC',
      depthFirst: true,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_TOP_LEVEL
    )
  }

  public final void testEmitEMCommentsRelatedMethodsDepthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13DIEMC',
      depthFirst: true,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_EACH_METHOD
    )
  }

  public final void testEmitELCommentsRelatedMethodsDepthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13BIELC',
      depthFirst: false,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL
    )
  }

  public final void testEmitNFCommentsRelatedMethodsDepthInvocation() throws Exception {
    doTestEmitComments(
      expected: 'RearrangementResult13DINFC',
      depthFirst: true,
      orderType: RelatedMethodsSettings.INVOCATION_ORDER,
      commentType: RelatedMethodsSettings.COMMENT_TYPE_NEW_FAMILY
    )
  }
  
  public final void testRelatedMethodsException() throws Exception {
    doTest('RearrangementTest13', 'RearrangementResult13Ex') {
      settings.'extracted methods'( 'depth-first order': true, order: RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER )
      methodRule.create { name('GF') }
  } }

  public final void testKeepOverloadedMethodsTogether() throws Exception {
    doTest('RearrangementTest14', 'RearrangementResult14') {
      settings.configure {
        'extracted methods'( 'depth-first order': false, order: RelatedMethodsSettings.INVOCATION_ORDER )
        'keep together'( 'overloaded' )
  } } }

  public final void testXML() throws Exception { doTest('RearrangementTest17', 'RearrangementTest17', 'xml') }

  public final void testKeepGSTogether() throws Exception {
    doTest('RearrangementTest18', 'RearrangementResult18') {
      settings.configure {
        'extracted methods'( order: RelatedMethodsSettings.INVOCATION_ORDER )
        'keep together'( 'getters and setters' )
      }
      fieldRule.create {}
      methodRule.create { target( MethodType.CONSTRUCTOR ) }
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'sort by'( SortOption.NAME )
  } } }

  public final void testKeepGSWithProperty() throws Exception {
    doTest('RearrangementTest18', 'RearrangementResult18A') {
      settings.configure {
        'extracted methods'( order: RelatedMethodsSettings.ALPHABETICAL_ORDER )
        'keep together'([ 'getters and setters', 'getters and setters with property' ])
      }
      fieldRule.create { }
      methodRule.create { target(MethodType.CONSTRUCTOR) }
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'sort by'( SortOption.NAME )
  } } }

  public final void testKeepGSWithPropertyElseTogether() throws Exception {
    doTest('RearrangementTest18B', 'RearrangementResult18B') {
      settings.configure {
        'extracted methods'( order: RelatedMethodsSettings.ALPHABETICAL_ORDER )
        'keep together'([ 'getters and setters', 'getters and setters with property' ])
      }
      fieldRule.create { }
      commentRule.create {
        comment('// Getters/Setters', condition: CommentRule.EMIT_ALWAYS)
      }
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
                name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
                body: GetterSetterDefinition.GETTER_BODY_IMMATERIAL
        )
        'setter criteria'(
                name: GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX,
                body: GetterSetterDefinition.SETTER_BODY_IMMATERIAL
        )
        'sort by'( SortOption.NAME )
      }
      commentRule.create { comment('// Other Methods', condition: CommentRule.EMIT_ALWAYS) }
      methodRule.create { 'sort by'( SortOption.NAME ) }
  } }

  public final void testKeepOverloadsTogetherOriginalOrder() throws Exception {
    doTest('RearrangementTest19', 'RearrangementResult19A') {
      settings.'overloaded methods'( 'keep together': true, order: RearrangerSettings.OVERLOADED_ORDER_RETAIN_ORIGINAL )
  } }

  public final void testKeepOverloadsTogetherAscendingOrder() throws Exception {
    doTest('RearrangementTest19', 'RearrangementResult19B') {
      settings.'overloaded methods'( 'keep together': true, order: RearrangerSettings.OVERLOADED_ORDER_ASCENDING_PARAMETERS )
  } }

  public final void testKeepOverloadsTogetherDescendingOrder() throws Exception {
    doTest('RearrangementTest19', 'RearrangementResult19C') {
      settings.'overloaded methods'( 'keep together': true, order: RearrangerSettings.OVERLOADED_ORDER_DESCENDING_PARAMETERS )
  } }

  public final void testInnerClassReferenceToChild() throws Exception {
    doTest('RearrangementTest20', 'RearrangementResult20') {
      mySettings.extractedMethodsSettings.moveExtractedMethods = true
  } }

  public final void testMultipleFieldDecl() throws Exception {
    doTest('RearrangementTest21', 'RearrangementResult21') {
      fieldRule.create { 'sort by'( SortOption.NAME ) }
  } }

  public final void testRemoveBlankLines() throws Exception {
    doTest('SpaceTest1', 'SpaceResult1') {
      spacingRule.create {
        spacing(anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE, SpacingAnchor.BEFORE_METHOD_RBRACE,
                          SpacingAnchor.BEFORE_CLASS_RBRACE, SpacingAnchor.AFTER_CLASS_RBRACE ],
                lines: 0)
      }
      spacingRule.create {
        spacing( anchor: SpacingAnchor.EOF, lines:  1 )
      }
      mySettings.removeBlanksInsideCodeBlocks = true
  } }

  public final void testAddBlankLines() throws Exception {
    doTest('SpaceTest2', 'SpaceResult2') {
      spacingRule.create {
        spacing( anchor: [ SpacingAnchor.AFTER_METHOD_RBRACE, SpacingAnchor.EOF ], lines: 1)
        spacing(anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE ], lines: 2)
        spacing( anchor: SpacingAnchor.AFTER_CLASS_RBRACE, lines: 4)
      }
      mySettings.removeBlanksInsideCodeBlocks = true
  } }

  public final void testInnerClassBlankLines() throws Exception {
    doTest('SpaceTest4', 'SpaceResult4') {
      spacingRule.create {
        spacing(anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE, SpacingAnchor.BEFORE_METHOD_RBRACE ],
                lines: 0)
        spacing(anchor: [ SpacingAnchor.AFTER_METHOD_RBRACE, SpacingAnchor.EOF ],
                lines: 1)
      }
      mySettings.removeBlanksInsideCodeBlocks = true
  } }

  public void testInnerClassSpacing() throws Exception {
    doTest('SpaceTest5', 'SpaceResult5') {
      spacingRule.create {
        spacing( anchor: SpacingAnchor.AFTER_CLASS_RBRACE, lines: 2 )
        spacing( anchor: SpacingAnchor.EOF, lines: 3 )
  } } }

  public void testSpacingWithTrailingWhitespace() throws Exception {
    doTest('SpaceTest6', 'SpaceResult6') {
      spacingRule.create { spacing( anchor: SpacingAnchor.EOF, lines: 1 ) }
  } }

  /**
   * Submitted by Brian Buckley.
   *
   * @throws Exception test exception
   */
  public void testSpacingConflictingSettingBug() throws Exception {
    doTest('SpaceTest8', 'SpaceResult8') {
      spacingRule.create { spacing( anchor: SpacingAnchor.AFTER_CLASS_LBRACE, lines: 0) }
      spacingRule.create {
        spacing( anchor: [ SpacingAnchor.BEFORE_METHOD_LBRACE, SpacingAnchor.EOF ], lines: 1)
  } } }

  public void testGetPrefixImmaterial() throws Exception {
    doTest('GetterDefinitionTest', 'GetPrefixImmaterialResult') {
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
          name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.GETTER_BODY_IMMATERIAL
  ) } } }

  public void testGetPrefixReturns() throws Exception {
    doTest('GetterDefinitionTest', 'GetPrefixReturnsResult') {
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
                name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
                body: GetterSetterDefinition.GETTER_BODY_RETURNS
  ) } } }

  public void testGetPrefixReturnsField() throws Exception {
    doTest('GetterDefinitionTest', 'GetPrefixReturnsFieldResult') {
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
                name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
                body: GetterSetterDefinition.GETTER_BODY_RETURNS_FIELD
    ) } } }

  public void testGetFieldReturns() throws Exception {
    doTest('GetterDefinitionTest', 'GetFieldReturnsResult') {
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
          name: GetterSetterDefinition.GETTER_NAME_MATCHES_FIELD,
          body: GetterSetterDefinition.GETTER_BODY_RETURNS
  ) } } }

  public void testGetFieldReturnsField() throws Exception {
    doTest('GetterDefinitionTest', 'GetFieldReturnsFieldResult') {
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
          name: GetterSetterDefinition.GETTER_NAME_MATCHES_FIELD,
          body: GetterSetterDefinition.GETTER_BODY_RETURNS_FIELD
  ) } } }

  public void testSpecialGS() throws Exception {
    doTest('RearrangementTest22', 'RearrangementResult22') {
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
        'getter criteria'(
          name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.GETTER_BODY_RETURNS
        )
        'setter criteria'(
          name: GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.SETTER_BODY_IMMATERIAL
      ) }
      mySettings.keepGettersSettersTogether = true
  } }

  public void testInterfaceNoNameNotAlphabeticalNoExcludeMethodAlphabetical() throws Exception {
    doTest('RearrangementTest23', 'RearrangementResult23NNNANXMA') {
      mySettings.keepGettersSettersTogether = false
      interfaceRule.create {
        'preceding comment'( '/**** Interface %IF% Header ****/' )
        'trailing comment'( '/**** Interface %IF% Trailer ***/' )
        setup( "don't group extracted methods": false, order: InterfaceAttributes.METHOD_ORDER_ALPHABETICAL, alphabetize: false )
  } } }

  public void testInterfaceNoNameNotAlphabeticalNoExcludeMethodEncountered() throws Exception {
    doTest('RearrangementTest23', 'RearrangementResult23NNNANXME') {
      mySettings.keepGettersSettersTogether = false
      interfaceRule.create {
        'preceding comment'( '/**** Interface %IF% Header ****/' )
        'trailing comment'( '/**** Interface %IF% Trailer ***/' )
        setup( "don't group extracted methods": false, order: InterfaceAttributes.METHOD_ORDER_ENCOUNTERED, alphabetize: false )
  } } }

  public void testInterfaceNoNameNotAlphabeticalNoExcludeMethodInterfaceOrder() throws Exception {
    doTest('RearrangementTest23', 'RearrangementResult23NNNANXMI') {
      mySettings.keepGettersSettersTogether = false
      interfaceRule.create {
        'preceding comment'( '/**** Interface %IF% Header ****/' )
        'trailing comment'( '/**** Interface %IF% Trailer ***/' )
        setup( "don't group extracted methods": false, order: InterfaceAttributes.METHOD_ORDER_INTERFACE_ORDER, alphabetize: false )
  } } }

  public void testInterfaceByNameNotAlphabeticalNoExcludeMethodEncountered() throws Exception {
    doTest('RearrangementTest23', 'RearrangementResult23BNNANXME') {
      mySettings.keepGettersSettersTogether = false
      interfaceRule.setup( "don't group extracted methods": true, order: InterfaceAttributes.METHOD_ORDER_ENCOUNTERED,
                           alphabetize: false, name : 'IFace1'
  ) } }

  public void testInterfaceIsAlphabeticalNoExcludeMethodEncountered() throws Exception {
    doTest('RearrangementTest23', 'RearrangementResult23NNIANXME') {
      interfaceRule.create {
        'preceding comment'( '/**** Interface %IF% Header ****/' )
        'trailing comment'( '/**** Interface %IF% Trailer ***/' )
        setup( "don't group extracted methods": true, order: InterfaceAttributes.METHOD_ORDER_ENCOUNTERED, alphabetize: true )
  } } }

  public void testSpacingOptions() throws Exception {
    /*
     * From Thomas Singer:
     * I've enabled
     * - Force 0 blank lines before class close brace "}"
     * - Force 0 blank lines before method close brace "}"
     * - Remove initial and final blank lines inside code block
     * but in the code below the blank lines don't get removed when invoking
     * Rearranger from editor's context menu:
     */
    doTest('RearrangementTest25', 'RearrangementResult25') {
      spacingRule.spacing(anchor: [ SpacingAnchor.BEFORE_CLASS_RBRACE, SpacingAnchor.BEFORE_METHOD_RBRACE ],
                          lines: 0, 'remove blank lines': true
  ) } }

  public void testPriority() throws Exception {
    doTest('RearrangementTest', 'RearrangementResult1A') {
      methodRule.create { priority( 1 ) }
      methodRule.create {
        priority( 2 )
        name( 'method.*' )
      }
      methodRule.create {
        priority( 2 )
        name( '.*Method' )
  } } }

  public void testGSRuleWithClassInitializer() throws Exception {
    doTest('RearrangementTest26', 'RearrangementResult26') {
      mySettings.keepOverloadedMethodsTogether = true
      methodRule.create {
        target( MethodType.GETTER_OR_SETTER )
  } } }

  public void testKeepGSTogetherAndExtractedMethods() throws Exception {
    doTest('RearrangementTest27', 'RearrangementResult27') {
      settings.configure {
        'keep together'([ 'getters and setters', 'overloaded' ])
        'extracted methods'  move: true 
  } } }

  public void testRegexEscape() throws Exception {
    String s = "// ********* start of fields *********";
    String result = RegexUtil.escape(s);
    assertEquals("sequence reduction failed", "// \\*{9} start of fields \\*{9}", result);
    s = "// \\ backslash \n \t \\d [...] (^...\$)";
    result = RegexUtil.escape(s);
    assertEquals("special character escape failed", "// \\\\ backslash \\n \\t \\\\d \\[\\.\\.\\.\\]" +
                                                    " \\(\\^\\.\\.\\.\\\$\\)", result);
  }

  public void testRegexCombine() throws Exception {
    String p1 = RegexUtil.escape("// ********* start of fields *********");
    String p2 = RegexUtil.escape("// ********* start of methods *********");
    List<String> list = new ArrayList<String>();
    list.add(p1);
    list.add(p2);
    String result = RegexUtil.combineExpressions(list);
    assertEquals("combination failed", "// \\*{9} start of (fiel|metho)ds \\*{9}", result);
    String p3 = RegexUtil.escape("// ***** start of interfaces *******");
    list.add(p3);
    result = RegexUtil.combineExpressions(list);
    assertEquals("combination failed", "// (\\*{9} start of (fiel|metho)ds \\*{9}|" +
                                       "\\*{5} start of interfaces \\*{7})", result);
  }

  public void testVariousComments() throws Exception {
    doTest('RearrangementTest28', 'RearrangementResult28') {
      settings.configure{
        'keep together'([ 'getters and setters', 'overloaded' ])
        'extracted methods'( 'depth-first order': true, commentType: RelatedMethodsSettings.COMMENT_TYPE_EACH_LEVEL,
                             'below first caller': false,
                             'non-private treatment': RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ANY_CALLERS,
                             'preceding comment': '// Level %LV% methods', 'trailing comment': '// end Level %LV% methods' )
      }
      commentRule.create {
        comment('// start of fields', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'all subsequent': true,
                                   'subsequent rules to match': 1)
      }
      fieldRule.create { }
      commentRule.create {
        comment('// end of fields', condition: CommentRule.EMIT_IF_ITEMS_MATCH_PRECEDING_RULE, 'all preceding': true,
                'preceding rules to match': 1)
      }
      interfaceRule.configure {
        'preceding comment'( '// start of interface %IF%' )
        'trailing comment'( '// end of interface %IF%' )
        setup( methodOrder: InterfaceAttributes.METHOD_ORDER_ENCOUNTERED, alphabetize: false, 'group extracted methods': false )
    } }
    // where a blank line, generated comment, and method occur in order; the generated comment is removed
    // and the blank line precedes the method; when the new comment is generated, it is inserted before
    // the blank line.
  }

  public void testParseBugInfiniteLoop() throws Exception { doTest('RearrangementTest29', 'RearrangementTest29') }

  public void testSpacingBug() throws Exception {
    doTest('RearrangementTest30', 'RearrangementResult30') {
      spacingRule.create {
        spacing( anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE, SpacingAnchor.BEFORE_CLASS_RBRACE ],
                 lines: 0 )
        spacing( anchor: [ SpacingAnchor.AFTER_CLASS_RBRACE, SpacingAnchor.AFTER_METHOD_RBRACE, SpacingAnchor.EOF ],
                 lines: 1 )
        spacing ( 'remove blank lines': true )
  } } }

  public void testSpacingBug2() throws Exception {
    doTest('RearrangementTest31', 'RearrangementResult31') {
      spacingRule.create {
        spacing( anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE, SpacingAnchor.BEFORE_CLASS_RBRACE,
                           SpacingAnchor.BEFORE_METHOD_RBRACE ],
                 lines: 0 )
        spacing( anchor: [ SpacingAnchor.AFTER_CLASS_RBRACE, SpacingAnchor.AFTER_METHOD_RBRACE, SpacingAnchor.EOF ],
                 lines: 1 )
        spacing ( 'remove blank lines': true )
  } } }

  public void testSpacingBug3() throws Exception {
    doTest('DomainExpanderTest', 'DomainExpanderResult') {
      spacingRule.create {
        spacing( anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE,
                           SpacingAnchor.BEFORE_CLASS_RBRACE, SpacingAnchor.BEFORE_METHOD_RBRACE],
                 lines: 0 )
        spacing( anchor: [ SpacingAnchor.AFTER_CLASS_RBRACE, SpacingAnchor.EOF, SpacingAnchor.AFTER_METHOD_RBRACE ],
                 lines: 1 )
        spacing ( 'remove blank lines': true )
  } } }
  
  private void setupSettings(@NotNull String relativePath) {
    // Using concat() because simple '+' here produces weird groovy.lang.MissingMethodException: No signature of method:
    // java.lang.String.positive() is applicable for argument types: () values: []
    def path = PlatformTestUtil.getCommunityPath().replace(File.separator, '/').concat("/plugins/rearranger")
      .concat(relativePath)
    mySettings = RearrangerSettings.getSettingsFromFile(new File(path));
    mySettings.askBeforeRearranging = false
    
    prepareBuilders(mySettings)
  }
  
  /**
   * Bug occurs when one or more blank lines precede a generated comment.
   * When comment is removed, blank lines now precede the item.  Comment is inserted
   * at the beginning (i.e. before the blank lines) and a newline character is
   * prefixed to the comment.  Net effect is that new blank line(s) appear after the comment.
   *
   * @throws Exception test exception
   */
  public void testGeneratedCommentSpacingBug() throws Exception {
    doTest('RearrangementTest32', 'RearrangementResult32') {
      setupSettings(InteractiveTest.DEFAULT_CONFIGURATION)
      spacingRule.spacing(anchor: SpacingAnchor.EOF, lines: 1)
  } }

  public void testGeneratedCommentSpacing() throws Exception {
    doTest('RearrangementTest32', 'RearrangementResult32') {
      setupSettings(InteractiveTest.DEFAULT_CONFIGURATION)
      spacingRule.spacing(anchor: SpacingAnchor.EOF, lines: 1)
  } }

  public void testInnerClassComments() throws Exception {
    doTest('RearrangementTest34', 'RearrangementResult34') {
      setupSettings(InteractiveTest.DEFAULT_CONFIGURATION)
      spacingRule.spacing(anchor: SpacingAnchor.EOF, lines: 1)
      settings.configure( 'rearrange inner classes': true, 'class comment': '// ----- OUTER CLASS -----\n' )
  } }

  public void testInnerClassCommentsNoRearrangement() throws Exception {
    doTest('RearrangementTest34', 'RearrangementResult34B') {
      setupSettings(InteractiveTest.DEFAULT_CONFIGURATION)
      spacingRule.spacing(anchor: SpacingAnchor.EOF, lines: 1)
      settings.configure( 'rearrange inner classes': false, 'class comment': '// ----- OUTER CLASS -----\n' )
  } }

  public void testFirstInsertionOfComment() throws Exception {
    doTest('RearrangementTest35', 'RearrangementResult35') {
      commentRule.comment('// ----- FIELDS -----\n')
      fieldRule.create { }
  } }

  public void testExcludeFromExtraction() throws Exception { doTest('RearrangementTest36', 'RearrangementTest36') }

  public void testInterferingGSNames() throws Exception {
    doTest('RearrangementTest37', 'RearrangementResult37') {
      settings.configure {
        'keep together'( 'getters and setters' )
        'getter criteria'(
          name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.GETTER_BODY_IMMATERIAL
        )
        'setter criteria'(
          name: GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.SETTER_BODY_IMMATERIAL
        )
    } } }

  public void testInterferingGSNamesNoKGSTogether() throws Exception {
    doTest('RearrangementTest37', 'RearrangementTest37') {
      settings.configure {
        'getter criteria'(
          name: GetterSetterDefinition.GETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.GETTER_BODY_IMMATERIAL
        )
        'setter criteria'(
          name: GetterSetterDefinition.SETTER_NAME_CORRECT_PREFIX,
          body: GetterSetterDefinition.SETTER_BODY_IMMATERIAL
        )
  } } }

  public void testRemoveBlankLineInsideMethodBug() throws Exception {
    doTest('RearrangementTest38', 'RearrangementTest38') {
      setupSettings('/test/testData/com/wrq/rearranger/RearrangementTest38cfg.xml')
  } }

  public void testSortFieldsByTypeAndName() throws Exception {
    doTest('RearrangementTest39', 'RearrangementResult39B') {
      fieldRule.create {
        'sort by'([ SortOption.NAME, SortOption.TYPE ])
  } } }

  public void testSortFieldsByType() throws Exception {
    doTest('RearrangementTest39', 'RearrangementResult39C') {
      fieldRule.create {
        'sort by' SortOption.TYPE
        'not sort by' SortOption.NAME
      }
  } }
  
  public void testSortFieldsByTypeICAndName() throws Exception {
    doTest('RearrangementTest39', 'RearrangementResult39') {
      fieldRule.create {
        'sort by'([ SortOption.NAME, SortOption.TYPE, SortOption.TYPE_CASE_INSENSITIVE ])
  } } }

  public void testSortFieldsByTypeIC() throws Exception {
    doTest('RearrangementTest39', 'RearrangementResult39A') {
      fieldRule.create {
        'sort by'([ SortOption.TYPE, SortOption.TYPE_CASE_INSENSITIVE ])
        'not sort by' SortOption.NAME
  } } }

  /**
   * test detection of method overrides/overridden/implements/implemented attributes.
   *
   * @throws Exception test exception
   */
  public void testOverImpl() throws Exception { doTest('RearrangementTest40', 'RearrangementResult40') }

  public final void testRemoveBlankLinesBeforeMethod() throws Exception {
    doTest('RearrangementTest41', 'RearrangementResult41') {
      spacingRule.create {
        spacing(anchor: [ SpacingAnchor.AFTER_CLASS_LBRACE, SpacingAnchor.BEFORE_METHOD_LBRACE, SpacingAnchor.AFTER_METHOD_LBRACE,
                          SpacingAnchor.BEFORE_METHOD_RBRACE, SpacingAnchor.AFTER_METHOD_RBRACE, SpacingAnchor.BEFORE_CLASS_RBRACE,
                          SpacingAnchor.AFTER_CLASS_RBRACE],
                lines: 0, 'remove blank lines': true)
      }
    }
  }

  public final void testEnumClass() throws Exception { doTest('RearrangementTest42', 'RearrangementResult42') }

  public final void testNumParameters() throws Exception {
    doTest('RearrangementTest43', 'RearrangementResult43') {
      methodRule.create {
        'arguments number' ( from: 2, to: 3)
      }
      methodRule.create {
        'arguments number' ( from: 1)
  } } }

  public final void testGeneratedComment() throws Exception {
    doTest('RearrangementTest44', 'RearrangementResult44') {
      commentRule.comment('// %FS% METHODS %FS%', condition: CommentRule.EMIT_ALWAYS, 'fill string': '-+', 
                          'use project width for fill': false, 'fill width': 30)
      methodRule.create { } // match all methods
  } }

  public void testEnum1() throws Exception {
    doTest('Enum1Test', 'Enum1Result') {
      setupSettings( InteractiveTest.DEFAULT_CONFIGURATION )
      spacingRule.create {
        spacing( anchor: SpacingAnchor.EOF, lines: 1 )
      }
      settings.configure('rearrange inner classes': true )
      settings.'extracted methods'( 'below first caller': true )
  } }

  public void testEnum2() throws Exception {
    doTest('Enum2Test', 'Enum2Result') {
      setupSettings( InteractiveTest.DEFAULT_CONFIGURATION )
      settings.configure('rearrange inner classes': true )
      mySettings.afterClassRBrace.force = false
  } }

  public void testEnum2A() throws Exception {
    doTest('Enum2Test', 'Enum2AResult') {
      setupSettings( InteractiveTest.DEFAULT_CONFIGURATION )
      settings.configure('rearrange inner classes': false )
      mySettings.afterClassRBrace.force = false
  } }

  public void testRearrangementIncompleteLastLine() throws Exception {
    doTest('IncompleteLineTest', 'IncompleteLineResult') {
      setupSettings( InteractiveTest.DEFAULT_CONFIGURATION )
  } }

  public void testOverloadedMethodCategorization() throws Exception {
    doTest('RearrangementTest45', 'RearrangementResult45') {
      setupSettings( InteractiveTest.DEFAULT_CONFIGURATION )
      spacingRule.create {
        spacing( anchor: SpacingAnchor.EOF, lines: 1 )
  } } }

  public void testSuperclassAlgorithm() throws Exception {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(getProject());
    final PsiClass aClass =
      psiFacade.getElementFactory().
        createClassFromText("public String toString() {return null;}", null);
    final PsiMethod method = aClass.getMethods()[0];
    final PsiMethod[] superMethods = method.findSuperMethods();
    assertEquals(1, superMethods.length);
    PsiClass psiClass = superMethods[0].getContainingClass();
    if (psiClass == null) {
      fail("missing containing class");
    }
    else {
      assertEquals("java.lang.Object", psiClass.getQualifiedName());
    }
  }

  public void testSortingOriginalOrder() throws Exception {
    doTest('RearrangementTest46', 'RearrangementResult46A') {
      fieldRule.create {
        modifier( PsiModifier.PROTECTED )
        'not sort by' ([ SortOption.TYPE, SortOption.NAME, SortOption.TYPE_CASE_INSENSITIVE,
                         SortOption.NAME_CASE_INSENSITIVE, SortOption.MODIFIER ])
  } } }

  public void testSortingModifierOrder() throws Exception {
    doTest('RearrangementTest46', 'RearrangementResult46B') {
      fieldRule.create {
        modifier( PsiModifier.PROTECTED )
        'sort by' ( SortOption.MODIFIER )
        'not sort by' ([ SortOption.TYPE, SortOption.NAME, SortOption.TYPE_CASE_INSENSITIVE, SortOption.NAME_CASE_INSENSITIVE ])
  } } }

  public void testSortingTypeOrder() throws Exception {
    doTest('RearrangementTest46', 'RearrangementResult46C') {
      fieldRule.create {
        modifier( PsiModifier.PROTECTED )
        'sort by' ( SortOption.TYPE )
        'not sort by' ([ SortOption.MODIFIER, SortOption.NAME, SortOption.TYPE_CASE_INSENSITIVE, SortOption.NAME_CASE_INSENSITIVE ])
  } } }

  public void testSortingTypeOrderCI() throws Exception {
    doTest('RearrangementTest46', 'RearrangementResult46D') {
      fieldRule.create {
        modifier( PsiModifier.PROTECTED )
        'sort by' ([ SortOption.TYPE, SortOption.TYPE_CASE_INSENSITIVE ])
        'not sort by' ([ SortOption.MODIFIER, SortOption.NAME, SortOption.NAME_CASE_INSENSITIVE ])
  } } }

  public void testSortingMTNOrderCI() throws Exception {
    doTest('RearrangementTest46', 'RearrangementResult46E') {
      fieldRule.create {
        modifier( PsiModifier.PROTECTED )
        'sort by' ([ SortOption.MODIFIER, SortOption.TYPE, SortOption.TYPE_CASE_INSENSITIVE, SortOption.NAME,
                     SortOption.NAME_CASE_INSENSITIVE ])
  } } }

  public void testEnumSelection() throws Exception {
    doTest('RearrangementTest47', 'RearrangementResult47') {
      fieldRule.create { modifier( PsiModifier.STATIC ) }
      innerClassRule.create ( 'enum': true )
  } }

  public void testOverloaded1() throws Exception {
    doTest('OverloadedTest1', 'OverloadedResult1') {
      setupSettings('/test/testData/com/wrq/rearranger/OverloadedConfig.xml')
  } }

  public void testGenerics1() throws Exception {
    doTest('GenericsTest1', 'GenericsResult1') {
      setupSettings('/test/testData/com/wrq/rearranger/OverloadedConfig.xml')
  } }

  public void testInterface1() throws Exception {
    doTest('InterfaceTest1', 'InterfaceResult1') {
      // Match all public fields; in an interface, all constants are regarded as public
      fieldRule.create { modifier([ PsiModifier.PUBLIC, PsiModifier.STATIC ]) }

      // Match all public methods; in an interface, all methods are regarded as public
      methodRule.create { modifier( PsiModifier.PUBLIC ) }
  } }
  
  public void testCommentSeparator1() throws Exception {
    doTest('CommentSeparatorTest1', 'CommentSeparatorResult1') {
      commentRule.create {
        comment('\n\t// Fields %FS%\n', condition: CommentRule.EMIT_IF_ITEMS_MATCH_SUBSEQUENT_RULE, 'fill string': '=',
                'use project width for fill': false, 'fill width': 80)
      }
      fieldRule.create { modifier( PsiModifier.PUBLIC ) }
  } }

  public void testEmptySetter() throws Exception {
    doTest('BitFieldTest', 'BitFieldResult') {
      setupSettings(InteractiveTest.DEFAULT_CONFIGURATION)
        spacingRule.create {
          spacing( anchor: SpacingAnchor.AFTER_CLASS_RBRACE, lines: 2 )
          spacing( anchor: SpacingAnchor.EOF, lines: 3 )
  } } }

  private void doTest(@NotNull String srcFileName, @Nullable String expectedResultFileName, @Nullable Closure adjustment = null) {
    doTest(srcFileName, expectedResultFileName, 'java', adjustment)
  }
  
  private void doTest(@NotNull String srcFileName, @Nullable String expectedResultFileName, @Nullable String extension,
                      @Nullable Closure adjustment = null)
  {
    myFixture.configureByFile("${srcFileName}.$extension")
    if (adjustment) {
      adjustment.call()
    }
    ApplicationManager.application.runWriteAction {
      new RearrangerActionHandler().rearrangeDocument(myFixture.project, myFixture.file, mySettings, myFixture.editor.document);
    }
    myFixture.checkResultByFile("${expectedResultFileName}.$extension")
  }
}