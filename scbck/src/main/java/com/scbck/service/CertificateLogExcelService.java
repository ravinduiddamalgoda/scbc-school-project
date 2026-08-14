package com.scbck.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.scbck.exception.ApiException;
import com.scbck.model.StudentCertificate;

/**
 * The register of certificates issued: who got what, and when.
 *
 * A school is asked this often enough - by a parent who lost theirs, by an
 * auditor, by a receiving school checking a document is genuine - that the
 * answer needs to be a list rather than a search through a filing cabinet.
 */
@Service
public class CertificateLogExcelService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] render(List<StudentCertificate> issued) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet("Certificates issued");

            Font headFont = workbook.createFont();
            headFont.setBold(true);
            CellStyle head = workbook.createCellStyle();
            head.setFont(headFont);
            head.setBorderBottom(BorderStyle.THIN);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle body = workbook.createCellStyle();

            List<String> headers = List.of("No", "Issued on", "Certificate", "Student",
                    "Admission No", "Last grade", "Reason for leaving", "Issued by (user id)");

            Row heading = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = heading.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(head);
            }

            int rowIndex = 1;
            for (StudentCertificate record : issued) {
                Row row = sheet.createRow(rowIndex);
                write(row, 0, String.valueOf(rowIndex), body);
                write(row, 1, record.getIssued_date() == null ? "" : record.getIssued_date().format(DATE), body);
                write(row, 2, StudentCertificate.CHARACTER.equals(record.getType())
                        ? "Character" : "Leaving", body);
                write(row, 3, record.getStudentName(), body);
                write(row, 4, record.getAdmissionNo(), body);
                write(row, 5, record.getLastGradeCompleted(), body);
                write(row, 6, record.getReasonForLeaving(), body);
                write(row, 7, record.getAdded_user_id() == null ? "" : String.valueOf(record.getAdded_user_id()),
                        body);
                rowIndex++;
            }

            sheet.setColumnWidth(0, 1600);
            sheet.setColumnWidth(1, 3800);
            sheet.setColumnWidth(2, 3200);
            sheet.setColumnWidth(3, 9000);
            sheet.setColumnWidth(4, 3600);
            sheet.setColumnWidth(5, 4000);
            sheet.setColumnWidth(6, 9000);
            sheet.setColumnWidth(7, 4200);
            sheet.createFreezePane(0, 1);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException error) {
            throw ApiException.badRequest("The certificate register could not be written: "
                    + error.getMessage());
        }
    }

    private void write(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }
}
