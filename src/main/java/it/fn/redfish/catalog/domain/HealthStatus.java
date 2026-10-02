package it.fn.redfish.catalog.domain;

public enum HealthStatus {
    UP("Operativo", "success"),
    DOWN("Non raggiungibile", "danger"),
    UNKNOWN("Non configurato", "secondary");

    private final String label;
    private final String variant;

    HealthStatus(String label, String variant) {
        this.label = label;
        this.variant = variant;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("enum.HealthStatus." + name());
    }

    public String getVariant() {
        return variant;
    }
}
