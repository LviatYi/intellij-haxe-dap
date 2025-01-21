package debugger;

import org.jetbrains.annotations.Nullable;

public class GetVariablesParam {
  public int variablesReference;
  @Nullable public Integer start;
  @Nullable public Integer count;

  public GetVariablesParam(int variablesReference) {
    this.variablesReference = variablesReference;
  }
}
