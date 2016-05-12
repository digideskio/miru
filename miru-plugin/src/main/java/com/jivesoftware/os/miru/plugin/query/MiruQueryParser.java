package com.jivesoftware.os.miru.plugin.query;

import com.jivesoftware.os.miru.api.query.filter.MiruFilter;

/**
 *
 */
public interface MiruQueryParser {

    MiruFilter parse(String locale, String query) throws Exception;
}
