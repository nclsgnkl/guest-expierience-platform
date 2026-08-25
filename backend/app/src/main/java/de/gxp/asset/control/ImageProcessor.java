package de.gxp.asset;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * JDK-only image pipeline (REQ-316, no new dependency): content-type
 * verification by decoding, downscaling of anything over 2560 px longest
 * edge (bilinear, re-encoded in the source format — JPEG q 0.85, PNG
 * lossless with alpha preserved), SHA-256/size/dimensions over the STORED
 * bytes. PNG and JPEG only: both are ImageIO-verifiable; SVG is a stored-XSS
 * vector and WebP has no JDK decoder.
 */
final class ImageProcessor {

    static final int MAX_EDGE_PX = 2560;

    /** The stored form of an accepted upload. */
    record ProcessedImage(byte[] bytes, int width, int height, String sha256) {
    }

    private ImageProcessor() {
    }

    /**
     * ImageIO's format name for the bytes ("png"/"JPEG"/…), or {@code null}
     * when no reader recognizes them.
     */
    static String detectFormat(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            return readers.next().getFormatName().toLowerCase();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Decodes, downscales when needed and re-encodes in {@code formatName}
     * ("png" or "jpeg"). Bytes that fit within the edge cap are stored
     * exactly as uploaded (no lossy round trip).
     *
     * @return the stored image, or {@code null} when the bytes do not decode
     */
    static ProcessedImage process(byte[] uploadedBytes, String formatName) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(uploadedBytes));
        if (image == null) {
            return null;
        }
        if (image.getWidth() <= MAX_EDGE_PX && image.getHeight() <= MAX_EDGE_PX) {
            return new ProcessedImage(uploadedBytes, image.getWidth(), image.getHeight(), sha256Hex(uploadedBytes));
        }
        double scale = (double) MAX_EDGE_PX / Math.max(image.getWidth(), image.getHeight());
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));

        boolean png = "png".equals(formatName);
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight,
                png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        byte[] stored = png ? encodePng(scaled) : encodeJpeg(scaled);
        return new ProcessedImage(stored, targetWidth, targetHeight, sha256Hex(stored));
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
