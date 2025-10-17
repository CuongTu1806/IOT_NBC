package com.example.IoTwebNBC.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DataTypeUltil {

    /** Kiểm tra chuỗi có phải số nguyên hoặc số thực (chỉ toàn số, không chữ) */
    public static boolean isNumber(String input) {
        if (input == null || input.isBlank()) return false;
        return input.matches("-?\\d+(\\.\\d+)?");
    }

    /** Kiểm tra chuỗi có đúng định dạng timestamp (ví dụ yyyy-MM-dd HH:mm:ss) */
    public static boolean isTimestamp(String input, String pattern) {
        if (input == null || input.isBlank()) return false;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime.parse(input, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Kiểm tra chuỗi là dạng “string” (có chữ cái hoặc chữ + số) */
    public static boolean isString(String input, String pattern) {
        if (input == null || input.isBlank()) return false;

        // Nếu là timestamp hoặc chỉ toàn số => KHÔNG coi là string
        if (isTimestamp(input, pattern) || isNumber(input)) return false;

        // Còn lại (có chữ cái hoặc ký tự khác) => String
        return true;
    }
}

