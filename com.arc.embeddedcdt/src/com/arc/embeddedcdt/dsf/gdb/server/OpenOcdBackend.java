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

public class OpenOcdBackend extends GdbServerBackend {

    private String commandLineTemplate = "%s"
            + " -d0"
            + " -c \"gdb_port %s\""
            + " -f %s"
            + " -s %s";

    public OpenOcdBackend(DsfSession session, ILaunchConfiguration launchConfiguration) {
        super(session, launchConfiguration);
    }

    @Override
    public String getCommandLine() {

        String openOcdPath = Configuration.getOpenOcdPath(launchConfiguration);
        String gdbPort = Configuration.getGdbServerPort(launchConfiguration);
        String openOcdConfig = Configuration.getOpenOcdConfig(launchConfiguration);

        final File rootDir = new File(openOcdPath).getParentFile().getParentFile();
        final File scriptsDir = new File(rootDir,
                "share" + File.separator + "openocd" + File.separator + "scripts");

        String commandLine = String.format(commandLineTemplate, openOcdPath, gdbPort, openOcdConfig,
                scriptsDir.getAbsolutePath());
        return commandLine;
    }

    @Override
    public String getProcessLabel() {
        return "OpenOCD";
    }

}
