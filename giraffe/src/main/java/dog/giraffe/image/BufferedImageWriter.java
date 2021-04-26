package dog.giraffe.image;

import dog.giraffe.points.UnsignedByteArrayPoints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class BufferedImageWriter implements ImageWriter {
    private class LineImpl implements Line {
        private final int yy;

        public LineImpl(int yy) {
            this.yy=yy;
        }

        @Override
        public void set(int dimension, int xx, double value) {
            data[dimensions*(yy*width+xx)+dimension]=(byte)value;
        }

        @Override
        public void setNormalized(int dimension, int xx, double value) {
            data[dimensions*(yy*width+xx)+dimension]=UnsignedByteArrayPoints.denormalize(value);
        }

        @Override
        public void write() {
        }
    }

    private final byte[] data;
    private final int dimensions;
    private final int height;
    private final int width;

    private BufferedImageWriter(int dimensions, int height, int width) {
        this.dimensions=dimensions;
        this.height=height;
        this.width=width;
        data=new byte[dimensions*height*width];
    }

    @Override
    public void close() throws IOException {
    }

    public static BufferedImageWriter create(int width, int height, int dimensions) {
        return new BufferedImageWriter(dimensions, height, width);
    }

    public static BufferedImageWriter createFile(int width, int height, int dimensions, String format, Path path) {
        return new BufferedImageWriter(dimensions, height, width) {
            @Override
            public void close() throws IOException {
                super.close();
                writeImage(format, path);
            }
        };
    }

    public BufferedImage createImage() {
        int[] buffer=new int[dimensions*width];
        BufferedImage image=Images.createUnsignedByte(width, height, dimensions);
        for (int ii=0, yy=0; height>yy; ++yy) {
            for (int jj=0, xx=0; width>xx; ++xx) {
                for (int dd=0; dimensions>dd; ++dd, ++ii, ++jj) {
                    buffer[jj]=data[ii]&0xff;
                }
            }
            image.getRaster().setPixels(0, yy, width, 1, buffer);
        }
        return image;
    }

    @Override
    public Line getLine(int yy) {
        return new LineImpl(yy);
    }

    public static Factory factory(String format, Path path) {
        return (width, height, dimension)->createFile(width, height, dimension, format, path);
    }

    public void writeImage(String format, Path path) throws IOException {
        if (!ImageIO.write(createImage(), format, path.toFile())) {
            throw new RuntimeException("no image writer for "+format);
        }
    }
}
