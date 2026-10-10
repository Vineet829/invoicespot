package com.invoicespot.settings;

// How a business's PDFs look. Classic is the layout PDFs have always had;
// the others restyle it with the theme of the same name in
// templates/pdf/fragments/themes.html.
public enum PdfTemplate {
    CLASSIC(null),
    MODERN("modern"),
    COMPACT("compact");

    private final String theme;

    PdfTemplate(String theme) {
        this.theme = theme;
    }

    // The theme fragment laid over Classic, if any.
    public String theme() {
        return theme;
    }
}
