package com.jivesoftware.os.wiki.miru.deployable.endpoints;

import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.wiki.miru.deployable.WikiMiruService;
import com.jivesoftware.os.wiki.miru.deployable.region.WikiMiruIndexPluginRegion;
import com.jivesoftware.os.wiki.miru.deployable.region.WikiMiruIndexPluginRegion.WikiMiruIndexPluginRegionInput;
import javax.inject.Singleton;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
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
@Path("/wiki/index")
public class WikiMiruIndexPluginEndpoints {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final WikiMiruService wikiMiruService;
    private final WikiMiruIndexPluginRegion pluginRegion;

    public WikiMiruIndexPluginEndpoints(@Context WikiMiruService wikiMiruService, @Context WikiMiruIndexPluginRegion pluginRegion) {
        this.wikiMiruService = wikiMiruService;
        this.pluginRegion = pluginRegion;
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response index(
        @QueryParam("indexerId") @DefaultValue("") String indexerId,
        @QueryParam("tenantId") @DefaultValue("") String tenantId,
        @QueryParam("wikiDumpFile") @DefaultValue("") String wikiDumpFile,
        @QueryParam("action") @DefaultValue("status") String action) {

        try {

            String rendered = wikiMiruService.renderPlugin(pluginRegion, new WikiMiruIndexPluginRegionInput(indexerId, tenantId, wikiDumpFile, action));
            return Response.ok(rendered).build();

        } catch (Exception x) {
            LOG.error("Failed to generating index ui.", x);
            return Response.serverError().build();
        }
    }
}
