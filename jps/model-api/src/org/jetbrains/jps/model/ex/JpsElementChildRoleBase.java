package org.jetbrains.jps.model.ex;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;

/**
 * @author nik
 */
public class JpsElementChildRoleBase<E extends JpsElement> extends JpsElementChildRole<E> {
  private String myDebugName;

  protected JpsElementChildRoleBase(String debugName) {
    myDebugName = debugName;
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  public static <E extends JpsElement> JpsElementChildRoleBase<E> create(String debugName) {
    return new JpsElementChildRoleBase<E>(debugName);
  }
}
