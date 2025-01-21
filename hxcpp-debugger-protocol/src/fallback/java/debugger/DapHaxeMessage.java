package debugger;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

class DapHaxePlainMessage {
  public int id;
  public String method;
  @Nullable public JsonElement params;
  @Nullable public JsonElement result;
  @Nullable public JsonElement error;
}

public class DapHaxeMessage<PT, RT> {
  public int id;
  public String method;
  @Nullable public PT params;
  @Nullable public RT result;
  @Nullable public DapHaxeMessageError error;

  public DapHaxeMessage(int id, String method) {
    this(id, method, null, null, null);
  }

  public DapHaxeMessage(int id, String method, PT params) {
    this(id, method, params, null, null);
  }

  private DapHaxeMessage(int id,
                         String method,
                         @Nullable PT params,
                         @Nullable RT result,
                         @Nullable DapHaxeMessageError error) {
    this.id = id;
    this.method = method;
    this.params = params;
    this.result = result;
    this.error = error;
  }

  public static DapHaxeMessage fromBytes(byte[] bytes) {
    var gson = new com.google.gson.Gson();
    var pm = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), DapHaxePlainMessage.class);
    var m = new DapHaxeMessage(pm.id, pm.method);

    switch (DebugProtocolTypes.fromString(pm.method)) {
      case StackTrace:
        m.result = gson.fromJson(pm.result, StackTraceInfo[].class);
        break;
      case SetBreakpoints:
        m.result = gson.fromJson(pm.result, Integer[].class);
        break;
      case ThreadStart:
      case ThreadExit:
        m.params = gson.fromJson(pm.params, ThreadInfo.class);
        break;
      case GetScopes:
        m.result = gson.fromJson(pm.result, ScopeInfo[].class);
        break;
      case GetVariables:
        m.result = gson.fromJson(pm.result, ValInfo[].class);
        break;
      case Evaluate:
        m.result = gson.fromJson(pm.result, ValInfo.class);
        break;
      case Pause:
      case Continue:
      case StepIn:
      case Next:
      case StepOut:
      case SetBreakpoint:
      case RemoveBreakpoint:
      case SwitchFrame:
      case SetVariable:
      case Threads:
      case Completions:
      case SetExceptionOptions:
      case BreakpointStop:
      case ExceptionStop:
      case PauseStop:
      case Unknown:
        break;
    }

    if (m.params == null && pm.params != null) {
      m.params = pm.params;
    }

    if (m.result == null && pm.result != null) {
      m.result = pm.result;
    }

    if (pm.error != null) {
      m.error = gson.fromJson(pm.error, DapHaxeMessageError.class);
    }

    return m;
  }

  public byte[] toBytes() {
    var gson = new com.google.gson.Gson();
    return gson.toJson(this).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("FCHaxeMessage{" + "id=").append(id);
    str.append(", method='").append(method).append("'");
    if (params != null) {
      str.append(", params=").append(params.toString());
    }
    if (result != null) {
      str.append(", result=").append(result.toString());
    }
    if (error != null) {
      str.append(", error=").append(error.toString());
    }
    str.append('}');

    return str.toString();
  }

  public static void main(String[] args) {
    Map<String, Object> dynamicArgs = new HashMap<>();
    dynamicArgs.put("someField", "abc");
    dynamicArgs.put("number", 123);
    var message = new DapHaxeMessage(1, "test", dynamicArgs);
    var bytes = message.toBytes();

    System.out.println(new String(bytes));
    var message2 = DapHaxeMessage.fromBytes(bytes);

    System.out.println(message2.id);
    System.out.println(message2.method);
    System.out.println(message2.params);
  }
}

