package debugger;

public class EvaluateParam {
  public String expr;
  public int frameId;

  public EvaluateParam(String expr, int frameId) {
    this.expr = expr;
    this.frameId = frameId;
  }
}
