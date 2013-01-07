package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
class GroovyReparseTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "reparse/";
  }

  void checkReparse(String text, String type) {
    myFixture.configureByText("a.groovy", text);
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    final String psiBefore = DebugUtil.psiToString(myFixture.getFile(), false);

    myFixture.type(type);
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    final String psiAfter = DebugUtil.psiToString(myFixture.getFile(), false);

    myFixture.configureByText("a.txt", psiBefore.trim() + "\n---\n" + psiAfter.trim());
    myFixture.checkResultByFile(getTestName(false) + ".txt");
  }

  public void testCodeBlockReparse() throws IOException {
    checkReparse("foo 'a', {<caret>}", '\n')
  }

  public void testSwitchCaseIf() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "if "
  }

  public void testSwitchCaseDef() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "def "
  }

  public void testSwitchCaseFor() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "for "
  }
  public void testSwitchCaseWhile() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "while "
  }
  public void testSwitchCaseDo() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "doo "
  }
  public void testSwitchCaseSwitch() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "switch "
  }

  public void testSwitchCaseDot() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
        return <caret>
      case 3:
        return false
      case 4:
        return false
    }
  }
""", "foo."
  }

  public void testOpeningParenthesisAtBlockStart() {
    checkReparse """
def foo() {
  <caret>String home
  simplePlugins.each {
    layoutPlugin it
  }

}
}""", "("
  }

  public void testNoVariableName() {
    checkReparse """
def foo() {
    switch (w) {
      case 2:
        def x<caret> = xxx
        if () {
        }
        return 2

    }
    return state
  }
""", "\b"
  }

  public void testSwitchRParen() {
    checkReparse """
def foo() {
    switch (word w w)<caret> {
      case 2:
        def x = (y)
    }
  }
""", "\b"
  }

  public void testWhileRParen() {
    checkReparse """
def foo() {
  def cl = {
    while (true<caret> {
      //
    }
  }
}""", ";"
  }

  public void testSynchronizedRParen() {
    checkReparse """
def foo() {
  def cl = {
    synchronized (x<caret> {
      //
    }
  }
}""", ";"
  }

  public void testMultilineToNormalString() {
    checkReparse '''
class a {
  def foo() {
    bar(""<caret>aaa")
  }

  def bar() {
    zoo()
  }
}
''', '\b'
  }


}
