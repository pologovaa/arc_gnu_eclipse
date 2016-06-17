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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.cdt.dsf.concurrent.CountingRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.DataRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.RequestMonitor;
import org.eclipse.cdt.dsf.concurrent.RequestMonitorWithProgress;
import org.eclipse.cdt.dsf.datamodel.DMContexts;
import org.eclipse.cdt.dsf.debug.service.IMemory.IMemoryDMContext;
import org.eclipse.cdt.dsf.gdb.launching.FinalLaunchSequence;
import org.eclipse.cdt.dsf.gdb.service.IGDBBackend;
import org.eclipse.cdt.dsf.gdb.service.IGDBMemory;
import org.eclipse.cdt.dsf.gdb.service.command.IGDBControl;
import org.eclipse.cdt.dsf.mi.service.IMIContainerDMContext;
import org.eclipse.cdt.dsf.mi.service.IMIProcesses;
import org.eclipse.cdt.dsf.mi.service.MIBreakpointsManager;
import org.eclipse.cdt.dsf.mi.service.MIProcesses;
import org.eclipse.cdt.dsf.mi.service.command.commands.CLICommand;
import org.eclipse.cdt.dsf.mi.service.command.output.MIInfo;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;

import com.arc.embeddedcdt.LaunchPlugin;
import com.arc.embeddedcdt.common.ArcGdbServer;
import com.arc.embeddedcdt.dsf.utils.Configuration;

public class ArcFinalLaunchSequence extends FinalLaunchSequence {

    private void queueCommands(List<String> commands, RequestMonitor rm) {
        if (!commands.isEmpty()) {
            fCommandControl.queueCommand(
                    new CLICommand<MIInfo>(fCommandControl.getContext(), composeCommand(commands)),
                    new DataRequestMonitor<MIInfo>(getExecutor(), rm));
        } else {
            rm.done();
        }
    }

    private IGDBControl fCommandControl;
    private IGDBBackend fGDBBackend;
    private IMIProcesses fProcService;

    private GdbServerBackend serverBackend;
    private DsfServicesTracker tracker;
    private IMIContainerDMContext fContainerCtx;
    private DsfSession session;
    private ArcLaunch launch;

    public ArcFinalLaunchSequence(DsfSession session, Map<String, Object> attributes,
            RequestMonitorWithProgress rm) {
        super(session, attributes, rm);
        this.session = session;
    }

    protected IMIContainerDMContext getContainerContext() {
        return fContainerCtx;
    }

    protected void setContainerContext(IMIContainerDMContext ctx) {
        fContainerCtx = ctx;
    }

    protected static final String GROUP_ARC = "GROUP_ARC";

    @Override
    protected String[] getExecutionOrder(String group) {
        if (GROUP_TOP_LEVEL.equals(group)) {
            /*
             * It is necessary to create new ArrayList here and not just use ArrayList returned by
             * Arrays.asList(). The reason is that Arrays.asList() returns an ArrayList which has a
             * reference to the source array in it and when ArrayList is modified, the source array
             * is modified as well. Apparently somewhere in DSF they rely on that this array is
             * immutable because if I use ArrayList returned from Arrays.asList() everything hangs
             * after the first step.
             */
            List<String> orderList = new ArrayList<String>(
                    Arrays.asList(super.getExecutionOrder(GROUP_TOP_LEVEL)));
            orderList.removeAll(Arrays.asList(new String[] { "stepNewProcess", }));
            orderList.add(orderList.indexOf("stepDataModelInitializationComplete"), GROUP_ARC);
            return orderList.toArray(new String[orderList.size()]);
        }

        if (GROUP_ARC.equals(group)) {
            return new String[] {
                    "stepInitializeArcFinalLaunchSequence",
                    "stepCreateGdbServerService",
                    "stepOpenGdbServerConsole",
                    "stepStartTerminal",
                    "stepSpecifyFileToDebug",
                    "stepUserInitCommands",
                    "stepConnectToTarget",
                    "stepUpdateContainer",
                    "stepInitializeMemory",
                    "stepSetEnvironmentVariables",
                    "stepStartTrackingBreakpoints",
                    "stepStopScript",
                    "stepResumeScript",
                    "stepUserDebugCommands",
                    "stepArcCleanup", };
        }
        return super.getExecutionOrder(group);
    }

