package com.invoicespot.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.common.QrText;
import com.invoicespot.document.PdfText;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class UpiQrIntegrationTest extends IntegrationTest {

    private User owner;

    @BeforeEach
    void paidByUpi() throws Exception {
        owner = createUser();
        mockMvc.perform(put("/api/v1/settings/payment-details")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountHolder\": \"Ada & Co\", \"upiId\": \"ada@okhdfcbank\"}"))
                .andExpect(status().isOk());
    }

    private String invoice(String currency, int unitPrice) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "%s",
                                 "billingItems": [{"itemName": "Design", "unitPrice": %d, "quantity": 2}]}
                                """.formatted(
                                        (String) JsonPath.read(customer, "$.createdCustomer._id"),
                                        currency,
                                        unitPrice)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private String shared(String id) throws Exception {
        String url = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", id)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        return mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private byte[] pdf(String id) throws Exception {
        return mockMvc.perform(get("/api/v1/document/{id}/pdf", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    // What every image in the PDF says as a QR code, page by page, looking
    // inside forms too.
    private static List<String> qrCodes(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            List<String> codes = new ArrayList<>();
            for (PDPage page : document.getPages()) {
                collect(page.getResources(), codes);
            }
            return codes;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void collect(PDResources resources, List<String> codes) throws IOException {
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDImageXObject image) {
                codes.add(QrText.of(image.getImage()));
            } else if (object instanceof PDFormXObject form) {
                collect(form.getResources(), codes);
            }
        }
    }

    private static String text(byte[] pdf) {
        return PdfText.of(pdf).replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("a rupee invoice's PDF has a QR code that reads as its share page's UPI link for the balance")
    void rupees() throws Exception {
        String id = invoice("INR", 750);

        String upi = JsonPath.read(shared(id), "$.payment.upi.uri");
        assertThat(upi).isEqualTo(
                "upi://pay?pa=ada@okhdfcbank&pn=Ada%20%26%20Co&am=1500.00&cu=INR&tn=Invoice%20INV-0001");
        byte[] pdf = pdf(id);
        assertThat(qrCodes(pdf)).containsExactly(upi);
        assertThat(text(pdf)).contains("Scan to pay INR 1,500.00 with any UPI app");

        mockMvc.perform(post("/api/v1/document/{id}/payment", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 500}"))
                .andExpect(status().isCreated());
        String rest = JsonPath.read(shared(id), "$.payment.upi.uri");
        assertThat(rest).contains("&am=1000.00&");
        assertThat(qrCodes(pdf(id))).containsExactly(rest);
    }

    @Test
    @DisplayName("a dollar invoice has no UPI code, and over a lakh the payer enters the amount")
    void otherwise() throws Exception {
        String dollars = invoice("USD", 750);
        String json = shared(dollars);
        assertThat((Object) JsonPath.read(json, "$.payment.upi")).isNull();
        assertThat(qrCodes(pdf(dollars))).isEmpty();

        String large = invoice("INR", 60_000);
        String upi = JsonPath.read(shared(large), "$.payment.upi.uri");
        assertThat(upi).doesNotContain("am=");
        assertThat((Boolean) JsonPath.read(shared(large), "$.payment.upi.amountIncluded")).isFalse();
        byte[] pdf = pdf(large);
        assertThat(qrCodes(pdf)).containsExactly(upi);
        assertThat(text(pdf)).contains("Scan with any UPI app and enter the amount. UPI limits apply");
    }
}
