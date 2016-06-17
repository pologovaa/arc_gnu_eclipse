/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Synopsys, Inc. - ARC GNU Toolchain support
 *******************************************************************************/

package com.arc.embeddedcdt.dsf;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.eclipse.cdt.core.parser.util.StringUtil;
import org.eclipse.cdt.dsf.concurrent.RequestMonitor;
import org.eclipse.cdt.dsf.gdb.launching.LaunchUtils;
import org.eclipse.cdt.dsf.gdb.service.GDBBackend;
import org.eclipse.cdt.dsf.gdb.service.SessionType;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.utils.CommandLineUtil;
import org.eclipse.cdt.utils.spawner.ProcessFactory;
import org.eclipse.cdt.utils.spawner.Spawner;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.osgi.framework.BundleContext;

import com.arc.embeddedcdt.LaunchConfigurationConstants;
import com.arc.embeddedcdt.LaunchPlugin;
import com.arc.embeddedcdt.dsf.utils.Configuration;

public abstract class GdbServerBackend extends GDBBackend {

    private Process fProcess;
    protected ILaunchConfiguration launchConfiguration;

    public String[] getCommandLineArray() {
        return CommandLineUtil.argumentsToArray(getCommandLine());
    }

    public abstract String getCommandLine();

    public abstract String getProcessLabel();

    public boolean doLaunchProcess() {
        return true;
    }

    public String getCommandToConnect() {
        return String.format("target remote %s:%s\nload\n", getHostAddress(),
                Configuration.getGdbServerPort(launchConfiguration));
    }

    protected String getHostAddress() {
        return LaunchConfigurationConstants.DEFAULT_GDB_HOST;
    }

    public abstract File getWorkingDirectory();

    public GdbServerBackend(DsfSession session, ILaunchConfiguration launchConfiguration) {
        super(session, launchConfiguration);
        this.launchConfiguration = launchConfiguration;
    }

    @Override
    protected BundleContext getBundleContext() {
        return LaunchPlugin.getDefault().getBundle().getBundleContext();
    }

    @Override
    public void initialize(final RequestMonitor requestMonitor) {
        register( new String[]{ GdbServerBackend.class.getName() },
            new Hashtable<String,String>() );
        if (!doLaunchProcess()) {
            requestMonitor.done();
            return;
        }
        try {
            fProcess = launchGDBProcess(getCommandLineArray(), getWorkingDirectory());
        } catch (CoreException e) {
            e.printStackTrace();
        } finally {
            requestMonitor.done();
        }
    }

    private Process launchGDBProcess(String[] cmdArray, File workingDir) throws CoreException {
        Process proc = null;
        try {
            proc = ProcessFactory.getFactory().exec(cmdArray,
                    LaunchUtils.getLaunchEnvironment(launchConfiguration), workingDir);
        } catch (IOException e) {
            String message = "Error while launching command: " + StringUtil.join(cmdArray, " ");
            throw new CoreException(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1, message, e));
        }
        return proc;
    }

    @Override
    public void shutdown(RequestMonitor requestMonitor) {
        unregister();
        interrupt();
        requestMonitor.done();
    }

    @Override
    public Process getProcess() {
        return fProcess;
    }

    @Override
    public void interrupt() {
        if (fProcess != null && fProcess instanceof Spawner) {
            Spawner gdbSpawner = (Spawner) fProcess;
            if (getSessionType() == SessionType.REMOTE) {
                gdbSpawner.interrupt();
            } else {
                gdbSpawner.interruptCTRLC();
            }
        }
    }

}
