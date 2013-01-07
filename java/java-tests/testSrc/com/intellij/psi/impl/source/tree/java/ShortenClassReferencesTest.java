package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;

/**
 * @author dsl
 */
public class ShortenClassReferencesTest extends PsiTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/shortenClassRefs";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  public void testSCR22368() throws Exception { doTest(); }

  public void testSCR37254() throws Exception { doTest(); }

  public void testSCR22368_1() throws Exception {
    final PsiElementFactory factory = myJavaFacade.getElementFactory();
    final PsiClass aClass = factory.createClass("X");
    final PsiMethod methodFromText = factory.createMethodFromText(
                                     "void method() {\n" +
                                     "    IntelliJIDEARulezz<\n" +
                                     "}", null);
    final PsiMethod method = (PsiMethod)aClass.add(methodFromText);
    final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) method.getBody().getStatements()[0];
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) declarationStatement.getFirstChild().getFirstChild();
    final PsiClass javaUtilListClass = myJavaFacade.findClass(CommonClassNames.JAVA_UTIL_LIST, GlobalSearchScope.allScope(myPsiManager.getProject()));
    assertNotNull(javaUtilListClass);
    final PsiElement resultingElement = referenceElement.bindToElement(javaUtilListClass);
    assertEquals("List<", resultingElement.getText());
    assertEquals("void method() {\n" +
                 "    List<\n" +
                 "}", method.getText());

  }

  private void doTest() throws Exception {
    String fileName = getTestName(false) + ".java";
    String text = loadFile(fileName);
    final PsiFile file = createFile(fileName, text);
    CommandProcessor.getInstance().executeCommand(
      getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(file);
              String textAfter = loadFile(getTestName(false) + "_after.java");
              PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
              PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
              String fileText = file.getText();
              assertEquals(textAfter, fileText);

            }
            catch (Exception e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");
  }
}
