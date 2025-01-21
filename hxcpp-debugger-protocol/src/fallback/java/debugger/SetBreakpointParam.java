package debugger;

import haxe.ds.List;
import org.jetbrains.annotations.Nullable;

public class SetBreakpointParam {
  public String file;
  public List<Breakpoint> breakpoints;

  public SetBreakpointParam(String file, @Nullable List<Breakpoint> breakpoints) {
    this.file = file;
    if (breakpoints == null) {
      breakpoints = new List<>();
    }
     
    this.breakpoints = breakpoints;
  }
}
