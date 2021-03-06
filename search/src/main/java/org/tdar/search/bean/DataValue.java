package org.tdar.search.bean;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DataValue implements Serializable {

    private static final long serialVersionUID = -3727963215882847225L;

    private Long columnId;
    private Long projectId;
    private List<String> value = new ArrayList<>();
    private String name;
    private boolean singleToken = false;
    
    public DataValue() {}
    
    public DataValue(Long projectId, Long columnId, String name, String value) {
        this.projectId = projectId;
        this.columnId = columnId;
        this.name = name;
        this.value.add(value);
    }

    public List<String> getValue() {
        return value;
    }

    public void setValue(List<String> value) {
        this.value = value;
    }

    public Long getColumnId() {
        return columnId;
    }

    public void setColumnId(Long columnId) {
        this.columnId = columnId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSingleToken() {
        return singleToken;
    }

    public void setSingleToken(boolean singleToken) {
        this.singleToken = singleToken;
    }
}
