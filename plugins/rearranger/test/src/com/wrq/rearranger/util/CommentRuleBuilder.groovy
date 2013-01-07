package com.wrq.rearranger.util;

import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/17/12 4:08 PM
 */
class CommentRuleBuilder extends AbstractRuleBuilder<CommentRule> {
  
  {
    registerHandler(RearrangerTestDsl.COMMENT, { data, attributes, rule ->
      rule.commentText = data
      RearrangerTestUtil.setIf(RearrangerTestDsl.CONDITION,                          attributes, 'emitCondition', rule)
      RearrangerTestUtil.setIf(RearrangerTestDsl.ALL_SUBSEQUENT,                     attributes, 'allSubsequentRules', rule)
      RearrangerTestUtil.setIf(RearrangerTestDsl.ALL_PRECEDING,                      attributes, 'allPrecedingRules', rule)
      RearrangerTestUtil.setIf(RearrangerTestDsl.SUBSEQUENT_RULES_TO_MATCH,          attributes, 'NSubsequentRulesToMatch', rule)
      RearrangerTestUtil.setIf(RearrangerTestDsl.PRECEDING_RULES_TO_MATCH,           attributes, 'NPrecedingRulesToMatch', rule)
      
      def fillString = rule.commentFillString
      RearrangerTestUtil.setIf(RearrangerTestDsl.USE_PROJECT_WIDTH_FOR_COMMENT_FILL, attributes, 'useProjectWidthForFill', fillString)
      RearrangerTestUtil.setIf(RearrangerTestDsl.FILL_WIDTH,                         attributes, 'fillWidth', fillString)
      if (attributes[RearrangerTestDsl.FILL_STRING.value]) {
        rule.commentFillString.fillString = attributes[RearrangerTestDsl.FILL_STRING.value]
      }
    })
  }
  
  @NotNull
  @Override
  protected CommentRule createRule() {
    new CommentRule()
  }

  @Override
  protected void registerRule(@NotNull RearrangerSettings settings, @NotNull CommentRule rule) {
    settings.addItem(rule) 
  }
}
