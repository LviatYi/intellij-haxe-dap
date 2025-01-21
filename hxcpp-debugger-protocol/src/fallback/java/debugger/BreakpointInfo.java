package debugger;

import org.jetbrains.annotations.Nullable;

public class BreakpointInfo {
  public int line;
  public @Nullable Integer column;
  public @Nullable String condition;
  public @Nullable String logMessage;

  public BreakpointInfo(int line) {
    this.line = line;
  }
}
