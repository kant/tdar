package org.tdar.tag;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;

/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.6 in JDK 6
 * Generated source version: 2.1
 * 
 */
@WebServiceClient(name = "TagGatewayService", targetNamespace = TagGatewayService.TAG_SCHEMA_LOCATION, wsdlLocation = TagGatewayService.WSDL)
public class TagGatewayService
        extends Service
{

    public static final String TAG_SCHEMA_LOCATION = "http://archaeologydataservice.ac.uk/tag/schema";
    public static final String WSDL = "/wsdl/tag/tag_gateway_service.wsdl";
    public final static URL TAGGATEWAYSERVICE_WSDL_LOCATION;
    private final static Logger logger = Logger.getLogger(org.tdar.tag.TagGatewayService.class.getName());

    static {
        URL url = null;
        try {
            URL baseUrl;
            baseUrl = org.tdar.tag.TagGatewayService.class.getResource(".");
            url = new URL(baseUrl, WSDL);
        } catch (MalformedURLException e) {
            logger.warning("Failed to create URL for the wsdl Location: '/wsdl/tag/tag_gateway_service.wsdl', retrying as a local file");
            logger.warning(e.getMessage());
        }
        TAGGATEWAYSERVICE_WSDL_LOCATION = url;
    }

    public TagGatewayService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public TagGatewayService() {
        super(TAGGATEWAYSERVICE_WSDL_LOCATION, new QName(TAG_SCHEMA_LOCATION, "TagGatewayService"));
    }

    /**
     * 
     * @return
     *         returns TagGatewayPort
     */
    @WebEndpoint(name = "TagGateway")
    public TagGatewayPort getTagGateway() {
        return super.getPort(new QName(TAG_SCHEMA_LOCATION, "TagGateway"), TagGatewayPort.class);
    }

    /**
     * 
     * @param features
     *            A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy. Supported features not in the <code>features</code> parameter will
     *            have their default values.
     * @return
     *         returns TagGatewayPort
     */
    @WebEndpoint(name = "TagGateway")
    public TagGatewayPort getTagGateway(WebServiceFeature... features) {
        return super.getPort(new QName(TAG_SCHEMA_LOCATION, "TagGateway"), TagGatewayPort.class, features);
    }

}