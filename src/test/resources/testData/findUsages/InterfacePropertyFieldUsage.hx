interface IFooTrait {
  public function foo():String;
  public var fooProp(get, never):String;
}

class SomeClassAImplFooTrait implements IFooTrait {
  public function new() {}

  public function foo():String {
    return "foo called from SomeClassAImplFooTrait";
  }

  public var foo<caret>Prop(get, never):String;

  function get_fooProp():String {
    return "fooProp called from SomeClassAImplFooTrait";
  }
}

class InterfacePropertyFieldUsage {
  public static function main() {
    var clazzAImplFoo:IFooTrait = new SomeClassAImplFooTrait();
    var str1 = clazzAImplFoo.fooProp;
  }
}


