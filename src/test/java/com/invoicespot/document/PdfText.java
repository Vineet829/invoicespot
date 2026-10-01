package com.invoicespot.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSArray;
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

    // Text printed over other text: each pair of pieces whose glyphs cross on
    // one line of a page, as "first / second". A piece is what one Tj or TJ
    // draws, which is how a table cell's text is drawn, so a heading that
    // hangs out of its cell crosses the one beside it. The same piece painted
    // twice in one spot looks like one, so it isn't counted: openhtmltopdf
    // paints a table's headings twice like that when it breaks across pages.
    static List<String> overlaps(byte[] pdf) {
        record Piece(int page, String text, float left, float right, float baseline) {}
        List<Piece> pieces = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                private StringBuilder text;
                private float left;
                private float right;
                private float baseline;
                private boolean inArray;

                private void start() {
                    text = new StringBuilder();
                    left = Float.MAX_VALUE;
                    right = -Float.MAX_VALUE;
                }

                private void finish() {
                    if (text != null && !text.toString().isBlank()) {
                        pieces.add(new Piece(getCurrentPageNo(), text.toString(), left, right, baseline));
                    }
                    text = null;
                }

                // TJ: its strings, with the spacing between them, are one piece.
                @Override
                public void showTextStrings(COSArray array) throws IOException {
                    start();
                    inArray = true;
                    try {
                        super.showTextStrings(array);
                    } finally {
                        inArray = false;
                        finish();
                    }
                }

                @Override
                protected void showText(byte[] string) throws IOException {
                    if (inArray) {
                        super.showText(string);
                        return;
                    }
                    start();
                    super.showText(string);
                    finish();
                }

                // Spaces print nothing, so only the other glyphs set where a
                // piece starts and ends.
                @Override
                protected void processTextPosition(TextPosition glyph) {
                    if (text != null) {
                        if (text.isEmpty()) {
                            baseline = glyph.getYDirAdj();
                        }
                        text.append(glyph.getUnicode());
                        if (!glyph.getUnicode().isBlank()) {
                            left = Math.min(left, glyph.getXDirAdj());
                            right = Math.max(right, glyph.getXDirAdj() + glyph.getWidthDirAdj());
                        }
                    }
                    super.processTextPosition(glyph);
                }
            }.getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        List<String> overlaps = new ArrayList<>();
        for (int first = 0; first < pieces.size(); first++) {
            for (int second = first + 1; second < pieces.size(); second++) {
                Piece a = pieces.get(first);
                Piece b = pieces.get(second);
                boolean oneLine = a.page() == b.page() && Math.abs(a.baseline() - b.baseline()) < 1;
                boolean repainted = a.text().equals(b.text()) && Math.abs(a.left() - b.left()) < 0.1f;
                if (oneLine && !repainted && Math.min(a.right(), b.right()) - Math.max(a.left(), b.left()) > 0.1f) {
                    overlaps.add(a.text() + " / " + b.text());
                }
            }
        }
        return overlaps;
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

    // How far, in points, a piece of text starts from the end of whatever is
    // printed before it on its line, where it last appears. Closer than a
    // space, the two read as one word.
    static float gapBefore(byte[] pdf, String text) {
        // Everything the PDF shows, in the order it is drawn, and the glyph
        // behind each of its characters.
        StringBuilder shown = new StringBuilder();
        List<TextPosition> glyphs = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition glyph) {
                    shown.append(glyph.getUnicode());
                    while (glyphs.size() < shown.length()) {
                        glyphs.add(glyph);
                    }
                }
            }.getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        int at = shown.lastIndexOf(text);
        if (at < 0) {
            throw new IllegalArgumentException("The PDF doesn't show " + text);
        }
        TextPosition start = glyphs.get(at);
        float end = Float.NEGATIVE_INFINITY;
        for (TextPosition glyph : glyphs.subList(0, at)) {
            if (!glyph.getUnicode().isBlank() && Math.abs(glyph.getYDirAdj() - start.getYDirAdj()) < 0.5f) {
                end = Math.max(end, glyph.getXDirAdj() + glyph.getWidthDirAdj());
            }
        }
        return start.getXDirAdj() - end;
    }
}
