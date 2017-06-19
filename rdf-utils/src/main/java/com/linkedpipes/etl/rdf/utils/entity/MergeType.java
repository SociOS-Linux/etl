package com.linkedpipes.etl.rdf.utils.entity;

/**
 * Define merge operation.
 */
public enum MergeType {
    /**
     * Value is added to current value list.
     */
    LOAD,
    /**
     * Value is skipped.
     */
    SKIP,
    /**
     * Only for entities. Merge multiple objects.
     */
    MERGE
}
