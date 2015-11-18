package com.jivesoftware.os.miru.tools.deployable.region;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.jivesoftware.os.miru.tools.deployable.MiruToolsService;
import com.jivesoftware.os.miru.tools.deployable.region.TrendingPluginRegion.TrendingPluginRegionInput;
import java.util.List;
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
@Path("/miru/tools/trending")
public class TrendingPluginEndpoints {

    private final MiruToolsService toolsService;
    private final TrendingPluginRegion trendingPluginRegion;

    public TrendingPluginEndpoints(@Context MiruToolsService toolsService, @Context TrendingPluginRegion trendingPluginRegion) {
        this.toolsService = toolsService;
        this.trendingPluginRegion = trendingPluginRegion;
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response getTrending(@QueryParam("tenantId") @DefaultValue("") String tenantId,
        @QueryParam("fromHoursAgo") @DefaultValue("72") int fromHoursAgo,
        @QueryParam("toHoursAgo") @DefaultValue("0") int toHoursAgo,
        @QueryParam("buckets") @DefaultValue("30") int buckets,
        @QueryParam("field") @DefaultValue("authors") String field,
        @QueryParam("typeField") @DefaultValue("") String typeField,
        @QueryParam("types") @DefaultValue("") String typesString,
        @QueryParam("logLevel") @DefaultValue("NONE") String logLevel) {

        List<String> types = null;
        if (typesString != null && !typesString.isEmpty()) {
            types = Lists.newArrayList(Splitter.onPattern("\\s*,\\s*").split(typesString));
            if (types.isEmpty()) {
                types = null;
            }
        }
        String rendered = toolsService.renderPlugin(trendingPluginRegion,
            Optional.of(new TrendingPluginRegionInput(
                tenantId,
                fromHoursAgo,
                toHoursAgo,
                buckets,
                field,
                typeField,
                types,
                logLevel)));
        return Response.ok(rendered).build();
    }
}
