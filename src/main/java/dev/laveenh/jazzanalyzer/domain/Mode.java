package dev.laveenh.jazzanalyzer.domain;

public enum Mode {
    MAJOR("major"),
    MINOR("minor");

    private final String label;

    Mode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
