class NewExpressionTypeParameter {
  public static function main() {
    var foo = new Foo<B<caret>ar>();
  }
}

class Foo<T> {
  public function new() {}
}

class Bar {
  public function new() {}
}
