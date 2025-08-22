package com.appdynamics.extensions.prometheus.util;

public class StringUtils {

    public static boolean isNullOrEmpty(String str) {
        return (str == null || str.isEmpty());
    }

    /**
     * Converts a string representing a floating-point number to its rounded long value as a string.
     * Example: "123.56" -> "124"
     *
     * @param input the number as a string
     * @return the rounded long value as a string
     * @throws NumberFormatException if the input is not a valid number
     */
    public static String strToLongStr(String input) {
        double value = Double.parseDouble(input);   // Parse as double for flexibility
        long roundedValue = Math.round(value);      // Round to nearest long
        return Long.toString(roundedValue);         // Convert to string
    }
}
