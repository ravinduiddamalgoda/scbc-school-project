package com.scbck.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
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
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.scbck.dto.ReportColumn;
import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportSection;
import com.scbck.exception.ApiException;

/**
 * Renders any {@link ReportDocument} to PDF.
 *
 * There is one renderer rather than four because the reports share a shape;
 * everything specific to a report - which bands, which columns, how a cell is
 * worded - was decided in {@link ReportService}. This class only knows about
 * paper.
 *
 * Generating server-side rather than in the browser means the PDF is produced
 * from the same query the on-screen table came from, under the same privilege
 * check, and cannot be altered on the way out.
 */
@Service
public class ReportPdfService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm");

    private static final String SCHOOL = "Sri Chandananda Buddhist College";

    private static final Color INK = new Color(0x1E, 0x29, 0x3B);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color RULE = new Color(0xD6, 0xDD, 0xE6);
    private static final Color HEAD_FILL = new Color(0xEE, 0xF2, 0xF7);
    private static final Color FOOT_FILL = new Color(0xF6, 0xF8, 0xFB);
    private static final Color STRIPE = new Color(0xFA, 0xFB, 0xFD);

    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK);
    private final Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, MUTED);
    private final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, INK);
    private final Font sectionNoteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f, MUTED);
    private final Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, INK);
    private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, INK);
    private final Font footFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, INK);
    private final Font emptyFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, MUTED);

    public byte[] render(ReportDocument report) {
        boolean landscape = "landscape".equalsIgnoreCase(report.orientation());
        Rectangle page = landscape ? PageSize.A4.rotate() : PageSize.A4;

        Document document = new Document(page, 32, 32, 34, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageNumbers(report));

            document.addTitle(report.title());
            document.addSubject(report.description());
            document.addCreator(SCHOOL);

            document.open();
            writeHeader(document, report);

            if (report.isEmpty()) {
                Paragraph empty = new Paragraph(
                        "There is nothing to report for " + report.academicYear()
                                + " yet. Set up the classes, their timetables and their enrolments first.",
                        emptyFont);
                empty.setSpacingBefore(24);
                document.add(empty);
            } else {
                for (ReportSection section : report.sections()) {
                    writeSection(document, section);
                }
            }

            document.close();
        } catch (DocumentException error) {
            throw ApiException.badRequest("The report could not be written to PDF: " + error.getMessage());
        }

        return out.toByteArray();
    }

    // -------------------------------------------------------------------------

    private void writeHeader(Document document, ReportDocument report) throws DocumentException {
        Paragraph school = new Paragraph(SCHOOL, subtitleFont);
        school.setSpacingAfter(1);
        document.add(school);

        document.add(new Paragraph(report.title(), titleFont));

        Paragraph meta = new Paragraph(
                report.description()
                        + "   ·   Academic year " + report.academicYear()
                        + "   ·   Generated " + STAMP.format(report.generatedAt()),
                subtitleFont);
        meta.setSpacingAfter(4);
        document.add(meta);

        // A rule under the header, drawn as a one-cell table so it spans the
        // text width whatever the page orientation is.
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(1.2f);
        cell.setBackgroundColor(INK);
        cell.setBorder(Rectangle.NO_BORDER);
        rule.addCell(cell);
        rule.setSpacingAfter(10);
        document.add(rule);
    }

    private void writeSection(Document document, ReportSection section) throws DocumentException {
        if (section.rows().isEmpty()) {
            return;
        }

        Paragraph heading = new Paragraph(section.title(), sectionFont);
        heading.setSpacingBefore(10);
        heading.setSpacingAfter(section.subtitle() == null ? 4 : 1);
        document.add(heading);

        if (section.subtitle() != null) {
            Paragraph note = new Paragraph(section.subtitle(), sectionNoteFont);
            note.setSpacingAfter(4);
            document.add(note);
        }

        List<ReportColumn> columns = section.columns();

        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        table.setWidths(widths(columns));
        // Repeat the headings when a long band runs past the end of the page.
        table.setHeaderRows(1);

        for (ReportColumn column : columns) {
            table.addCell(cell(column.header(), headFont, column.align(), HEAD_FILL));
        }

        int index = 0;
        for (List<String> row : section.rows()) {
            Color fill = index++ % 2 == 0 ? Color.WHITE : STRIPE;
            for (int position = 0; position < columns.size(); position++) {
                String value = position < row.size() ? row.get(position) : "";
                table.addCell(cell(value, bodyFont, columns.get(position).align(), fill));
            }
        }

        if (section.footer() != null) {
            for (int position = 0; position < columns.size(); position++) {
                String value = position < section.footer().size() ? section.footer().get(position) : "";
                table.addCell(cell(value, footFont, columns.get(position).align(), FOOT_FILL));
            }
        }

        table.setSpacingAfter(6);
        document.add(table);
    }

    private float[] widths(List<ReportColumn> columns) {
        float[] widths = new float[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            float weight = columns.get(index).weight();
            widths[index] = weight <= 0 ? 1f : weight;
        }
        return widths;
    }

    private PdfPCell cell(String text, Font font, String align, Color fill) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(alignmentOf(align));
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4f);
        cell.setBackgroundColor(fill);
        cell.setBorderColor(RULE);
        cell.setBorderWidth(0.4f);
        return cell;
    }

    private int alignmentOf(String align) {
        return switch (align == null ? "left" : align) {
            case "center" -> Element.ALIGN_CENTER;
            case "right" -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    /**
     * "Page n" plus the report identity in the footer, so a page that gets
     * separated from the rest of the stack still says what it belongs to.
     */
    private final class PageNumbers extends PdfPageEventHelper {

        private final ReportDocument report;
        private final Font font = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, MUTED);

        private PageNumbers(ReportDocument report) {
            this.report = report;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            float baseline = document.bottom() - 18;

            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase(report.title() + " · " + report.academicYear(), font),
                    document.left(), baseline, 0);

            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), font),
                    page.getWidth() - document.rightMargin(), baseline, 0);
        }
    }
}
