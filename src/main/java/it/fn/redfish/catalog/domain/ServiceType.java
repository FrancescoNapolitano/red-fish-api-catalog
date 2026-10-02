package it.fn.redfish.catalog.domain;

public enum ServiceType {
    REST("REST"),
    GRPC("gRPC");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("enum.ServiceType." + name());
    }
}
