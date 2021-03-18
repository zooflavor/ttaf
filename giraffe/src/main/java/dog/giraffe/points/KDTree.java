package dog.giraffe.points;

import dog.giraffe.Doubles;
import dog.giraffe.QuickSort;
import dog.giraffe.Sum;
import dog.giraffe.Vector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;

public abstract class KDTree<P extends L2Points<P> & QuickSort.Swap> extends L2Points<KDTree<P>> {
    private static class NearestCenter {
        public Vector center;
        public double distance;
    }

    private static final DoubleBinaryOperator ADD=Double::sum;
    private static final DoubleBinaryOperator MAX=Math::max;
    private static final DoubleBinaryOperator MIN=Math::min;

    private static class Branch<P extends L2Points<P> & QuickSort.Swap> extends KDTree<P> {
        private final KDTree<P> left;
        private final KDTree<P> right;

        public Branch(KDTree<P> left, KDTree<P> right) {
            super(
                    left.dimensions,
                    perform(MAX, left.max, right.max),
                    perform(MIN, left.min, right.min),
                    left.size()+right.size(),
                    perform(ADD, left.sum, right.sum));
            this.left=left;
            this.right=right;
        }

        @Override
        public <C> void classify(
                Function<C, Vector> centerPoint, List<C> centers,
                Classification<C, Distance, Mean, KDTree<P>, Vector> classification) {
            centers=filterCenters(centerPoint, centers);
            left.classify(centerPoint, centers, classification);
            right.classify(centerPoint, centers, classification);
        }

        @Override
        public double get(int dimension, int index) {
            return (left.size()>index)
                    ?left.get(dimension, index)
                    :right.get(dimension, index-left.size());
        }

        @Override
        protected void nearestCenter(NearestCenter nearestCenter, Vector point) {
            double leftHeuristic=nearestCenterHeuristic(left, point);
            double rightHeuristic=nearestCenterHeuristic(right, point);
            if (leftHeuristic<=rightHeuristic) {
                left.nearestCenter(nearestCenter, point);
                if (nearestCenter.distance<=rightHeuristic) {
                    return;
                }
                Vector leftCenter=nearestCenter.center;
                double leftDistance=nearestCenter.distance;
                right.nearestCenter(nearestCenter, point);
                if (leftDistance<nearestCenter.distance) {
                    nearestCenter.center=leftCenter;
                    nearestCenter.distance=leftDistance;
                }
            }
            else {
                right.nearestCenter(nearestCenter, point);
                if (nearestCenter.distance<=leftHeuristic) {
                    return;
                }
                Vector rightCenter=nearestCenter.center;
                double rightDistance=nearestCenter.distance;
                left.nearestCenter(nearestCenter, point);
                if (rightDistance<nearestCenter.distance) {
                    nearestCenter.center=rightCenter;
                    nearestCenter.distance=rightDistance;
                }
            }
        }

        private double nearestCenterHeuristic(KDTree<?> tree, Vector point) {
            double distance=0.0;
            for (int dd=0; dimensions>dd; ++dd) {
                double c0=point.coordinate(dd);
                double c1=Math.max(tree.min.coordinate(dd), Math.min(tree.max.coordinate(dd), point.coordinate(dd)));
                distance+=Doubles.square(c0-c1);
            }
            return distance;
        }

        private static Vector perform(DoubleBinaryOperator operator, Vector value0, Vector value1) {
            Vector result=new Vector(value0.dimensions());
            for (int dd=0; result.dimensions()>dd; ++dd) {
                result.coordinate(dd, operator.applyAsDouble(value0.coordinate(dd), value1.coordinate(dd)));
            }
            return result;
        }

        @Override
        protected boolean split(Deque<KDTree<P>> deque) {
            deque.addFirst(right);
            deque.addFirst(left);
            return true;
        }
    }

    private static class Leaf<P extends L2Points<P> & QuickSort.Swap> extends KDTree<P> {
        private final int offset;
        private final P points;

        public Leaf(int offset, P points, int size, List<Sum> sums) {
            super(points.dimensions(),
                    points.perform(Double.NEGATIVE_INFINITY, offset, MAX, size),
                    points.perform(Double.POSITIVE_INFINITY, offset, MIN, size),
                    size,
                    points.sum(offset, size, sums));
            this.offset=offset;
            this.points=points;
        }

        @Override
        public <C> void classify(
                Function<C, Vector> centerPoint, List<C> centers,
                Classification<C, Distance, Mean, KDTree<P>, Vector> classification) {
            centers=filterCenters(centerPoint, centers);
            if (1==centers.size()) {
                classification.nearestCenter(centers.get(0), this);
            }
            else {
                super.classify(centerPoint, centers, classification);
            }
        }

        @Override
        public double get(int dimension, int index) {
            return points.get(dimension, offset+index);
        }

        @Override
        protected void nearestCenter(NearestCenter nearestCenter, Vector point) {
            Vector nc=null;
            double nd=Double.POSITIVE_INFINITY;
            for (int oo=offset, ss=size; 0<ss; ++oo, --ss) {
                Vector cc=points.get(oo);
                double dd=L2Points.DISTANCE.distance(cc, point);
                if (nd>dd) {
                    nc=cc;
                    nd=dd;
                }
            }
            nearestCenter.center=nc;
            nearestCenter.distance=nd;
        }

