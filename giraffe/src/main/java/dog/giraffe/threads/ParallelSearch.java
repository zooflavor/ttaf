package dog.giraffe.threads;

import dog.giraffe.util.Block;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Searches an interval for an element in parallel.
 */
public interface ParallelSearch<T, U> {
    /**
     * Decides to complete the search or to continue it.
     *
     * @param newElements the elements computed since the last call to this method
     */
    void search(Map<Integer, T> newElements, Block continueSearch, Continuation<U> continuation) throws Throwable;

    /**
     * Starts a new search.
     * The elements will be computed in the interval [fromIndex, toIndex) by elements.
     *
     * @param executor used to run the element generation and the call to {@link #search(Map, Block, Continuation)}
     * @param threads the maximum number of parallel computations that can be used to generate elements
     */
    static <T, U> void search(
            AsyncFunction<Integer, T> elements, int fromIndex, int toIndex, ParallelSearch<T, U> search,
            Executor executor, int threads, Continuation<U> continuation) throws Throwable {
        if (0>=threads) {
            throw new IllegalArgumentException("0 >= threads "+threads);
        }
        class Proc implements SleepProcess<U> {
            class ElementCont implements Continuation<T> {
                private final int index;
                private final Block wakeup;

                public ElementCont(int index, Block wakeup) {
                    this.index=index;
                    this.wakeup=wakeup;
                }

                @Override
                public void completed(T result) throws Throwable {
                    synchronized (lock) {
                        --running;
                        newElements.put(index, result);
                    }
                    wakeup.run();
                }

                @Override
                public void failed(Throwable throwable) throws Throwable {
                    if (null==throwable) {
                        throwable=new RuntimeException("ElementCont.failed() without exception");
                    }
                    synchronized (lock) {
                        --running;
                        if (null==error) {
                            error=throwable;
                        }
                        else {
                            error.addSuppressed(throwable);
                        }
                    }
                    wakeup.run();
                }
            }

            class SearchCont implements Continuation<U>, Block {
                private final Block sleep;
                private final Block wakeup;

                public SearchCont(Block sleep, Block wakeup) {
                    this.sleep=sleep;
                    this.wakeup=wakeup;
                }

                @Override
                public void completed(U result) throws Throwable {
                    synchronized (lock) {
                        hasResult=true;
                        Proc.this.result=result;
                    }
                    run();
                }

                @Override
                public void failed(Throwable throwable) throws Throwable {
                    if (null==throwable) {
                        throwable=new RuntimeException("SearchCont.failed() without exception");
                    }
                    synchronized (lock) {
                        if (null==error) {
                            error=throwable;
                        }
                        else {
                            error.addSuppressed(throwable);
                        }
                    }
                    run();
                }

                @Override
                public void run() throws Throwable {
                    wakeup.run();
                    sleep.run();
                }
            }

            private Throwable error;
            private boolean hasResult;
            private final Map<Integer, T> newElements=new HashMap<>(threads);
            private int nextIndex=fromIndex;
            private final Object lock=new Object();
            private U result;
            private int running;

            private boolean competed() {
                synchronized (lock) {
                    return hasResult || (null!=error);
                }
            }

            @Override
            public void run(Block wakeup, Block sleep, Continuation<U> continuation) throws Throwable {
                Objects.requireNonNull(sleep, "sleep");
                Block block=null;
                Map<Integer, T> newElements2=null;
                int running2=0;
                synchronized (lock) {
                    if (competed()) {
                        if (0<running) {
                            block=sleep;
                        }
                        else if (hasResult) {
                            block=()->continuation.completed(result);
                        }
                        else {
                            block=()->continuation.failed(error);
                        }
                    }
                    else {
                        if (!newElements.isEmpty()) {
                            newElements2=new HashMap<>(newElements);
                            newElements.clear();
                        }
                        running2=running;
                    }
                }
                if (null!=block) {
                    block.run();
                }
                else if (null!=newElements2) {
                    SearchCont searchCont=new SearchCont(sleep, wakeup);
                    search.search(newElements2, searchCont, searchCont);
                }
                else if ((0==running2)
                        && (toIndex<=nextIndex)) {
                    continuation.failed(new NoSuchElementException());
                }
                else {
                    int newThreads=Math.min(threads-running2, toIndex-nextIndex);
                    if (0<newThreads) {
                        for (int tt=newThreads; 0<tt; --tt) {
                            int index=nextIndex;
                            ElementCont elementCont=new ElementCont(index, wakeup);
                            ++nextIndex;
                            executor.execute(()->{
                                try {
                                    if (competed()) {
                                        elementCont.completed(null);
                                    }
                                    else {
                                        elements.apply(index, elementCont);
                                    }
                                }
                                catch (Throwable throwable) {
                                    elementCont.failed(throwable);
                                }
                            });
                        }
                        synchronized (lock) {
                            running+=newThreads;
                        }
                    }
                    sleep.run();
                }
            }
        }
        SleepProcess.create(continuation, executor, new Proc())
                .run();
    }
}
