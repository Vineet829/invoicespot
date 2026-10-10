package com.invoicespot.common;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;

// What a QR code says, read back the way a scanner would, so tests check a
// code by what it holds.
public final class QrText {

    private QrText() {}

    public static String of(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        try {
            return new QRCodeReader().decode(bitmap).getText();
        } catch (NotFoundException | ChecksumException | FormatException exception) {
            throw new AssertionError("No QR code could be read from the image", exception);
        }
    }

    public static String ofDataUri(String dataUri) {
        byte[] png = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
        try {
            return of(ImageIO.read(new ByteArrayInputStream(png)));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
