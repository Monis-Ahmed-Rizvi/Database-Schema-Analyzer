package com.schema.analyzer.model;

/**
 * Enum representing different database normalization forms.
 */
public enum NormalizationForm {
    FIRST_NORMAL_FORM("1NF"),
    SECOND_NORMAL_FORM("2NF"),
    THIRD_NORMAL_FORM("3NF"),
    BOYCE_CODD_NORMAL_FORM("BCNF"),
    FOURTH_NORMAL_FORM("4NF"),
    FIFTH_NORMAL_FORM("5NF");
    
    private final String display;
    
    NormalizationForm(String display) {
        this.display = display;
    }
    
    public String getDisplay() {
        return display;
    }
    
    @Override
    public String toString() {
        return display;
    }
}
