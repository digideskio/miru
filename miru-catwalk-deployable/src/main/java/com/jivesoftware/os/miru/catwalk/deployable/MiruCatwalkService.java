package com.jivesoftware.os.miru.catwalk.deployable;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.jivesoftware.os.miru.catwalk.deployable.region.MiruCatwalkPlugin;
import com.jivesoftware.os.miru.catwalk.deployable.region.MiruChromeRegion;
import com.jivesoftware.os.miru.catwalk.deployable.region.MiruHeaderRegion;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import java.util.List;

/**
 *
 */
public class MiruCatwalkService {

    private final MiruSoyRenderer renderer;
    private final MiruHeaderRegion headerRegion;
    private final MiruPageRegion<Void> adminRegion;
    private final MiruPageRegion<Optional<Void>> somethingRegion;

    private final List<MiruCatwalkPlugin> plugins = Lists.newCopyOnWriteArrayList();

    public MiruCatwalkService(
        MiruSoyRenderer renderer,
        MiruHeaderRegion headerRegion,
        MiruPageRegion<Void> adminRegion,
        MiruPageRegion<Optional<Void>> somethingRegion) {
        this.renderer = renderer;
        this.headerRegion = headerRegion;
        this.adminRegion = adminRegion;
        this.somethingRegion = somethingRegion;
    }

    public void registerPlugin(MiruCatwalkPlugin plugin) {
        plugins.add(plugin);
    }

    private <I, R extends MiruPageRegion<I>> MiruChromeRegion<I, R> chrome(R region) {
        return new MiruChromeRegion<>("soy.miru.chrome.chromeRegion", renderer, headerRegion, plugins, region);
    }

    public String render() {
        return chrome(adminRegion).render(null);
    }

    public String renderSomething() {
        return chrome(somethingRegion).render(Optional.<Void>absent());
    }

}
