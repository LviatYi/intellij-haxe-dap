/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2014 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017-2018 Eric Bishton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.plugins.haxe.runner.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.plugins.haxe.HaxeBundle;
import com.intellij.plugins.haxe.buildsystem.hxml.model.HXMLProjectModel;
import com.intellij.plugins.haxe.compilation.HaxeCompilerUtil;
import com.intellij.plugins.haxe.compilation.LimeUtil;
import com.intellij.plugins.haxe.config.HaxeTarget;
import com.intellij.plugins.haxe.config.NMETarget;
import com.intellij.plugins.haxe.config.OpenFLTarget;
import com.intellij.plugins.haxe.haxelib.HaxelibClasspathUtils;
import com.intellij.plugins.haxe.ide.module.HaxeModuleSettings;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes;
import com.intellij.plugins.haxe.lang.psi.HaxeComponentName;
import com.intellij.plugins.haxe.lang.psi.HaxeFieldDeclaration;
import com.intellij.plugins.haxe.lang.psi.HaxePsiCompositeElement;
import com.intellij.plugins.haxe.lang.psi.HaxeReferenceExpression;
import com.intellij.plugins.haxe.lang.psi.impl.HaxePsiTokenImpl;
import com.intellij.plugins.haxe.runner.DirectRunningState;
import com.intellij.plugins.haxe.runner.HaxeApplicationConfiguration;
import com.intellij.plugins.haxe.runner.NMERunningState;
import com.intellij.plugins.haxe.runner.OpenFLRunningState;
import com.intellij.plugins.haxe.util.HaxeFileUtil;
import com.intellij.plugins.haxe.util.HaxeResolveUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.io.URLUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import debugger.*;
import haxe.root.JavaProtocol;
import org.apache.commons.collections.map.HashedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: Fedor.Korotkov
 * @author: Bryan Ischo <bji@tivo.com>
 * <p/>
 * This is the singular Haxe debug runner, that can debug:
 * 1. Flash targets
 * 2. Hxcpp targets, run locally by the IDE
 * 3. Hxcpp targets, run by an external command
 */
public class HaxeDebugRunner extends GenericProgramRunner<RunnerSettings> {
  public static final String HAXE_DEBUG_RUNNER_ID = "HaxeDebugRunner";
  private static final Logger LOG = Logger.getInstance(HaxeDebugRunner.class);

  private enum DebugMessageSeverity {
    DEBUG,
    INFO,
    IMPORTANT,
    EXCEPTION,
    PROTOCOL_ERROR
  }

