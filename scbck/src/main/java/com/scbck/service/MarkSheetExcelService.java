package com.scbck.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.scbck.dto.MarkSheet;
import com.scbck.exception.ApiException;

/**
 * Renders a {@link MarkSheet} as the workbook the school already files.
 *
 * The layout follows their sheet: identity columns, the marks banded by
 * category, totals, then the same columns again as letter grades, then the
 * grade summary, and the per-subject analysis block underneath.
 *
 * What it does not reproduce is the formulas. The original recalculated on
 * open, so a sheet could print differently from the day it was filed once a
 * formula had been dragged over the wrong range - and several in the sample
 * had been. Values are written instead: the workbook now records what the
 * system calculated, and {@link MarkSheetService} is the only thing that
 * calculates.
 */
@Service
public class MarkSheetExcelService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm");
    private static final String SCHOOL = "Sri Chandananda Buddhist College, Kandy";

    /** Identity columns before the first subject: index, admission no, name. */
    private static final int IDENTITY_COLUMNS = 3;

    public byte[] render(MarkSheet sheet) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet target = workbook.createSheet(sheetName(sheet));
            Styles styles = new Styles(workbook);

            int cursor = writeTitle(target, styles, sheet);
            cursor = writeHeadings(target, styles, sheet, cursor);
            cursor = writeRows(target, styles, sheet, cursor);
            writeSummary(target, styles, sheet, cursor + 1);

            sizeColumns(target, sheet);
            freezeHeader(target, cursor);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException error) {
            throw ApiException.badRequest("The mark sheet could not be written: " + error.getMessage());
        }
    }

    /** Filename the browser saves the download under. */
    public String fileNameFor(MarkSheet sheet) {
        return (sheet.className() + " " + sheet.termName() + " Marks")
                .replaceAll("[^A-Za-z0-9 .-]", " ")
                .replaceAll("\\s+", " ")
                .trim() + ".xlsx";
    }

    // ---- header --------------------------------------------------------------

    private int writeTitle(XSSFSheet target, Styles styles, MarkSheet sheet) {
        int lastColumn = lastColumnIndex(sheet);

        Row title = target.createRow(0);
        cell(title, 0, SCHOOL + " — " + sheet.className() + " " + sheet.termName() + " Marks Analysis",
                styles.title);
        target.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));

        Row meta = target.createRow(1);
        cell(meta, 0, "Class: " + sheet.className()
                + (sheet.medium() == null ? "" : "  (" + sheet.medium() + " medium)")
                + "    Class Teacher: " + orDash(sheet.classTeacher())
                + "    Academic Year: " + orDash(sheet.academicYear())
                + "    Generated: " + sheet.generatedAt().format(STAMP), styles.meta);
        target.addMergedRegion(new CellRangeAddress(1, 1, 0, lastColumn));

        return 3;
    }

    /**
     * Two heading rows: category bands merged across their subjects, then the
     * subject names underneath.
     */
    private int writeHeadings(XSSFSheet target, Styles styles, MarkSheet sheet, int firstRow) {
        Row bands = target.createRow(firstRow);
        Row names = target.createRow(firstRow + 1);

        String[] identity = { "No", "Admission No", "Name of the Student" };
        for (int i = 0; i < identity.length; i++) {
            cell(bands, i, identity[i], styles.head);
            cell(names, i, "", styles.head);
            target.addMergedRegion(new CellRangeAddress(firstRow, firstRow + 1, i, i));
        }

        int column = IDENTITY_COLUMNS;
        column = writeBandedHeadings(target, styles, sheet, bands, names, firstRow, column, false);

        // Total / Average / Rank sit between the marks and the grades, as they do
        // on the school's sheet.
        for (String label : List.of("Total", "Average", "Rank")) {
            cell(bands, column, label, styles.head);
            cell(names, column, "", styles.head);
            target.addMergedRegion(new CellRangeAddress(firstRow, firstRow + 1, column, column));
            column++;
        }

        column = writeBandedHeadings(target, styles, sheet, bands, names, firstRow, column, true);

        cell(bands, column, "Grade Summary", styles.head);
        target.addMergedRegion(new CellRangeAddress(firstRow, firstRow, column,
                column + GradeScale.LETTERS.size() - 1));
        for (String letter : GradeScale.LETTERS) {
            cell(names, column++, letter, styles.head);
        }

        return firstRow + 2;
    }

    /**
     * One pass of the subject columns, merged under their category names.
     *
     * Called twice - once for the marks, once for the grades - because the
     * sheet prints the same column structure twice over.
     */
    private int writeBandedHeadings(XSSFSheet target, Styles styles, MarkSheet sheet, Row bands, Row names,
            int firstRow, int startColumn, boolean grades) {

        int column = startColumn;
        for (MarkSheet.Category category : sheet.categories()) {
            cell(bands, column, grades ? category.name() + " (Grade)" : category.name(), styles.head);
            if (category.span() > 1) {
                target.addMergedRegion(
                        new CellRangeAddress(firstRow, firstRow, column, column + category.span() - 1));
            }
            column += category.span();
        }

        column = startColumn;
        for (MarkSheet.Subject subject : sheet.subjects()) {
            cell(names, column++, subject.code(), styles.head);
        }
        return column;
    }

    // ---- roster --------------------------------------------------------------

    private int writeRows(XSSFSheet target, Styles styles, MarkSheet sheet, int firstRow) {
        int rowIndex = firstRow;

        for (MarkSheet.Row row : sheet.rows()) {
            Row targetRow = target.createRow(rowIndex++);
            boolean lift = row.highlight();

            cell(targetRow, 0, row.index(), styles.body(lift));
            cell(targetRow, 1, orDash(row.admissionNo()), styles.body(lift));
            cell(targetRow, 2, row.studentName(), styles.name(lift));

            int column = IDENTITY_COLUMNS;
            for (MarkSheet.Cell mark : row.cells()) {
                if (mark.absent()) {
                    cell(targetRow, column, GradeScale.ABSENT, styles.body(lift));
                } else if (mark.marks() != null) {
                    cell(targetRow, column, mark.marks(), styles.body(lift));
                } else {
                    cell(targetRow, column, "", styles.body(lift));
                }
                column++;
            }

            cell(targetRow, column++, row.total(), styles.body(lift));
            if (row.average() == null) {
                cell(targetRow, column++, "—", styles.body(lift));
            } else {
                cell(targetRow, column++, row.average(), styles.average(lift));
            }
            cell(targetRow, column++, row.rank() == null ? "—" : String.valueOf(row.rank()), styles.body(lift));

            for (MarkSheet.Cell mark : row.cells()) {
                cell(targetRow, column++, mark.grade(), styles.body(lift));
            }

            for (MarkSheet.LetterCount count : row.gradeCounts()) {
                cell(targetRow, column++, count.count(), styles.body(lift));
            }
        }

        return rowIndex;
    }

    // ---- analysis block ------------------------------------------------------

    /**
     * The per-subject block the school prints under the roster: how many of each
     * letter the subject awarded, then how many marks fell in each band.
     */
    private void writeSummary(XSSFSheet target, Styles styles, MarkSheet sheet, int firstRow) {
        int rowIndex = firstRow;

        Row heading = target.createRow(rowIndex++);
        cell(heading, 0, "Subject analysis", styles.head);
        int column = IDENTITY_COLUMNS;
        for (MarkSheet.Subject subject : sheet.subjects()) {
            cell(heading, column++, subject.code(), styles.head);
        }

        Map<String, List<Integer>> lines = new LinkedHashMap<>();
        for (String letter : GradeScale.LETTERS) {
            lines.put(letter + " passes", sheet.summary().stream()
                    .map(summary -> countOf(summary.letterCounts(), letter))
                    .toList());
        }
        lines.put("Absent", sheet.summary().stream().map(MarkSheet.SubjectSummary::absent).toList());
        for (GradeScale.Band band : GradeScale.BANDS) {
            lines.put(band.label(), sheet.summary().stream()
                    .map(summary -> bandCountOf(summary, band.label()))
                    .toList());
        }
        lines.put("Marks recorded", sheet.summary().stream()
                .map(MarkSheet.SubjectSummary::recorded).toList());

        for (Map.Entry<String, List<Integer>> line : lines.entrySet()) {
            Row row = target.createRow(rowIndex++);
            cell(row, 0, line.getKey(), styles.summaryLabel);
            target.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, IDENTITY_COLUMNS - 1));

            int at = IDENTITY_COLUMNS;
            for (Integer value : line.getValue()) {
                cell(row, at++, value, styles.body(false));
            }
        }

        rowIndex += 2;
        Row signatures = target.createRow(rowIndex);
        cell(signatures, 0, "Class Teacher's Signature: ..............................", styles.meta);
        cell(signatures, IDENTITY_COLUMNS + 4, "Principal's Signature: ..............................",
                styles.meta);
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

    // ---- sheet mechanics -----------------------------------------------------

    private void sizeColumns(XSSFSheet target, MarkSheet sheet) {
        target.setColumnWidth(0, 1500);
        target.setColumnWidth(1, 3600);
        target.setColumnWidth(2, 8000);
        for (int column = IDENTITY_COLUMNS; column <= lastColumnIndex(sheet); column++) {
            target.setColumnWidth(column, 2400);
        }
    }

    /**
     * Names stay on screen while a wide sheet is scrolled sideways - the reason
     * the original was hard to check was that the student's name scrolled away
     * long before the last subject column arrived.
     */
    private void freezeHeader(XSSFSheet target, int firstDataRow) {
        target.createFreezePane(IDENTITY_COLUMNS, firstDataRow);
    }

    private int lastColumnIndex(MarkSheet sheet) {
        return IDENTITY_COLUMNS
                + sheet.subjects().size() // marks
                + 3 // total, average, rank
                + sheet.subjects().size() // grades
                + GradeScale.LETTERS.size()
                - 1;
    }

    private String sheetName(MarkSheet sheet) {
        // Excel refuses sheet names over 31 characters or containing []:*?/\
        String raw = sheet.className() + " " + sheet.termName();
        String safe = raw.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        return safe.length() <= 31 ? safe : safe.substring(0, 31);
    }

    private void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void cell(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /**
     * The workbook's styles, created once.
     *
     * POI caps a workbook at 64,000 styles and creating one per cell hits that
     * on a large sheet, so every cell shares one of these.
     */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle meta;
        private final CellStyle head;
        private final CellStyle summaryLabel;
        private final CellStyle plain;
        private final CellStyle plainLifted;
        private final CellStyle nameCell;
        private final CellStyle nameLifted;
        private final CellStyle averageCell;
        private final CellStyle averageLifted;

        Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);

            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headFont.setFontHeightInPoints((short) 9);

            Font bodyFont = workbook.createFont();
            bodyFont.setFontHeightInPoints((short) 9);

            Font liftFont = workbook.createFont();
            liftFont.setFontHeightInPoints((short) 9);
            liftFont.setBold(true);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);

            meta = workbook.createCellStyle();
            meta.setFont(bodyFont);

            head = bordered(workbook, headFont);
            head.setAlignment(HorizontalAlignment.CENTER);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setWrapText(true);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            summaryLabel = bordered(workbook, headFont);
            summaryLabel.setAlignment(HorizontalAlignment.LEFT);

            plain = bordered(workbook, bodyFont);
            plain.setAlignment(HorizontalAlignment.CENTER);

            nameCell = bordered(workbook, bodyFont);
            nameCell.setAlignment(HorizontalAlignment.LEFT);

            averageCell = bordered(workbook, bodyFont);
            averageCell.setAlignment(HorizontalAlignment.CENTER);
            averageCell.setDataFormat(workbook.createDataFormat().getFormat("0.0"));

            plainLifted = lifted(workbook, plain, liftFont);
            nameLifted = lifted(workbook, nameCell, liftFont);
            averageLifted = lifted(workbook, averageCell, liftFont);
            averageLifted.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
        }

        private static CellStyle bordered(XSSFWorkbook workbook, Font font) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /**
         * The emphasis put on a student averaging 80 or more: a green wash and
         * bold text, rather than colour alone, so the distinction survives a
         * monochrome printout and a colour-blind reader.
         */
        private static XSSFCellStyle lifted(XSSFWorkbook workbook, CellStyle base, Font font) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(base);
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 0xD6, (byte) 0xF0, (byte) 0xD8 }, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        CellStyle body(boolean lift) {
            return lift ? plainLifted : plain;
        }

        CellStyle name(boolean lift) {
            return lift ? nameLifted : nameCell;
        }

        CellStyle average(boolean lift) {
            return lift ? averageLifted : averageCell;
        }
    }
}
