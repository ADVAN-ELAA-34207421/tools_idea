package org.jetbrains.idea.svn.change;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNDepth;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdChangeListClient extends BaseSvnClient implements ChangeListClient {

  @Override
  public void add(@NotNull String changeList, @NotNull File path, @Nullable String[] changeListsToOperate) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add(changeList);
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, SVNDepth.EMPTY);
    SvnBindUtil.changelistsToCommand(changeListsToOperate, parameters);

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    CommandUtil.execute(myVcs, SvnCommandName.changelist, parameters, null);
  }

  @Override
  public void remove(@NotNull File path) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    parameters.add("--remove");
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, SVNDepth.EMPTY);

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    CommandUtil.execute(myVcs, SvnCommandName.changelist, parameters, null);
  }
}
