class FunctionLiteralBraceStyle {
    function demo()
    {
        var str = array.map(
    v -> v * 2)
                .    filter(function(v)
        {return v != 0;})
            .join(",");
    }

    function demo2()
    {
        var str = array.map(
            v -> v * 2)
        .    filter(function(v)        {return v != 0;})
            .join(",");
    }

    function demo3()
    {
        var str = array.map(
            v -> v * 2)
        .    filter(function(v)
        {
                someCall();
            return v != 0;})
        .join(",");
    }

    function demo4()
    {
        var str = array.map(                v -> v * 2)
        .    filter(function(v)
        {        someCall();            return v != 0;})
            .join(",");
    }
}
