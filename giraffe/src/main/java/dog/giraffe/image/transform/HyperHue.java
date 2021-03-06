package dog.giraffe.image.transform;

import dog.giraffe.Context;
import dog.giraffe.image.Image;
import dog.giraffe.points.FloatArrayPoints;
import dog.giraffe.points.MutablePoints;
import dog.giraffe.threads.Continuation;
import java.util.Map;

/**
 * A transformation replacing every pixel of an image with its hyper-hue.
 * Hyper-hue for a vector x is defined to vector x with is coordinate for the vector (1, 1, 1, ...) zeroed out.
 */
public class HyperHue extends Image.Transform {
    private HyperHue(Image image) {
        super(image);
    }

    /**
     * Creates a new {@link HyperHue} instance.
     */
    public static Image create(Image image) {
        return new HyperHue(image);
    }

    @Override
    public MutablePoints createPoints(int dimensions, int expectedSize){
        return new FloatArrayPoints(dimensions, expectedSize);
    }

    @Override
    public void log(Map<String, Object> log) {
        log.put("type", "hyper-hue");
    }

    @Override
    protected void prepareImpl(Context context, Continuation<Dimensions> continuation) throws Throwable {
        continuation.completed(new Dimensions(image));
    }

    @Override
    public Reader reader() throws Throwable {
        return new TransformReader() {
            private final double[] temp=new double[dimensions()];

            @Override
            protected void setNormalizedLineToTransform(MutablePoints points, int offset) {
                for (int xx=0; width()>xx; ++xx, ++offset) {
                    double dotProduct=0.0;
                    for (int dd=0; dimensions()>dd; ++dd) {
                        double cc=line.getNormalized(dd, xx);
                        dotProduct+=cc;
                        temp[dd]=cc;
                    }
                    dotProduct/=dimensions();
                    for (int dd=0; dimensions()>dd; ++dd) {
                        points.setNormalized(dd, offset, 0.5+0.5*(temp[dd]-dotProduct));
                    }
                }
            }
        };
    }
}
