package dog.giraffe.points;

import dog.giraffe.Distance;
import dog.giraffe.QuickSort;
import dog.giraffe.Sum;
import dog.giraffe.Vector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

public class KDTreeTest {
    private static class TestPoints extends L2Points<TestPoints> implements QuickSort.Swap {
        private final List<Vector> data;

        public TestPoints(List<Vector> data) {
            super(data.get(0).dimensions());
            this.data=data;
        }

        @Override
        public double get(int dimension, int index) {
            return data.get(index).coordinate(dimension);
        }

        @Override
        public TestPoints self() {
            return this;
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public List<TestPoints> split(int parts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void swap(int index0, int index1) {
            data.set(index0, data.set(index1, data.get(index0)));
        }
    }

    private static List<Vector> create(int dimensions, Random random, int size) {
        List<Vector> list=new ArrayList<>();
        for (; 0<size; --size) {
            Vector vector=new Vector(dimensions);
            for (int dd=0; dimensions>dd; ++dd) {
                vector.coordinate(dd, random.nextDouble());
            }
            list.add(vector);
        }
        return list;
    }

    @Test
    public void test() {
        for (long seed=1L; 1000L>seed; ++seed) {
            Random random=new Random(seed);
            final int dimensions=random.nextInt(4)+1;
            List<Vector> centers=Collections.unmodifiableList(create(dimensions, random, 10));
            TestPoints points=new TestPoints(create(dimensions, random, 1000));
            KDTree<TestPoints> tree=KDTree.create(random.nextInt(10)+1, points, Sum.HEAP);
            tree.classify(
                    Function.identity(),
                    centers,
                    new Points.Classification<Vector, L2Points.Distance, L2Points.Mean, KDTree<TestPoints>, Vector>() {
                        @Override
                        public void nearestCenter(Vector center, KDTree<TestPoints> points) {
                            for (int ii=0; points.size()>ii; ++ii) {
                                nearestCenter(center, points, ii);
                            }
                        }

                        @Override
                        public void nearestCenter(Vector center, KDTree<TestPoints> points, int index) {
                            Vector point=points.get(index);
                            Vector center2=Distance.nearestCenter(centers, tree.distance(), point);
                            if (!center.equals(center2)) {
                                fail();
                            }
                        }
                    });
        }
    }
}