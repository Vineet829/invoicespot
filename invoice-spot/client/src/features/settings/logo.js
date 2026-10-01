// PDFs print a logo only when it is a PNG, JPEG or GIF uploaded here (the
// server's rule, in PdfService.logo): a WebP from before, or a link to another
// site, is left off every PDF.
export const printsOnPdfs = (logo) =>
  !logo?.trim() || /^\/uploads\/[^/]+\.(png|jpe?g|gif)$/i.test(logo.trim());
