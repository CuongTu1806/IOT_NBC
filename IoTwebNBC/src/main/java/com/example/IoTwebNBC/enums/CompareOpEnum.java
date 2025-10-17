package com.example.IoTwebNBC.enums;

import lombok.Getter;

@Getter
public enum CompareOpEnum {
    LESS_THAN("<"),
    GRATER_THAN(">"),
    EQUAL("="),
    RANGE("-");

    private final String symbol;
    CompareOpEnum(String s) { this.symbol = s; }

    public static CompareOpEnum fromText(String text) {
        return switch (text.toLowerCase()) {
            case "less than" -> LESS_THAN;
            case "grater than" -> GRATER_THAN;
            case "equal" -> EQUAL;
            default -> RANGE;
        };
    }
}
