package debugger;

public class DapHaxeCommand<PT> {
  public DebugProtocolTypes type;
  public PT params;

  public DapHaxeCommand(DebugProtocolTypes type) {
    this(type, null);
  }

  public DapHaxeCommand(DebugProtocolTypes type, PT params) {
    this.type = type;
    this.params = params;
  }

  @Override
  public String toString() {
    return "FCHaxeCommand{" + "method='" + type.toString() + '\'' + ", params=" + params.toString() + '}';
  }

  public String getTypeStr() {
    return type.toString();
  }
}