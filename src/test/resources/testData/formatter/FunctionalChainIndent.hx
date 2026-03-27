class FunctionalChainIndent {
    function demo() {
        var result = values
.map(transform).filter(predicate)
.join(",");
    }

    function demo2() {
        var result = values.map(transform).filter(predicate).join(",");
    }

    function demo3() {
        var result = values.map(transform).filter(predicate)
        .join(",");
    }

    function demo4() {
        var result = values.map(transform)
.filter(predicate).join(",");
    }

    function demo5() {
        var result = values.map(transform)
.filter(predicate)
        .join(",");
    }
}
