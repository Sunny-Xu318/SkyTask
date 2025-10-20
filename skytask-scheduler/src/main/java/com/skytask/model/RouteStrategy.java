package com.skytask.model;

public enum RouteStrategy {
    ROUND_ROBIN,
    CONSISTENT_HASH,
    SHARDING,
    FIXED_NODE
}
