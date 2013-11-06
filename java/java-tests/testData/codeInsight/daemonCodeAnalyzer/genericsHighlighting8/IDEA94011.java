import java.util.*;
class Test {

    class Parent { }

    interface Consumer<T> { }

    interface MyConsumer<T extends Parent> extends Consumer<T> { }


    public void test(Set<MyConsumer> set) {
        @SuppressWarnings("unchecked")
        <error descr="Incompatible types. Found: 'java.util.Map<Test.Parent,Test.MyConsumer>', required: 'java.util.Map<Test.Parent,Test.MyConsumer<Test.Parent>>'">Map<Parent, MyConsumer<Parent>> map = create(set);</error>

    }

    public <S, T extends Consumer<S>> Map<S, T> create(Set<T> consumers) {
        return null;
    }

}
