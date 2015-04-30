package org.tdar.dataone.service;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.tdar.utils.SimpleHttpUtils;
import org.tdar.utils.TestConfiguration;
import org.tdar.web.AbstractWebTestCase;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;

public class DataOneWebITCase extends AbstractWebTestCase {

    private static final String TEST_DOI = "doi:10.6067:XCV8SN0B29" + DataOneService.D1_SEP + "format=d1rem";

    @Test
    public void ping() {
        Assert.assertEquals(gotoPage("/dataone/v1/monitor/ping"), 200);
    }

    @Test
    public void systemInfo() {
        Assert.assertEquals(200, gotoPage("/dataone/v1/"));
        logger.debug(getPageCode());
        Assert.assertEquals(200, gotoPage("/dataone/v1/node"));
        logger.debug(getPageCode());
    }

    @Test
    public void testObject() throws FailingHttpStatusCodeException, MalformedURLException, IOException {
        String path = "/dataone/v1/object/" + TEST_DOI;
        
        HttpHead headMethod = null;                 
        headMethod = new HttpHead(TestConfiguration.getInstance().getBaseSecureUrl() + path);
                        
       
        CloseableHttpClient httpClient = SimpleHttpUtils.createClient();
     
        HttpResponse httpResponse = httpClient.execute(headMethod);
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        Assert.assertEquals(200, statusCode);
        for (Header header : httpResponse.getAllHeaders()) {
            logger.debug("headers: {}", header);
        }
    }

    @Test
    public void testMeta() {
        Assert.assertEquals(200, gotoPage("/dataone/v1/meta/"+ TEST_DOI));
        logger.debug(getPageCode());
    }
    @Test
    public void testMetaIvalid() {
        Assert.assertEquals(404, gotoPage("/dataone/v1/meta/a"+ TEST_DOI));
        logger.debug(getPageCode());
    }
}
