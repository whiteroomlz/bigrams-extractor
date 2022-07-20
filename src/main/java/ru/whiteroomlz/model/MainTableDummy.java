package ru.whiteroomlz.model;

import javafx.beans.property.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MainTableDummy {
    private static final int FIXED_PRECISION = 2;

    private boolean isSignificant = false;
    private final String concept;
    private final int frequency;
    private final double weight;

    public MainTableDummy(ConceptsPair conceptsPair) {
        this.concept = conceptsPair.parent();
        this.frequency = conceptsPair.frequency();
        this.weight = conceptsPair.weight();
    }

    public boolean isSignificant() {
        return isSignificant;
    }

    public BooleanProperty isSignificantProperty() {
        return new SimpleBooleanProperty(isSignificant);
    }

    public void setSignificant(boolean significant) {
        isSignificant = significant;
    }

    public StringProperty conceptProperty() {
        return new SimpleStringProperty(concept);
    }

    public String getConcept() {
        return concept;
    }

    public int getFrequency() {
        return frequency;
    }

    public IntegerProperty frequencyProperty() {
        return new SimpleIntegerProperty(frequency);
    }

    public double getWeight() {
        return weight;
    }

    public DoubleProperty weightProperty() {
        BigDecimal bigDecimal = BigDecimal.valueOf(weight);
        bigDecimal = bigDecimal.setScale(FIXED_PRECISION, RoundingMode.HALF_UP);
        return new SimpleDoubleProperty(bigDecimal.doubleValue());
    }
}
