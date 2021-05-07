package dog.giraffe;

import dog.giraffe.isodata.Isodata;
import dog.giraffe.kmeans.EmptyClusterException;
import dog.giraffe.kmeans.InitialCenters;
import dog.giraffe.kmeans.KMeans;
import dog.giraffe.kmeans.ReplaceEmptyCluster;
import dog.giraffe.points.Points;
import dog.giraffe.threads.AsyncSupplier;
import dog.giraffe.threads.Block;
import dog.giraffe.threads.Continuation;
import dog.giraffe.threads.Continuations;
import dog.giraffe.threads.Function;
import dog.giraffe.threads.ParallelSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public interface ClusteringStrategy<P extends Points> extends Log {
    static <P extends Points> ClusteringStrategy<P> best(List<ClusteringStrategy<P>> strategies) {
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("empty strategies");
        }
        if (1==strategies.size()) {
            return strategies.get(0);
        }
        return new ClusteringStrategy<>() {
            @Override
            public void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable {
                List<AsyncSupplier<Clusters>> forks=new ArrayList<>(strategies.size());
                for (ClusteringStrategy<P> strategy: strategies) {
                    forks.add((continuation2)->{
                        context.checkStopped();
                        strategy.cluster(context, points, continuation2);
                    });
                }
                Continuation<List<Clusters>> join=Continuations.map(
                        (clustersList, continuation2)->{
                            Clusters best=null;
                            for (Clusters clusters: clustersList) {
                                if ((null==best)
                                        || (best.error>clusters.error)) {
                                    best=clusters;
                                }
                            }
                            continuation.completed(best);
                        },
                        continuation);
                Continuations.forkJoin(forks, join, context.executor());
            }

            @Override
            public void log(Map<String, Object> log) throws Throwable {
                log.put("type", "best");
                for (int ii=0; strategies.size()>ii; ++ii) {
                    Log.logField(String.format("strategy%1$02d", ii), strategies.get(ii), log);
                }
            }
        };
    }

    void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable;

    static <P extends Points> ClusteringStrategy<P> elbow(
            double errorLimit, int maxClusters, int minClusters,
            Function<Integer, ClusteringStrategy<P>> strategy, int threads) {
        return new ClusteringStrategy<>() {
            @Override
            public void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable {
                class ClustersOrEmpty {
                    public final Clusters clusters;
                    public final EmptyClusterException empty;

                    public ClustersOrEmpty(Clusters clusters) {
                        this.clusters=Objects.requireNonNull(clusters, "clusters");
                        empty=null;
                    }

                    public ClustersOrEmpty(EmptyClusterException empty) {
                        this.empty=Objects.requireNonNull(empty, "empty");
                        clusters=null;
                    }
                }
                ParallelSearch.search(
                        (clusters, continuation2)->{
                            Continuation<Clusters> continuation3=new Continuation<>() {
                                @Override
                                public void completed(Clusters result) throws Throwable {
                                    continuation2.completed(new ClustersOrEmpty(result));
                                }

                                @Override
                                public void failed(Throwable throwable) throws Throwable {
                                    if (throwable instanceof EmptyClusterException) {
                                        continuation2.completed(new ClustersOrEmpty((EmptyClusterException)throwable));
                                    }
                                    else {
                                        continuation2.failed(throwable);
                                    }
                                }
                            };
                            try {
                                strategy.apply(clusters).cluster(context, points, continuation3);
                            }
                            catch (Throwable throwable) {
                                continuation3.failed(throwable);
                            }
                        },
                        minClusters,
                        maxClusters+1,
                        new ParallelSearch<ClustersOrEmpty, Clusters>() {
                            private final NavigableMap<Integer, ClustersOrEmpty> clusters=new TreeMap<>();
                            private int index;
                            private Clusters selected;

                            @Override
                            public void search(
                                    Map<Integer, ClustersOrEmpty> newElements, Block continueSearch,
                                    Continuation<Clusters> continuation) throws Throwable {
                                context.checkStopped();
                                clusters.putAll(newElements);
                                while (true) {
                                    if (null==selected) {
                                        ClustersOrEmpty next=clusters.remove(minClusters);
                                        if (null==next) {
                                            continueSearch.run();
                                            return;
                                        }
                                        else if (null==next.clusters) {
                                            continuation.failed(new EmptyClusterException(next.empty));
                                            return;
                                        }
                                        else {
                                            selected=next.clusters;
                                            index=minClusters;
                                        }
                                    }
                                    else if (maxClusters<=index) {
                                        continuation.completed(selected);
                                        return;
                                    }
                                    else {
                                        ClustersOrEmpty next=clusters.remove(index+1);
                                        if (null==next) {
                                            continueSearch.run();
                                            return;
                                        }
                                        else if (null==next.clusters) {
                                            continuation.completed(selected);
                                            return;
                                        }
                                        else if (selected.error*errorLimit>next.clusters.error) {
                                            selected=next.clusters;
                                            ++index;
                                        }
                                        else {
                                            continuation.completed(selected);
                                            return;
                                        }
                                    }
                                }
                            }
                        },
                        context.executor(),
                        threads,
                        continuation);
            }

            @Override
            public void log(Map<String, Object> log) throws Throwable {
                log.put("type", "elbow");
                log.put("error-limit", errorLimit);
                log.put("max-clusters", maxClusters);
                log.put("min-clusters", minClusters);
                Log.logField("strategy", strategy.apply(minClusters), log);
            }
        };
    }

    static <P extends Points> ClusteringStrategy<P> isodata(
            int startClusters, int desiredClusters, double errorLimit, int maxIteration,
            InitialCenters<P> initialCenters, ReplaceEmptyCluster<P> replaceEmptyCluster) {
        return new ClusteringStrategy<>() {
            @Override
            public void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable {
                Isodata.cluster(
                        startClusters,
                        desiredClusters,
                        context,
                        continuation,
                        errorLimit,
                        initialCenters,
                        maxIteration,
                        points,
                        replaceEmptyCluster);
            }

            @Override
            public void log(Map<String, Object> log) {
                log.put("type", "isodata");
                log.put("error-liit", errorLimit);
                log.put("start-clusters", startClusters);
                log.put("desired-clusters", desiredClusters);
                log.put("error-limit", errorLimit);
                log.put("max-iteration", maxIteration);
            }
        };
    }

    static <P extends Points> ClusteringStrategy<P> kMeans(
            int clusters, double errorLimit, InitialCenters<P> initialCenters, int maxIterations,
            ReplaceEmptyCluster<P> replaceEmptyCluster) {
        return new ClusteringStrategy<>() {
            @Override
            public void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable {
                KMeans.cluster(
                        clusters,
                        context,
                        continuation,
                        errorLimit,
                        initialCenters,
                        maxIterations,
                        points,
                        replaceEmptyCluster);
            }

            @Override
            public void log(Map<String, Object> log) throws Throwable {
                log.put("type", "k-means");
                log.put("clusters", clusters);
                log.put("error-limit", errorLimit);
                Log.logField("initial-centers", initialCenters, log);
                log.put("max-iterations", maxIterations);
                Log.logField("replace-empty-cluster", replaceEmptyCluster, log);
            }
        };
    }

    static <P extends Points> ClusteringStrategy<P> otsuCircular(int bins, int clusters) {
        return new ClusteringStrategy<>() {
            @Override
            public void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable {
                Otsu.circular(context, bins, clusters, points, continuation);
            }

            @Override
            public void log(Map<String, Object> log) {
                log.put("type", "otsu-circular");
                log.put("bins", bins);
                log.put("clusters", clusters);
            }
        };
    }

    static <P extends Points> ClusteringStrategy<P> otsuLinear(int bins, int clusters) {
        return new ClusteringStrategy<>() {
            @Override
            public void cluster(Context context, P points, Continuation<Clusters> continuation) throws Throwable {
                Otsu.linear(context, bins, clusters, points, continuation);
            }

            @Override
            public void log(Map<String, Object> log) {
                log.put("type", "otsu-linear");
                log.put("bins", bins);
                log.put("clusters", clusters);
            }
        };
    }
}
