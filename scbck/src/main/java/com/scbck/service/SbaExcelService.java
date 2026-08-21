package com.scbck.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.scbck.dto.SbaSheet;
import com.scbck.exception.ApiException;

/**
 * Renders a {@link SbaSheet} as the Department of Examinations' detailed mark
 * sheet.
 *
 * The layout is not ours to choose: the Department reads this workbook by cell
 * position, so the numbered identity block ("01. School", "02. School No" and
 * the rest) and the two-row assessment heading are reproduced exactly as the
 * school's sample has them, down to the numbering.
 *
 * Values only, never formulas - the same reasoning as
 * {@link MarkSheetExcelService}: a total that recalculates on open is a total
 * that can differ from the one the school checked before submitting.
 */
@Service
public class SbaExcelService {

    /** Index, Group, Project, Name, Total - then one column per assessment. */
    private static final int IDENTITY_COLUMNS = 5;

    public byte[] render(SbaSheet sheet) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet target = workbook.createSheet(sheetName(sheet));
            Styles styles = new Styles(workbook);

            int cursor = writeTitle(target, styles, sheet);
            cursor = writeIdentity(target, styles, sheet, cursor);
            cursor = writeHeadings(target, styles, sheet, cursor);
            writeRows(target, styles, sheet, cursor);

