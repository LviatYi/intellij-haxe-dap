package debugger;

public class StackTraceInfo {
  public int id;

  /// <summary>
  /// className.functionName
  /// </summary>
  public String name;

  /// <summary>
  /// The source file path.
  /// </summary>
  public String source;

  public int line;

  public int column;

  public boolean artificial;

  private String _className = null;

  private String _funcName = null;

  public String getClassName() {
    if (_className == null) {
      parseName();
    }
    
    return _className;
  }

  public String getFuncName() {
    if (_funcName == null) {
      parseName();
    }

    return _funcName;
  }

  private void parseName() {
    var methodIndex = name.lastIndexOf('.');
    if (methodIndex != -1) {
      var classIndex = name.lastIndexOf('.', methodIndex - 1);
      _className = name.substring(classIndex + 1, methodIndex);
      _funcName = name.substring(methodIndex + 1);
    }
    else {
      _className = "";
      _funcName = "";
    }
  }
}
