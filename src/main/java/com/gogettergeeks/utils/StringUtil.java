package com.gogettergeeks.utils;

public class StringUtil {
    public static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static String camelCaseToUnderscore(String input) {
        StringBuilder result = new StringBuilder();
        if (input != null && !input.isEmpty()) {
            result.append(Character.toLowerCase(input.charAt(0)));
            for (int i = 1; i < input.length(); i++) {
                char currentChar = input.charAt(i);
                if (Character.isUpperCase(currentChar)) {
                    result.append('_').append(Character.toLowerCase(currentChar));
                } else {
                    result.append(currentChar);
                }
            }
        }
        return result.toString();
    }
}
