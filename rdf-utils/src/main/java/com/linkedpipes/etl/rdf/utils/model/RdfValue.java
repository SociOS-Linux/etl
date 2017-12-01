package com.linkedpipes.etl.rdf.utils.model;

import com.linkedpipes.etl.rdf.utils.RdfUtilsException;

/**
 * Library independent representation of a RDF object with value.
 */
public interface RdfValue {

    String asString();

    long asLong() throws RdfUtilsException;

    boolean asBoolean() throws RdfUtilsException;

    /**
     * @return Can be null.
     */
    String getType();

    /**
     * @return Can be null.
     */
    String getLanguage();

    boolean isIri();

}
