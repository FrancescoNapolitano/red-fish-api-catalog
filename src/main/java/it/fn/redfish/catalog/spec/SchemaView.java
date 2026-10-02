package it.fn.redfish.catalog.spec;

import java.util.ArrayList;
import java.util.List;

public class SchemaView {

    private String name;
    private String type = "object";
    private String format;
    private String description;
    private boolean required;
    private boolean deprecated;
    private boolean nullable;
    private boolean readOnly;
    private boolean writeOnly;
    private String refName;
    private String constraints;
    private String defaultValue;
    private String example;
    private String protoType;
    private Integer fieldNumber;
    private String oneofName;
    private List<String> enumValues = new ArrayList<>();
    private final List<SchemaView> children = new ArrayList<>();

    private boolean truncated;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isWriteOnly() {
        return writeOnly;
    }

    public void setWriteOnly(boolean writeOnly) {
        this.writeOnly = writeOnly;
    }

    public String getRefName() {
        return refName;
    }

    public void setRefName(String refName) {
        this.refName = refName;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getProtoType() {
        return protoType;
    }

    public void setProtoType(String protoType) {
        this.protoType = protoType;
    }

    public Integer getFieldNumber() {
        return fieldNumber;
    }

    public void setFieldNumber(Integer fieldNumber) {
        this.fieldNumber = fieldNumber;
    }

    public String getOneofName() {
        return oneofName;
    }

    public void setOneofName(String oneofName) {
        this.oneofName = oneofName;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }

    public List<SchemaView> getChildren() {
        return children;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public String getTypeLabel() {
        StringBuilder sb = new StringBuilder();
        if (refName != null && "array".equals(type)) {
            sb.append("array<").append(refName).append('>');
        } else if (refName != null) {
            sb.append(refName);
        } else {
            sb.append(type == null ? "—" : type);
        }
        if (format != null && !format.isBlank()) {
            sb.append(" (").append(format).append(')');
        }
        if (nullable) {
            sb.append(" | null");
        }
        return sb.toString();
    }
}
