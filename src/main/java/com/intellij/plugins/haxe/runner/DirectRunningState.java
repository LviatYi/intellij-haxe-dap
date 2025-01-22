/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2014 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017 Eric Bishton
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
package com.intellij.plugins.haxe.runner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.plugins.haxe.HaxeCommonBundle;
import com.intellij.plugins.haxe.config.sdk.HaxeSdkData;
import com.intellij.plugins.haxe.ide.module.HaxeModuleSettings;
import com.intellij.plugins.haxe.util.HaxeCommandLine;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;

/**
 * @author: Fedor.Korotkov
 */
public class DirectRunningState extends CommandLineState {
  private final Module module;
  private final boolean myRunInTest;
  private final boolean myDebug;
  private final int myDebugPort;

  public DirectRunningState(ExecutionEnvironment env, Module module, boolean runInTest) {
    this(env, module, runInTest, false, 0);
  }

  public DirectRunningState(ExecutionEnvironment env, Module module, boolean runInTest, boolean debug) {
    this(env, module, runInTest, debug, 6972);
  }

  public DirectRunningState(ExecutionEnvironment env, Module module,
                            boolean runInTest, boolean debug, int debugPort) {
    super(env);
    this.module = module;
    myRunInTest = runInTest;
    myDebug = debug;
    myDebugPort = debugPort;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    final HaxeApplicationConfiguration configuration =
      (HaxeApplicationConfiguration)(this.getEnvironment().getRunProfile());

    HaxeCommandLine commandLine = getCommand(configuration);

    System.out.println("CD: " + commandLine.getWorkDirectory());
    System.out.println("Command: " + commandLine.getCommandLineString());

    var p = commandLine.createProcess();
    System.out.println("Process: " + p.pid());

    final var processHandler = new KillableProcessHandler(p, commandLine.getCommandLineString());
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        int exitCode = event.getExitCode();
        System.out.println("Process terminated with code: " + exitCode);
      }
    });

    processHandler.startNotify();
    return processHandler;
  }

  private HaxeCommandLine getCommand(HaxeApplicationConfiguration config) throws ExecutionException {
    final HaxeCommandLine commandLine = new HaxeCommandLine(module);

    //VirtualFile workDir = ProjectUtil.guessModuleDir(module);
    //if(workDir == null) {
    //  throw new ExecutionException("Unable to to determine workdirectory");
    //}
    //commandLine.setWorkDirectory(workDir.getCanonicalPath());

    commandLine.setWorkDirectory(config.getCustomWorkingDirectory());
    commandLine.setExePath(config.getCustomFileToLaunchPath());
    commandLine.addParameter("/Log2VSC:True");
    //commandLine.setExePath("C:/Workspace/self-tools/bin/echoEnv.cmd");
    //commandLine.setExePath("cmd.exe");
    //commandLine.addParameters(
    //  "/c",
    //  "start",
    //  "C:/Users/jiajunyi/Desktop/Temp/pc1/FIFAMobile.exe"
    //);

    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(module.getProject());
    setConsoleBuilder(consoleBuilder);
    return commandLine;
  }
}
