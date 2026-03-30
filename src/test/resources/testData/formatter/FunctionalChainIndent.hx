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

    function demo6() {
        var result = values.map(transform).map(transform2).map(transform3).map(transform4).map(transform5).map(transform6).map(transform7).filter(predicate).join(",");
    }

    function demo7() {
        var result = values.map(transform).map(transform2).map(transform3).map(transform4).map(transform5).map(transform6).map(transform7)
            .filter(predicate).join(",");
    }
}
