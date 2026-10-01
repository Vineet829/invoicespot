package com.invoicespot.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

// The text a PDF shows, as PDFBox reads it, so tests can check a PDF by what
// it says rather than by the HTML it was made from.
public final class PdfText {

    private PdfText() {}

    public static String of(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // Each page's text on its own.
    static List<String> pages(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            List<String> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            return pages;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // The size, in points, of the type a piece of text is set in where it
    // first appears, or -1 when the PDF doesn't show it.
    static float fontSizeOf(byte[] pdf, String text) {
        float[] size = {-1};
        try (PDDocument document = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void writeString(String line, List<TextPosition> positions) throws IOException {
                    int at = line.indexOf(text);
                    if (size[0] < 0 && at >= 0 && at < positions.size()) {
                        size[0] = positions.get(at).getFontSizeInPt();
                    }
                    super.writeString(line, positions);
                }
            }.getText(document);
            return size[0];
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
