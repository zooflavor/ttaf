package dog.giraffe.image;

import dog.giraffe.Vector;
import dog.giraffe.points.FloatArrayL2Points;
import dog.giraffe.points.L2Points;

public interface Projection1<P extends L2Points<P>> {
    interface Factory {
        interface Callback {
            <P extends L2Points.Mutable<P>> void projection(Projection1<P> projection);
        }

        <P extends L2Points.Mutable<P>>void create(ImageReader<P> imageReader, Callback callback);
    }

    P createPoints(int expectedSize);

    int dimensions();

    static Factory multidimensionalHue(int... selectedDimensions) {
        return new Factory() {
            @Override
            public <P extends L2Points.Mutable<P>> void create(ImageReader<P> imageReader, Callback callback) {
                callback.projection(new Projection1<FloatArrayL2Points>() {
                    @Override
                    public FloatArrayL2Points createPoints(int expectedSize) {
                        return new FloatArrayL2Points(selectedDimensions.length, expectedSize);
                    }

                    @Override
                    public int dimensions() {
                        return selectedDimensions.length;
                    }

                    @Override
                    public void project(L2Points<?> input, FloatArrayL2Points output) {
                        Vector vector=new Vector(selectedDimensions.length);
                        for (int ii=0; input.size()>ii; ++ii) {
                            project(input, ii, vector);
                            output.add(vector);
                        }
                    }

                    @Override
                    public void project(L2Points<?> input, int index, Vector output) {
                        double dotProduct=0.0;
                        for (int dd=0; selectedDimensions.length>dd; ++dd) {
                            double cc=(input.get(selectedDimensions[dd], index)-input.minValue())
                                    /(input.maxValue()-input.minValue());
                            dotProduct+=cc;
                            output.coordinate(dd, cc);
                        }
                        dotProduct/=selectedDimensions.length;
                        for (int dd=0; selectedDimensions.length>dd; ++dd) {
                            output.coordinate(dd, 0.5+0.5*(output.coordinate(dd)-dotProduct));
                        }
                    }
                });
            }
        };
    }

    static Factory multidimensionalHueNormalized(double maxZero, int... selectedDimensions) {
        return new Factory() {
            @Override
            public <P extends L2Points.Mutable<P>> void create(ImageReader<P> imageReader, Callback callback) {
                callback.projection(new Projection1<FloatArrayL2Points>() {
                    @Override
                    public FloatArrayL2Points createPoints(int expectedSize) {
                        return new FloatArrayL2Points(selectedDimensions.length, expectedSize);
                    }

                    @Override
                    public int dimensions() {
                        return selectedDimensions.length;
                    }

                    @Override
                    public void project(L2Points<?> input, FloatArrayL2Points output) {
                        Vector vector=new Vector(selectedDimensions.length);
                        for (int ii=0; input.size()>ii; ++ii) {
                            project(input, ii, vector);
                            output.add(vector);
                        }
                    }

                    @Override
                    public void project(L2Points<?> input, int index, Vector output) {
                        double dotProduct=0.0;
                        for (int dd=0; selectedDimensions.length>dd; ++dd) {
                            double cc=(input.get(selectedDimensions[dd], index)-input.minValue())
                                    /(input.maxValue()-input.minValue());
                            dotProduct+=cc;
                            output.coordinate(dd, cc);
                        }
                        dotProduct/=selectedDimensions.length;
                        double length=0.0;
                        for (int dd=0; selectedDimensions.length>dd; ++dd) {
                            double cc=output.coordinate(dd)-dotProduct;
                            length+=cc*cc;
                            output.coordinate(dd, cc);
                        }
                        length=Math.sqrt(length);
                        if (maxZero>length) {
                            for (int dd=0; selectedDimensions.length>dd; ++dd) {
                                output.coordinate(dd, 0.5);
                            }
                        }
                        else {
                            for (int dd=0; selectedDimensions.length>dd; ++dd) {
                                output.coordinate(dd, 0.5+0.5*output.coordinate(dd)/length);
                            }
                        }
                    }
                });
            }
        };
    }

    void project(L2Points<?> input, P output);

    void project(L2Points<?> input, int index, Vector output);

    static Factory select(int... selectedDimensions) {
        return new Factory() {
            @Override
            public <P extends L2Points.Mutable<P>> void create(ImageReader<P> imageReader, Callback callback) {
                callback.projection(new Projection1<P>() {
                    @Override
                    public P createPoints(int expectedSize) {
                        return imageReader.createPoints(selectedDimensions.length, expectedSize);
                    }

                    @Override
                    public int dimensions() {
                        return selectedDimensions.length;
                    }

                    @Override
                    public void project(L2Points<?> input, P output) {
                        Vector vector=new Vector(selectedDimensions.length);
                        for (int ii=0; input.size()>ii; ++ii) {
                            project(input, ii, vector);
                            output.add(vector);
                        }
                    }

                    @Override
                    public void project(L2Points<?> input, int index, Vector output) {
                        for (int dd=0; selectedDimensions.length>dd; ++dd) {
                            output.coordinate(dd, input.get(selectedDimensions[dd], index));
                        }
                    }
                });
            }
        };
    }
}