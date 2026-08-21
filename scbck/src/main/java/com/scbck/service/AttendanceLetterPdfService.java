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
import com.scbck.dto.StudentAttendanceSummary;
import com.scbck.exception.ApiException;
import com.scbck.model.SchoolProfile;

/**
 * The three letters the school sends a family about attendance.
 *
 * Two of them are formal notices under Circular No. 53/2023: their wording is
 * the Ministry's, not the school's, and it is reproduced here as written rather
 * than paraphrased. The third is a plain weekly summary the school sends of its
 * own accord.
 *
 * The dates that used to be dotted lines on a photocopied form - when the
 * absence began, how many days it has run - are filled in from the register,
 * because that is the whole reason for generating the letter rather than typing
 * it. The two the office cannot know, the date and time of the meeting it wants
 * to call, stay as dotted lines unless they are supplied.
 */
@Service
public class AttendanceLetterPdfService {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color RULE = new Color(0x9C, 0xA3, 0xAF);
    private static final Color HEAD = new Color(0xF3, 0xF4, 0xF6);

    /** The circular both absence notices are issued under. */
    private static final String CIRCULAR =
            "Therefore, please be informed that the school is required to take appropriate action "
                    + "in accordance with Circular No. 53/2023, bearing reference No. "
                    + "ED/09/12/08/01, dated 29 December 2023, issued by the Secretary of the "
                    + "Ministry of Education.";

