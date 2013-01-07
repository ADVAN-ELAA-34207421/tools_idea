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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ArrangementEntryMatcher} which is based on standard match conditions like {@link ArrangementEntryType entry type}
 * or {@link ArrangementModifier modifier}.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/26/12 11:07 PM
 */
public class StdArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final ArrangementMatchCondition myCondition;
  @NotNull private final ArrangementEntryMatcher   myDelegate;

  public StdArrangementEntryMatcher(@NotNull ArrangementMatchCondition condition) {
    myCondition = condition;
    myDelegate = doBuildMatcher(condition);
  }

  @NotNull
  public ArrangementMatchCondition getCondition() {
    return myCondition;
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    return myDelegate.isMatched(entry);
  }

  @Override
  public int hashCode() {
    return myCondition.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    StdArrangementEntryMatcher matcher = (StdArrangementEntryMatcher)o;
    return myCondition.equals(matcher.myCondition);
  }

  @Override
  public String toString() {
    return myCondition.toString();
  }
  
  @NotNull
  private static ArrangementEntryMatcher doBuildMatcher(@NotNull ArrangementMatchCondition condition) {
    MyVisitor visitor = new MyVisitor();
    condition.invite(visitor);
    return visitor.getMatcher();
  }

  private static class MyVisitor implements ArrangementMatchConditionVisitor {

    @NotNull private final List<ArrangementEntryMatcher> myMatchers  = ContainerUtilRt.newArrayList();
    @NotNull private final Set<ArrangementEntryType>     myTypes     = EnumSet.noneOf(ArrangementEntryType.class);
    @NotNull private final Set<ArrangementModifier>      myModifiers = EnumSet.noneOf(ArrangementModifier.class);

    @Nullable private String myNamePattern;

    private boolean nestedComposite;

    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition condition) {
      switch (condition.getType()) {
        case TYPE:
          myTypes.add((ArrangementEntryType)condition.getValue());
          break;
        case MODIFIER:
          myModifiers.add((ArrangementModifier)condition.getValue());
          break;
        case NAME:
          myNamePattern = condition.getValue().toString();
      }
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      if (!nestedComposite) {
        nestedComposite = true;
        for (ArrangementMatchCondition c : condition.getOperands()) {
          c.invite(this);
        }
      }
      else {
        myMatchers.add(doBuildMatcher(condition));
      }
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    public ArrangementEntryMatcher getMatcher() {
      ByTypeArrangementEntryMatcher byType = myTypes.isEmpty() ? null : new ByTypeArrangementEntryMatcher(myTypes);
      ByModifierArrangementEntryMatcher byModifiers = myModifiers.isEmpty() ? null : new ByModifierArrangementEntryMatcher(myModifiers);
      ByNameArrangementEntryMatcher byName = myNamePattern == null ? null : new ByNameArrangementEntryMatcher(myNamePattern);
      int i = countNonNulls(byType, byModifiers, byName);
      if (i == 0 && myMatchers.isEmpty()) {
        return ArrangementEntryMatcher.EMPTY;
      }
      if (myMatchers.isEmpty() && i == 1) {
        if (byType != null) {
          return byType;
        }
        else if (byModifiers != null) {
          return byModifiers;
        }
        else {
          return byName;
        }
      }
      else if (myMatchers.size() == 1) {
        return myMatchers.get(0);
      }
      else {
        CompositeArrangementEntryMatcher result = new CompositeArrangementEntryMatcher();
        for (ArrangementEntryMatcher matcher : myMatchers) {
          result.addMatcher(matcher);
        }
        if (byType != null) {
          result.addMatcher(byType);
        }
        if (byModifiers != null) {
          result.addMatcher(byModifiers);
        }
        if (byName != null) {
          result.addMatcher(byName);
        }
        return result;
      }
    }

    private static int countNonNulls(Object... data) {
      int result = 0;
      for (Object o : data) {
        if (o != null) {
          result++;
        }
      }
      return result;
    }
  }
}
