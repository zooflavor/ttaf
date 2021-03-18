package dog.giraffe;

import com.github.sarxos.webcam.Webcam;
import dog.giraffe.kmeans.ReplaceEmptyCluster;
import dog.giraffe.kmeans.KMeans;
import dog.giraffe.points.ByteArrayL2Points;
import dog.giraffe.points.KDTree;
import dog.giraffe.threads.AsyncFunction;
import dog.giraffe.threads.AsyncSupplier;
import dog.giraffe.threads.Block;
import dog.giraffe.threads.Consumer;
import dog.giraffe.threads.Continuation;
import dog.giraffe.threads.Continuations;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class WebcamFrame extends JFrame {
    private class ImageConsumer implements Consumer<BufferedImage> {
        class LimitRateContinuation implements Continuation<BufferedImage> {
            private final Continuation<BufferedImage> continuation;

            public LimitRateContinuation(Continuation<BufferedImage> continuation) {
                this.continuation=continuation;
            }

            private void check() throws Throwable {
                BufferedImage image3;
                synchronized (lock) {
                    if (null==image2) {
                        running=false;
                        return;
                    }
                    image3=image2;
                    image2=null;
                }
                execute(image3);
            }

            @Override
            public void completed(BufferedImage result) throws Throwable {
                try {
                    continuation.completed(result);
                }
                finally {
                    check();
                }
            }

            @Override
            public void failed(Throwable throwable) throws Throwable {
                try {
                    continuation.failed(throwable);
                }
                finally {
                    check();
                }
            }
        }

        private final AsyncFunction<BufferedImage, BufferedImage> function;
        private final ImageComponent image;
        private BufferedImage image2;
        private final Object lock=new Object();
        private boolean running;

        public ImageConsumer(AsyncFunction<BufferedImage, BufferedImage> function, ImageComponent image) {
            this.function=function;
            this.image=image;
        }

        @Override
        public void accept(BufferedImage value) throws Throwable {
            synchronized (lock) {
                if ((null==value)
                        || running) {
                    image2=value;
                    return;
                }
                running=true;
            }
            execute(value);
        }

        private void execute(BufferedImage image) throws Throwable {
            Continuation<BufferedImage> continuation
                    =Continuations.async(
                            Continuations.consume(this.image::setImage, context.logger()),
                            context.executorGui());
            continuation=Continuations.singleRun(new LimitRateContinuation(continuation));
            continuation=Continuations.map(function, continuation);
            continuation=Continuations.async(continuation, context.executor());
            Block block=Block.supply(AsyncSupplier.constSupplier(image), continuation);
            try {
                context.executor().execute(block);
            }
            catch (Throwable throwable) {
                continuation.failed(throwable);
            }
        }
    }

    private interface Projection {
        Projection HUE=new Projection() {
            @Override
            public int dimensions() {
                return 1;
            }

            @Override
            public void project(byte[] buf, Color.Converter colorConverter, int offset, int rgb) {
                colorConverter.rgbToHslv(rgb);
                buf[offset]=(byte)Color.Converter.toInt255(colorConverter.hue/(2.0*Math.PI));
            }

            @Override
            public int rgb(Color.Converter colorConverter, Vector point) {
                colorConverter.hsvToRgb(
                        2.0*Math.PI*point.coordinate(0)/255.0, 1.0, 1.0);
                return colorConverter.toRGB();
            }
        };

        Projection RGB=new Projection() {
            @Override
            public int dimensions() {
                return 3;
            }

            @Override
            public void project(byte[] buf, Color.Converter colorConverter, int offset, int rgb) {
                buf[offset]=(byte)(rgb&0xff);
                buf[offset+1]=(byte)((rgb>>8)&0xff);
                buf[offset+2]=(byte)((rgb>>16)&0xff);
            }

            @Override
            public int rgb(Color.Converter colorConverter, Vector point) {
                return 0xff000000
                        |Color.Converter.toInt(point.coordinate(0))
                        |(Color.Converter.toInt(point.coordinate(1))<<8)
                        |(Color.Converter.toInt(point.coordinate(2))<<16);
            }
        };

        int dimensions();

        void project(byte[] buf, Color.Converter colorConverter, int offset, int rgb);

        int rgb(Color.Converter colorConverter, Vector point);
    }

    private static class WebcamGrabber implements Runnable {
        private final WebcamFrame frame;

        public WebcamGrabber(WebcamFrame frame) {
            this.frame=frame;
        }

        @Override
        public void run() {
            try {
                Webcam webcam=Webcam.getDefault();
                if (null==webcam) {
                    throw new RuntimeException("no webcam");
                }
                try {
                    Dimension viewSize=null;
                    for (Dimension dimension: webcam.getViewSizes()) {
                        if ((null==viewSize)
                                || (viewSize.height*viewSize.width<dimension.height*dimension.width)) {
                            viewSize=dimension;
                        }
                    }
                    if (null!=viewSize) {
                        webcam.setViewSize(viewSize);
                    }
                    webcam.open();
                    while (!frame.context.stopped()) {
                        BufferedImage image2=webcam.getImage();
                        if (!frame.context.stopped()) {
                            frame.context.executor().execute(()->frame.image.accept(image2));
                        }
                        sleep();
                    }
                }
                finally {
                    webcam.close();
                }
            }
            catch (Throwable throwable) {
                throwable.printStackTrace(System.err);
            }
        }

        private void sleep() throws Throwable {
            Thread.sleep(25L);
        }
    }

    private class WindowListenerImpl extends WindowAdapter {
        @Override
        public void windowClosed(WindowEvent event) {
            context.close();
        }
    }

    private static final long serialVersionUID=0L;

    private final Context context;
    private final Consumer<BufferedImage> image;

    public WebcamFrame(Context context) throws Throwable {
        super("Giraffe webcam");
        this.context=context;

        List<AsyncFunction<BufferedImage, BufferedImage>> functions=new ArrayList<>();
        functions.add(AsyncFunction.identity());
        functions.add(otsu(Collections.singletonList(
                new Pair<>(
                        (rgb)->0.2126*((rgb>>>16)&0xff)+0.7152*((rgb>>>8)&0xff)+0.0722*(rgb&0xff),
                        0xffffff))));
        functions.add(otsu(Arrays.asList(
                new Pair<>(
                        (rgb)->(rgb>>>16)&0xff,
                        0xff0000),
                new Pair<>(
                        (rgb)->(rgb>>>8)&0xff,
                        0x00ff00),
                new Pair<>(
                        (rgb)->rgb&0xff,
                        0x0000ff))));
        functions.add(kMeans(2, Projection.RGB));
        functions.add(kMeans(3, Projection.RGB));
        functions.add(kMeans(5, Projection.RGB));
        functions.add(kMeans(2, Projection.HUE));
        functions.add(kMeans(3, Projection.HUE));
        functions.add(kMeans(5, Projection.HUE));

        addWindowListener(new WindowListenerImpl());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setIconImages(Icons.icons());

        int columns=1;
        while (columns*columns<functions.size()) {
            ++columns;
        }
        JPanel panel=new JPanel(new GridLayout(0, columns));
        getContentPane().add(panel);
        List<Consumer<BufferedImage>> images=new ArrayList<>(functions.size());
        for (AsyncFunction<BufferedImage, BufferedImage> function: functions) {
            ImageComponent image=new ImageComponent();
            images.add(new ImageConsumer(function, image));
            panel.add(image);
        }
        image=Consumer.fork(images);
        pack();
        Dimension screen=getToolkit().getScreenSize();
        if (16*screen.height<9*screen.width) {
            setBounds(screen.width/16, screen.height/8, 3*screen.width/8, 3*screen.height/4);
        }
        else {
            setBounds(screen.width/8, screen.height/8, 3*screen.width/4, 3*screen.height/4);
        }
        setVisible(true);
    }

    private AsyncFunction<BufferedImage, BufferedImage> kMeans(int clusters, Projection projection) {
        return (image, continuation)->{
            int height=image.getHeight();
            int width=image.getWidth();
            int[] pixels=new int[height*width];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            Color.Converter coloConverter=new Color.Converter();
            byte[] pointsData=new byte[projection.dimensions()*height*width];
            for (int ii=0, oo=0; pixels.length>ii; ++ii, oo+=projection.dimensions()) {
                projection.project(pointsData, coloConverter, oo, pixels[ii]);
            }
            ByteArrayL2Points points=new ByteArrayL2Points(pointsData, projection.dimensions());
            KMeans.cluster(
                    clusters,
                    context,
                    Continuations.map(
                            (centers, continuation1)->{
                                byte[] buf=new byte[projection.dimensions()];
                                Vector point=new Vector(projection.dimensions());
                                int[] pixels2=new int[height*width];
                                for (int ii=0; pixels.length>ii; ++ii) {
                                    projection.project(buf, coloConverter, 0, pixels[ii]);
                                    for (int dd=0; projection.dimensions()>dd; ++dd) {
                                        point.coordinate(dd, buf[dd]&0xff);
                                    }
                                    Vector center=Distance.nearestCenter(centers, points.distance(), point);
                                    pixels2[ii]=projection.rgb(coloConverter, center);
                                }
                                BufferedImage image2
                                        =new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
                                image2.setRGB(0, 0, width, height, pixels2, 0, width);
                                continuation1.completed(image2);
                            },
                            continuation),
                    0.95,
                    1000,
                    //points,
                    KDTree.create(4096, points, Sum.PREFERRED),
                    ReplaceEmptyCluster.notNear(),
                    Sum.PREFERRED);
        };
    }

    public static void main(String[] args) throws Throwable {
        boolean error=true;
        Context threads=new SwingContext();
        try {
            new Thread(new WebcamGrabber(new WebcamFrame(threads)))
                    .start();
            error=false;
        }
        finally {
            if (error) {
                threads.close();
            }
        }
    }

    private AsyncFunction<BufferedImage, BufferedImage> otsu(List<Pair<IntToDoubleFunction, Integer>> channels) {
        return (image, continuation)->{
            int height=image.getHeight();
            int width=image.getWidth();
            int[] pixels=new int[height*width];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            List<Double> values=new ArrayList<>(pixels.length);
            int[] pixels2=new int[height*width];
            Arrays.fill(pixels2, 0xff000000);
            for (Pair<IntToDoubleFunction, Integer> channel: channels) {
                values.clear();
                for (int pixel: pixels) {
                    values.add(channel.first.applyAsDouble(pixel));
                }
                double threshold=Otsu2.threshold(512, context, Sum.PREFERRED, values);
                for (int ii=0; pixels.length>ii; ++ii) {
                    if (values.get(ii)>=threshold) {
                        pixels2[ii]|=channel.second;
                    }
                }
            }
            BufferedImage image2=new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            image2.setRGB(0, 0, width, height, pixels2, 0, width);
            continuation.completed(image2);
        };
    }
}
