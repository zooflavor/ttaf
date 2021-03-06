package dog.giraffe.cluster;

import dog.giraffe.points.Points;
import dog.giraffe.points.Vector;
import dog.giraffe.util.ColorConverter;
import dog.giraffe.util.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Assigns colors to clusters.
 */
public abstract class ClusterColors {
    private static abstract class Abstract extends ClusterColors {
        @Override
        public Map<Vector, Vector> colors(List<List<Vector>> clusters, Points points) {
            if (1>=clusters.size()) {
                throw new RuntimeException("too few clusters "+clusters.size());
            }
            Map<Vector, Double> intensity=new IdentityHashMap<>(clusters.size());
            Map<Vector, Vector> normalized=new IdentityHashMap<>(clusters.size());
            clusters.forEach((cluster)->cluster.forEach((vector)->{
                Vector vector2=vector.copy();
                double intensity2=0.0;
                for (int dd=0; vector2.dimensions()>dd; ++dd) {
                    double cc=vector2.coordinate(dd);
                    cc=(cc-points.minValue())/(points.maxValue()-points.minValue());
                    vector2.coordinate(dd, cc);
                    intensity2+=cc*cc;
                }
                intensity.put(vector, Math.sqrt(intensity2/vector2.dimensions()));
                normalized.put(vector, vector2);
            }));
            return colors(clusters, intensity, normalized);
        }

        protected abstract Map<Vector, Vector> colors(
                List<List<Vector>> clusters, Map<Vector, Double> intensity, Map<Vector, Vector> normalized);
    }

    /**
     * Assign distinct colors to all the centers in clusters.
     */
    public abstract Map<Vector, Vector> colors(List<List<Vector>> clusters, Points points);

    /**
     * Returns the number of components the colors chosen will have.
     */
    public abstract int dimensions();

    /**
     * Assigns RGB colors to centers.
     * The colors are generated by splitting the [0, 2*PI] interval to equal parts
     * and assigning the endpoints as hues to centers.
     * It will try to match the hue of the colors to the hue of the centers.
     *
     * @param blue the channel of the input image that is considered to be the blue component
     * @param green the channel of the input image that is considered to be the green component
     * @param red the channel of the input image that is considered to be the red component
     */
    public static ClusterColors falseColors(int blue, int green, int red) {
        return new Abstract() {
            @Override
            public int dimensions() {
                return 3;
            }

            @Override
            protected Map<Vector, Vector> colors(
                    List<List<Vector>> clusters, Map<Vector, Double> intensity, Map<Vector, Vector> normalized) {
                ColorConverter colorConverter=new ColorConverter();
                Map<Vector, Double> hues=new IdentityHashMap<>(normalized.size());
                normalized.forEach((vector, vector2)->{
                    colorConverter.rgbToHsvAndHsl(
                            (vector2.dimensions()>blue)?(vector2.coordinate(blue)):0.0,
                            (vector2.dimensions()>green)?(vector2.coordinate(green)):0.0,
                            (vector2.dimensions()>red)?(vector2.coordinate(red)):0.0);
                    hues.put(vector, colorConverter.hue);
                });
                Set<Double> remainingHues=new TreeSet<>();
                Map<List<Vector>, Void> remainingClusters=new TreeMap<>(Lists.lexicographicComparator());
                for (int cc=0; clusters.size()>cc; ++cc) {
                    List<Vector> centers=new ArrayList<>(clusters.get(cc));
                    centers.sort(null);
                    remainingClusters.put(centers, null);
                    remainingHues.add(2.0*Math.PI*cc/clusters.size());
                }
                Map<Vector, Vector> result=new TreeMap<>();
                while (!remainingClusters.isEmpty()) {
                    double minDistance=Double.POSITIVE_INFINITY;
                    Double minHue=null;
                    List<Vector> minCluster=null;
                    for (Double hue: remainingHues) {
                        for (List<Vector> cluster: remainingClusters.keySet()) {
                            for (Vector vector: cluster) {
                                double hh=hues.get(vector);
                                double dd=Math.min(
                                        Math.min(
                                                Math.abs(hue+2.0*Math.PI-hh),
                                                Math.abs(hue-2.0*Math.PI-hh)),
                                        Math.abs(hue-hh));
                                if (minDistance>dd) {
                                    minDistance=dd;
                                    minHue=hue;
                                    minCluster=cluster;
                                }
                            }
                        }
                    }
                    Objects.requireNonNull(minHue);
                    remainingHues.remove(minHue);
                    remainingClusters.remove(minCluster);
                    Vector hue=new Vector(3);
                    colorConverter.hsvToRgb(minHue, 1.0, 1.0);
                    hue.coordinate(0, colorConverter.blue);
                    hue.coordinate(1, colorConverter.green);
                    hue.coordinate(2, colorConverter.red);
                    for (Vector vector: minCluster) {
                        result.put(vector, hue);
                    }
                }
                return Collections.unmodifiableMap(result);
            }
        };
    }

    /**
     * Assigns gray colors of any dimension to centers.
     * The grays are generated by splitting the [0, 1] interval to equal parts
     * and assigning the endpoints to centers.
     * It will try to match the intensity of grays to the intensity of the centers.
     */
    public static ClusterColors falseGrays(int dimensions) {
        return new Abstract() {
            @Override
            public int dimensions() {
                return dimensions;
            }

            @Override
            protected Map<Vector, Vector> colors(
                    List<List<Vector>> clusters, Map<Vector, Double> intensity, Map<Vector, Vector> normalized) {
                Set<Double> remainingGrays=new TreeSet<>();
                Map<List<Vector>, Void> remainingClusters=new TreeMap<>(Lists.lexicographicComparator());
                for (int cc=0; clusters.size()>cc; ++cc) {
                    List<Vector> centers=new ArrayList<>(clusters.get(cc));
                    centers.sort(null);
                    remainingClusters.put(centers, null);
                    remainingGrays.add(1.0*cc/(clusters.size()-1));
                }
                Map<Vector, Vector> result=new IdentityHashMap<>(intensity.size());
                while (!remainingClusters.isEmpty()) {
                    double minDistance=Double.POSITIVE_INFINITY;
                    Double minGray=null;
                    List<Vector> minCluster=null;
                    for (Double gray: remainingGrays) {
                        for (List<Vector> cluster: remainingClusters.keySet()) {
                            for (Vector vector: cluster) {
                                double dd=Math.abs(gray-intensity.get(vector));
                                if (minDistance>dd) {
                                    minDistance=dd;
                                    minGray=gray;
                                    minCluster=cluster;
                                }
                            }
                        }
                    }
                    Objects.requireNonNull(minGray);
                    remainingGrays.remove(minGray);
                    remainingClusters.remove(minCluster);
                    Vector gray=new Vector(dimensions);
                    for (int dd=0; dimensions>dd; ++dd) {
                        gray.coordinate(dd, minGray);
                    }
                    for (Vector vector: minCluster) {
                        result.put(vector, gray);
                    }
                }
                return Collections.unmodifiableMap(result);
            }
        };
    }
}