    @Execute
    public void stepInitializeArcFinalLaunchSequence(RequestMonitor rm) {
        tracker = new DsfServicesTracker(LaunchPlugin.getBundleContext(), getSession().getId());
        fGDBBackend = tracker.getService(IGDBBackend.class);
        if (fGDBBackend == null) {
            rm.done(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot obtain GDBBackend service", null));
            return;
        }

        fCommandControl = tracker.getService(IGDBControl.class);
        if (fCommandControl == null) {
            rm.done(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot obtain control service", null));
            return;
        }

        fProcService = tracker.getService(IMIProcesses.class);
        if (fProcService == null) {
            rm.done(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot obtain process service", null));
            return;
        }

        launch = (ArcLaunch) session.getModelAdapter(ILaunch.class);
        if (launch == null) {
            rm.done(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot obtain launch", null));
            return;
        }

        // When we are starting to debug a new process, the container is the default process used by
        // GDB.
        // We don't have a pid yet, so we can simply create the container with the UNIQUE_GROUP_ID
        setContainerContext(fProcService.createContainerContextFromGroupId(
                fCommandControl.getContext(), MIProcesses.UNIQUE_GROUP_ID));

        rm.done();
    }

    @RollBack("stepInitializeArcFinalLaunchSequence")
    public void rollBackInitializeFinalLaunchSequence(RequestMonitor rm) {
        if (tracker != null)
            tracker.dispose();
        tracker = null;
        rm.done();
    }

    @Execute
    public void stepCreateGdbServerService(final RequestMonitor rm) {
        serverBackend = launch.getServiceFactory().createService(GdbServerBackend.class,
                launch.getSession(), launch.getLaunchConfiguration());
        if (serverBackend != null) {
            serverBackend.initialize(rm);
        } else {
            rm.setStatus(new Status(Status.ERROR, LaunchPlugin.PLUGIN_ID,
                    "Unable to start GdbServerBackend"));
            rm.done();
        }
    }

    @Execute
    public void stepOpenGdbServerConsole(final RequestMonitor rm) {
        try {
            launch.initializeServerConsole();
        } catch (CoreException e) {
            rm.setStatus(new Status(Status.ERROR, LaunchPlugin.PLUGIN_ID,
                    "Unable to initialize GdbServer console", e));
        } finally {
            rm.done();
        }
    }

    @Execute
    public void stepSpecifyFileToDebug(final RequestMonitor rm) {
        String command = "file " + Configuration.getProgramName(launch.getLaunchConfiguration());
        queueCommands(Arrays.asList(command), rm);
    }

    @Execute
    public void stepUserInitCommands(final RequestMonitor rm) {
        try {
            String userCmd = Configuration.getUserInitCommands(launch.getLaunchConfiguration());
            userCmd = VariablesPlugin.getDefault().getStringVariableManager()
                    .performStringSubstitution(userCmd);
            if (userCmd.length() > 0) {
                String[] commands = userCmd.split("\\r?\\n");

                CountingRequestMonitor crm = new CountingRequestMonitor(getExecutor(), rm);
                crm.setDoneCount(commands.length);
                for (int i = 0; i < commands.length; ++i) {
                    fCommandControl.queueCommand(
                            new CLICommand<MIInfo>(fCommandControl.getContext(), commands[i]),
                            new DataRequestMonitor<MIInfo>(getExecutor(), crm));
                }
            } else {
                rm.done();
            }
        } catch (CoreException e) {
            rm.setStatus(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot run user defined init commands", e));
            rm.done();
        }
    }

    @Execute
    public void stepUpdateContainer(RequestMonitor rm) {
        String groupId = getContainerContext().getGroupId();
        setContainerContext(fProcService
                .createContainerContextFromGroupId(fCommandControl.getContext(), groupId));
        rm.done();
    }

    @Execute
    public void stepSetEnvironmentVariables(RequestMonitor rm) {
        boolean clear = false;
        Properties properties = new Properties();
        try {
            // here we need to pass the proper container context
            clear = fGDBBackend.getClearEnvironment();
            properties = fGDBBackend.getEnvironmentVariables();
        } catch (CoreException e) {
            rm.setStatus(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot get environment information", e));
            rm.done();
            return;
        }

        if (clear == true || properties.size() > 0) {
            fCommandControl.setEnvironment(properties, clear, rm);
        } else {
            rm.done();
        }
    }

    /*
     * Start tracking the breakpoints once we know we are connected to the target (necessary for
     * remote debugging)
     */
    @Execute
    public void stepStartTrackingBreakpoints(final RequestMonitor rm) {
        MIBreakpointsManager bpmService = tracker.getService(MIBreakpointsManager.class);
        bpmService.startTrackingBpForProcess(getContainerContext(), rm);
    }

    /*
     * Run any user defined commands to start debugging
     */
    /** @since 8.2 */
    @Execute
    public void stepUserDebugCommands(final RequestMonitor rm) {
        try {
            String userCmd = Configuration.getUserRunCommands(launch.getLaunchConfiguration());
            if (!userCmd.isEmpty()) {
                userCmd = VariablesPlugin.getDefault().getStringVariableManager()
                        .performStringSubstitution(userCmd);
                String[] commands = userCmd.split("\\r?\\n");

                CountingRequestMonitor crm = new CountingRequestMonitor(getExecutor(), rm);
                crm.setDoneCount(commands.length);
                for (int i = 0; i < commands.length; ++i) {
                    fCommandControl.queueCommand(
                            new CLICommand<MIInfo>(fCommandControl.getContext(), commands[i]),
                            new DataRequestMonitor<MIInfo>(getExecutor(), crm));
                }
            } else {
                rm.done();
            }
        } catch (CoreException e) {
            rm.setStatus(new Status(IStatus.ERROR, LaunchPlugin.PLUGIN_ID, -1,
                    "Cannot run user defined run commands", e));
            rm.done();
        }
    }

    private String composeCommand(Collection<String> commands) {
        if (commands.isEmpty())
            return null;
        StringBuffer sb = new StringBuffer();
        Iterator<String> it = commands.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
        }
        return sb.toString();
    }

    @Execute
    public void stepArcCleanup(final RequestMonitor requestMonitor) {
        tracker.dispose();
        tracker = null;
        requestMonitor.done();
    }

    @Execute
    public void stepInitializeMemory(final RequestMonitor rm) {
        IGDBMemory memory = tracker.getService(IGDBMemory.class);
        IMemoryDMContext memContext = DMContexts.getAncestorOfType(getContainerContext(),
                IMemoryDMContext.class);
        if (memory == null || memContext == null) {
            rm.done();
            return;
        }
        memory.initializeMemoryData(memContext, rm);
    }

    @Execute
    public void stepConnectToTarget(RequestMonitor rm) {
        String command = serverBackend.getCommandToConnect();
        queueCommands(Arrays.asList(command), rm);
    }

    @Execute
    public void stepStopScript(final RequestMonitor rm) {
        if (!launch.getLaunchMode().equals(ILaunchManager.DEBUG_MODE)) {
            rm.done();
            return;
        }
        boolean stopAtMain = Configuration.doStopAtMain(launch.getLaunchConfiguration());
        if (stopAtMain) {
            String stopSymbol = Configuration.getStopSymbol(launch.getLaunchConfiguration());
            queueCommands(Arrays.asList("tbreak " + stopSymbol), rm);
        } else {
            rm.done();
        }
    }

    @Execute
    public void stepResumeScript(final RequestMonitor rm) {
        queueCommands(Arrays.asList("continue"), rm);
    }

    @Execute
    public void stepStartTerminal(final RequestMonitor rm) {
        ArcGdbServer gdbServer = Configuration.getGdbServer(launch.getLaunchConfiguration());
        String serialPort = Configuration.getComPort(launch.getLaunchConfiguration());
        if (Configuration.doLaunchTerminal(launch.getLaunchConfiguration())
                && gdbServer != ArcGdbServer.NSIM && !serialPort.isEmpty()) {
            new TerminalService(session).initialize(rm);
        } else {
            rm.done();
        }
    }
}