    private final Font schoolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
    private final Font addressFont = FontFactory.getFont(FontFactory.HELVETICA, 11, INK);
    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
    private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11, INK);
    private final Font boldBodyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK);
    private final Font tableFont = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
    private final Font tableHeadFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INK);

    /**
     * Renders one letter.
     *
     * @param meetingDate the day the school asks the family to come in, or null
     *                    to leave the form's dotted line
     * @param meetingTime the time, as the school writes it ("2.30"), or null
     */
    public byte[] render(String letter, StudentAttendanceSummary summary,
            LocalDate meetingDate, String meetingTime) {

        Document document = new Document(PageSize.A4, 56, 56, 48, 52);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle(titleOf(letter, summary));
            document.addCreator(SchoolProfile.NAME_WITH_CITY);
            document.open();

            writeLetterhead(document);

            switch (letter) {
                case StudentAttendanceService.LETTER_TWENTY_DAY ->
                        writeAbsenceNotice(document, summary, meetingDate, meetingTime, false);
                case StudentAttendanceService.LETTER_FORTY_DAY ->
                        writeAbsenceNotice(document, summary, meetingDate, meetingTime, true);
                default -> writeWeekSummary(document, summary);
            }

            document.close();
            return out.toByteArray();

        } catch (DocumentException error) {
            throw ApiException.badRequest("The letter could not be written: " + error.getMessage());
        }
    }

    public String fileNameFor(String letter, StudentAttendanceSummary summary) {
        String kind = switch (letter) {
            case StudentAttendanceService.LETTER_TWENTY_DAY -> "20 Day Absence Notice";
            case StudentAttendanceService.LETTER_FORTY_DAY -> "40 Day Absence Notice";
            default -> "Week Attendance Letter";
        };
        String who = summary.admissionNo() == null ? summary.studentName() : summary.admissionNo();
        return (kind + " - " + orText(who, "Student")).replaceAll("[\\\\/:*?\"<>|]", "-") + ".pdf";
    }

    // ---- Shared parts -------------------------------------------------------

    /** The school's own address block, top left, as the samples show it. */
    private void writeLetterhead(Document document) throws DocumentException {
        for (String line : SchoolProfile.LETTERHEAD) {
            Paragraph paragraph = new Paragraph(line,
                    line.startsWith(SchoolProfile.NAME) ? schoolFont : addressFont);
            paragraph.setSpacingAfter(1);
            document.add(paragraph);
        }

        Paragraph date = new Paragraph("Date: " + LocalDate.now().format(LONG_DATE), addressFont);
        date.setSpacingBefore(6);
        document.add(date);
    }

    /** "Admission No.: 3960 - D.A. Malm (Grade 11-B)". */
    private void writeAddressee(Document document, StudentAttendanceSummary summary)
            throws DocumentException {

        StringBuilder line = new StringBuilder("Admission No.: ")
                .append(orText(summary.admissionNo(), "...................."))
                .append(" - ")
                .append(orText(summary.studentName(), "...................."));

        if (summary.className() != null && !summary.className().isBlank()) {
            line.append(" (").append(summary.className()).append(")");
        }

        Paragraph paragraph = new Paragraph(line.toString(), boldBodyFont);
        paragraph.setSpacingBefore(18);
        paragraph.setSpacingAfter(14);
        document.add(paragraph);
    }

    private void writeHeading(Document document, String text) throws DocumentException {
        Paragraph heading = new Paragraph(text, titleFont);
        heading.setSpacingBefore(24);
        heading.setAlignment(Element.ALIGN_LEFT);
        document.add(heading);
    }

    private void writeBody(Document document, String text) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, bodyFont);
        paragraph.setAlignment(Element.ALIGN_JUSTIFIED);
        paragraph.setSpacingAfter(12);
        paragraph.setLeading(16.5f);
        document.add(paragraph);
    }

    private void writeSignature(Document document) throws DocumentException {
        Paragraph principal = new Paragraph("Principal", bodyFont);
        principal.setSpacingBefore(40);
        document.add(principal);

        Paragraph school = new Paragraph(
                SchoolProfile.NAME + ", " + SchoolProfile.ADDRESS_LINE + ", " + SchoolProfile.CITY,
                bodyFont);
        document.add(school);
    }

    // ---- The two absence notices --------------------------------------------

    /**
     * The twenty- and forty-day notices, which differ in three places.
     *
     * The school's sample of the forty-day letter repeats the twenty-day
     * paragraph above its own, which reads as a copy-and-paste left in the
     * draft rather than an intention: the same letter would then tell the
     * family both that action is required and that the student has already been
     * struck off. The forty-day notice is written here as the escalation it is
     * - the forty-day wording, the circular, the invitation, then the statement
     * that the student is considered to have left.
     */
    private void writeAbsenceNotice(Document document, StudentAttendanceSummary summary,
            LocalDate meetingDate, String meetingTime, boolean forty) throws DocumentException {

        int threshold = forty
                ? StudentAttendanceService.FORTY_DAY_THRESHOLD
                : StudentAttendanceService.TWENTY_DAY_THRESHOLD;
        String inWords = forty ? "forty" : "twenty";

        writeHeading(document, "Notice Regarding Continuous Absence from School for More Than "
                + threshold + " Days Without Notification");

        writeAddressee(document, summary);

        String since = summary.absentSince() == null
                ? "...................."
                : summary.absentSince().format(LONG_DATE);

        writeBody(document, "This is to inform you that your child, the above-mentioned student, "
                + "has been continuously absent from school for more than " + inWords
                + " days, from " + since + " to date. It is further noted that, to date, neither "
                + "the student's mother, father, nor guardian has "
                + (forty
                        ? "provided any response or formally informed the school regarding the "
                                + "reason for the student's prolonged absence, despite repeated "
                                + "attempts of communication."
                        : "formally informed the school of the reason for the student's absence."));

        writeBody(document, CIRCULAR);

        writeBody(document, "If necessary, you are kindly requested to visit the school on "
                + (meetingDate == null ? "...................." : meetingDate.format(LONG_DATE))
                + " at " + orText(meetingTime, "....................")
                + " p.m. to discuss this matter and take the necessary steps regarding your "
                + "child's continued absence.");

        if (forty) {
            writeBody(document, "Therefore, please be informed that, in accordance with Circular "
                    + "No. 53/2023, bearing reference No. ED/09/12/08/01, dated 29 December 2023, "
                    + "issued by the Secretary of the Ministry of Education, the school hereby "
                    + "considers the student as having left the school due to continuous "
                    + "unexplained absence.");
        }

        writeSignature(document);
    }

    // ---- The weekly summary -------------------------------------------------

    private void writeWeekSummary(Document document, StudentAttendanceSummary summary)
            throws DocumentException {

        writeHeading(document, "Attendance Report");
        writeAddressee(document, summary);

        writeBody(document, "The attendance of the above-mentioned student for the period "
                + summary.from().format(LONG_DATE) + " to " + summary.to().format(LONG_DATE)
                + " is set out below. School was conducted on " + summary.daysConducted()
                + " day(s) in this period, of which your child was present on "
                + summary.daysPresent() + " and absent on " + summary.daysAbsent() + " - an "
                + "attendance of " + summary.attendancePercentage() + "%.");

        writeWeekTable(document, summary);

        if (summary.consecutiveAbsentDays() > 0) {
            writeBody(document, "Please note that the student has been absent on the last "
                    + summary.consecutiveAbsentDays() + " consecutive school day(s)"
                    + (summary.absentSince() == null
                            ? "."
                            : ", from " + summary.absentSince().format(LONG_DATE) + " to date.")
                    + " If there is a reason for this absence that the school has not been "
                    + "informed of, kindly notify us at your earliest convenience.");
        }

        writeBody(document, "Regular attendance is essential to your child's progress. We thank "
                + "you for your continued support.");

        writeSignature(document);
    }

    private void writeWeekTable(Document document, StudentAttendanceSummary summary)
            throws DocumentException {

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(14);
        table.setWidths(new float[] { 1.1f, 2.8f, 1.3f, 1.2f, 1.2f });

        for (String heading : List.of("Week", "Dates", "Conducted", "Present", "Absent")) {
            PdfPCell cell = new PdfPCell(new Phrase(heading, tableHeadFont));
            cell.setBackgroundColor(HEAD);
            cell.setBorderColor(RULE);
            cell.setPadding(5);
            table.addCell(cell);
        }

        for (StudentAttendanceSummary.Week week : summary.weeks()) {
            // A week nobody marked is a week school was not conducted for this
            // class; printing a row of zeroes for it would read as five
            // absences.
            if (week.conducted() == 0) {
                continue;
            }
            addCell(table, "Week " + week.number(), Element.ALIGN_LEFT);
            addCell(table, week.from() + " to " + week.to(), Element.ALIGN_LEFT);
            addCell(table, String.valueOf(week.conducted()), Element.ALIGN_CENTER);
            addCell(table, String.valueOf(week.present()), Element.ALIGN_CENTER);
            addCell(table, String.valueOf(week.absent()), Element.ALIGN_CENTER);
        }

        addTotal(table, summary);
        document.add(table);
    }

    private void addTotal(PdfPTable table, StudentAttendanceSummary summary) {
        PdfPCell label = new PdfPCell(new Phrase("Total", tableHeadFont));
        label.setColspan(2);
        label.setBackgroundColor(HEAD);
        label.setBorderColor(RULE);
        label.setPadding(5);
        table.addCell(label);

        for (int value : new int[] { summary.daysConducted(), summary.daysPresent(),
                summary.daysAbsent() }) {
            PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(value), tableHeadFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(HEAD);
            cell.setBorderColor(RULE);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, tableFont));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(RULE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    // -------------------------------------------------------------------------

    private String titleOf(String letter, StudentAttendanceSummary summary) {
        String kind = switch (letter) {
            case StudentAttendanceService.LETTER_TWENTY_DAY ->
                    "Notice of Continuous Absence (20 Days)";
            case StudentAttendanceService.LETTER_FORTY_DAY ->
                    "Notice of Continuous Absence (40 Days)";
            default -> "Attendance Report";
        };
        return kind + " - " + orText(summary.studentName(), "Student");
    }

    private static String orText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
