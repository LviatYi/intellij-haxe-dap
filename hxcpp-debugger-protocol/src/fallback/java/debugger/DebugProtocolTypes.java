package debugger;

public enum DebugProtocolTypes {
  Unknown("unknown"),
  Pause("pause"),
  Continue("continue"),
  StepIn("stepIn"),
  Next("next"),
  StepOut("stepOut"),
  StackTrace("stackTrace"),
  SetBreakpoints("setBreakpoints"),
  SetBreakpoint("setBreakpoint"),
  RemoveBreakpoint("removeBreakpoint"),
  SwitchFrame("switchFrame"),
  GetScopes("getScopes"),
  GetVariables("getVariables"),
  SetVariable("setVariable"),
  Threads("threads"),
  Evaluate("evaluate"),
  Completions("completions"),
  SetExceptionOptions("setExceptionOptions"),
  BreakpointStop("breakpointStop"),
  ExceptionStop("exceptionStop"),
  PauseStop("pauseStop"),
  ThreadStart("threadStart"),
  ThreadExit("ThreadExit");

  private final String name;

  DebugProtocolTypes(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static DebugProtocolTypes fromString(String name) {
    for (DebugProtocolTypes type : DebugProtocolTypes.values()) {
      if (type.getName().equals(name)) {
        return type;
      }
    }

    return Unknown;
  }

  @Override
  public String toString() {
    return name;
  }
}
