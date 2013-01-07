package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;

/**
 * @author nik
 */
public class JpsSdkReferencesTableImpl extends JpsCompositeElementBase<JpsSdkReferencesTableImpl> implements JpsSdkReferencesTable {
  public static final JpsSdkReferencesTableRole ROLE = new JpsSdkReferencesTableRole();

  public JpsSdkReferencesTableImpl() {
    super();
  }

  private JpsSdkReferencesTableImpl(JpsSdkReferencesTableImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsSdkReferencesTableImpl createCopy() {
    return new JpsSdkReferencesTableImpl(this);
  }

  @Override
  public <P extends JpsElement> void setSdkReference(@NotNull JpsSdkType<P> type, @Nullable JpsSdkReference<P> sdkReference) {
    JpsSdkReferenceRole<P> role = new JpsSdkReferenceRole<P>(type);
    if (sdkReference != null) {
      myContainer.setChild(role, sdkReference);
    }
    else {
      myContainer.removeChild(role);
    }
  }

  @Override
  public <P extends JpsElement> JpsSdkReference<P> getSdkReference(@NotNull JpsSdkType<P> type) {
    return myContainer.getChild(new JpsSdkReferenceRole<P>(type));
  }

  private static class JpsSdkReferencesTableRole extends JpsElementChildRoleBase<JpsSdkReferencesTable> implements JpsElementCreator<JpsSdkReferencesTable> {
    public JpsSdkReferencesTableRole() {
      super("sdk references");
    }

    @NotNull
    @Override
    public JpsSdkReferencesTable create() {
      return new JpsSdkReferencesTableImpl();
    }
  }
}
