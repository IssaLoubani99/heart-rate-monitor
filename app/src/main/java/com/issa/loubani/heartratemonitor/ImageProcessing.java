package com.issa.loubani.heartratemonitor;

/**
 * This abstract class is used to process images.
 *
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public abstract class ImageProcessing {

    public static final int RED = 0;
    public static final int GREEN = 1;
    public static final int BLUE = 2;

    private static int[] decodeYUV420SPtoRGBSum(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return null;

        final int frameSize = width * height;

        int sumR = 0;
        int sumG = 0;
        int sumB = 0;
        int[] sumsOfAll = new int[3]; // hold all colors sum

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv420sp[yp]) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    if (uvp + 1 < yuv420sp.length) {
                        v = (0xff & yuv420sp[uvp++]) - 128;
                        u = (0xff & yuv420sp[uvp++]) - 128;
                    }

                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                int pixel = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;
                sumR += red;
                sumG += green;
                sumB += blue;
            }
        }
        sumsOfAll[RED] = sumR;
        sumsOfAll[GREEN] = sumG;
        sumsOfAll[BLUE] = sumB;

        return sumsOfAll;
    }

    /**
     * Given a byte array representing a yuv420sp image, determine the average
     * amount of red in the image. Note: returns 0 if the byte array is NULL.
     *
     * @param yuv420sp Byte array representing a yuv420sp image
     * @param width    Width of the image.
     * @param height   Height of the image.
     * @return int representing the average amount of red in the image.
     */
    public static int[] decodeYUV420SPtoRGBAvg(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return null;

        final int frameSize = width * height;

        int[] sum = decodeYUV420SPtoRGBSum(yuv420sp, width, height);
        for (int i = 0; i < sum.length; i++) {
            sum[i] = sum[i] / frameSize;
        }
        return sum;
    }

    public static int decodeYUV420SPtoRedAvg(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = decodeYUV420SPtoRGBSum(yuv420sp, width, height)[RED];
        return (sum / frameSize);
    }

    public static int decodeYUV420SPtoGreenAvg(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = decodeYUV420SPtoRGBSum(yuv420sp, width, height)[GREEN];
        return (sum / frameSize);
    }

    public static int decodeYUV420SPtoBlueAvg(byte[] yuv420sp, int width, int height) {
        if (yuv420sp == null) return 0;

        final int frameSize = width * height;

        int sum = decodeYUV420SPtoRGBSum(yuv420sp, width, height)[BLUE];
        return (sum / frameSize);
    }

}