            sizeColumns(target, sheet);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException error) {
            throw ApiException.badRequest(
                    "The assessment sheet could not be written: " + error.getMessage());
        }
    }

    public String fileNameFor(SbaSheet sheet) {
        return (sheet.examLabel() + " " + sheet.examYear() + " SBA - " + sheet.subjectName())
                .replaceAll("[^A-Za-z0-9 ./-]", " ")
                .replaceAll("[/]", "-")
                .replaceAll("\\s+", " ")
                .trim() + ".xlsx";
    }

    // ---- Header -------------------------------------------------------------

    private int writeTitle(XSSFSheet target, Styles styles, SbaSheet sheet) {
        int lastColumn = IDENTITY_COLUMNS + sheet.columns().size() - 1;

        Row title = target.createRow(0);
        cell(title, 0, sheet.examLabel() + " Examination - " + sheet.examYear(), styles.title);
        target.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));

        Row subtitle = target.createRow(1);
        cell(subtitle, 0, "School Based Assessment - " + gradeSpan(sheet)
                + " - Detailed Mark Sheet", styles.subtitle);
        target.addMergedRegion(new CellRangeAddress(1, 1, 0, lastColumn));

        return 3;
    }

    /**
     * The Department's numbered identity block.
     *
     * Six labelled items over three rows, in the order and with the numbering
     * the sample uses. The census number is not one of the six, so it goes
     * alongside the school number rather than being given an invented item
     * number of its own.
     */
    private int writeIdentity(XSSFSheet target, Styles styles, SbaSheet sheet, int firstRow) {
        Row one = target.createRow(firstRow);
        labelled(one, 0, "01. Scool", ": " + sheet.schoolName(), styles);
        labelled(one, 3, "02. School No :", sheet.schoolNo(), styles);
        cell(one, 5, "Census No. " + sheet.censusNo(), styles.value);

        Row two = target.createRow(firstRow + 1);
        labelled(two, 0, "03. Zone", ":" + sheet.zone(), styles);
        labelled(two, 3, "04. Medium", sheet.medium(), styles);

        Row three = target.createRow(firstRow + 2);
        labelled(three, 0, "05. Subject No",
                ":" + (sheet.subjectCode() == null ? "" : sheet.subjectCode()), styles);
        labelled(three, 3, "06. Subject", sheet.subjectName(), styles);

        return firstRow + 4;
    }

    /**
     * The two-row assessment heading.
     *
     * "Assesment Category Marks" spans every mark column - spelled as the
     * school's sample spells it, because this is a form the Department reads
     * and a corrected spelling is a changed form. Each grade then spans its own
     * terms.
     */
    private int writeHeadings(XSSFSheet target, Styles styles, SbaSheet sheet, int firstRow) {
        int lastColumn = IDENTITY_COLUMNS + sheet.columns().size() - 1;

        Row banner = target.createRow(firstRow);
        cell(banner, IDENTITY_COLUMNS, "Assesment Category Marks", styles.head);
        target.addMergedRegion(
                new CellRangeAddress(firstRow, firstRow, IDENTITY_COLUMNS, lastColumn));

        Row grades = target.createRow(firstRow + 1);
        Row terms = target.createRow(firstRow + 2);

        String[] identity = { "Index", "Group", "Project", "Name with initials", "Total" };
        for (int index = 0; index < identity.length; index++) {
            cell(grades, index, identity[index], styles.head);
            cell(terms, index, "", styles.head);
            target.addMergedRegion(
                    new CellRangeAddress(firstRow, firstRow + 2, index, index));
        }

        // Group the columns by grade so each grade's heading merges across its
        // own terms, which is what the sample shows.
        Map<Integer, List<SbaSheet.Column>> byGrade = new LinkedHashMap<>();
        for (SbaSheet.Column column : sheet.columns()) {
            byGrade.computeIfAbsent(column.grade(), key -> new java.util.ArrayList<>()).add(column);
        }

        int column = IDENTITY_COLUMNS;
        for (Map.Entry<Integer, List<SbaSheet.Column>> entry : byGrade.entrySet()) {
            int span = entry.getValue().size();
            cell(grades, column, "Grade " + entry.getKey(), styles.head);
            if (span > 1) {
                target.addMergedRegion(new CellRangeAddress(
                        firstRow + 1, firstRow + 1, column, column + span - 1));
            }
            for (SbaSheet.Column term : entry.getValue()) {
                cell(terms, column, term.termLabel(), styles.head);
                column++;
            }
        }

        return firstRow + 3;
    }

    // ---- Body ---------------------------------------------------------------

    private void writeRows(XSSFSheet target, Styles styles, SbaSheet sheet, int firstRow) {
        int rowIndex = firstRow;

        for (SbaSheet.Row row : sheet.rows()) {
            Row line = target.createRow(rowIndex++);

            number(line, 0, row.index(), styles.number);
            cell(line, 1, row.groupName() == null ? "" : row.groupName(), styles.centred);
            if (row.projectMarks() == null) {
                cell(line, 2, "", styles.centred);
            } else {
                number(line, 2, row.projectMarks(), styles.number);
            }
            cell(line, 3, row.nameWithInitials(), styles.text);
            number(line, 4, row.total(), styles.total);

            int column = IDENTITY_COLUMNS;
            for (Integer mark : row.marks()) {
                if (mark == null) {
                    // An empty cell, not a zero: a term nobody has assessed yet
                    // must not read as a candidate who scored nothing.
                    cell(line, column, "", styles.number);
                } else {
                    number(line, column, mark, styles.number);
                }
                column++;
            }
        }

        if (sheet.rows().isEmpty()) {
            Row empty = target.createRow(rowIndex);
            cell(empty, 0, "No candidates are enrolled in the examination grade for "
                    + sheet.examYear() + ".", styles.text);
        }
    }

    // ---- Plumbing -----------------------------------------------------------

    private void labelled(Row row, int column, String label, String value, Styles styles) {
        cell(row, column, label, styles.label);
        cell(row, column + 1, value == null ? "" : value, styles.value);
    }

    private void sizeColumns(XSSFSheet target, SbaSheet sheet) {
        target.setColumnWidth(0, 1800);
        target.setColumnWidth(1, 1800);
        target.setColumnWidth(2, 1800);
        target.setColumnWidth(3, 9000);
        target.setColumnWidth(4, 2200);
        for (int index = 0; index < sheet.columns().size(); index++) {
            target.setColumnWidth(IDENTITY_COLUMNS + index, 2600);
        }
    }

    private String gradeSpan(SbaSheet sheet) {
        List<Integer> grades = com.scbck.model.SbaMark.gradesFor(sheet.exam());
        return "Grade " + grades.get(0) + "-" + grades.get(grades.size() - 1);
    }

    private String sheetName(SbaSheet sheet) {
        String name = sheet.exam() + " " + sheet.subjectName();
        // Excel rejects these characters in a sheet name and truncates at 31.
        String safe = name.replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** The workbook's styles, created once per render. */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle subtitle;
        private final CellStyle label;
        private final CellStyle value;
        private final CellStyle head;
        private final CellStyle text;
        private final CellStyle number;
        private final CellStyle centred;
        private final CellStyle total;

        private Styles(XSSFWorkbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);

            subtitle = workbook.createCellStyle();
            subtitle.setFont(boldFont);
            subtitle.setAlignment(HorizontalAlignment.CENTER);

            label = workbook.createCellStyle();
            label.setFont(boldFont);

            value = workbook.createCellStyle();

            head = workbook.createCellStyle();
            head.setFont(boldFont);
            head.setAlignment(HorizontalAlignment.CENTER);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setWrapText(true);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(head);

            text = workbook.createCellStyle();
            border(text);

            number = workbook.createCellStyle();
            number.setAlignment(HorizontalAlignment.CENTER);
            border(number);

            centred = workbook.createCellStyle();
            centred.setAlignment(HorizontalAlignment.CENTER);
            border(centred);

            total = workbook.createCellStyle();
            total.setFont(boldFont);
            total.setAlignment(HorizontalAlignment.CENTER);
            border(total);
        }

        private static void border(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
