class Bar {
    static int x = 5
    final f = x + 2

    def foo() {
        print <selection>f</selection>
    }

    static def bar() {
        print x + 2
    }
}