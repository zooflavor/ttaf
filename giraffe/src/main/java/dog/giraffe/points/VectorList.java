package dog.giraffe.points;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link List} of {@link Vector Vectors}.
 */
public class VectorList extends MutablePoints {
    private final List<Vector> points;

    /**
     * Creates a new instance with the vectors of points.
     */
    public VectorList(List<Vector> points) {
        super(points.get(0).dimensions());
        this.points=points;
    }

    @Override
    public void add(Vector vector) {
        points.add(vector.copy());
    }

    @Override
    public Vector get(int index) {
        return points.get(index);
    }

    @Override
    public double get(int dimension, int index) {
        return points.get(index).coordinate(dimension);
    }

    @Override
    public double getNormalized(int dimension, int index) {
        return get(dimension, index);
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public void set(int dimension, int index, double value) {
        points.get(index).coordinate(dimension, value);
    }

    @Override
    public void setNormalized(int dimension, int index, double value) {
        set(dimension, index, value);
    }

    @Override
    public int size() {
        return points.size();
    }

    @Override
    public void size(int size) {
        if (points.size()<size) {
            while (points.size()<size) {
                points.add(new Vector(dimensions));
            }
        }
        else if (points.size()>size) {
            points.subList(size, points.size()).clear();
        }
    }

    @Override
    public List<Points> split(int parts) {
        if ((2>parts)
                || (2>points.size())) {
            return Collections.singletonList(this);
        }
        parts=Math.min(parts, points.size());
        List<VectorList> result=new ArrayList<>(parts);
        for (int ii=0; parts>ii; ++ii) {
            result.add(new VectorList(
                    points.subList(ii*points.size()/parts, (ii+1)*points.size()/parts)));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public VectorList subPoints(int fromIndex, int toIndex) {
        return new VectorList(points.subList(fromIndex, toIndex));
    }

    @Override
    public void swap(int index0, int index1) {
        points.set(index0, points.set(index1, points.get(index0)));
    }
}