  @NotNull
  @Override
  public String getRunnerId() {
    return HAXE_DEBUG_RUNNER_ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId,
                        @NotNull RunProfile profile) {
    return (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) &&
            (profile instanceof HaxeApplicationConfiguration));
  }

  @Override
  protected RunContentDescriptor doExecute(RunProfileState state, ExecutionEnvironment env)
    throws ExecutionException {
    final HaxeApplicationConfiguration configuration =
      (HaxeApplicationConfiguration)(env.getRunProfile());
    final Module module =
      configuration.getConfigurationModule().getModule();
    final Executor executor = env.getExecutor();

    if (module == null) {
      throw new ExecutionException
        (HaxeBundle.message("no.module.for.run.configuration",
                            configuration.getName()));
    }

    final HaxeModuleSettings settings =
      HaxeModuleSettings.getInstance(module);

    boolean flashDebug = false, hxcppDebug = false;

    if (settings.isUseHxmlToBuild() || settings.isUseUserPropertiesToBuild()) {
      if (settings.getHaxeTarget() == HaxeTarget.FLASH) {
        flashDebug = true;
      }
      else if (settings.getHaxeTarget() == HaxeTarget.CPP) {
        hxcppDebug = true;
      }
    }
    else if (settings.isUseNmmlToBuild()) {
      NMETarget target = settings.getNmeTarget();
      if (target == NMETarget.FLASH) {
        flashDebug = true;
      }
      else if ((target == NMETarget.IOS) ||
               (target == NMETarget.ANDROID) ||
               (target == NMETarget.WINDOWS) ||
               (target == NMETarget.MAC) ||
               (target == NMETarget.LINUX) ||
               (target == NMETarget.LINUX64)) {
        hxcppDebug = true;
      }
    }
    else if (settings.isUseOpenFLToBuild()) {
      OpenFLTarget target = settings.getOpenFLTarget();
      if ((target == OpenFLTarget.FLASH) ||
          (target == OpenFLTarget.AIR)) {
        flashDebug = true;
      }
      else if ((target == OpenFLTarget.IOS) ||
               (target == OpenFLTarget.ANDROID) ||
               (target == OpenFLTarget.WINDOWS) ||
               (target == OpenFLTarget.MAC) ||
               (target == OpenFLTarget.LINUX) ||
               (target == OpenFLTarget.LINUX64)) {
        hxcppDebug = true;
      }
    }

    if (flashDebug) {
      String flashFileToDebug = null;
      if(configuration.isCustomFileToLaunch()) {
        flashFileToDebug = configuration.getCustomFileToLaunchPath();
      }
      else {
        if (!settings.isUseOpenFLToBuild()) {
          flashFileToDebug = HaxeCompilerUtil.calculateCompilerOutput(module);
        } else {
          HXMLProjectModel lime = LimeUtil.getLimeProjectModel(module, true);
          flashFileToDebug = HaxeFileUtil.joinPath(module.getProject().getBasePath(), lime.getSwfOutputFileName());
        }
      }
      return runFlash(module, settings, env, executor, flashFileToDebug);
    }
    else if (hxcppDebug) {
      final Project project = env.getProject();
      if (settings.isUseDebugAdapterProtocol()) {
        var remoteDebugging = configuration.isCustomRemoteDebugging();
        var remoteUrl = configuration.getCustomRemoteUrl();
        if (remoteUrl != null && remoteUrl.trim().isEmpty()) {
          remoteUrl = "127.0.0.1";
        }
        return runDapHxcpp(env.getProject(), module, settings, env, executor,
                           configuration.getCustomDebugPort(),
                           remoteDebugging ? remoteUrl : null);
      }
      else {
        return runHxcpp(project, module, settings, env, executor,
                      configuration.getCustomDebugPort(),
                      configuration.isCustomRemoteDebugging());
      }
    }
    else {
      throw new ExecutionException
        (HaxeBundle.message("haxe.proper.debug.targets"));
    }
  }

  private RunContentDescriptor runFlash(final Module module,
                                        final HaxeModuleSettings settings,
                                        final ExecutionEnvironment env,
                                        final Executor executor,
                                        final String launchPath)
    throws ExecutionException {
    final IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId("com.intellij.flex"));
    if (plugin == null) {
      throw new ExecutionException
        (HaxeBundle.message("install.flex.plugin"));
    }
    if (!plugin.isEnabled()) {
      throw new ExecutionException
        (HaxeBundle.message("enable.flex.plugin"));
    }

    String flexSdkName = settings.getFlexSdkName();
    if (StringUtil.isEmpty(flexSdkName)) {
      throw new ExecutionException
        (HaxeBundle.message("flex.sdk.not.specified"));
    }

    if (settings.isUseNmmlToBuild()) {
      return HaxeFlashDebuggingUtil.getNMEDescriptor
        (this, module, env, executor, flexSdkName);
    }
    else if (settings.isUseOpenFLToBuild()) {
      return HaxeFlashDebuggingUtil.getOpenFLDescriptor
        (this, module, env, executor, flexSdkName);
    }
    else {
      return HaxeFlashDebuggingUtil.getDescriptor
        (module, env, launchPath, flexSdkName);
    }
  }

  private RunContentDescriptor runHxcpp(final Project project,
                                        final Module module,
                                        final HaxeModuleSettings settings,
                                        final ExecutionEnvironment env,
                                        final Executor executor,
                                        final int port,
                                        final boolean remoteDebugging)
    throws ExecutionException {
    final XDebugSession debugSession =
      XDebuggerManager.getInstance(project).startSession
        (env,
         new XDebugProcessStarter() {
           @NotNull
           public XDebugProcess start(@NotNull final XDebugSession session)
             throws ExecutionException {
             try {
               // Start the debugger process, which is a class that
               // implements the actual debugger functionality.  In this
               // case, it does so by message passing through a socket.
               final DebugProcess debugProcess = new DebugProcess
                 (session, project, module, port);

               // If using remote debugging, emit a console message
               // indicating that the debugger is waiting for the remote
               // process to start.
               if (remoteDebugging) {
                 showDebugMessage
                   (project, "Listening for debugged process " +
                             "on port " + port + " ... Press OK after " +
                             "remote debugged process has started.",
                    DebugMessageSeverity.IMPORTANT);
               }
               // Else, start the being-debugged process and make the
               // local debug process instance aware of it.
               else {
                 if (settings.isUseOpenFLToBuild()) {
                   debugProcess.setExecutionResult
                     (new OpenFLRunningState
                        (env, module,
                         // runInTest if android or ios
                         ((settings.getOpenFLTarget() == OpenFLTarget.ANDROID) ||
                          (settings.getOpenFLTarget() == OpenFLTarget.IOS)),
                         true, port).
                        execute(executor, HaxeDebugRunner.this));
                 }
                 else {
                   debugProcess.setExecutionResult
                     (new NMERunningState
                        (env, module,
                         // runInTest if android or ios
                         ((settings.getNmeTarget() == NMETarget.ANDROID) ||
                          (settings.getNmeTarget() == NMETarget.IOS)),
                         true, port).
                        execute(executor, HaxeDebugRunner.this));
                 }
               }

               // Now accept the connection from the being-debugged
               // process.
               debugProcess.start();

               return debugProcess;
             }
             catch (IOException e) {
               throw new ExecutionException(e.getMessage(), e);
             }
           }
         });

    return debugSession.getRunContentDescriptor();
  }

  private RunContentDescriptor runDapHxcpp(final Project project,
                                           final Module module,
                                           final HaxeModuleSettings settings,
                                           final ExecutionEnvironment env,
                                           final Executor executor,
                                           final int port,
                                           @Nullable final String remoteUrl) throws ExecutionException {
    final XDebugSession debugSession = XDebuggerManager.getInstance(project).startSession(env, new XDebugProcessStarter() {
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) throws ExecutionException {
        try {
          final DapDebugProcess debugProcess = new DapDebugProcess(session, project, module, port, remoteUrl);
          if (remoteUrl != null) {
            showDebugMessage(project, "Listening for debugged process " +
                                      "on port " +
                                      port +
                                      " ... Press OK after " +
                                      "remote debugged process has started.",
                             DebugMessageSeverity.IMPORTANT);
          }
          else {
            debugProcess.setExecutionResult(
              new DirectRunningState(env, module, settings.getNmeTarget() == NMETarget.ANDROID || settings.getNmeTarget() == NMETarget.IOS,
                                     true, port).execute(executor, HaxeDebugRunner.this));
          }

          debugProcess.start();
          return debugProcess;
        }
        catch (IOException e) {
          throw new ExecutionException(e.getMessage(), e);
        }
      }
    });

    return debugSession.getRunContentDescriptor();
  }

  private class DebugProcess extends XDebugProcess {
    public DebugProcess(@NotNull XDebugSession session,
                        Project project, Module module,
                        int port) throws IOException {
      super(session);
      mClassesWithStatics = new Vector<String>();
      mProject = project;
      mModule = module;
      mDeferredQueue = new LinkedList<Pair<debugger.Command, MessageListener>>();
      mListenerQueue = new LinkedList<MessageListener>();
      mServerSocket = new java.net.ServerSocket(port);
      mBreakpointHandlers = this.createBreakpointHandlers();
      mMap = new HashMap<XLineBreakpoint<XBreakpointProperties>, Integer>();

      mWriteQueue = QueueProcessor.createRunnableQueueProcessor(QueueProcessor.ThreadToUse.POOLED);
    }

    public void setExecutionResult(ExecutionResult executionResult) {
      mExecutionResult = executionResult;
    }

    public void start() {
      ApplicationManager.getApplication().executeOnPooledThread
        (new Runnable() {
          public void run() {
            try {
              DebugProcess.this.readLoop();
            }
            catch (final Throwable t) {
              SwingUtilities.invokeLater
                (new Runnable() {
                  public void run() {
                    DebugProcess.this.error("Debugging loop failed: " + t);
                  }
                });
            }
          }
        });
    }

    @Override
    protected ProcessHandler doGetProcessHandler() {
      return ((mExecutionResult == null) ? null :
              mExecutionResult.getProcessHandler());
    }

    @Override
    @NotNull
    public ExecutionConsole createConsole() {
      return ((mExecutionResult == null) ? super.createConsole() :
              mExecutionResult.getExecutionConsole());
    }

    @Override
    @NotNull
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
      return mBreakpointHandlers;
    }

    @Override
    @NotNull
    public XDebuggerEditorsProvider getEditorsProvider() {
      return new HaxeDebuggerEditorsProvider();
    }

    @Override
    public void startPausing() {
      this.expectOK(debugger.Command.BreakNow);
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
      this.expectOK(debugger.Command.Continue(1));
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
      this.expectOK(debugger.Command.Next(1));
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
      this.expectOK(debugger.Command.Step(1));
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
      this.expectOK(debugger.Command.Finish(1));
    }

    @Override
    public void stop() {
      synchronized (this) {
        if (mServerSocket != null) {
          try {
            mServerSocket.close();
            mServerSocket = null;
          }
          catch (IOException e) {
          }
        }
        if (mDebugSocket != null) {
          try {
            mDebugSocket.close();
            mDebugSocket = null;
          }
          catch (IOException e) {
          }
        }
        // Stop the write queue. Otherwise we get a bunch of pointless dialogs.
        mWriteQueue.dismissLastTasks(0);
      }
    }

    @Override
    public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
      // Complicated!  Basically, just make sure that there is a single
      // enabled breakpoint at [position], and then when [position] is
      // hit, set breakpoints back to how they were before ... but ...
      // what about breaking and setting breakpoints and stuff while
      // waiting on runToPosition?  Have to be clever there ...
    }

    private void info(String message) {
      showDebugMessage(mProject, message, DebugMessageSeverity.DEBUG);
    }

    private void warn(String message) {
      showDebugMessage(mProject, message, DebugMessageSeverity.INFO);
    }

    private void error(String message) {
      showDebugMessage(mProject, message, DebugMessageSeverity.PROTOCOL_ERROR);
      reportSessionError(message);
      this.stop();
    }

    private void reportSessionError(String message) {
      getSession().reportError(message);
      ConsoleView consoleView = getSession().getConsoleView();
      if (consoleView != null) {
        consoleView.print("\n\n", ConsoleViewContentType.ERROR_OUTPUT);
        consoleView.print(message + "\n", ConsoleViewContentType.ERROR_OUTPUT);
      }
    }

    private void expectOK(debugger.Command command) {
      this.enqueueCommand(command, new MessageListener() {
        public void handleMessage(int messageId,
                                  debugger.Message message) {
          if (messageId != JavaProtocol.IdOK) {
            DebugProcess.this.error
              ("Debugger protocol error: expected OK, but got: " +
               JavaProtocol.messageToString(message));
          }
        }
      });
    }

    private void where() {
      this.enqueueCommand(debugger.Command.WhereCurrentThread(false),
                          new MessageListener() {
                            public void handleMessage(int messageId,
                                                      debugger.Message message) {
                              if (messageId != JavaProtocol.IdThreadsWhere) {
                                DebugProcess.this.error
                                  ("Debugger protocol error: expected " +
                                   "IdThreadsWhere, but got: " +
                                   JavaProtocol.messageToString(message));
                                return;
                              }
                              getSession().positionReached
                                (new SuspendContext(DebugProcess.this.mProject,
                                                    DebugProcess.this.mModule,
                                                    message));
                              focusDebugSession(getSession());
                            }
                          });
    }

    private void enqueueCommand(final debugger.Command command,
                                MessageListener listener) {
//            System.out.println("Writing command: " +
//                               JavaProtocol.commandToString(command));
      try {
        synchronized (this) {
          if (mDebugSocket == null) {
            mDeferredQueue.add(Pair.create(command, listener));
            return;
          }
          mListenerQueue.add(listener);
          final OutputStream os = mDebugSocket.getOutputStream();
          mWriteQueue.add(new Runnable() {
            public void run() {
              try {
                JavaProtocol.writeCommand(os, command);
              }
              catch (RuntimeException e) {
                DebugProcess.this.error
                  ("Debugger protocol error: exception while writing " +
                   "command " + JavaProtocol.commandToString(command) + ": " +
                   e);
              }
            }
          });
        }
      }
      catch (IOException e) {
        DebugProcess.this.error
          ("Debugger error: exception queueing write " +
           "command " + JavaProtocol.commandToString(command) + ": " +
           e);
      }
    }

    private void readLoop() throws IOException {
      java.net.ServerSocket serverSocket;
      synchronized (this) {
        serverSocket = mServerSocket;
      }
      // Don't synchronize around the accept.  It locks up the rest of the debugger still
      // running on the AWT thread if the application isn't starting correctly.
      java.net.Socket debugSocket = serverSocket.accept();
      synchronized (this) {
        mDebugSocket = debugSocket;
        mServerSocket.close();
        mServerSocket = null;
        //JavaProtocol.readClientIdentification
        //  (mDebugSocket.getInputStream());
        //// XXX: Put this on the write thread/queue, instead of just posting it?
        //JavaProtocol.writeServerIdentification
        //  (mDebugSocket.getOutputStream());
        // Enqueue a classList callback to populate the class list
        this.enqueueCommand(debugger.Command.Classes(null),
                            new MessageListener() {
                              public void handleMessage(int messageId,
                                                        debugger.Message message) {
                                if (messageId == JavaProtocol.IdClasses) {
                                  DebugProcess.this.handlePartialClassList
                                    ((debugger.ClassList)message.params[0]);
                                }
                              }
                            });
      }
      while (true) {
        synchronized (this) {
          debugSocket = mDebugSocket;
        }
        if (debugSocket == null) {
          break;
        }
        debugger.Message message = JavaProtocol.readMessage
          (debugSocket.getInputStream());
//      System.out.println("Received message: " +
//                         JavaProtocol.messageToString(message));
        int messageId = JavaProtocol.getMessageId(message);
        if (messageId == JavaProtocol.IdThreadCreated) {
          // Console it out
        }
        else if (messageId == JavaProtocol.IdThreadTerminated) {
          // Console it out
        }
        else if (messageId == JavaProtocol.IdThreadStarted) {
          // Console it out
        }
        else if (messageId == JavaProtocol.IdThreadStopped) {
          if (mStoppedOnce) {
            // Send a where to solicit current thread stack frame
            this.where();
          }
          else {
            mStoppedOnce = true;
            while (!mDeferredQueue.isEmpty()) {
              Pair<debugger.Command, MessageListener> p =
                mDeferredQueue.removeFirst();
              this.enqueueCommand(p.getFirst(),
                                  p.getSecond());
            }
            this.resume(null);
          }
        }
        else {
          MessageListener listener = null;
          synchronized (this) {
            if (!mListenerQueue.isEmpty()) {
              listener = mListenerQueue.removeFirst();
            }
          }
          if (listener == null) {
            DebugProcess.this.error
              ("Debugger protocol error: unsolicited response: " +
               JavaProtocol.messageToString(message));
            break;
          }
          else {
            listener.handleMessage(messageId, message);
          }
        }
      }
    }

    private void handlePartialClassList(debugger.ClassList classList) {
      while (true) {
        if (classList.index == 0) {
          // Terminator
          break;
        }
        if (classList.index == 1) {
          // Continued
          this.enqueueCommand
            (debugger.Command.Classes
               ((String)classList.params[0]),
             new MessageListener() {
               public void handleMessage(int messageId,
                                         debugger.Message message) {
                 if (messageId == JavaProtocol.IdClasses) {
                   DebugProcess.this.handlePartialClassList
                     ((debugger.ClassList)
                        message.params[0]);
                 }
                 else {
                   throw new RuntimeException
                     ("Unexpected message in response " +
                      "to class list request: " +
                      JavaProtocol.messageToString(message));
                 }
               }
             });
          break;
        }
        // Element
        if (((Boolean)classList.params[1]).booleanValue()) {
          mClassesWithStatics.addElement
            ((String)classList.params[0]);
        }
        classList = (debugger.ClassList)classList.params[2];
      }
    }

    private void registerBreakpoint
      (@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint) {
      final XSourcePosition position = breakpoint.getSourcePosition();
      if (position == null) {
        return;
      }

      String path = getRelativePath(mProject, position.getFile());

      DebugProcess.this.enqueueCommand
        (debugger.Command.AddFileLineBreakpoint
          (path, position.getLine() + 1), new MessageListener() {
          public void handleMessage(int messageId,
                                    debugger.Message message) {
            if (messageId == JavaProtocol.IdFileLineBreakpointNumber) {
              mMap.put(breakpoint, (Integer)(message.params[0]));
            }
            else {
              getSession().updateBreakpointPresentation
                (breakpoint,
                 AllIcons.Debugger.Db_invalid_breakpoint, null);
              DebugProcess.this.warn("Cannot set breakpoint");
            }
          }
        });
    }

    private void unregisterBreakpoint
      (@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint,
       final boolean temporary) {
      if (!mMap.containsKey(breakpoint)) {
        return;
      }

      int id = mMap.remove(breakpoint);
      DebugProcess.this.enqueueCommand
        (debugger.Command.DeleteBreakpointRange(id, id),
         new MessageListener() {
           public void handleMessage(int messageId,
                                     debugger.Message message) {
             // Could verify that the response was Deleted ...
           }
         });
    }

    private XBreakpointHandler<?>[] createBreakpointHandlers() {
      return new XBreakpointHandler<?>[]
        {
          new XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>>
            (HaxeBreakpointType.class) {
            public void registerBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint) {
              DebugProcess.this.registerBreakpoint(breakpoint);
            }

            public void unregisterBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint, final boolean temporary) {
              DebugProcess.this.unregisterBreakpoint
                (breakpoint, temporary);
            }
          }
        };
    }

    private abstract class MessageListener {
      public abstract void handleMessage(int messageId,
                                         debugger.Message message);
    }

    private class SuspendContext extends XSuspendContext {
      public SuspendContext(Project project, Module module,
                            debugger.Message threadsWhereMessages) {
        mExecutionStacks = this.buildWhereList(project, module, (debugger.ThreadWhereList)threadsWhereMessages.params[0]).
          toArray(new XExecutionStack[0]);
      }

      public XExecutionStack getActiveExecutionStack() {
        return ((mExecutionStacks.length > 0) ?
                mExecutionStacks[0] : null);
      }

      public XExecutionStack[] getExecutionStacks() {
        return mExecutionStacks;
      }

      private Vector<XExecutionStack> buildWhereList
        (Project project, Module module,
         debugger.ThreadWhereList whereList) {
        Vector<XExecutionStack> executionStacks = new
          Vector<XExecutionStack>();

        this.addWhereList(project, module, executionStacks, whereList);

        return executionStacks;
      }

      private void addWhereList(Project project, Module module,
                                Vector<XExecutionStack> executionStacks,
                                debugger.ThreadWhereList whereList) {
        if (whereList == debugger.ThreadWhereList.Terminator) {
          return;
        }

        int number = ((Integer)whereList.params[0]).intValue();
        debugger.ThreadStatus status = (debugger.ThreadStatus)
          whereList.params[1];
        debugger.FrameList frameList = (debugger.FrameList)
          whereList.params[2];

        executionStacks.addElement
          (new ExecutionStack(project, module, number, frameList));

        this.addWhereList(project, module, executionStacks,
                          (debugger.ThreadWhereList)
                            whereList.params[3]);
      }

      private XExecutionStack[] mExecutionStacks;
    }

    private class ExecutionStack extends XExecutionStack {
      public ExecutionStack(Project project, Module module, int number,
                            debugger.FrameList frameList) {
        super("Thread " + number);

        mStackFrames = new Vector<XStackFrame>();

        this.addFrameList(project, module, frameList);
      }

      public XStackFrame getTopFrame() {
        return ((mStackFrames.size() > 0) ?
                mStackFrames.elementAt(0) : null);
      }

      public void computeStackFrames(int firstFrameIndex,
                                     XStackFrameContainer container) {
        if (firstFrameIndex < mStackFrames.size()) {
          container.addStackFrames
            (mStackFrames.subList(firstFrameIndex,
                                  mStackFrames.size() - 1), true);
        }
      }

      private void addFrameList(Project project, Module module,
                                debugger.FrameList frameList) {
        if (frameList == debugger.FrameList.Terminator) {
          return;
        }

        mStackFrames.addElement(new StackFrame(project, module,
                                               frameList));

        this.addFrameList(project, module, (debugger.FrameList)
          frameList.params[6]);
      }

      private Vector<XStackFrame> mStackFrames;
    }

    private class StackFrame extends XStackFrame {
      public StackFrame(final Project project, final Module module,
                        debugger.FrameList frameList) {
        mFrameNumber = (Integer)frameList.params[1];
        mFileName = (String)frameList.params[4];
        mLineNumber = (((Integer)frameList.params[5]).intValue());
        mClassAndFunctionName =
          ((String)frameList.params[2] + "." +
           (String)frameList.params[3]);

        VirtualFileManager vfm = VirtualFileManager.getInstance();
        VirtualFile file = vfm.findFileByUrl(vfm.constructUrl(URLUtil.FILE_PROTOCOL, mFileName));
        if (null == file || !file.exists()) {

          // Filename index can only deal with the name, not any paths.
          String fileName = VfsUtil.extractFileName(mFileName);
          if (fileName == null) {
            fileName = mFileName;
          }

          final String fileNameToLookFor = fileName;
          final Collection<VirtualFile> files =
            ApplicationManager.getApplication().runReadAction(
              new Computable<Collection<VirtualFile>>() {
                @Override
                public Collection<VirtualFile> compute() {

                  Collection<VirtualFile> files =
                    FilenameIndex.getVirtualFilesByName(
                      project, fileNameToLookFor, GlobalSearchScope.moduleScope(module));
                  if (files.isEmpty()) {
                    files = FilenameIndex.getVirtualFilesByName(
                      project, fileNameToLookFor, GlobalSearchScope.moduleWithDependenciesScope(module));
                  }
                  if (files.isEmpty()) {
                    files = FilenameIndex.getVirtualFilesByName(
                      project, fileNameToLookFor, GlobalSearchScope.moduleWithLibrariesScope(module));
                  }
                  if (files.isEmpty()) {
                    files = FilenameIndex.getVirtualFilesByName(
                      project, fileNameToLookFor, GlobalSearchScope.allScope(project));
                  }
                  return files;
                }
              }
            );

          Collection<VirtualFile> matches = new HashSet<VirtualFile>();
          if (!files.isEmpty()) {
            for (VirtualFile f : files) {
              if (f.getPath().endsWith(mFileName)) {
                matches.add(f);
              }
            }
          }
          if (matches.isEmpty()) {
            // If we don't have a match yet, then walk the classpath looking for
            // an appropriate file.
            file = HaxelibClasspathUtils.findFileOnClasspath(module, mFileName);
          }
          else if (matches.size() == 1) {
            // Got one.  If it's a good file, keep it.  Otherwise, try to pick it
            // out of the classpath.
            VirtualFile possible = matches.iterator().next();
            file = possible.isValid() ? possible : HaxelibClasspathUtils.findFileOnClasspath(module, possible.toString());
          }
          else {
            // Too many matches. Get the first that occurs on the classpath.
            file = HaxelibClasspathUtils.findFirstFileOnClasspath(module, matches);
          }
        }

        // Now, work around the fact that IDEA treats symlinks as separate files.
        // XXX: This should be controlled via an UI option.
        if (null != file) {
          file = HaxeFileUtil.getCanonicalFile(file);
        }

        mSourcePosition =
          XSourcePositionImpl.create(file, mLineNumber - 1);
      }

      public Object getEqualityObject() {
        return (mFileName + mClassAndFunctionName).intern();
      }

      public XDebuggerEvaluator getEvaluator() {
        return new XDebuggerEvaluator() {
          public void evaluate
            (@NotNull String expression,
             @NotNull XEvaluationCallback callback,
             XSourcePosition expressionPosition) {
            callback.evaluated(new Value(expression));
          }
        };
      }

      public XSourcePosition getSourcePosition() {
        return mSourcePosition;
      }

      @Override
      public void computeChildren(@NotNull final XCompositeNode node) {
        // Move to the stack frame
        DebugProcess.this.enqueueCommand
          (debugger.Command.SetFrame(mFrameNumber),
           new MessageListener() {
             public void handleMessage(int messageId,
                                       debugger.Message message) {
               if (messageId == JavaProtocol.IdThreadLocation) {
                 StackFrame.this.computeChildrenCurrentFrame
                   (node);
               }
               else {
                 DebugProcess.this.warn
                   ("Failed to set stack frame to " +
                    mFrameNumber + "; got message; " +
                    JavaProtocol.messageToString(message));
               }
             }
             // Get var names
           });
      }

      public void customizePresentation
        (@NotNull ColoredTextContainer component) {
        SimpleTextAttributes attr = (mSourcePosition == null) ?
                                    SimpleTextAttributes.GRAYED_ATTRIBUTES :
                                    SimpleTextAttributes.REGULAR_ATTRIBUTES;

        component.append(mClassAndFunctionName + "  [" + mFileName +
                         ":" + mLineNumber + "]", attr);
        component.setIcon(getStackFrameIcon());
      }

      private Icon getStackFrameIcon() {
        return AllIcons.Debugger.Frame;
      }

      private void computeChildrenCurrentFrame
        (@NotNull final XCompositeNode node) {
        DebugProcess.this.enqueueCommand
          (debugger.Command.Variables(false),
           new MessageListener() {
             public void handleMessage(int messageId,
                                       debugger.Message message) {
               if (messageId == JavaProtocol.IdVariables) {
                 XValueChildrenList childrenList =
                   new XValueChildrenList();
                 debugger.StringList stringList =
                   (debugger.StringList)
                     message.params[0];
                 addChildren(childrenList, stringList);
                 if (true) {
                   node.addChildren(childrenList, true);
                 } else {
                   // Note: Removed because it cluttered the variable list.
                   //       It's a candidate for reinstatement, possibly with
                   //       a control variable.
                   node.addChildren(childrenList, false);
                   // Add all statics to the list of variables.
                   addStaticChildren(node);
                 }
               }
               else {
                 DebugProcess.this.warn
                   ("Failed to get variables; got message " +
                    JavaProtocol.messageToString(message));
               }
             }
           });
      }

      private void addChildren(XValueChildrenList childrenList,
                               debugger.StringList stringList) {
        while (stringList != debugger.StringList.Terminator) {

          String string = (String)stringList.params[0];
          if (!isIntermediateVariableName(string)) {
            childrenList.add(string, new Value(string));
          }

          stringList = (debugger.StringList)stringList.params[1];
        }
      }

      /** Determines whether a variable name has been introduced by
       * the compiler's target backend (e.g. hxcpp).
       */
      private boolean isIntermediateVariableName(String s) {
        return s.startsWith("_g");
      }

      private void addStaticChildren(@NotNull final XCompositeNode node) {
        XValueChildrenList childrenList = new XValueChildrenList();

        for (String c : DebugProcess.this.mClassesWithStatics) {
          childrenList.add("statics of " + c,
                           new Value(c, true));
        }
        node.addChildren(childrenList, true);
      }


      private class Value extends XValue {
        public Value(String name) {
          mName = name;
          mExpression = name;
        }

        public Value(String name, boolean isClassStatics) {
          if (!isClassStatics) {
            mName = name;
            mExpression = name;
            return;
          }

          // Indirect so that the value is not fetched immediately
          // by the UI - the user has to click to see the statics,
          // otherwise, all statics of all classes would be fetched
          // on every breakpoint which would suck
          mName = "statics of " + name;
          mExpression = name;
          mIcon = AllIcons.Debugger.Value;
          mType = "";
          mValue = "";
          mChildren = new LinkedList<Value>();
          mChildren.add(new Value(name));
        }

        public void computePresentation(@NotNull XValueNode node,
                                        @NotNull XValuePlace place) {
          // If no icon has been calculated, then the value must be
          // fetched
          if (mIcon == null) {
            this.fetchValue(node, place);
            return;
          }

          setPresentation(node);
        }

        private void setPresentation(@NotNull XValueNode node) {
          node.setPresentation
            (mIcon, mType, mValue, (mChildren != null));
        }

        // getModifierPsi() is temporarily disabled as it does not work
        // due to PSI errors in the haxe PSI tree.
//                public XValueModifier getModifierPsi()
//                {
//                    return new XValueModifier()
//                    {
//                        public void setValue(@NotNull String expression,
//                                   @NotNull final XModificationCallback callback)
//                        {
//                            System.out.println("Setting value of " +
//                                               Value.this.mName + " with " +
//                                               "expression " +
//                                               Value.this.mExpression  + 
//                                               " to " + expression);
//                            DebugProcess.this.enqueueCommand
//                                (debugger.Command.SetExpression
//                                 (false, mExpression, expression),
//                                 new MessageListener()
//                            {
//                                public void handleMessage(int messageId,
//                                                       debugger.Message message)
//                                {
//                                    // Just indicate that the value was
//                                    // modified, it may end up being the same
//                                    // value if the set expression failed
//                                    callback.valueModified();
//                                }
//                            });
//                        }
//
//                        public String getInitialValueEditorText()
//                        {
//                            System.out.println("Getting initial value of " +
//                                               Value.this.mName + " as " +
//                                               Value.this.mValue);
//                            return Value.this.mValue;
//                        }
//                    };
//                }

        public String getEvaluationExpression() {
          return mExpression;
        }

        @Override
        public boolean canNavigateToSource() {
          return false;
        }

        @Override
        public boolean canNavigateToTypeSource() {
          // XXX todo -- implement source navigation for class types
          return false;
        }

        @Override
        public void computeChildren(@NotNull final XCompositeNode node) {
          internalComputeChildren(node);
        }

        private void internalComputeChildren(@NotNull Obsolescent node) {
          // mWaitingforChildrenResults tells us wether a request for
          // the children is already in flight.  In the case that one
          // is, we do NOT want to call node.addChildren(list,true),
          // because that tells the UI that we have computed all of the
          // children for this node.
          //
          // mChildrenComputationRequested notifies the fetchValue
          // callback that the UI has attempted to draw any elements
          // and won't request them again, so the callback should
          // notify the UI that new children are available (by calling
          // this function again).

          if (mChildren == null || mWaitingForChildrenResults) {
            mChildrenComputationRequested = true;
            return;
          }
          mChildrenComputationRequested = false;

          XValueChildrenList childrenList =
            new XValueChildrenList(mChildren.size());
          for (Value child : mChildren) {
            childrenList.add(child.mName, child);
          }

          // Stupid hack to get around the fact that we're abusing the APIs.
          if (node instanceof XValueNodeImpl) {
            ((XValueNodeImpl)node).addChildren(childrenList, true);
          } else if(node instanceof XCompositeNode) {
            ((XCompositeNode)node).addChildren(childrenList, true);
          } else {
            error("Unexpected node type in debugger screen: " +
                  node.getClass().toString());
          }
        }

        private void fetchValue(@NotNull final XValueNode node,
                                @NotNull final XValuePlace place) {
          mWaitingForChildrenResults = true;
          DebugProcess.this.enqueueCommand
            (debugger.Command.GetStructured(false, mExpression),
             new MessageListener() {
               public void handleMessage(int messageId,
                                         debugger.Message message) {
                 if (messageId == JavaProtocol.IdStructured) {
                   debugger.StructuredValue structuredValue =
                     (debugger.StructuredValue)
                       message.params[0];
                   Value.this.fromStructuredValue
                     (structuredValue);
                 }
                 else {
                   mIcon = AllIcons.General.Error;
                   mValue = mType = "<Unavailable - " +
                                    getErrorString(message) + ">";
                 }

                 // If fromStructuredValue contained a list, the nodes
                 // need to be added to the UI.  The UI usually requests
                 // them via computeChildren().  In some cases,
                 // computeChildren() is called before we have retrieved
                 // the results, and we need to re-trigger the computation.
                 mWaitingForChildrenResults = false;
                 if (mChildrenComputationRequested) {
                   Value.this.internalComputeChildren(node);
                 }

                 Value.this.setPresentation(node);
               }
             });
        }

        private void fromStructuredValue
          (debugger.StructuredValue structuredValue) {
          if (structuredValue.index == 0) {  // Elided
            mExpression = (String)structuredValue.params[1];
          }
          else if (structuredValue.index == 1) {  // Single
            debugger.StructuredValueType type =
              (debugger.StructuredValueType)
                structuredValue.params[0];
            String value = (String)
              structuredValue.params[1];
            mIcon = AllIcons.Debugger.Value;
            mType = getTypeString(type);
            mValue = stripErrorAdornments(value);
          }
          else if (structuredValue.index == 2) {  // List
            debugger.StructuredValueListType type =
              (debugger.StructuredValueListType)
                structuredValue.params[0];
            debugger.StructuredValueList list =
              (debugger.StructuredValueList)
                structuredValue.params[1];
            mIcon = AllIcons.Debugger.Value;
            mType = getTypeString(type);
            mValue = "";
            mChildren = new LinkedList<Value>();
            this.addChildren(list);
          }
          // Anything else, including Elided, is an error
          else {
            mIcon = AllIcons.General.Error;
            mValue = mType = "<Unavailable>";
          }
        }

        private void addChildren(debugger.StructuredValueList list) {
          if (list == debugger.StructuredValueList.Terminator) {
            return;
          }

          String name = (String)list.params[0];
          debugger.StructuredValue structuredValue =
            (debugger.StructuredValue)list.params[1];
          debugger.StructuredValueList next =
            (debugger.StructuredValueList)list.params[2];

          Value val = new Value(name);
          val.fromStructuredValue(structuredValue);
          mChildren.add(val);

          addChildren(next);
        }

        private String getTypeString(debugger.StructuredValueType type) {
          if (type.index == 0) {
            return "Null";
          }
          else if (type.index == 1) {
            return "Bool";
          }
          else if (type.index == 2) {
            return "Int";
          }
          else if (type.index == 3) {
            return "Float";
          }
          else if (type.index == 4) {
            return "String";
          }
          else if (type.index == 5) {
            return (String)type.params[0];
          }
          else if (type.index == 6) {
            return (String)type.params[0];
          }
          else if (type.index == 7) {
            return "{ ... }";
          }
          else if (type.index == 8) {
            return (String)type.params[0];
          }
          else if (type.index == 9) {
            return "Function";
          }
          else {
            return "<Unavailable>";
          }
        }

        private String getTypeString
          (debugger.StructuredValueListType type) {
          if (type.index == 0) {
            return "{ ... }";
          }
          else if (type.index == 1) {
            return (String)type.params[0];
          }
          else if (type.index == 2) {
            return "Array";
          }
          else {
            return "<Unavailable>";
          }
        }

        private String getErrorString(debugger.Message debugMessage) {
          return stripErrorAdornments(debugMessage.toString());
        }

        private String stripErrorAdornments(String message) {

          // Strip the enumeration text.
          Pattern wrapperPattern = Pattern.compile("ErrorEvaluatingExpression\\((.*)\\)", Pattern.DOTALL);
          Matcher m = wrapperPattern.matcher(message);
          String description = m.matches() ? m.group(1) : message;

          // Strip the call stack.
          final String callStackMarker = "\nCalled from ";
          if (description.contains(callStackMarker)) {
            description = description.substring(0, description.indexOf(callStackMarker));
          }

          return description;
        }

        private String mName;
        private String mExpression;
        private javax.swing.Icon mIcon;
        private String mType;
        private String mValue;
        private LinkedList<Value> mChildren;

        // These two manage how/when the child nodes are fetched.
        // See internalComputeChildren for an explanation.
        private boolean mWaitingForChildrenResults;
        private boolean mChildrenComputationRequested;
      }

      private int mFrameNumber;
      private String mFileName;
      private int mLineNumber;
      private String mClassAndFunctionName;
      private XSourcePosition mSourcePosition;
    }

    private Vector<String> mClassesWithStatics;
    private Project mProject;
    private Module mModule;
    private boolean mStoppedOnce;
    private LinkedList<Pair<debugger.Command,
      MessageListener>> mDeferredQueue;
    private QueueProcessor<Runnable> mWriteQueue;
    private LinkedList<MessageListener> mListenerQueue;
    private java.net.ServerSocket mServerSocket;
    private java.net.Socket mDebugSocket;
    private ExecutionResult mExecutionResult;
    private XBreakpointHandler[] mBreakpointHandlers;
    private HashMap<XLineBreakpoint<XBreakpointProperties>, Integer> mMap;
  }

  private class DapDebugProcess extends XDebugProcess {
    private static final String NON_EXIST_VALUE = "NONEXISTENT_VALUE";

    public DapDebugProcess(@NotNull XDebugSession session, Project project, Module module, int port, @Nullable String remoteUrl)
      throws IOException {
      super(session);
      this.project = project;
      this.module = module;
      this.port = port;
      this.remoteUrl = remoteUrl;
      deferredQueue = new LinkedList<>();
      breakpointHandlers = this.createBreakpointHandlers();
      callbacks = new HashMap<>();
      writeQueue = QueueProcessor.createRunnableQueueProcessor(QueueProcessor.ThreadToUse.POOLED);
    }

    public void setExecutionResult(ExecutionResult executionResult) {
      mExecutionResult = executionResult;
    }

    public void start() {
      waitForPaused = true;
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          try {
            DapDebugProcess.this.readLoop();
          }
          catch (final Throwable t) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                DapDebugProcess.this.error("Debugging loop failed: " + t);
              }
            });
          }
        }
      });
    }

    @Override
    protected ProcessHandler doGetProcessHandler() {
      return ((mExecutionResult == null) ? null : mExecutionResult.getProcessHandler());
    }

    @Override
    @NotNull
    public ExecutionConsole createConsole() {
      return ((mExecutionResult == null) ? super.createConsole() : mExecutionResult.getExecutionConsole());
    }

    @Override
    @NotNull
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
      return breakpointHandlers;
    }

    @Override
    @NotNull
    public XDebuggerEditorsProvider getEditorsProvider() {
      return new HaxeDebuggerEditorsProvider();
    }

    @Override
    public void startPausing() {
      this.expectOK(new DapHaxeCommand(DebugProtocolTypes.Pause));
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
      this.expectOK(new DapHaxeCommand<ThreadInfo>(DebugProtocolTypes.Continue, new ThreadInfo(0)));
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
      this.expectOK(new DapHaxeCommand(DebugProtocolTypes.Next));
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
      this.expectOK(new DapHaxeCommand(DebugProtocolTypes.StepIn));
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
      this.expectOK(new DapHaxeCommand(DebugProtocolTypes.StepOut));
    }

    @Override
    public void stop() {
      synchronized (this) {
        DapHaxeProtocol.clearBuffer();
        if (serverSocket != null) {
          try {
            serverSocket.close();
            serverSocket = null;
          }
          catch (IOException ignored) {
          }
        }
        if (debugSocket != null) {
          try {
            debugSocket.close();
            debugSocket = null;
          }
          catch (IOException ignored) {
          }
        }
        if (writeQueue != null) {
          writeQueue.dismissLastTasks(0);
        }
        callbacks.clear();
        deferredQueue.clear();
        resolvedSourceCache.clear();
        runToCursorPosition = null;
      }
    }

    @Override
    public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
      var file = position.getFile();

      this.runToCursorPosition = position;
      updateBreakpointByFileUrl(file.getUrl());
      resume(null);
    }

    private void info(String message) {
      showDebugMessage(project, message, DebugMessageSeverity.DEBUG);
    }

    private void warn(String message) {
      showDebugMessage(project, message, DebugMessageSeverity.INFO);
    }

    private void error(String message) {
      showDebugMessage(project, message, DebugMessageSeverity.PROTOCOL_ERROR);
      reportSessionError(message);
      this.stop();
    }

    private void reportSessionError(String message) {
      getSession().reportError(message);
      ConsoleView consoleView = getSession().getConsoleView();
      if (consoleView != null) {
        consoleView.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
        consoleView.print(message + "\n", ConsoleViewContentType.ERROR_OUTPUT);
      }
    }

    private <PT> void expectOK(DapHaxeCommand<PT> command) {
      this.enqueueCommand(command, message -> DapDebugProcess.this.info("Received message: " + message.toString()));
    }

    private <PT, RT> void expectResult(DapHaxeCommand<PT> command,
                                       DapHaxeProtocol.CommandCallback<PT, RT> callback) {
      this.expectResult(command, callback, null);
    }

    private <PT, RT> void expectResult(DapHaxeCommand<PT> command,
                                       DapHaxeProtocol.CommandCallback<PT, RT> callback,
                                       @Nullable DapHaxeProtocol.CommandCallback<PT, RT> errorCallback) {
      this.<PT, RT>enqueueCommand(command, message -> {
        if (message.result == null) {
          if (errorCallback != null) {
            errorCallback.onResponse(message);
          }
          else {
            DapDebugProcess.this.error("Received message without result: " + message);
          }
        }
        else {
          callback.onResponse(message);
        }
      });
    }

    private <PT, RT> void enqueueCommand(final DapHaxeCommand<PT> command, DapHaxeProtocol.CommandCallback<PT, RT> callback) {
      try {
        synchronized (this) {
          if (debugSocket == null) {
            deferredQueue.add(Pair.create(command, callback));
            return;
          }
          var commandId = this.nextRequestId++;
          callbacks.put(commandId, callback);
          final OutputStream os = debugSocket.getOutputStream();
          writeQueue.add(() -> {
            try {
              DapHaxeProtocol.writeCommand(os, new DapHaxeMessage<PT, RT>(commandId, command.getTypeStr(), command.params));
            }
            catch (RuntimeException e) {
              DapDebugProcess.this.error(
                "Debugger protocol error: exception while writing " + "command " + command + ": " + e);
            }
          });
        }
      }
      catch (IOException e) {
        DapDebugProcess.this.error(
          "Debugger error: exception queueing write " + "command " + command + ": " + e);
      }
    }

    private void readLoop() throws IOException {
      synchronized (this) {
        try {
          if (remoteUrl != null) {
            this.debugSocket = new java.net.Socket();
            this.debugSocket.connect(new java.net.InetSocketAddress(remoteUrl, port), 3000);
          }
        }
        catch (BindException e) {
          killProcessUsePort(port);
          this.debugSocket = null;
          JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("Port " + port + " is already in use. Already tried to run the kill program. Please try again.",
                                          MessageType.ERROR,
                                          null)
            .setFadeoutTime(5000)
            .createBalloon()
            .show(RelativePoint.getSouthWestOf(
                    WindowManager.getInstance().getIdeFrame(project).getComponent()),
                  Balloon.Position.above);
          throw new BindException();
        }
        catch (Exception e) {
          this.debugSocket = null;
        }
      }
      
      // Don't synchronize around the accept.  It locks up the rest of the debugger still
      // running on the AWT thread if the application isn't starting correctly.
      if (debugSocket == null) {
        var port = remoteUrl != null ? this.port : 6972;

        try {
          serverSocket = new ServerSocket(port);
          debugSocket = serverSocket.accept();
        }
        catch (BindException e) {
          killProcessUsePort(port);
          debugSocket = null;
          JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("Port " + port + " is already in use. Already tried to run the kill program. Please try again.",
                                          MessageType.ERROR,
                                          null)
            .setFadeoutTime(5000)
            .createBalloon()
            .show(RelativePoint.getSouthWestOf(
                    WindowManager.getInstance().getIdeFrame(project).getComponent()),
                  Balloon.Position.above);
          throw new BindException();

        }
      }

      synchronized (this) {
        if (this.serverSocket != null) {
          this.serverSocket.close();
        }
        this.serverSocket = null;
      }
      while (debugSocket != null) {
        var message = DapHaxeProtocol.readMessage(debugSocket.getInputStream());
        if (message != null) {
          var dpt = DebugProtocolTypes.fromString(message.method);
          switch (dpt) {
            case BreakpointStop -> {
              this.info("Breakpoint stop.");
              this.checkRunToAndTraceStack(true);
            }
            case ExceptionStop -> {
              String msg = "";
              try {
                var exception = (DapHaxeMessage<ExceptionInfo, Object>)message;
                if (exception.params != null) {
                  msg = exception.params.text;
                }
              }
              catch (Exception ignored) {
              }

              // Error Dialog
              String finalMsg = msg;
              ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showErrorDialog(project, finalMsg, "Haxe Exception");
              });
              showDebugMessage(project,
                               StringUtil.isEmpty(finalMsg) ? "Exception stop." : finalMsg,
                               DebugMessageSeverity.EXCEPTION);
              reportSessionError(StringUtil.isEmpty(finalMsg) ? "Exception stop." : finalMsg);
              
              if (StringUtil.isEmpty(msg)) {
                this.info("Exception stop." + msg);
              }
              else {
                this.info("Exception stop. Error message:" + message);
              }

              this.checkRunToAndTraceStack(true);
            }
            case PauseStop -> {
              if (waitForPaused) {
                this.info("Pause stop for first time. Debug Server is wait for resume.");
                waitForPaused = false;
                while (!deferredQueue.isEmpty()) {
                  var cmd = deferredQueue.removeFirst();
                  this.enqueueCommand(cmd.getFirst(), cmd.getSecond());
                }
                this.resume(null);
              }
              else {
                this.info("Pause stop for Breakpoint step.");
                this.checkRunToAndTraceStack(false);
              }
            }
            case ThreadExit, ThreadStart -> {
              //@SuppressWarnings("unchecked")
              //var m = (DapHaxeMessage<ThreadInfo, Object>)message;
              //if (m.params != null) {
              //  var params = m.params;
              //
              //  var title = dpt == DebugProtocolTypes.ThreadExit ? "Thread exited. " : "Thread start. ";
              //  this.info(title + "Thread: " + String.valueOf(params.threadId));
              //}
            }
            default -> {
              this.info("message: " + message);
            }
          }
          var callback = callbacks.remove(message.id);
          if (callback != null) {
            callback.onResponse(message);
          }
        }
      }
    }

    private void checkRunToAndTraceStack(boolean getFocus) {
      if (getFocus) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame instanceof IdeFrame) {
          frame.toFront();  // 让窗口前置（非最小化时）
          frame.requestFocus(); // 请求焦点
        }
      }

      if (runToCursorPosition != null) {
        var file = runToCursorPosition.getFile();
        runToCursorPosition = null;
        this.updateBreakpointByFileUrl(file.getUrl());
      }

      this.traceStack();
    }

    private void traceStack() {
      this.<ThreadInfo, StackTraceInfo[]>expectResult(new DapHaxeCommand<>(DebugProtocolTypes.StackTrace),
                                                      message -> {
                                                        getSession().positionReached(
                                                          new SuspendContext(DapDebugProcess.this.project,
                                                                             DapDebugProcess.this.module,
                                                                             message));
                                                        focusDebugSession(getSession());
                                                      });
    }

    private XBreakpointHandler<?>[] createBreakpointHandlers() {
      return new XBreakpointHandler<?>[]{new XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>>(HaxeBreakpointType.class) {
        public void registerBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint) {
          DapDebugProcess.this.updateBreakpoint(breakpoint);
        }

        public void unregisterBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint, final boolean temporary) {
          DapDebugProcess.this.updateBreakpoint(breakpoint);
        }
      }};
    }

    private void updateBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakPoint) {
      updateBreakpointByFileUrl(breakPoint.getFileUrl());
    }

    private void updateBreakpointByFileUrl(String focusFileUrl) {
      String absFilePath;
      var vfm = VirtualFileManager.getInstance();
      VirtualFile file = vfm.findFileByUrl(focusFileUrl);
      if (null == file || !file.exists() || null == (absFilePath = file.getCanonicalPath())) {
        return;
      }

      XBreakpointManager breakpointManager = XDebuggerManager.getInstance(this.project).getBreakpointManager();
      Collection<? extends XBreakpoint<?>> breakpoints = breakpointManager.getBreakpoints(HaxeBreakpointType.class);
      List<BreakpointInfo> breakpointsInFile = new ArrayList<>();

      for (XBreakpoint<?> breakpoint : breakpoints) {
        if (breakpoint instanceof XLineBreakpoint<?>) {
          XLineBreakpoint<?> lineBreakpoint = (XLineBreakpoint<?>)breakpoint;
          if (!lineBreakpoint.isEnabled()) {
            continue;
          }

          if (!lineBreakpoint.getFileUrl().equals(focusFileUrl)) {
            continue;
          }
          BreakpointInfo bp = new BreakpointInfo(lineBreakpoint.getLine() + 1);

          if (lineBreakpoint.getConditionExpression() != null) {
            bp.condition = lineBreakpoint.getConditionExpression().toString();
          }
          breakpointsInFile.add(bp);
        }
      }

      if (this.runToCursorPosition != null) {
        var runToCursorFile = this.runToCursorPosition.getFile();
        var url = runToCursorFile.getUrl();
        if (url.equals(focusFileUrl)) {
          BreakpointInfo bp = new BreakpointInfo(this.runToCursorPosition.getLine() + 1);
          breakpointsInFile.add(bp);
        }
      }

      DapHaxeCommand<SetBreakpointsParam> cmd = new DapHaxeCommand(DebugProtocolTypes.SetBreakpoints, new SetBreakpointsParam(
        absFilePath,
        breakpointsInFile.toArray(new BreakpointInfo[0])
      ));
      this.expectOK(cmd);
    }

    @Nullable
    private VirtualFile resolveSourceFile(String relativePath) {
      Optional<VirtualFile> cachedFile = resolvedSourceCache.get(relativePath);
      if (cachedFile != null) {
        return cachedFile.orElse(null);
      }

      VirtualFile file = findProjectSourceFile(relativePath);
      if (file == null) {
        file = findClasspathSourceFile(relativePath);
      }
      if (file == null) {
        file = findIndexedSourceFile(relativePath);
      }

      if (file != null) {
        file = HaxeFileUtil.getCanonicalFile(file);
      }

      resolvedSourceCache.put(relativePath, Optional.ofNullable(file));
      return file;
    }

    @Nullable
    private VirtualFile findProjectSourceFile(String relativePath) {
      VirtualFileManager vfm = VirtualFileManager.getInstance();
      VirtualFile file = vfm.findFileByUrl(VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, relativePath));
      if (file != null && file.exists()) {
        return file;
      }

      ModuleRootManager mrm = ModuleRootManager.getInstance(module);
      for (String rootUrl : mrm.getSourceRootUrls()) {
        VirtualFile sourceFile = vfm.findFileByUrl(rootUrl + "/" + relativePath);
        if (sourceFile != null && sourceFile.exists()) {
          return sourceFile;
        }
      }
      return null;
    }

    @Nullable
    private VirtualFile findClasspathSourceFile(String relativePath) {
      VirtualFile file = HaxelibClasspathUtils.findFileOnClasspath(module, relativePath);
      return file != null && file.exists() ? file : null;
    }

    @Nullable
    private VirtualFile findIndexedSourceFile(String relativePath) {
      String fileName = VfsUtil.extractFileName(relativePath);
      if (fileName == null) {
        fileName = relativePath;
      }

      final String fileNameToLookFor = fileName;
      final Collection<VirtualFile> files =
        ApplicationManager.getApplication().runReadAction((Computable<Collection<VirtualFile>>)() -> {
          var files1 =
            FilenameIndex.getVirtualFilesByName(fileNameToLookFor, GlobalSearchScope.moduleScope(module));
          if (files1.isEmpty()) {
            files1 =
              FilenameIndex.getVirtualFilesByName(fileNameToLookFor, GlobalSearchScope.moduleWithDependenciesScope(module));
          }
          if (files1.isEmpty()) {
            files1 =
              FilenameIndex.getVirtualFilesByName(fileNameToLookFor, GlobalSearchScope.moduleWithLibrariesScope(module));
          }
          if (files1.isEmpty()) {
            files1 = FilenameIndex.getVirtualFilesByName(fileNameToLookFor, GlobalSearchScope.allScope(project));
          }
          return files1;
        });

      if (files.isEmpty()) {
        return null;
      }

      Collection<VirtualFile> matches = new HashSet<>();
      for (VirtualFile file : files) {
        if (file.getPath().endsWith(relativePath)) {
          matches.add(file);
        }
      }

      if (matches.isEmpty()) {
        return null;
      }
      if (matches.size() == 1) {
        VirtualFile possible = matches.iterator().next();
        return possible.isValid() ? possible : null;
      }
      return HaxelibClasspathUtils.findFirstFileOnClasspath(module, matches);
    }

    private class SuspendContext extends XSuspendContext {
      public SuspendContext(Project project, Module module, DapHaxeMessage<ThreadInfo, StackTraceInfo[]> message) {
        var list = new Vector<XExecutionStack>();
        list.addElement(new ExecutionStack(project, module, message.result));

        executionStacks = list.toArray(new XExecutionStack[0]);
      }

      public XExecutionStack getActiveExecutionStack() {
        return ((executionStacks.length > 0) ? executionStacks[0] : null);
      }

      @lombok.Getter private final XExecutionStack[] executionStacks;
    }

    private class ExecutionStack extends XExecutionStack {
      public ExecutionStack(Project project, Module module, StackTraceInfo[] frameList) {
        super("Thread with unknown id.");

        stackFrames = new Vector<XStackFrame>();
        for (StackTraceInfo frame : frameList) {
          stackFrames.addElement(new StackFrame(project, module, frame));
        }
      }

      public XStackFrame getTopFrame() {
        return ((!stackFrames.isEmpty()) ? stackFrames.elementAt(0) : null);
      }

      public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
        if (firstFrameIndex < stackFrames.size()) {
          container.addStackFrames(stackFrames.subList(firstFrameIndex, stackFrames.size() - 1), true);
        }
      }

      private Vector<XStackFrame> stackFrames;
    }

    private class StackFrame extends XStackFrame {
      public StackFrame(final Project project, final Module module, StackTraceInfo frame) {
        frameInfo = frame;
        VirtualFile file = DapDebugProcess.this.resolveSourceFile(frameInfo.source);

        sourcePosition = XSourcePositionImpl.create(file, frameInfo.line - 1);
      }

      public Object getEqualityObject() {
        return (frameInfo.source + frameInfo.name).intern();
      }

      public XDebuggerEvaluator getEvaluator() {
        return new XDebuggerEvaluator() {
          public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, XSourcePosition expressionPosition) {
            if (expression.trim().isEmpty()) {
              callback.errorOccurred("Expression cannot be empty.");
              return;
            }

            PsiFile curPsiFile = PsiManager.getInstance(project).findFile(sourcePosition.getFile());
            if (curPsiFile == null) {
              callback.errorOccurred("Cannot evaluate expression: " + expression);
              return;
            }
            
            PsiElement originElement = curPsiFile.findElementAt(expressionPosition.getOffset() + expression.length() - 1);
            if (originElement != null) {
              PsiElement p = originElement.getParent();
              while (p != null) {
                if (p instanceof HaxeReferenceExpression) {
                  var resolve = ((HaxeReferenceExpression)p).resolve();
                  if (!(resolve instanceof HaxeFieldDeclaration declaration)) {
                    break;
                  }

                  var modifierList = declaration.getModifierList();
                  if (modifierList != null && modifierList.hasModifierProperty("static")) {
                    var expr = "static " + originElement.getText();
                    for (XValueChildrenList list : StackFrame.this.xvalueChildrenMap.values()) {
                      for (int i = 0; i < list.size(); i++) {
                        if (list.getName(i).equals(expr)) {
                          callback.evaluated(list.getValue(i));
                          return;
                        }
                      }
                    }

                    break;
                  }
                }

                p = p.getParent();
              }
            }
            
            DapDebugProcess.this.<EvaluateParam, ValInfo>expectResult(
              new DapHaxeCommand<>(DebugProtocolTypes.Evaluate, new EvaluateParam(expression, frameInfo.id)),
              message -> {
                if (message != null && message.result != null) {
                  callback.evaluated(new Value(message.result));
                }
                else {
                  callback.errorOccurred("Failed to evaluate expression: " + expression);
                }
              });
          }

          @Override
          public @Nullable TextRange getExpressionRangeAtOffset(Project project,
                                                                Document document,
                                                                int offset,
                                                                boolean sideEffectsAllowed) {
            PsiFile curPsiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            var stackFrameVirtualFile = curPsiFile.getVirtualFile();
            if (stackFrameVirtualFile == null || !stackFrameVirtualFile.equals(curPsiFile.getVirtualFile())) {
              return null;
            }

            PsiElement endElement = curPsiFile.findElementAt(offset);
            if (!(endElement instanceof HaxePsiTokenImpl)) {
              return null;
            }

            PsiElement wrapper = endElement.getParent();
            if (!(wrapper instanceof HaxePsiCompositeElement) ||
                !((HaxePsiCompositeElement)wrapper).getTokenType().equals(HaxeTokenTypes.IDENTIFIER)) {
              return null;
            }

            while (wrapper.getParent() != null) {
              wrapper = wrapper.getParent();
              if (wrapper instanceof HaxePsiCompositeElement &&
                  ((HaxePsiCompositeElement)wrapper).getTokenType().equals(HaxeTokenTypes.REFERENCE_EXPRESSION)) {
                var wrapperParent = (HaxePsiCompositeElement)wrapper.getParent();
                if (wrapperParent != null && wrapperParent.getTokenType().equals(HaxeTokenTypes.CALL_EXPRESSION)) {
                  return null;
                }

                return wrapper.getTextRange();
              }
              if (wrapper instanceof HaxeComponentName) {
                return wrapper.getTextRange();
              }
            }

            return null;
          }
        };
      }

      public XSourcePosition getSourcePosition() {
        return sourcePosition;
      }

      @Override
      public void computeChildren(@NotNull final XCompositeNode node) {
        DapDebugProcess.this.<GetScopeParam, ScopeInfo[]>expectResult(
          new DapHaxeCommand<>(DebugProtocolTypes.GetScopes, new GetScopeParam(this.frameInfo.id)),
          message -> {
            if (message.result != null) {
              for (var scope : message.result) {
                this.computeChildrenCurrentFrame(node, scope.id);
              }
            }
          });
      }

      @Override
      public void customizePresentation(@NotNull ColoredTextContainer component) {
        super.customizePresentation(component);

        if (this.frameInfo != null) {
          var className = this.frameInfo.getClassName();
          var fileStem = this.frameInfo.getFileStem();
          component.append(
            "  " + (className.equals(fileStem) ? "" : this.frameInfo.getClassName() + ".") + this.frameInfo.getFuncName() + "()",
                           SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

      private void computeChildrenCurrentFrame(@NotNull final XCompositeNode node, int variableRef) {
        xvalueChildrenMap.put(variableRef, new XValueChildrenList());
        DapDebugProcess.this.<GetVariablesParam, ValInfo[]>expectResult(
          new DapHaxeCommand<>(DebugProtocolTypes.GetVariables, new GetVariablesParam(variableRef)),
          message -> {
            if (message.result != null) {
              var list = xvalueChildrenMap.get(variableRef);
              for (ValInfo val : message.result) {
                list.add(val.name, new Value(val));
              }
              node.addChildren(list, true);
            }
          });
      }

      private class Value extends XValue {
        public Value(ValInfo val) {
          valInfo = val;
          expression = valInfo.name;
          icon = AllIcons.Debugger.Value;
          if (!valInfo.vRefIsZero()) {
            children = new LinkedList<>();
          }
        }

        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
          refreshPresentation(node);
        }

        private void refreshPresentation(@NotNull XValueNode node) {
          node.setPresentation(icon, this.valInfo.type, this.valInfo.value, (children != null));
        }

        public String getEvaluationExpression() {
          return expression;
        }

        @Override
        public boolean canNavigateToSource() {
          return false;
        }

        @Override
        public boolean canNavigateToTypeSource() {
          return false;
        }

        @Override
        public void computeChildren(@NotNull final XCompositeNode node) {
          if (children == null) {
            return;
          }

          DapDebugProcess.this.<GetVariablesParam, ValInfo[]>expectResult(
            new DapHaxeCommand<>(DebugProtocolTypes.GetVariables, new GetVariablesParam(this.valInfo.variablesReference)),
            message -> {
              for (ValInfo info : message.result) {
                children.add(new Value(info));
              }
              XValueChildrenList childrenList = new XValueChildrenList(children.size());
              for (Value child : children) {
                childrenList.add(child.valInfo.name, child);
              }

              node.addChildren(childrenList, true);
            }
          );
        }

        @Override
        public @Nullable XValueModifier getModifier() {
          return new XValueModifier() {
            @Override
            public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
              DapDebugProcess.this.<SetVariableParam, SetVariableResult>expectResult(
                new DapHaxeCommand<>(DebugProtocolTypes.SetVariable,
                                     new SetVariableParam(Value.this.valInfo.name, expression.getExpression())),
                message -> {
                  if (message.result == null) {
                    callback.errorOccurred("Failed to set value of " + valInfo.name);
                  }
                  else if (Objects.equals(message.result.value, NON_EXIST_VALUE)) {
                    callback.errorOccurred("Failed to set value of " + valInfo.name + ". Value not exist.");
                  }
                  else {
                    valInfo.value = message.result.value;
                    //refreshPresentation(node);
                    callback.valueModified();
                  }
                },
                message -> {
                  callback.errorOccurred("Failed to set value of " + valInfo.name);
                });
            }
          };
        }

        private final ValInfo valInfo;
        private String expression;
        private javax.swing.Icon icon;
        private LinkedList<Value> children;
      }

      private final StackTraceInfo frameInfo;
      private final XSourcePosition sourcePosition;
      private final Map<Integer, XValueChildrenList> xvalueChildrenMap = new HashedMap();
    }

    private final Project project;
    private final Module module;
    private String remoteUrl;
    private int port;
    private LinkedList<Pair<DapHaxeCommand, DapHaxeProtocol.CommandCallback>> deferredQueue;
    private java.net.ServerSocket serverSocket;
    private java.net.Socket debugSocket;
    private ExecutionResult mExecutionResult;
    private XBreakpointHandler[] breakpointHandlers;
    private QueueProcessor<Runnable> writeQueue;

    public int nextRequestId = 1;
    private Map<Integer, DapHaxeProtocol.CommandCallback> callbacks = new HashMap<>();
    private boolean waitForPaused = false;
    private final Map<String, Optional<VirtualFile>> resolvedSourceCache = new HashMap<>();

    @Nullable private XSourcePosition runToCursorPosition = null;
  }

  private static String getRelativePath(Project project, VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    String packageName = HaxeResolveUtil.getPackageName(psiFile);
    String fileName = VfsUtil.extractFileName(file.getPath());
    return getPath(packageName, fileName);
  }

  private static String getPath(String packageName, String fileName) {
    if (StringUtil.isEmpty(packageName)) {
      return fileName;
    }

    return packageName.replaceAll("\\.", "/") + "/" + fileName;
  }

  private static void showDebugMessage(final Project project,
                                       final String message,
                                       final DebugMessageSeverity severity) {
    String title = "Haxe Debugger";
    switch (severity) {
      case INFO -> title += " Warning";
      case EXCEPTION -> title += " Exception";
      case PROTOCOL_ERROR -> title += " Error";
    }
    String logMessage = title + ": " + message;
    switch (severity) {
      case DEBUG -> LOG.debug(logMessage);
      case INFO, IMPORTANT -> LOG.info(logMessage);
      case EXCEPTION, PROTOCOL_ERROR -> LOG.warn(logMessage);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (severity == DebugMessageSeverity.IMPORTANT
          || severity == DebugMessageSeverity.EXCEPTION
          || severity == DebugMessageSeverity.PROTOCOL_ERROR) {
        StatusBarUtil.setStatusBarInfo(project, message);
      }
    });
  }

  private static void focusDebugSession(XDebugSession session) {
    if (!(session instanceof XDebugSessionImpl debugSession)) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      debugSession.showSessionTab();
      XDebugSessionTab.showFramesView(debugSession);
    });
  }

  private static void killProcessUsePort(int port) {
    System.out.println("Port " + port + " is already in use. Trying to force kill...");
    try {
      ProcessBuilder pb = new ProcessBuilder(
        "cmd.exe", "/c", "netstat -ano | findstr :" + port);
      Process p = pb.start();
      
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.trim().isEmpty()) {
          String[] tokens = line.trim().split("\\s+");
          String pid = tokens[tokens.length - 1];

          Process kill = Runtime.getRuntime().exec("taskkill /PID " + pid + " /F");
          kill.waitFor();
          System.out.println("Killed process with PID: " + pid);
        }
      }
    }
    catch (IOException | InterruptedException ex) {
      System.out.println("Force kill failed: " + ex.getMessage());
    }
  }
}
