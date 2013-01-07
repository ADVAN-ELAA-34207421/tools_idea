package org.jetbrains.jps.javac;

import javax.tools.*;
import java.util.Locale;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/24/11
 */
public final class PlainMessageDiagnostic implements Diagnostic<JavaFileObject>{

  private final Kind myKind;
  private final String myMessage;

  public PlainMessageDiagnostic(Kind kind, String message) {
    myKind = kind;
    myMessage = message;
  }

  public Kind getKind() {
    return myKind;
  }

  public JavaFileObject getSource() {
    return null;
  }

  public long getPosition() {
    return 0;
  }

  public long getStartPosition() {
    return 0;
  }

  public long getEndPosition() {
    return 0;
  }

  public long getLineNumber() {
    return 0;
  }

  public long getColumnNumber() {
    return 0;
  }

  public String getCode() {
    return null;
  }

  public String getMessage(Locale locale) {
    return myMessage;
  }
}
