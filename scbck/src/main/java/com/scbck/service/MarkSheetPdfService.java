package com.scbck.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.scbck.dto.MarkSheet;
import com.scbck.exception.ApiException;

/**
 * Renders a {@link MarkSheet} to PDF.
 *
 * The workbook prints the marks and the letter grades as one continuous run of
 * columns, which is why the original needs A3 and still comes out small enough
 * to misread. Here the same data is three tables on landscape A4 - marks with
 * the totals, grades with the summary, then the subject analysis - so each is
 * legible at the size a class list is actually read at.
 */
@Service
public class MarkSheetPdfService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm");
    private static final String SCHOOL = "Sri Chandananda Buddhist College, Kandy";

    private static final Color INK = new Color(0x1E, 0x29, 0x3B);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color RULE = new Color(0xD6, 0xDD, 0xE6);
    private static final Color HEAD_FILL = new Color(0xEE, 0xF2, 0xF7);
    /** The wash on a row averaging 80 or more. */
    private static final Color LIFT_FILL = new Color(0xD6, 0xF0, 0xD8);

    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, INK);
    private final Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, MUTED);
    private final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, INK);
    private final Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, INK);
    private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, INK);
    private final Font liftFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, INK);
    private final Font emptyFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, MUTED);

    public byte[] render(MarkSheet sheet) {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 32, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle(sheet.className() + " " + sheet.termName() + " Marks Analysis");
            document.addCreator(SCHOOL);

            document.open();
            writeHeader(document, sheet);

            if (sheet.rows().isEmpty()) {
                Paragraph empty = new Paragraph(
                        "No students are enrolled in " + sheet.className() + " yet, so there are no marks to print.",
                        emptyFont);
                empty.setSpacingBefore(24);
                document.add(empty);
            } else {
                document.add(section("Marks"));
                document.add(marksTable(sheet));

                document.add(section("Grades"));
                document.add(gradesTable(sheet));

                document.add(section("Subject analysis"));
                document.add(analysisTable(sheet));

                document.add(signatures());
            }

            document.close();
            return out.toByteArray();

        } catch (DocumentException error) {
            throw ApiException.badRequest("The mark sheet PDF could not be written: " + error.getMessage());
        }
    }

    public String fileNameFor(MarkSheet sheet) {
        return (sheet.className() + " " + sheet.termName() + " Marks")
                .replaceAll("[^A-Za-z0-9 .-]", " ")
                .replaceAll("\\s+", " ")
                .trim() + ".pdf";
    }

    // -------------------------------------------------------------------------

    private void writeHeader(Document document, MarkSheet sheet) throws DocumentException {
        Paragraph school = new Paragraph(SCHOOL, titleFont);
        school.setAlignment(Element.ALIGN_CENTER);
        document.add(school);

        Paragraph title = new Paragraph(
                sheet.className() + " — " + sheet.termName() + " Marks Analysis", sectionFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        document.add(title);

        Paragraph meta = new Paragraph(
                "Class Teacher: " + orDash(sheet.classTeacher())
                        + "     Medium: " + orDash(sheet.medium())
                        + "     Academic Year: " + orDash(sheet.academicYear())
                        + "     Students: " + sheet.rows().size()
                        + "     Generated: " + sheet.generatedAt().format(STAMP),
                metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(10);
        document.add(meta);

        Paragraph key = new Paragraph(
                "A 75-100   B 65-74   C 55-64   S 35-54   F below 35   AB absent   "
                        + "Shaded rows average " + (int) sheet.highlightAverageFrom() + " or above.",
                metaFont);
        key.setAlignment(Element.ALIGN_CENTER);
        key.setSpacingAfter(8);
        document.add(key);
    }

    private Paragraph section(String label) {
        Paragraph heading = new Paragraph(label, sectionFont);
        heading.setSpacingBefore(12);
        heading.setSpacingAfter(5);
        return heading;
    }

    /**
     * No / Admission / Name / one column per subject / Total / Average / Rank.
     */
    private PdfPTable marksTable(MarkSheet sheet) throws DocumentException {
        int columns = 3 + sheet.subjects().size() + 3;
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setWidths(identityWidths(sheet.subjects().size(), 3));
        table.setHeaderRows(2);

        addCategoryBands(table, sheet, 3);

        head(table, "No");
        head(table, "Adm. No");
        head(table, "Name of the Student");
        for (MarkSheet.Subject subject : sheet.subjects()) {
            head(table, subject.code());
        }
        head(table, "Total");
        head(table, "Avg");
        head(table, "Rank");

        for (MarkSheet.Row row : sheet.rows()) {
            boolean lift = row.highlight();
            body(table, String.valueOf(row.index()), lift, Element.ALIGN_CENTER);
            body(table, orDash(row.admissionNo()), lift, Element.ALIGN_CENTER);
            body(table, row.studentName(), lift, Element.ALIGN_LEFT);

            for (MarkSheet.Cell cell : row.cells()) {
                body(table, markText(cell), lift, Element.ALIGN_CENTER);
            }

            body(table, String.valueOf(row.total()), lift, Element.ALIGN_CENTER);
            body(table, row.average() == null ? "—" : String.format("%.1f", row.average()), lift,
                    Element.ALIGN_CENTER);
            body(table, row.rank() == null ? "—" : String.valueOf(row.rank()), lift, Element.ALIGN_CENTER);
        }

        return table;
    }

    /** No / Name / one grade per subject / the A-F tally. */
    private PdfPTable gradesTable(MarkSheet sheet) throws DocumentException {
        int columns = 2 + sheet.subjects().size() + GradeScale.LETTERS.size();
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);

        float[] widths = new float[columns];
        widths[0] = 1.1f;
        widths[1] = 6.5f;
        for (int i = 2; i < columns; i++) {
            widths[i] = 1.6f;
        }
        table.setWidths(widths);
        table.setHeaderRows(1);

        head(table, "No");
        head(table, "Name of the Student");
        for (MarkSheet.Subject subject : sheet.subjects()) {
            head(table, subject.code());
        }
        for (String letter : GradeScale.LETTERS) {
            head(table, letter);
        }

        for (MarkSheet.Row row : sheet.rows()) {
            boolean lift = row.highlight();
            body(table, String.valueOf(row.index()), lift, Element.ALIGN_CENTER);
            body(table, row.studentName(), lift, Element.ALIGN_LEFT);

            for (MarkSheet.Cell cell : row.cells()) {
                body(table, cell.grade(), lift, Element.ALIGN_CENTER);
            }
            for (MarkSheet.LetterCount count : row.gradeCounts()) {
                body(table, String.valueOf(count.count()), lift, Element.ALIGN_CENTER);
            }
        }

        return table;
    }

    /** One row per statistic, one column per subject. */
    private PdfPTable analysisTable(MarkSheet sheet) throws DocumentException {
        int columns = 1 + sheet.subjects().size();
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);

        float[] widths = new float[columns];
        widths[0] = 4f;
        for (int i = 1; i < columns; i++) {
            widths[i] = 1.6f;
        }
        table.setWidths(widths);
        table.setHeaderRows(1);

        head(table, "");
        for (MarkSheet.Subject subject : sheet.subjects()) {
            head(table, subject.code());
        }

        for (String letter : GradeScale.LETTERS) {
            body(table, letter + " passes", false, Element.ALIGN_LEFT);
            for (MarkSheet.SubjectSummary summary : sheet.summary()) {
                body(table, String.valueOf(countOf(summary.letterCounts(), letter)), false, Element.ALIGN_CENTER);
            }
        }

        body(table, "Absent", false, Element.ALIGN_LEFT);
        for (MarkSheet.SubjectSummary summary : sheet.summary()) {
            body(table, String.valueOf(summary.absent()), false, Element.ALIGN_CENTER);
        }

        for (GradeScale.Band band : GradeScale.BANDS) {
            body(table, "Marks " + band.label(), false, Element.ALIGN_LEFT);
            for (MarkSheet.SubjectSummary summary : sheet.summary()) {
                body(table, String.valueOf(bandCountOf(summary, band.label())), false, Element.ALIGN_CENTER);
            }
        }

        body(table, "Marks recorded", false, Element.ALIGN_LEFT);
        for (MarkSheet.SubjectSummary summary : sheet.summary()) {
            body(table, String.valueOf(summary.recorded()), false, Element.ALIGN_CENTER);
        }

        return table;
    }

    /**
     * The category names merged across the subjects they cover, so a reader can
     * tell an optional basket from a compulsory subject at a glance.
     */
    private void addCategoryBands(PdfPTable table, MarkSheet sheet, int identityColumns) {
        PdfPCell spacer = new PdfPCell(new Phrase("", headFont));
        spacer.setColspan(identityColumns);
        spacer.setBorderColor(RULE);
        spacer.setBackgroundColor(HEAD_FILL);
        table.addCell(spacer);

        for (MarkSheet.Category category : sheet.categories()) {
            PdfPCell cell = new PdfPCell(new Phrase(category.name(), headFont));
            cell.setColspan(category.span());
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(HEAD_FILL);
            cell.setBorderColor(RULE);
            cell.setPadding(3);
            table.addCell(cell);
        }

        PdfPCell trailing = new PdfPCell(new Phrase("", headFont));
        trailing.setColspan(3);
        trailing.setBorderColor(RULE);
        trailing.setBackgroundColor(HEAD_FILL);
        table.addCell(trailing);
    }

    private float[] identityWidths(int subjectColumns, int trailingColumns) {
        List<Float> widths = new ArrayList<>();
        widths.add(1.1f);
        widths.add(2.2f);
        widths.add(6.5f);
        for (int i = 0; i < subjectColumns; i++) {
            widths.add(1.6f);
        }
        for (int i = 0; i < trailingColumns; i++) {
            widths.add(1.9f);
        }

        float[] result = new float[widths.size()];
        for (int i = 0; i < widths.size(); i++) {
            result[i] = widths.get(i);
        }
        return result;
    }

    private Paragraph signatures() {
        Paragraph line = new Paragraph(
                "Class Teacher's Signature: ...................................              "
                        + "Principal's Signature: ...................................",
                metaFont);
        line.setSpacingBefore(26);
        return line;
    }

    private String markText(MarkSheet.Cell cell) {
        if (cell.absent()) {
            return GradeScale.ABSENT;
        }
        if (!cell.enrolled()) {
            return GradeScale.NOT_TAKEN;
        }
        return cell.marks() == null ? "" : String.valueOf(cell.marks());
    }

    private void head(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, headFont));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(HEAD_FILL);
        cell.setBorderColor(RULE);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void body(PdfPTable table, String text, boolean lift, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, lift ? liftFont : bodyFont));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(RULE);
        cell.setPadding(2.5f);
        if (lift) {
            cell.setBackgroundColor(LIFT_FILL);
        }
        table.addCell(cell);
    }

    private int countOf(List<MarkSheet.LetterCount> counts, String letter) {
        return counts.stream()
                .filter(count -> count.letter().equals(letter))
                .mapToInt(MarkSheet.LetterCount::count)
                .findFirst()
                .orElse(0);
    }

    private int bandCountOf(MarkSheet.SubjectSummary summary, String label) {
        return summary.bandCounts().stream()
                .filter(count -> count.label().equals(label))
                .mapToInt(MarkSheet.BandCount::count)
                .findFirst()
                .orElse(0);
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
