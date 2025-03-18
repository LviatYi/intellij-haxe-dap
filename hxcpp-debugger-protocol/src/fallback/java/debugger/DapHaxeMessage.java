package debugger;

import com.google.gson.Gson;
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

  private static final Gson gsonCache = new com.google.gson.Gson();

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
    final int SIZE_THRESHOLD = 1024 * 10;
    if (bytes.length > SIZE_THRESHOLD) {
      System.out.println("Lviat Warning: DapHaxeMessage.fromBytes: bytes.length > SIZE_THRESHOLD. bytes.length=" + bytes.length);
      System.out.println("Lviat Warning: DapHaxeMessage.fromBytes: bytes content=" + new String(bytes));
    }

    var pm = gsonCache.fromJson(new String(bytes, StandardCharsets.UTF_8), DapHaxePlainMessage.class);
    var m = new DapHaxeMessage(pm.id, pm.method);

    switch (DebugProtocolTypes.fromString(pm.method)) {
      case StackTrace:
        m.result = gsonCache.fromJson(pm.result, StackTraceInfo[].class);
        break;
      case SetBreakpoints:
        m.result = gsonCache.fromJson(pm.result, Integer[].class);
        break;
      case ThreadStart:
      case ThreadExit:
        //m.params = gsonCache.fromJson(pm.params, ThreadInfo.class);
        break;
      case GetScopes:
        m.result = gsonCache.fromJson(pm.result, ScopeInfo[].class);
        break;
      case GetVariables:
        m.result = gsonCache.fromJson(pm.result, ValInfo[].class);
        break;
      case Evaluate:
        m.result = gsonCache.fromJson(pm.result, ValInfo.class);
        break;
      case ExceptionStop:
        m.params = gsonCache.fromJson(pm.params, ExceptionInfo.class);
        break;
      case SetVariable:
        m.result = gsonCache.fromJson(pm.result, SetVariableResult.class);
        break;
      case Pause:
      case Continue:
      case StepIn:
      case Next:
      case StepOut:
      case SetBreakpoint:
      case RemoveBreakpoint:
      case SwitchFrame:
      case Threads:
      case Completions:
      case SetExceptionOptions:
      case BreakpointStop:
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
      m.error = gsonCache.fromJson(pm.error, DapHaxeMessageError.class);
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

