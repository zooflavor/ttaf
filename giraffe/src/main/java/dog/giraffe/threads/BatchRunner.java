package dog.giraffe.threads;

import dog.giraffe.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Schedules tasks of a {@link Batch} for execution.
 * If any task fails the rest of the tasks of the batch will not be executed.
 */
public class BatchRunner {
    private BatchRunner() {
    }

    /**
     * Runs all tasks of batch. All tasks can use all threads of the executor of the context.
     *
     * @param parallelism the maximum number of tasks that can be run in parallel
     */
    public static <T> void runMultiThreaded(
            Batch<T> batch, Context context, int parallelism, Continuation<Void> continuation) throws Throwable {
        if (1>parallelism) {
            throw new IllegalStateException("non-positive parallelism "+parallelism);
        }
        AtomicBoolean failed=new AtomicBoolean(false);
        Object lock=new Object();
        List<AsyncSupplier<Void>> forks=new ArrayList<>(parallelism);
        for (int ii=parallelism; 0<ii; --ii) {
            Context context2=new DelegatorContext(context) {
                @Override
                public boolean stopped() {
                    return failed.get()
                            || super.stopped();
                }
            };
            forks.add(new AsyncSupplier<>() {
                @Override
                public void get(Continuation<Void> continuation) throws Throwable {
                    Continuation<Void> continuation2=new Continuation<>() {
                        @Override
                        public void completed(Void result) throws Throwable {
                            continuation.completed(result);
                        }

                        @Override
                        public void failed(Throwable throwable) throws Throwable {
                            failed.set(true);
                            continuation.failed(throwable);
                        }
                    };
                    try {
                        get2(continuation2);
                    }
                    catch (Throwable throwable) {
                        continuation2.failed(throwable);
                    }
                }

                private void get2(Continuation<Void> continuation) throws Throwable {
                    context2.checkStopped();
                    Optional<T> optional;
                    synchronized (lock) {
                        optional=batch.next();
                    }
                    if (optional.isEmpty()) {
                        continuation.completed(null);
                        return;
                    }
                    batch.process(
                            context2,
                            optional.get(),
                            Continuations.async(
                                    Continuations.map(
                                            (result, continuation2)->get2(continuation2),
                                            continuation),
                                    context.executor()));
                }
            });
        }
        Continuations.forkJoin(
                forks,
                Continuations.map(
                        (result, continuation2)->continuation2.completed(null),
                        continuation),
                context.executor());
    }

    /**
     * Runs all tasks of batch.
     * Every thread of the executor of the context is used to run a tasks but the tasks will use one thread only.
     */
    public static <T> void runParallelSingleThreaded(
            Batch<T> batch, Context context, Continuation<Void> continuation) throws Throwable {
        AtomicBoolean failed=new AtomicBoolean(false);
        Object lock=new Object();
        List<AsyncSupplier<Void>> forks=new ArrayList<>(context.executor().threads());
        for (int ii=context.executor().threads(); 0<ii; --ii) {
            SingleThreadedExecutor executor=new SingleThreadedExecutor();
            Context context2=new DelegatorContext(context) {
                @Override
                public Executor executor() {
                    return executor;
                }

                @Override
                public boolean stopped() {
                    return failed.get()
                            || super.stopped();
                }
            };
            forks.add(new AsyncSupplier<>() {
                @Override
                public void get(Continuation<Void> continuation) throws Throwable {
                    Continuation<Void> continuation2=new Continuation<>() {
                        @Override
                        public void completed(Void result) throws Throwable {
                            continuation.completed(result);
                        }

                        @Override
                        public void failed(Throwable throwable) throws Throwable {
                            failed.set(true);
                            executor.clear();
                            continuation.failed(throwable);
                        }
                    };
                    try {
                        get2(continuation2);
                    }
                    catch (Throwable throwable) {
                        continuation2.failed(throwable);
                    }
                }

                private void get2(Continuation<Void> continuation) throws Throwable {
                    context2.checkStopped();
                    Optional<T> optional;
                    synchronized (lock) {
                        optional=batch.next();
                    }
                    if (optional.isEmpty()) {
                        continuation.completed(null);
                        return;
                    }
                    runSingleThreaded(batch, context2, executor, optional.get());
                    executor.clear();
                    context.executor().execute(()->{
                        try {
                            get2(continuation);
                        }
                        catch (Throwable throwable) {
                            continuation.failed(throwable);
                        }
                    });
                }
            });
        }
        Continuations.forkJoin(
                forks,
                Continuations.map(
                        (result, continuation2)->continuation2.completed(null),
                        continuation),
                context.executor());
    }

    /**
     * Runs all tasks of batch.
     * All tasks will be run in the thread of the caller.
     */
    public static <T> void runSingleThreaded(Batch<T> batch, Context context) throws Throwable {
        SingleThreadedExecutor executor=new SingleThreadedExecutor();
        Context context2=new DelegatorContext(context) {
            @Override
            public Executor executor() {
                return executor;
            }
        };
        while (true) {
            context.checkStopped();
            Optional<T> optional=batch.next();
            if (optional.isEmpty()) {
                break;
            }
            runSingleThreaded(batch, context2, executor, optional.get());
            executor.clear();
        }
    }

    private static <T> void runSingleThreaded(
            Batch<T> batch, Context context, SingleThreadedExecutor executor, T value) throws Throwable {
        SingleThreadedJoin<Void> join=new SingleThreadedJoin<>();
        batch.process(context, value, join);
        executor.runJoin(context, join);
    }
}
