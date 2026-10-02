package it.fn.redfish.catalog.support;

import it.fn.redfish.catalog.support.I18n;
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + I18n.text("message.not.found") + id);
    }
}
