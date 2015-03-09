package com.jivesoftware.os.miru.stumptown.deployable.endpoints;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.jivesoftware.os.jive.utils.jaxrs.util.ResponseHelper;
import com.jivesoftware.os.miru.stumptown.deployable.MiruStumptownService;
import com.jivesoftware.os.miru.stumptown.deployable.region.StumptownQueryPluginRegion;
import com.jivesoftware.os.miru.stumptown.deployable.region.StumptownQueryPluginRegion.StumptownPluginRegionInput;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 *
 */
@Singleton
@Path("/stumptown/query")
public class StumptownQueryPluginEndpoints {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruStumptownService stumptownService;
    private final StumptownQueryPluginRegion pluginRegion;

    private final ResponseHelper responseHelper = ResponseHelper.INSTANCE;

    public StumptownQueryPluginEndpoints(@Context MiruStumptownService stumptownService, @Context StumptownQueryPluginRegion pluginRegion) {
        this.stumptownService = stumptownService;
        this.pluginRegion = pluginRegion;
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response query(
        @QueryParam("cluster") @DefaultValue("dev") String cluster,
        @QueryParam("host") @DefaultValue("") String host,
        @QueryParam("service") @DefaultValue("") String service,
        @QueryParam("instance") @DefaultValue("") String instance,
        @QueryParam("version") @DefaultValue("") String version,
        @QueryParam("logLevels") @DefaultValue("INFO") List<String> logLevels,
        @QueryParam("fromAgo") @DefaultValue("8") int fromAgo,
        @QueryParam("toAgo") @DefaultValue("0") int toAgo,
        @QueryParam("fromTimeUnit") @DefaultValue("MINUTES") String fromTimeUnit,
        @QueryParam("toTimeUnit") @DefaultValue("MINUTES") String toTimeUnit,
        @QueryParam("thread") @DefaultValue("") String thread,
        @QueryParam("logger") @DefaultValue("") String logger,
        @QueryParam("message") @DefaultValue("") String message,
        @QueryParam("thrown") @DefaultValue("") String thrown,
        @QueryParam("buckets") @DefaultValue("30") int buckets,
        @QueryParam("messageCount") @DefaultValue("100") int messageCount,
        @QueryParam("graphType") @DefaultValue("Line") String graphType) {
        String rendered = stumptownService.renderPlugin(pluginRegion,
            Optional.of(new StumptownPluginRegionInput(cluster,
                host,
                service,
                instance,
                version,
                Joiner.on(',').join(logLevels),
                fromAgo,
                toAgo,
                fromTimeUnit,
                toTimeUnit,
                thread,
                logger,
                message,
                thrown,
                buckets,
                messageCount,
                graphType)));
        return Response.ok(rendered).build();
    }

    @POST
    @Path("/poll")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response poll(
        @QueryParam("cluster") @DefaultValue("dev") String cluster,
        @QueryParam("host") @DefaultValue("") String host,
        @QueryParam("service") @DefaultValue("") String service,
        @QueryParam("instance") @DefaultValue("") String instance,
        @QueryParam("version") @DefaultValue("") String version,
        @QueryParam("logLevels") @DefaultValue("INFO") List<String> logLevels,
        @QueryParam("fromAgo") @DefaultValue("8") int fromAgo,
        @QueryParam("toAgo") @DefaultValue("0") int toAgo,
        @QueryParam("fromTimeUnit") @DefaultValue("MINUTES") String fromTimeUnit,
        @QueryParam("toTimeUnit") @DefaultValue("MINUTES") String toTimeUnit,
        @QueryParam("thread") @DefaultValue("") String thread,
        @QueryParam("logger") @DefaultValue("") String logger,
        @QueryParam("message") @DefaultValue("") String message,
        @QueryParam("thrown") @DefaultValue("") String thrown,
        @QueryParam("buckets") @DefaultValue("30") int buckets,
        @QueryParam("messageCount") @DefaultValue("100") int messageCount) {
        try {
            Map<String, Object> result = pluginRegion.poll(new StumptownPluginRegionInput(cluster,
                host,
                service,
                instance,
                version,
                Joiner.on(',').join(logLevels),
                fromAgo,
                toAgo,
                fromTimeUnit,
                toTimeUnit,
                thread,
                logger,
                message,
                thrown,
                buckets,
                messageCount,
                null));
            return responseHelper.jsonResponse(result != null ? result : "");
        } catch (Exception e) {
            LOG.error("Stumptown poll failed", e);
            return responseHelper.errorResponse("Stumptown poll failed", e);
        }
    }

}
