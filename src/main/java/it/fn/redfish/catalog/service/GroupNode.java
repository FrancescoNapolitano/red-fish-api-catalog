package it.fn.redfish.catalog.service;

import java.util.ArrayList;
import java.util.List;

import it.fn.redfish.catalog.domain.ApiGroup;

public class GroupNode {

    private final ApiGroup group;
    private final List<GroupNode> children = new ArrayList<>();
    private long serviceCount;
    private long totalServiceCount;

    public GroupNode(ApiGroup group) {
        this.group = group;
    }

    public ApiGroup getGroup() {
        return group;
    }

    public Long getId() {
        return group.getId();
    }

    public String getName() {
        return group.getName();
    }

    public String getPath() {
        return group.getPath();
    }

    public int getDepth() {
        return group.getDepth();
    }

    public List<GroupNode> getChildren() {
        return children;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public long getServiceCount() {
        return serviceCount;
    }

    public void setServiceCount(long serviceCount) {
        this.serviceCount = serviceCount;
    }

    public long getTotalServiceCount() {
        return totalServiceCount;
    }

    public void setTotalServiceCount(long totalServiceCount) {
        this.totalServiceCount = totalServiceCount;
    }
}
