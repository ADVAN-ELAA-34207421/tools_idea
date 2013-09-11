package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdCheckoutClient extends BaseSvnClient implements CheckoutClient {
  @Override
  public void checkout(@NotNull SvnTarget source,
                       @NotNull File destination,
                       @Nullable SVNRevision revision,
                       @Nullable SVNDepth depth,
                       boolean ignoreExternals,
                       @Nullable WorkingCopyFormat format,
                       @Nullable ISVNEventHandler handler) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    // TODO: check format

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, ignoreExternals, "--ignore-externals");
    parameters.add("--force"); // this is to conform to currently used SVNKit behavior - allowUnversionedObstructions

    run(destination, handler, parameters);
  }

  private void run(@NotNull File destination, @Nullable ISVNEventHandler handler, @NotNull List<String> parameters) throws VcsException {
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(destination, handler);

    CommandUtil.execute(myVcs, SvnCommandName.checkout, parameters, null, listener);

    listener.throwWrappedIfException();
  }
}
