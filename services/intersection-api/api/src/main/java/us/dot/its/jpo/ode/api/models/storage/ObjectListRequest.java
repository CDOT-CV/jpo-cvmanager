package us.dot.its.jpo.ode.api.models.storage;

/** Provider-neutral controls for one bounded object-listing request. */
public record ObjectListRequest(String prefix, boolean recursive, int pageSize, String pageToken) {}
