package debugger;

public class SetBreakpointsParam {
  public String file;

  public BreakpointInfo[] breakpoints;

  public SetBreakpointsParam(String file) {
    this(file, new BreakpointInfo[0]);
  }

  public SetBreakpointsParam(String file, BreakpointInfo[] breakpoints) {
    this.file = file;
    this.breakpoints = breakpoints;
  }
}
