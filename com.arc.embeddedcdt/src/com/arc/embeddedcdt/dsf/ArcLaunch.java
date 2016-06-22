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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.dsf.gdb.IGdbDebugConstants;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunch;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISourceLocator;

import com.arc.embeddedcdt.LaunchPlugin;

public class ArcLaunch extends GdbLaunch {

    public ArcLaunch(ILaunchConfiguration launchConfiguration, String mode,
            ISourceLocator locator) {
        super(launchConfiguration, mode, locator);
    }

    public void initializeServerConsole() throws CoreException {
        IProcess newProcess = addServerProcess("GDB Server");
        newProcess.setAttribute(IProcess.ATTR_CMDLINE, "openOCD");
    }

    public IProcess addServerProcess(String label) throws CoreException {
        IProcess newProcess = null;
        final DsfServicesTracker tracker = new DsfServicesTracker(LaunchPlugin.getBundleContext(),
                getSession().getId());
        GdbServerBackend backend = (GdbServerBackend) tracker.getService(GdbServerBackend.class);
        Process serverProc = null;
        if (backend != null) {
            serverProc = backend.getProcess();
        }
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(IGdbDebugConstants.PROCESS_TYPE_CREATION_ATTR,
                IGdbDebugConstants.GDB_PROCESS_CREATION_VALUE);
        if (serverProc != null) {
            newProcess = DebugPlugin.newProcess(this, serverProc, label, attributes);
        }
        tracker.dispose();
        return newProcess;
    }
}
