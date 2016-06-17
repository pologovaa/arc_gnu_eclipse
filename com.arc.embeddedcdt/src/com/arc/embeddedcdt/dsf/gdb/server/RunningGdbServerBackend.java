/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Synopsys, Inc. - ARC GNU Toolchain support
 *******************************************************************************/

package com.arc.embeddedcdt.dsf.gdb.server;

import java.io.File;

import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.debug.core.ILaunchConfiguration;

import com.arc.embeddedcdt.dsf.GdbServerBackend;
import com.arc.embeddedcdt.dsf.utils.Configuration;

public class RunningGdbServerBackend extends GdbServerBackend {

    public RunningGdbServerBackend(DsfSession session, ILaunchConfiguration launchConfiguration) {
        super(session, launchConfiguration);
    }

    @Override
    public String getCommandLine() {
        return null;
    }

    @Override
    public String getProcessLabel() {
        return null;
    }

    @Override
    public boolean doLaunchProcess() {
        return false;
    }

    @Override
    protected String getHostAddress() {
        return Configuration.getHostAddress(launchConfiguration);
    }

    @Override
    public File getWorkingDirectory() {
        return null;
    }

}
