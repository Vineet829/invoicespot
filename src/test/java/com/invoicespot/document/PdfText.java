package com.invoicespot.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
}
