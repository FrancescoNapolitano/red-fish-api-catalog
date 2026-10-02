package it.fn.redfish.catalog.domain;

public enum ModelKind {
    SCHEMA("Schema"),
    MESSAGE("Message"),
    ENUM("Enum");

    private final String label;

    ModelKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("enum.ModelKind." + name());
    }
}
