class NewExpressionArgumentType {
  public static function main() {
    new Foo(B<caret>ar);
  }
}

class Foo {
  public function new(value:Class<Bar>) {}
}

class Bar {
  public function new() {}
}
