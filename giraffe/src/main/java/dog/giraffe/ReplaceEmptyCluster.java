package dog.giraffe;

import dog.giraffe.Context;
import dog.giraffe.Distance;
import dog.giraffe.VectorMean;
import dog.giraffe.points.Points;
import dog.giraffe.threads.AsyncSupplier;
import dog.giraffe.threads.Continuation;
import dog.giraffe.threads.Continuations;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@FunctionalInterface
public interface ReplaceEmptyCluster
        <D extends Distance<T>, M extends VectorMean<M, T>, P extends Points<D, M, P, T>, T> {
    static <D extends Distance<T>, M extends VectorMean<M, T>, P extends Points<D, M, P, T>, T>
    ReplaceEmptyCluster<D, M, P, T> error() {
        return (centers, context, maxIterations, points, points2, continuation)->
                continuation.failed(new EmptyClusterException());
    }

    static <D extends Distance<T>, M extends VectorMean<M, T>, P extends Points<D, M, P, T>, T>
    ReplaceEmptyCluster<D, M, P, T> farthest(boolean notNear) {
        return (centers, context, maxIterations, points, points2, continuation)->{
            class Candidate {
                public final double distance;
                public final int index;
                public final P points;

                public Candidate(double distance, int index, P points) {
                    this.distance=distance;
                    this.index=index;
                    this.points=points;
                }
            }
            List<AsyncSupplier<Candidate>> forks=new ArrayList<>(points2.size());
            for (P points3: points2) {
                forks.add(new AsyncSupplier<Candidate>() {
                    private double bestDistance;
                    private int bestIndex;
                    private P bestPoints;

                    @Override
                    public void get(Continuation<Candidate> continuation2) throws Throwable {
                        if (notNear) {
                            points3.classify(
                                    Function.identity(),
                                    centers,
                                    new Points.Classification<T, D, M, P, T>() {
                                        @Override
                                        public void nearestCenter(T center, P points) {
                                            for (int ii=0; points.size()>ii; ++ii) {
                                                nearestCenter(center, points, ii);
                                            }
                                        }

                                        @Override
                                        public void nearestCenter(T center, P points, int index) {
                                            double dd=points.distance(center, index);
                                            if (dd>bestDistance) {
                                                bestDistance=dd;
                                                bestIndex=index;
                                                bestPoints=points;
                                            }
                                        }
                                    });
                        }
                        else {
                            points3.forEach((points, index)->{
                                double dd=0.0;
                                for (T center: centers) {
                                    double d2=points.distance(center, index);
                                    if (0.0>=d2) {
                                        return;
                                    }
                                    dd+=d2;
                                }
                                if (dd>bestDistance) {
                                    bestDistance=dd;
                                    bestIndex=index;
                                    bestPoints=points;
                                }
                            });
                        }
                        continuation2.completed(
                                (0.0>=bestDistance)
                                        ?null
                                        :new Candidate(bestDistance, bestIndex, bestPoints));
                    }
                });
            }
            Continuation<List<Candidate>> join=Continuations.map(
                    (candidates, continuation2)->{
                        Candidate bestCandidate=null;
                        double bestDistance=0.0;
                        for (Candidate candidate: candidates) {
                            if ((null!=candidate)
                                    && (bestDistance<candidate.distance)) {
                                bestCandidate=candidate;
                                bestDistance=candidate.distance;
                            }
                        }
                        if (0.0>=bestDistance) {
                            continuation2.failed(new EmptyClusterException());
                        }
                        else {
                            continuation2.completed(bestCandidate.points.get(bestCandidate.index));
                        }
                    },
                    continuation);
            Continuations.forkJoin(forks, join, context.executor());
        };
    }

    void newCenter(
            List<T> centers, Context context, int maxIterations, P points, List<P> points2,
            Continuation<T> continuation) throws Throwable;

    static <D extends Distance<T>, M extends VectorMean<M, T>, P extends Points<D, M, P, T>, T>
    ReplaceEmptyCluster<D, M, P, T> random() {
        return (centers, context, maxIterations, points, points2, continuation)->{
            for (int ii=maxIterations; 0<ii; --ii) {
                T point=points.get(context.random().nextInt(points.size()));
                if (!centers.contains(point)) {
                    continuation.completed(point);
                    return;
                }
            }
            continuation.failed(new EmptyClusterException());
        };
    }
}