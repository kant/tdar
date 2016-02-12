package org.tdar.tag;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;

/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.6 in JDK 6
 * Generated source version: 2.1
 * 
 */
@WebService(name = "TagGatewayPort", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema")
@XmlSeeAlso({
        ObjectFactory.class
})
public interface TagGatewayPort {

    /**
     * 
     * @param numberOfRecords
     * @param sessionId
     * @param query
     * @return
     *         returns org.tdar.tag.SearchResults
     */
    @WebMethod(operationName = "GetTopRecords", action = "http://archaeologydataservice.ac.uk/tag/schema/GetTopRecords")
    @WebResult(name = "SearchResults", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema")
    @RequestWrapper(localName = "GetTopRecords", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema", className = "org.tdar.tag.GetTopRecords")
    @ResponseWrapper(localName = "GetTopRecordsResponse", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema",
            className = "org.tdar.tag.GetTopRecordsResponse")
    SearchResults getTopRecords(
            @WebParam(name = "sessionId", targetNamespace = "") String sessionId,
            @WebParam(name = "Query", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema") Query query,
            @WebParam(name = "numberOfRecords", targetNamespace = "") int numberOfRecords);

    /**
     * 
     * @param parameters
     * @return
     *         returns org.tdar.tag.GetXsltTemplateResponse
     */
    @WebMethod(operationName = "GetXsltTemplate", action = "http://archaeologydataservice.ac.uk/tag/schema/GetXsltTemplate")
    @WebResult(name = "GetXsltTemplateResponse", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema", partName = "parameters")
    @SOAPBinding(parameterStyle = SOAPBinding.ParameterStyle.BARE)
    GetXsltTemplateResponse getXsltTemplate(
            @WebParam(name = "GetXsltTemplate", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema", partName = "parameters") GetXsltTemplate parameters);

    /**
     * 
     * @return
     *         returns java.lang.String
     */
    @WebMethod(operationName = "GetVersion", action = "http://archaeologydataservice.ac.uk/tag/schema/GetVersion")
    @WebResult(name = "version", targetNamespace = "")
    @RequestWrapper(localName = "GetVersion", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema", className = "org.tdar.tag.GetVersion")
    @ResponseWrapper(localName = "GetVersionResponse", targetNamespace = "http://archaeologydataservice.ac.uk/tag/schema",
            className = "org.tdar.tag.GetVersionResponse")
    String getVersion();

}