package it.fn.redfish.catalog.web;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.ApiService;
import it.fn.redfish.catalog.service.GroupService;

@Component
public class ServiceBreadcrumbs {

    private final GroupService groups;

    public ServiceBreadcrumbs(GroupService groups) {
        this.groups = groups;
    }

    public List<Breadcrumb> of(ApiService service, String trailing) {
        List<Breadcrumb> crumbs = new ArrayList<>();
        crumbs.add(Breadcrumb.of(I18n.text("ui.catalog"), "/catalog"));
        for (ApiGroup group : groups.ancestry(service.getGroup())) {
            crumbs.add(Breadcrumb.of(group.getName(), "/catalog/" + group.getId()));
        }
        if (trailing == null) {
            crumbs.add(Breadcrumb.current(service.getName()));
        } else {
            crumbs.add(Breadcrumb.of(service.getName(), "/services/" + service.getId()));
            crumbs.add(Breadcrumb.current(trailing));
        }
        return crumbs;
    }
}
