class Foo implements Comparable<Foo> {

    def Foo next() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    def Foo previous() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    int compareTo(Foo o) {
        <selection>return 0  //To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}

print new Foo()..new Foo()