package it.fn.redfish.catalog.domain;

public enum ServiceStatus {
    ACTIVE("Attivo"),
    DEPRECATED("Deprecated");

    private final String label;

    ServiceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("enum.ServiceStatus." + name());
    }
}
