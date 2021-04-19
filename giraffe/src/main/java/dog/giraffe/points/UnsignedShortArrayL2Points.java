package dog.giraffe.points;

import dog.giraffe.Vector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sub-points are only mutable as far as swap goes.
 */
public class UnsignedShortArrayL2Points extends L2Points.Mutable<UnsignedShortArrayL2Points> {
    private short[] data;
    private final int offset;
    private int size;

    public UnsignedShortArrayL2Points(short[] data, int dimensions, int offset, int size) {
        super(dimensions);
        this.data=data;
        this.offset=offset;
        this.size=size;
    }

    public UnsignedShortArrayL2Points(short[] data, int dimensions) {
        this(data, dimensions, 0, data.length/dimensions);
    }

    public UnsignedShortArrayL2Points(int dimensions, int expectedSize) {
        this(new short[dimensions*expectedSize], dimensions, 0, 0);
    }

    public void add(short coordinate0) {
        ensureSize(size+1);
        data[dimensions*size]=coordinate0;
        ++size;
    }

    public void add(short coordinate0, short coordinate1) {
        ensureSize(size+1);
        data[dimensions*size]=coordinate0;
        data[dimensions*size+1]=coordinate1;
        ++size;
    }

    public void add(short coordinate0, short coordinate1, short coordinate2) {
        ensureSize(size+1);
        data[dimensions*size]=coordinate0;
        data[dimensions*size+1]=coordinate1;
        data[dimensions*size+2]=coordinate2;
        ++size;
    }

    public void add(short[] vector) {
        ensureSize(size+1);
        System.arraycopy(vector, 0, data, dimensions*size, dimensions);
        ++size;
    }

    @Override
    public void add(Vector vector) {
        ensureSize(size+1);
        for (int dd=0; dimensions>dd; ++dd) {
            data[dimensions*size+dd]=(short)vector.coordinate(dd);
        }
        ++size;
    }

    @Override
    public void addNormalized(Vector vector) {
        ensureSize(size+1);
        for (int dd=0; dimensions>dd; ++dd) {
            data[dimensions*size+dd]=denormalize(vector.coordinate(dd));
        }
        ++size;
    }

    @Override
    public void addTo(UnsignedShortArrayL2Points points, int from, int to) {
        int length=to-from;
        points.ensureSize(points.size+length);
        System.arraycopy(
                data, dimensions*(offset+from),
                points.data, dimensions*points.size,
                dimensions*length);
        points.size+=length;
    }

    @Override
    public void clear(int size) {
        ensureSize(size);
        this.size=size;
        Arrays.fill(data, 0, dimensions*size, (short)0);
    }

    public static short denormalize(double value) {
        return (short)Math.max(0L, Math.min(65535L, Math.round(65535.0*value)));
    }

    private void ensureSize(int newSize) {
        if (dimensions*newSize>data.length) {
            data=Arrays.copyOf(data, Math.max(dimensions*newSize, 2*data.length));
        }
    }

    @Override
    public double get(int dimension, int index) {
        return data[dimensions*(offset+index)+dimension]&0xffff;
    }

    @Override
    public double getNormalized(int dimension, int index) {
        return get(dimension, index)/65535.0;
    }

    @Override
    public double maxValue() {
        return 65535.0;
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public UnsignedShortArrayL2Points self() {
        return this;
    }

    public void set(int dimension, int index, short value) {
        data[dimension+dimensions*index]=value;
    }

    @Override
    public void set(int dimension, int index, double value) {
        data[dimension+dimensions*index]=(short)value;
    }

    @Override
    public void setNormalized(int dimension, int index, double value) {
        data[dimension+dimensions*index]=denormalize(value);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public List<UnsignedShortArrayL2Points> split(int parts) {
        if ((2>parts)
                || (2>size())) {
            return Collections.singletonList(this);
        }
        parts=Math.min(parts, size());
        List<UnsignedShortArrayL2Points> result=new ArrayList<>(parts);
        for (int ii=0; parts>ii; ++ii) {
            result.add(subPoints(ii*size/parts, (ii+1)*size/parts));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public UnsignedShortArrayL2Points subPoints(int fromIndex, int toIndex) {
        return new UnsignedShortArrayL2Points(data, dimensions, offset+fromIndex, toIndex-fromIndex);
    }

    @Override
    public void swap(int index0, int index1) {
        index0=dimensions*(offset+index0);
        index1=dimensions*(offset+index1);
        for (int dd=dimensions; 0<dd; --dd, ++index0, ++index1) {
            short temp=data[index0];
            data[index0]=data[index1];
            data[index1]=temp;
        }
    }
}