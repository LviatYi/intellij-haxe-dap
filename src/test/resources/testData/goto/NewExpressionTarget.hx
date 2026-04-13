class NewExpressionTarget {
  public static function main() {
    new Fo<caret>o();
  }
}

class Foo {
  public function new() {}
}
