interface IFooTrait {
  public function foo():String;
  public var fooProp(get, never):String;
}

class SomeClassAImplFooTrait implements IFooTrait {
  public function new() {}

  public function foo():String {
    return "foo called from SomeClassAImplFooTrait";
  }

  public var fooProp(get, never):String;

  function get<caret>_fooProp():String {
    return "fooProp called from SomeClassAImplFooTrait";
  }
}

class InterfacePropertyGetterUsage {
  public static function main() {
    var clazzAImplFoo:IFooTrait = new SomeClassAImplFooTrait();
    var str1 = clazzAImplFoo.fooProp;
  }
}

