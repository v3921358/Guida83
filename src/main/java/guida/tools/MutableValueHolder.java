package guida.tools;

public interface MutableValueHolder<T> extends ValueHolder<T> {
    void setValue(T value);
}
