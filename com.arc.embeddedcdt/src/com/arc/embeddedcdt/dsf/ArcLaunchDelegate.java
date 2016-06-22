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

import org.eclipse.cdt.dsf.debug.service.IDsfDebugServicesFactory;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunchDelegate;
import org.eclipse.cdt.dsf.gdb.launching.LaunchMessages;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;

public class ArcLaunchDelegate extends GdbLaunchDelegate {

    @Override
    protected IDsfDebugServicesFactory newServiceFactory(ILaunchConfiguration config,
            String version) {
        return new ArcDebugServicesFactory(version);
    }

    @Override
    public void launch(ILaunchConfiguration config, String mode, ILaunch launch,
            IProgressMonitor monitor) throws CoreException {

        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        if (mode.equals(ILaunchManager.DEBUG_MODE) || mode.equals(ILaunchManager.RUN_MODE)) {
            launchDebugger(config, launch, monitor);
        }
    }

    private void launchDebugger(ILaunchConfiguration config, ILaunch launch,
            IProgressMonitor monitor) throws CoreException {

        int totalWork = 10;
        monitor.beginTask(LaunchMessages.getString("GdbLaunchDelegate.0"), totalWork);
        if (monitor.isCanceled()) {
            cleanupLaunch();
            return;
        }

        try {
            launchDebugSession(config, launch, monitor);
        } finally {
            monitor.done();
        }
    }

}
