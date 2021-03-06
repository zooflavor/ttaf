package dog.giraffe.image.transform;

import dog.giraffe.Context;
import dog.giraffe.image.Image;
import dog.giraffe.points.MutablePoints;
import dog.giraffe.threads.Continuation;
import java.util.Map;

/**
 * A transformation replacing every pixel of an input image with the normalized difference vegetation index
 * of the first two components.
 */
public class NormalizedDifferenceVegetationIndex extends Image.Transform {
    private NormalizedDifferenceVegetationIndex(Image image) {
        super(image);
    }

    /**
     * Creates a new {@link NormalizedDifferenceVegetationIndex} instance.
     */
    public static Image create(Image image) {
        return new NormalizedDifferenceVegetationIndex(image);
    }

    @Override
    public void log(Map<String, Object> log) {
        log.put("type", "normalized-difference-vegetation-index");
    }

    @Override
    protected void prepareImpl(Context context, Continuation<Dimensions> continuation) throws Throwable {
        if (image.dimensions()<2) {
            throw new RuntimeException(String.format(
                    "not enough dimensions. image: %1$d, selected: 2",
                    image.dimensions()));
        }
        continuation.completed(new Dimensions(2, image.height(), image.width()));
    }

    @Override
    public Reader reader() throws Throwable {
        return new TransformReader() {
            @Override
            protected void setNormalizedLineToTransform(MutablePoints points, int offset) {
                for (int xx=0; width()>xx; ++xx, ++offset) {
                    double nir=line.getNormalized(0, xx);
                    double red=line.getNormalized(1, xx);
                    double value;
                    if (0.0==nir+red) {
                        value=0.0;
                    }
                    else {
                        value=(nir-red)/(nir+red);
                    }
                    if (0.0<=value) {
                        points.setNormalized(0, offset, 0.0);
                        points.setNormalized(1, offset, value);
                    }
                    else {
                        points.setNormalized(0, offset, -value);
                        points.setNormalized(1, offset, 0.0);
                    }
                }
            }
        };
    }
}