        @Override
        protected boolean split(Deque<KDTree<P>> deque) {
            return false;
        }
    }

    private final Vector max;
    private final Vector mean;
    private final Vector min;
    private final Vector sum;
    protected final int size;

    private KDTree(int dimensions, Vector max, Vector min, int size, Vector sum) {
        super(dimensions);
        this.max=max;
        this.min=min;
        this.size=size;
        this.sum=sum;
        mean=sum.copy();
        for (int dd=0; dimensions>dd; ++dd) {
            mean.coordinate(dd, mean.coordinate(dd)/size);
        }
    }

    @Override
    public void addAllTo(Mean mean) {
        mean.addAll(size(), sum);
    }

    public static <P extends L2Points<P> & QuickSort.Swap> KDTree<P> create(
            int maxLeafSize, P points, Sum.Factory sum) {
        List<Sum> sums=new ArrayList<>(points.dimensions());
        for (int dd=0; points.dimensions()>dd; ++dd) {
            sums.add(sum.create(maxLeafSize));
        }
        return create(0, maxLeafSize, points, sums, points.size());
    }

    private static <P extends L2Points<P> & QuickSort.Swap> KDTree<P> create(
            int from, int maxLeafSize, P points, List<Sum> sums, int to) {
        if (1>maxLeafSize) {
            throw new IllegalArgumentException(Integer.toString(maxLeafSize));
        }
        if (maxLeafSize>=to-from) {
            return new Leaf<>(from, points, to-from, sums);
        }
        int widestDimension=points.widestDimension(from, to);
        int middle=QuickSort.medianSplit(
                (index0, index1)->
                        Double.compare(points.get(widestDimension, index0), points.get(widestDimension, index1)),
                from,
                points,
                to);
        return new Branch<>(
                create(from, maxLeafSize, points, sums, middle),
                create(middle, maxLeafSize, points, sums, to));
    }

    protected <C> List<C> filterCenters(Function<C, Vector> centerPoint, List<C> centers) {
        if (1>=centers.size()) {
            return centers;
        }
        int nc=0;
        double nd=Double.POSITIVE_INFINITY;
        for (int cc=0; centers.size()>cc; ++cc) {
            double di=distance().distance(centerPoint.apply(centers.get(cc)), mean);
            if (nd>di) {
                nc=cc;
                nd=di;
            }
        }
        List<C> centers2=new ArrayList<>(centers.size());
        centers2.add(centers.get(nc));
        Vector nc2=centerPoint.apply(centers2.get(0));
        Vector ex=new Vector(dimensions);
        for (int cc=0; centers.size()>cc; ++cc) {
            if (cc==nc) {
                continue;
            }
            C cc2=centers.get(cc);
            Vector cc3=centerPoint.apply(cc2);
            for (int dd=0; dimensions>dd; ++dd) {
                ex.coordinate(dd, ((cc3.coordinate(dd)>nc2.coordinate(dd))?max:min).coordinate(dd));
            }
            if (distance().distance(nc2, ex)>distance().distance(cc3, ex)) {
                centers2.add(cc2);
            }
        }
        return Collections.unmodifiableList(centers2);
    }

    public static Function<Vector, Vector> nearestCenter(List<Vector> centers, Sum.Factory sum) {
        NearestCenter nearestCenter=new NearestCenter();
        KDTree<?> tree=KDTree.create(1, new VectorList(new ArrayList<>(centers)), sum);
        return (point)->{
            tree.nearestCenter(nearestCenter, point);
            return nearestCenter.center;
        };
    }

    protected abstract void nearestCenter(NearestCenter nearestCenter, Vector point);

    @Override
    public KDTree<P> self() {
        return this;
    }

    @Override
    public int size() {
        return size;
    }

    protected abstract boolean split(Deque<KDTree<P>> deque);

    @Override
    public List<KDTree<P>> split(int parts) {
        if (2>parts) {
            return Collections.singletonList(this);
        }
        int maxSize=size()/(4*parts);
        Deque<KDTree<P>> deque=new ArrayDeque<>();
        List<KDTree<P>> trees=new ArrayList<>();
        deque.addFirst(this);
        while (!deque.isEmpty()) {
            KDTree<P> tree=deque.removeFirst();
            if ((tree.size()<=maxSize)
                    || (!tree.split(deque))) {
                trees.add(tree);
            }
        }
        if (trees.size()<=parts) {
            return Collections.unmodifiableList(new ArrayList<>(trees));
        }
        List<KDTree<P>> trees2=new ArrayList<>(parts);
        for (int ii=0; parts>ii; ++ii) {
            trees2.add(split(ii*trees.size()/parts, (ii+1)*trees.size()/parts, trees));
        }
        return Collections.unmodifiableList(new ArrayList<>(trees2));
    }

    private KDTree<P> split(int from, int to, List<KDTree<P>> trees) {
        if (2>to-from) {
            return trees.get(from);
        }
        int middle=(from+to)/2;
        return new Branch<>(
                split(from, middle, trees),
                split(middle, to, trees));
    }
}
