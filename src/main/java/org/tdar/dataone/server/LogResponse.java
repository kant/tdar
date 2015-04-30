package org.tdar.dataone.server;

import java.util.Date;

import javax.persistence.Transient;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.dataone.bean.Event;
import org.tdar.dataone.service.DataOneService;

@Path("/v1/log")
@Component
@Scope("prototype")
public class LogResponse extends AbstractDataOneResponse {

    @Transient
    private final transient Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private DataOneService service;

    @Context
    private HttpServletResponse response;

    @Context 
    private HttpServletRequest request;

    /**
     * Exceptions.NotAuthorized – (errorCode=401, detailCode=1460)
     * Raised if the user making the request is not authorized to access the log records. This is determined by the policy of the Member Node.

     * Exceptions.InvalidRequest –
     * (errorCode=400, detailCode=1480)
     * The request parameters were malformed or an invalid date range was specified.

     * Exceptions.ServiceFailure – (errorCode=500, detailCode=1490)
     * Exceptions.InvalidToken – (errorCode=401, detailCode=1470)
     * Exceptions.NotImplemented – (errorCode=501, detailCode=1461)
     * 
     * @param fromDate
     * @param toDate
     * @param event
     * @param idFilter
     * @param start
     * @param count
     * @return
     */

    @GET
    @Produces(APPLICATION_XML)
    public Response log(@QueryParam(FROM_DATE) Date fromDate,
            @QueryParam(TO_DATE) Date toDate,
            @QueryParam(EVENT) Event event,
            @QueryParam(ID_FILTER) String idFilter,
            @QueryParam(START) @DefaultValue("0") int start,
            @QueryParam(COUNT) @DefaultValue("1000") int count) {
        setupResponseContext(response);
        try {
            return Response.ok().entity(service.getLogResponse(fromDate, toDate, event, idFilter, start, count, request)).build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.serverError().status(Status.INTERNAL_SERVER_ERROR).build();
    }

}
