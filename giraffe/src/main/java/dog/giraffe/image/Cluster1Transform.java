package dog.giraffe.image;

import dog.giraffe.ClusterColors;
import dog.giraffe.Clusters;
import dog.giraffe.Context;
import dog.giraffe.Lists;
import dog.giraffe.Sum;
import dog.giraffe.points.Distance;
import dog.giraffe.points.KDTree;
import dog.giraffe.points.MutablePoints;
import dog.giraffe.points.Vector;
import dog.giraffe.threads.Continuation;
import dog.giraffe.threads.Continuations;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Cluster1Transform implements ImageTransform {
    public interface Strategy {
        <P extends MutablePoints> void cluster(
                Context context, KDTree points, Continuation<Clusters> continuation) throws Throwable;
    }

    private class State<P extends MutablePoints, Q extends MutablePoints> {
        private List<Vector> centers;
        private Map<Vector, Vector> colorMap;
        private Clusters clusters;
        private final ImageReader<P> imageReader;
        private final Projection1<Q> projection;

        public State(ImageReader<P> imageReader, Projection1<Q> projection) {
            this.imageReader=imageReader;
            this.projection=projection;
        }

        public void prepare(Context context, Continuation<Void> continuation) throws Throwable {
            Q points=projection.createPoints(imageReader.width()*imageReader.height());
            P line=imageReader.createPoints(imageReader.width());
            for (int yy=0; imageReader.height()>yy; ++yy) {
                line.clear();
                imageReader.addLineTo(yy, line);
                projection.project(line, points);
            }
            strategy.cluster(
                    context,
                    KDTree.create(4096, points, context.sum()),
                    Continuations.map(
                            (result, continuation2)->{
                                clusters=result;
                                centers=Lists.flatten(clusters.centers);
                                colorMap=colors.colors(clusters.centers, points);
                                continuation2.completed(null);
                            },
                            continuation));
        }

        public void write(MutablePoints inputLine, ImageWriter.Line outputLine, int dimension) {
            Vector point=new Vector(projection.dimensions());
            Function<Vector, Vector> nearestCenter=(16>centers.size())
                    ?Distance.nearestCenter(centers)
                    :KDTree.nearestCenter(centers, Sum.PREFERRED);
            for (int xx=0; inputLine.size()>xx; ++xx) {
                projection.project(inputLine, xx, point);
                Vector center=nearestCenter.apply(point);
                Vector color=colorMap.get(center);
                for (int dd=0; color.dimensions()>dd; ++dd) {
                    outputLine.setNormalized(dimension+dd, xx, color.coordinate(dd));
                }
            }
        }
    }

    private final ClusterColors colors;
    private final Projection1.Factory projection;
    private State<?, ?> state;
    private final Strategy strategy;

    public Cluster1Transform(ClusterColors colors, Projection1.Factory projection, Strategy strategy) {
        this.colors=colors;
        this.projection=projection;
        this.strategy=strategy;
    }

    @Override
    public int dimensions() {
        return colors.dimensions();
    }

    @Override
    public <P extends MutablePoints> void prepare(ImageReader<P> imageReader) {
        projection.create(
                imageReader,
                new Projection1.Factory.Callback() {
                    @Override
                    public <R extends MutablePoints> void projection(Projection1<R> projection) {
                        state=new State<>(imageReader, projection);
                    }
                });
    }

    @Override
    public <P extends MutablePoints> void prepare(
            Context context, ImageReader<P> imageReader, Continuation<Void> continuation) throws Throwable {
        state.prepare(context, continuation);
    }

    @Override
    public <P extends MutablePoints> void prepare(Context context, ImageReader<P> imageReader, P inputLine) {
    }

    @Override
    public boolean prepareLines() {
        return false;
    }

    @Override
    public <P extends MutablePoints> void write(
            Context context, P inputLine, ImageWriter.Line outputLine, int dimension) {
        state.write(inputLine, outputLine, dimension);
    }
}
