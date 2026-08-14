package com.scbck.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
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
import com.scbck.exception.ApiException;
import com.scbck.model.StudentCertificate;

/**
 * Renders an issued certificate to PDF, in the format the school uses.
 *
 * The leaving certificate follows the Ministry's "Student Performance Record"
 * exactly - the same eighteen numbered items, in the same order, because it is
 * a form a receiving school reads by position. The character certificate is a
 * letter, so it is set as one.
 *
 * Both print from the stored record rather than from the student, which is what
 * makes a reprint identical to the original.
 */
@Service
public class CertificatePdfService {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final String SCHOOL = "Sri Chandananda Buddhist College, Kandy";

    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color RULE = new Color(0x9C, 0xA3, 0xAF);

    private final Font schoolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, INK);
    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
    private final Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8.5f, INK);
    private final Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, INK);
    private final Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, INK);
    private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, INK);

    public byte[] render(StudentCertificate certificate) {
        Document document = new Document(PageSize.A4, 54, 54, 46, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle(titleOf(certificate));
            document.addCreator(SCHOOL);
            document.open();

            if (StudentCertificate.CHARACTER.equals(certificate.getType())) {
                writeCharacter(document, certificate);
            } else {
                writeLeaving(document, certificate);
            }

            document.close();
            return out.toByteArray();

        } catch (DocumentException error) {
            throw ApiException.badRequest("The certificate could not be written: " + error.getMessage());
        }
    }

    public String fileNameFor(StudentCertificate certificate) {
        String kind = StudentCertificate.CHARACTER.equals(certificate.getType())
                ? "Character Certificate"
                : "Leaving Certificate";

        return (kind + " " + orBlank(certificate.getStudentName()))
                .replaceAll("[^A-Za-z0-9 .-]", " ")
                .replaceAll("\\s+", " ")
                .trim() + ".pdf";
    }

    // ---- Leaving -------------------------------------------------------------

    /**
     * The Ministry form. Item numbers are printed because the receiving school
     * reads it by number, not by heading.
     */
    private void writeLeaving(Document document, StudentCertificate certificate) throws DocumentException {
        centred(document, "Ministry of Education", schoolFont);
        centred(document, "Student Performance Record", titleFont);

        Paragraph instruction = new Paragraph(
                "This Student Performance Record shall be maintained by the principal of the school attended "
                        + "by the student. When the student leaves the school, this record must be duly completed "
                        + "by the principal and handed over to the student's father, mother, or legal guardian. It "
                        + "is especially important to ensure that accurate and correct information is provided for "
                        + "every item in this record.",
                noteFont);
        instruction.setAlignment(Element.ALIGN_JUSTIFIED);
        instruction.setSpacingBefore(10);
        instruction.setSpacingAfter(14);
        document.add(instruction);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 0.7f, 6.5f, 6.5f });

        int item = 1;
        item = row(table, item, "Student's Name in full (as stated in the Birth Certificate)",
                certificate.getStudentName());
        item = row(table, item, "Name with Initials (as recorded in the School Admission Register)",
                certificate.getNameWithInitials());
        item = row(table, item, "Date of Birth", date(dobOf(certificate)));
        item = row(table, item, "Religion", certificate.getReligion());
        item = row(table, item, "Full Name of the Father, Mother, or Legal Guardian",
                certificate.getGuardianName());
        item = row(table, item, "Address of the Father, Mother, or Legal Guardian",
                certificate.getGuardianAddress());
        item = row(table, item, "Name of the School", SCHOOL);
        item = row(table, item, "Date of Admission", date(certificate.getDate_of_admission()));
        item = row(table, item, "Admission Number", certificate.getAdmissionNo());
        item = row(table, item, "Date of Leaving", date(certificate.getDate_of_leaving()));
        item = row(table, item, "Reason for Leaving", certificate.getReasonForLeaving());
        item = row(table, item, "Last Grade Successfully Completed by the Student",
                certificate.getLastGradeCompleted());
        item = row(table, item, "Medium of Instruction of the Last Grade Successfully Completed",
                certificate.getMediumOfInstruction());
        item = row(table, item,
                "Last Grade Attended and the Subjects Studied, with Language Medium of Instruction",
                certificate.getSubjectsStudied());
        item = row(table, item, "Conduct and Behaviour", certificate.getConduct());
        item = row(table, item,
                "Any Weaknesses or Health Conditions Identified During a Medical Examination",
                certificate.getHealthNotes());
        item = row(table, item, "Details of Co-curricular Activities and Leadership Qualities",
                certificate.getCoCurricular());
        row(table, item, "Details of Any Other Special Talents or Abilities",
                certificate.getSpecialTalents());

        document.add(table);
        writeSignatures(document, certificate);
    }

    private int row(PdfPTable table, int number, String label, String value) {
        table.addCell(cell(String.valueOf(number) + ".", labelFont, Element.ALIGN_RIGHT));
        table.addCell(cell(label, labelFont, Element.ALIGN_LEFT));
        table.addCell(cell(orBlank(value), valueFont, Element.ALIGN_LEFT));
        return number + 1;
    }

    private PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(RULE);
        cell.setPadding(5);
        return cell;
    }

    // ---- Character -----------------------------------------------------------

    private void writeCharacter(Document document, StudentCertificate certificate) throws DocumentException {
        centred(document, SCHOOL, schoolFont);

        Paragraph issued = new Paragraph(date(certificate.getIssued_date()), labelFont);
        issued.setAlignment(Element.ALIGN_RIGHT);
        issued.setSpacingBefore(18);
        document.add(issued);

        Paragraph to = new Paragraph("To whom it may concern", bodyFont);
        to.setSpacingBefore(16);
        document.add(to);

        Paragraph heading = new Paragraph("CHARACTER CERTIFICATE", titleFont);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingBefore(22);
        heading.setSpacingAfter(20);
        document.add(heading);

        // The body is the principal's own wording, stored as issued. Blank
        // lines separate paragraphs, so they are turned back into paragraphs.
        String body = certificate.getBody() == null ? "" : certificate.getBody();
        for (String block : body.split("\\R{2,}")) {
            if (block.isBlank()) {
                continue;
            }
            Paragraph paragraph = new Paragraph(block.trim().replaceAll("\\R", " "), bodyFont);
            paragraph.setAlignment(Element.ALIGN_JUSTIFIED);
            paragraph.setSpacingAfter(12);
            paragraph.setLeading(17);
            document.add(paragraph);
        }

        writeSignatures(document, certificate);
    }

    // -------------------------------------------------------------------------

    private void writeSignatures(Document document, StudentCertificate certificate) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(46);
        table.setWidths(new float[] { 1f, 1f });

        for (String line : List.of(
                "Date of Issue: " + date(certificate.getIssued_date()),
                "...............................................")) {
            PdfPCell cell = new PdfPCell(new Phrase(line, labelFont));
            cell.setBorder(0);
            cell.setPaddingBottom(4);
            table.addCell(cell);
        }

        PdfPCell seal = new PdfPCell(new Phrase("Official Seal", labelFont));
        seal.setBorder(0);
        seal.setPaddingTop(18);
        table.addCell(seal);

        PdfPCell principal = new PdfPCell(new Phrase(
                "Signature of the Principal\n" + orBlank(certificate.getPrincipalName()), labelFont));
        principal.setBorder(0);
        principal.setPaddingTop(4);
        table.addCell(principal);

        document.add(table);
    }

    private void centred(Document document, String text, Font font) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private String titleOf(StudentCertificate certificate) {
        return (StudentCertificate.CHARACTER.equals(certificate.getType())
                ? "Character Certificate — "
                : "Student Performance Record — ") + orBlank(certificate.getStudentName());
    }

    private LocalDate dobOf(StudentCertificate certificate) {
        return certificate.getStudent_id() == null ? null : certificate.getStudent_id().getDob();
    }

    private String date(LocalDate value) {
        return value == null ? "...................." : value.format(LONG_DATE);
    }

    private String orBlank(String value) {
        return value == null || value.isBlank() ? "...................." : value;
    }
}
