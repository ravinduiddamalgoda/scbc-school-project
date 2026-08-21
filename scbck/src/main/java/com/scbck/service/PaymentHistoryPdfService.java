package com.scbck.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.scbck.dto.FeePosition;
import com.scbck.dto.PaymentResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.SchoolProfile;

/**
 * A student's fee history as a page the office can hand across the counter.
 *
 * The question it answers - "what has this family paid, and what is left" - was
 * previously answered by scrolling a table and adding up by eye, which is both
 * slow and the sort of arithmetic that gets disputed. Printing it puts the
 * total, the fee and the balance on the same sheet as the receipts they are
 * derived from.
 */
@Service
public class PaymentHistoryPdfService {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color RULE = new Color(0x9C, 0xA3, 0xAF);
    private static final Color HEAD = new Color(0xF3, 0xF4, 0xF6);

    private final Font schoolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, INK);
    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11.5f, INK);
    private final Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 10, INK);
    private final Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, INK);
    private final Font tableFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
    private final Font tableHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);

    public byte[] render(FeePosition position) {
        Document document = new Document(PageSize.A4, 48, 48, 44, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Payment History - " + orBlank(position.studentName()));
            document.addCreator(SchoolProfile.NAME_WITH_CITY);
            document.open();

            centred(document, SchoolProfile.NAME_WITH_CITY, schoolFont);
            centred(document, "Census No. " + SchoolProfile.CENSUS_NO
                    + "   |   School No. " + SchoolProfile.SCHOOL_ID, labelFont);
            centred(document, "Student Payment History", titleFont);

            writeStudent(document, position);
            writeReceipts(document, position.payments());
            writeTotals(document, position);
            writeFooter(document);

            document.close();
            return out.toByteArray();

        } catch (DocumentException error) {
            throw ApiException.badRequest("The payment history could not be written: "
                    + error.getMessage());
        }
    }

    public String fileNameFor(FeePosition position) {
        String who = position.admissionNo() == null ? position.studentName() : position.admissionNo();
        return ("Payment History - " + orText(who, "Student"))
                .replaceAll("[\\\\/:*?\"<>|]", "-") + ".pdf";
    }

    // -------------------------------------------------------------------------

    private void writeStudent(Document document, FeePosition position) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(18);
        table.setSpacingAfter(14);
        table.setWidths(new float[] { 1.3f, 2.4f, 1.3f, 2.4f });

        pair(table, "Admission No.", orBlank(position.admissionNo()));
        pair(table, "Name", orBlank(position.studentName()));
        pair(table, "Grade", position.grade() == null ? "-" : position.grade().name());
        pair(table, "Class", position.classroom() == null ? "-" : position.classroom().name());
        pair(table, "Academic year",
                position.academicYear() == null ? "-" : position.academicYear().name());
        pair(table, "Printed", LocalDate.now().format(LONG_DATE));

        document.add(table);
    }

    private void writeReceipts(Document document, List<PaymentResponse> payments)
            throws DocumentException {

        if (payments == null || payments.isEmpty()) {
            Paragraph none = new Paragraph("No payments have been recorded for this student.",
                    labelFont);
            none.setSpacingAfter(14);
            document.add(none);
            return;
        }

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        table.setWidths(new float[] { 1.5f, 1.8f, 1.6f, 1.4f, 1.8f, 1.6f });

        for (String heading : List.of("Receipt No.", "Date", "Year", "Grade", "Method", "Amount")) {
            PdfPCell cell = new PdfPCell(new Phrase(heading, tableHeadFont));
            cell.setBackgroundColor(HEAD);
            cell.setBorderColor(RULE);
            cell.setPadding(5);
            table.addCell(cell);
        }

        for (PaymentResponse payment : payments) {
            cell(table, orBlank(payment.billNo()), Element.ALIGN_LEFT);
            cell(table, payment.paidDate() == null ? "-" : payment.paidDate().format(LONG_DATE),
                    Element.ALIGN_LEFT);
            cell(table, payment.academicYear() == null ? "-" : payment.academicYear().name(),
                    Element.ALIGN_LEFT);
            cell(table, payment.grade() == null ? "-" : payment.grade().name(), Element.ALIGN_LEFT);
            cell(table, payment.paymentType() == null ? "-" : payment.paymentType().name(),
                    Element.ALIGN_LEFT);
            cell(table, money(payment.amountPaid()), Element.ALIGN_RIGHT);
        }

        document.add(table);
    }

    private void writeTotals(Document document, FeePosition position) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(52);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[] { 1.6f, 1f });

        // The fee is only shown when the school has set one. A blank is honest;
        // a zero would read as "nothing to pay".
        total(table, "Fee for the year",
                position.annualFee() == null ? "not set" : money(position.annualFee()), false);
        total(table, "Total paid", money(position.totalPaid()), false);
        total(table, "Balance",
                position.balance() == null ? "-" : money(position.balance()), true);

        document.add(table);

        if (position.feeNote() != null && !position.feeNote().isBlank()) {
            Paragraph note = new Paragraph(position.feeNote(), labelFont);
            note.setSpacingBefore(10);
            document.add(note);
        }
    }

    private void writeFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "This statement is issued for information and is not a receipt.", labelFont);
        footer.setSpacingBefore(34);
        document.add(footer);

        Paragraph signature = new Paragraph(
                "...............................................\nBursar / Office", labelFont);
        signature.setSpacingBefore(38);
        document.add(signature);
    }

    // -------------------------------------------------------------------------

    private void pair(PdfPTable table, String label, String value) {
        PdfPCell key = new PdfPCell(new Phrase(label, labelFont));
        key.setBorder(0);
        key.setPaddingBottom(5);
        table.addCell(key);

        PdfPCell cell = new PdfPCell(new Phrase(value, valueFont));
        cell.setBorder(0);
        cell.setPaddingBottom(5);
        table.addCell(cell);
    }

    private void total(PdfPTable table, String label, String value, boolean emphasise) {
        Font font = emphasise ? valueFont : labelFont;

        PdfPCell key = new PdfPCell(new Phrase(label, font));
        key.setBorderColor(RULE);
        key.setPadding(5);
        if (emphasise) {
            key.setBackgroundColor(HEAD);
        }
        table.addCell(key);

        PdfPCell amount = new PdfPCell(new Phrase(value, font));
        amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amount.setBorderColor(RULE);
        amount.setPadding(5);
        if (emphasise) {
            amount.setBackgroundColor(HEAD);
        }
        table.addCell(amount);
    }

    private void cell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, tableFont));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(RULE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void centred(Document document, String text, Font font) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "-" : MONEY.format(amount);
    }

    private static String orBlank(String value) {
        return orText(value, "-");
    }

    private static String orText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
