package it.fn.redfish.catalog.domain;

public enum SourceType {
    UPLOAD("Upload file"),
    URL("URL remoto");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("enum.SourceType." + name());
    }
}
