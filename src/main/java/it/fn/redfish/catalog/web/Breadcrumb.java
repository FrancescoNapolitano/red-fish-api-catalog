package it.fn.redfish.catalog.web;

import java.util.ArrayList;
import java.util.List;

public record Breadcrumb(String label, String url) {

    public static Breadcrumb of(String label, String url) {
        return new Breadcrumb(label, url);
    }

    public static Breadcrumb current(String label) {
        return new Breadcrumb(label, null);
    }

    public static List<Breadcrumb> trail(Breadcrumb... items) {
        return new ArrayList<>(List.of(items));
    }
}
