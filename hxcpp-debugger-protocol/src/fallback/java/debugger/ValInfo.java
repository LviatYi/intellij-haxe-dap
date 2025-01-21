package debugger;

import org.jetbrains.annotations.Nullable;

public class ValInfo {
  public String name;

  public String type;

  public String value;

  public int variablesReference;

  public @Nullable Integer namedVariables;

  public @Nullable Integer indexedVariables;

  public ValInfo() {
  }

  public ValInfo(String name) {
    this.name = name;
    this.type = "";
    this.value = "";
    this.variablesReference = -1;
  }

  public boolean vRefIsZero() {
    return variablesReference == 0;
  }
}
