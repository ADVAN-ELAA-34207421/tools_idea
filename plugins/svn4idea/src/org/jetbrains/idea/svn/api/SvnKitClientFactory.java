package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.SvnKitAddClient;
import org.jetbrains.idea.svn.annotate.SvnKitAnnotateClient;
import org.jetbrains.idea.svn.change.SvnKitChangeListClient;
import org.jetbrains.idea.svn.checkout.SvnKitCheckoutClient;
import org.jetbrains.idea.svn.conflict.SvnKitConflictClient;
import org.jetbrains.idea.svn.content.SvnKitContentClient;
import org.jetbrains.idea.svn.copy.SvnKitCopyMoveClient;
import org.jetbrains.idea.svn.delete.SvnKitDeleteClient;
import org.jetbrains.idea.svn.history.SvnKitHistoryClient;
import org.jetbrains.idea.svn.integrate.SvnKitMergeClient;
import org.jetbrains.idea.svn.lock.SvnKitLockClient;
import org.jetbrains.idea.svn.portable.SvnkitSvnStatusClient;
import org.jetbrains.idea.svn.portable.SvnkitSvnWcClient;
import org.jetbrains.idea.svn.properties.SvnKitPropertyClient;
import org.jetbrains.idea.svn.revert.SvnKitRevertClient;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitClientFactory extends ClientFactory {

  public SvnKitClientFactory(@NotNull SvnVcs vcs) {
    super(vcs);
  }

  @Override
  protected void setup() {
    addClient = new SvnKitAddClient();
    annotateClient = new SvnKitAnnotateClient();
    contentClient = new SvnKitContentClient();
    historyClient = new SvnKitHistoryClient();
    revertClient = new SvnKitRevertClient();
    deleteClient = new SvnKitDeleteClient();
    copyMoveClient = new SvnKitCopyMoveClient();
    conflictClient = new SvnKitConflictClient();
    propertyClient = new SvnKitPropertyClient();
    mergeClient = new SvnKitMergeClient();
    changeListClient = new SvnKitChangeListClient();
    checkoutClient = new SvnKitCheckoutClient();
    myLockClient = new SvnKitLockClient();
    statusClient = new SvnkitSvnStatusClient(myVcs.createStatusClient());
    infoClient = new SvnkitSvnWcClient(myVcs);
  }
}
