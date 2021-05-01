package dog.giraffe.points;

import dog.giraffe.LexicographicComparator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class Vector implements Comparable<Vector> {
    public static final Comparator<Vector> COMPARATOR=new LexicographicComparator<>() {
        @Override
        protected int compare(Vector object1, int index1, Vector object2, int index2) {
            return Double.compare(object1.coordinate(index1), object2.coordinate(index2));
        }

        @Override
        protected int length(Vector object) {
            return object.dimensions();
        }
    };

    private final double[] coordinates;

    public Vector(double[] coordinates) {
        this.coordinates=Objects.requireNonNull(coordinates, "coordinates");
    }

   public Vector(double blue, double green, double red) {
        this(new double[3]);
        coordinates[0]=blue;
        coordinates[1]=green;
        coordinates[2]=red;
   }

    public Vector(int dimensions) {
        this(new double[dimensions]);
    }

    public Vector add(Vector other) {
        final int dim = dimensions();
        double[] d = new double[dim];
        for (int i=0;i<dim;++i) d[i] = this.coordinates[i] + other.coordinates[i];
        return new Vector(d);
    }

    @Override
    public int compareTo(Vector vector) {
        return COMPARATOR.compare(this, vector);
    }

    public double coordinate(int dimension) {
        return coordinates[dimension];
    }

    public void coordinate(int dimension, double coordinate) {
        coordinates[dimension]=coordinate;
    }

    public void clear() {
        for (int dd=0; dimensions()>dd; ++dd) {
            coordinate(dd, 0.0);
        }
    }

    public Vector copy() {
        return new Vector(Arrays.copyOf(coordinates, coordinates.length));
    }

    public int dimensions() {
        return coordinates.length;
    }

    public Vector div(double divisor) {
        final int dim = dimensions();
        double[] d = new double[dim];
        for (int i=0;i<dim;++i) d[i] = this.coordinates[i] / divisor;
        return new Vector(d);
    }

    @Override
    public boolean equals(Object obj) {
        if (this==obj) {
            return true;
        }
        if ((null==obj)
                || (!getClass().equals(obj.getClass()))) {
            return false;
        }
        return Arrays.equals(coordinates, ((Vector)obj).coordinates);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(coordinates);
    }

    public Vector mul(double multiplier) {
        final int dim = dimensions();
        double[] d = new double[dim];
        for (int i=0;i<dim;++i) d[i] = multiplier * this.coordinates[i];
        return new Vector(d);
    }

    public double normSquared() {
        double result=0.0;
        for (double value: coordinates) {
            result+=value*value;
        }
        return result;
    }

    public Vector pow() {
        final int dim = dimensions();
        double[] d = new double[dim];
        for (int i=0;i<dim;++i) d[i] = Math.pow(this.coordinates[i], 2);
        return new Vector(d);
    }

    public Vector sub(Vector other) {
        final int dim = dimensions();
        double[] d = new double[dim];
        for (int i=0;i<dim;++i) d[i] = this.coordinates[i] - other.coordinates[i];
        return new Vector(d);
    }

    public Vector sqrt() {
        final int dim = dimensions();
        double[] d = new double[dim];
        for (int i=0;i<dim;++i) d[i] = Math.sqrt(this.coordinates[i]);
        return new Vector(d);
    }

    @Override
    public String toString() {
        return Arrays.toString(coordinates);
    }
}
