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

  private String _fileName = null;

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

  public String getFileStem() {
    if (_fileName == null) {
      parseSource();
    }

    return _fileName;
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

  private void parseSource() {
    var fileIndex = source.lastIndexOf('/');
    var fileName = source.substring(fileIndex + 1);
    if (!fileName.isEmpty()) {
      var fileStemIndex = fileName.lastIndexOf('.');
      if (fileStemIndex != -1) {
        _fileName = fileName.substring(0, fileStemIndex);
      }
    }
  }
}
