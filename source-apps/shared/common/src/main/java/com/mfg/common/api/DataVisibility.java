package com.mfg.common.api;
import java.util.List;
/** Shared SQL visibility hook; security supplies the implementation without a module cycle. */
public interface DataVisibility {
    record Filter(String sql, List<Object> parameters) {}
    Filter filter(String qualifiedTable, String alias);
}
