package debugger;

import org.jetbrains.annotations.Nullable;

public class Breakpoint {
  public int line;
  public @Nullable Integer column; // Use Integer for optional
  public @Nullable String condition;
  public @Nullable String logMessage;

  public Breakpoint(int line,
                    @Nullable Integer column,
                    @Nullable String condition,
                    @Nullable String logMessage) {
    this.line = line;
    this.column = column;
    this.condition = condition;
    this.logMessage = logMessage;
  }
}