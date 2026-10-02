package it.fn.redfish.catalog.domain;

public enum SpecFormat {
    OPENAPI_2("OpenAPI 2.0 (Swagger)", ServiceType.REST, "json"),
    OPENAPI_3("OpenAPI 3.x", ServiceType.REST, "json"),
    PROTO("Protocol Buffers", ServiceType.GRPC, "proto");

    private final String label;
    private final ServiceType serviceType;
    private final String defaultExtension;

    SpecFormat(String label, ServiceType serviceType, String defaultExtension) {
        this.label = label;
        this.serviceType = serviceType;
        this.defaultExtension = defaultExtension;
    }

    public String getLabel() {
        return it.fn.redfish.catalog.support.I18n.text("enum.SpecFormat." + name());
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }
}
