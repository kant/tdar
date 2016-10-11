package org.tdar.struts.action.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.tdar.core.service.SerializationService;
import org.tdar.struts.action.AbstractAuthenticatableAction;
import org.tdar.struts_base.action.TdarActionSupport;

import com.opensymphony.xwork2.Preparable;

@Results(value = {
        @Result(name = TdarActionSupport.SUCCESS, type = TdarActionSupport.JSONRESULT, params = { "stream", "jsonInputStream" }),
        @Result(name = TdarActionSupport.INPUT, type = TdarActionSupport.JSONRESULT, params = { "stream", "jsonInputStream", "statusCode", "500" })
})
public abstract class AbstractJsonApiAction extends AbstractAuthenticatableAction implements Preparable {

    private static final long serialVersionUID = -1603470633052691056L;
    private InputStream jsonInputStream;

    @Autowired
    protected transient SerializationService serializationService;

    public InputStream getJsonInputStream() {
        return jsonInputStream;
    }

    protected final void setJsonInputStream(InputStream jsonInputStream) {
        this.jsonInputStream = jsonInputStream;
    }

    /**
     * Convenience method for serializing the specified object and converting it to an inputStream.
     * 
     * @param obj
     *            object to stringify
     * @param jsonFilter
     *            JSON filter view to use during serialization
     * @throws IOException
     */
    protected final void setJsonObject(Object obj, Class<?> jsonFilter) throws IOException {
        String message = serializationService.convertToFilteredJson(obj, jsonFilter);
        getLogger().trace(message);
        setJsonInputStream(new ByteArrayInputStream(message.getBytes()));
    }

    /**
     * Convenience method for serializing the specified object and converting it to an inputStream.
     * 
     * @param obj
     *            object to stringify
     * @throws IOException
     */
    protected final void setJsonObject(Object obj) throws IOException {
        setJsonObject(obj, null);
    }

    public void prepare() throws Exception {};
}