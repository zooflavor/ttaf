package dog.giraffe.threads;

import dog.giraffe.Context;
import dog.giraffe.points.Sum;
import java.util.Random;

class DelegatorContext implements Context {
    private final Context context;

    public DelegatorContext(Context context) {
        this.context=context;
    }

    @Override
    public void checkStopped() throws Throwable {
        context.checkStopped();
    }

    @Override
    public void close() {
        context.close();
    }

    @Override
    public Executor executor() {
        return context.executor();
    }

    @Override
    public Random random() {
        return context.random();
    }

    @Override
    public boolean stopped() {
        return context.stopped();
    }

    @Override
    public Sum.Factory sum() {
        return context.sum();
    }
}
