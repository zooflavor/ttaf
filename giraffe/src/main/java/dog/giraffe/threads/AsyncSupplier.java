package dog.giraffe.threads;

public interface AsyncSupplier<T> {
    static <T> AsyncSupplier<T> constSupplier(T value) {
        return (continuation)->continuation.completed(value);
    }

    void get(Continuation<T> continuation) throws Throwable;
}
