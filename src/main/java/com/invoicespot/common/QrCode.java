package com.invoicespot.common;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

// A QR code as a PNG data URI: medium error correction, a quiet zone of four
// modules, 5 pixels a module, black on white. The text goes in as it is, with
// no character set marker, which some scanners trip on, so it should be
// plain ASCII.
public final class QrCode {

    private static final int MODULE_PIXELS = 5;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private QrCode() {}

    public static String dataUri(String text) {
        BitMatrix modules;
        try {
            modules = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 4));
        } catch (WriterException exception) {
            throw new IllegalStateException("The QR code could not be made", exception);
        }
        int width = modules.getWidth() * MODULE_PIXELS;
        int height = modules.getHeight() * MODULE_PIXELS;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, modules.get(x / MODULE_PIXELS, y / MODULE_PIXELS) ? BLACK : WHITE);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", png);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
    }
}
