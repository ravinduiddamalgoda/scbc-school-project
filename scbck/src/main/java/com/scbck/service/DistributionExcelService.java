package com.scbck.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

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

import com.scbck.dto.DistributionSheet;
import com.scbck.exception.ApiException;

/**
 * Renders a {@link DistributionSheet} as the workbook the school files.
 *
 * Follows their layout - title, grade and year, then a numbered roster with a
 * column per item - and keeps the signature and notes columns, printed empty.
 * Those are the point of the sheet: it is carried to the store room and signed
 * by each student as they collect, so the export has to be a form, not a
 * report.
 */
@Service
public class DistributionExcelService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm");
    private static final String SCHOOL = "Sri Chandananda Buddhist College, Kandy";

    /** Number and name, before the first item column. */
    private static final int IDENTITY_COLUMNS = 3;

    public byte[] render(DistributionSheet sheet) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet target = workbook.createSheet(sheetName(sheet));
            Styles styles = new Styles(workbook);

            int lastColumn = IDENTITY_COLUMNS + sheet.items().size() + 1;

            Row title = target.createRow(0);
            cell(title, 0, sheet.title() + " — " + orDash(sheet.academicYear()), styles.title);
            target.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));

            Row meta = target.createRow(1);
            cell(meta, 0, "Grade: " + orDash(sheet.gradeName())
                    + "    Class: " + sheet.className()
                    + "    No of Students: " + sheet.rows().size()
                    + "    Generated: " + sheet.generatedAt().format(STAMP), styles.meta);
            target.addMergedRegion(new CellRangeAddress(1, 1, 0, lastColumn));

            // Headings. Item codes head the columns, with the full name as a
            // second row so the sheet carries its own legend - the originals
            // were headed JB(S)/IB(S) and needed someone who already knew.
            Row codes = target.createRow(3);
            Row names = target.createRow(4);

            String[] identity = { "No", "Admission No", "Name of the Student" };
            for (int i = 0; i < identity.length; i++) {
                cell(codes, i, identity[i], styles.head);
                cell(names, i, "", styles.head);
                target.addMergedRegion(new CellRangeAddress(3, 4, i, i));
            }

            int column = IDENTITY_COLUMNS;
            for (DistributionSheet.Item item : sheet.items()) {
                cell(codes, column, item.code(), styles.head);
                cell(names, column, item.name(), styles.headSmall);
                column++;
            }

            cell(codes, column, "Signature of the student", styles.head);
            cell(names, column, "", styles.head);
            target.addMergedRegion(new CellRangeAddress(3, 4, column, column));
            column++;

            cell(codes, column, "Notes", styles.head);
            cell(names, column, "", styles.head);
            target.addMergedRegion(new CellRangeAddress(3, 4, column, column));

            int rowIndex = 5;
            for (DistributionSheet.Row row : sheet.rows()) {
                Row line = target.createRow(rowIndex++);
                line.setHeightInPoints(22);

                cell(line, 0, row.index(), styles.body);
                cell(line, 1, orDash(row.admissionNo()), styles.body);
                cell(line, 2, row.studentName(), styles.name);

                int at = IDENTITY_COLUMNS;
                for (DistributionSheet.Cell issued : row.cells()) {
                    if (issued.quantity() == null) {
                        cell(line, at, "", styles.body);
                    } else {
                        cell(line, at, issued.quantity(), styles.body);
                    }
                    at++;
                }

                // Signature and notes stay blank: they are filled in on paper.
                cell(line, at++, "", styles.body);
                cell(line, at, noteOf(row), styles.name);
            }

            target.setColumnWidth(0, 1400);
            target.setColumnWidth(1, 3600);
            target.setColumnWidth(2, 8000);
            for (int i = IDENTITY_COLUMNS; i < IDENTITY_COLUMNS + sheet.items().size(); i++) {
                target.setColumnWidth(i, 2600);
            }
            target.setColumnWidth(IDENTITY_COLUMNS + sheet.items().size(), 7000);
            target.setColumnWidth(IDENTITY_COLUMNS + sheet.items().size() + 1, 6000);
            target.createFreezePane(IDENTITY_COLUMNS, 5);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException error) {
            throw ApiException.badRequest("The distribution sheet could not be written: " + error.getMessage());
        }
    }

    public String fileNameFor(DistributionSheet sheet) {
        return (sheet.title() + " " + sheet.className() + " " + orDash(sheet.academicYear()))
                .replaceAll("[^A-Za-z0-9 .-]", " ")
                .replaceAll("\\s+", " ")
                .trim() + ".xlsx";
    }

    // -------------------------------------------------------------------------

    /** The first note recorded against the student, if any. */
    private String noteOf(DistributionSheet.Row row) {
        return row.cells().stream()
                .map(DistributionSheet.Cell::note)
                .filter(note -> note != null && !note.isBlank())
                .findFirst()
                .orElse("");
    }

    private String sheetName(DistributionSheet sheet) {
        String raw = sheet.className() + " " + (sheet.kind().charAt(0) + sheet.kind().substring(1).toLowerCase());
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

    /** Created once per workbook; POI caps a file at 64,000 styles. */
    private static final class Styles {

        private final CellStyle title;
        private final CellStyle meta;
        private final CellStyle head;
        private final CellStyle headSmall;
        private final CellStyle body;
        private final CellStyle name;

        Styles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);

            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headFont.setFontHeightInPoints((short) 9);

            Font smallFont = workbook.createFont();
            smallFont.setFontHeightInPoints((short) 8);

            Font bodyFont = workbook.createFont();
            bodyFont.setFontHeightInPoints((short) 10);

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

            headSmall = bordered(workbook, smallFont);
            headSmall.setAlignment(HorizontalAlignment.CENTER);
            headSmall.setWrapText(true);
            headSmall.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headSmall.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            body = bordered(workbook, bodyFont);
            body.setAlignment(HorizontalAlignment.CENTER);
            body.setVerticalAlignment(VerticalAlignment.CENTER);

            name = bordered(workbook, bodyFont);
            name.setAlignment(HorizontalAlignment.LEFT);
            name.setVerticalAlignment(VerticalAlignment.CENTER);
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
    }
}
