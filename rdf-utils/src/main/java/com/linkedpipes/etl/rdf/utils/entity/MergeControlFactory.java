package com.linkedpipes.etl.rdf.utils.entity;

import com.linkedpipes.etl.rdf.utils.RdfUtilsException;

public interface MergeControlFactory {

    /**
     * @param type
     * @return Control for object of given type.
     */
    MergeControl create(String type) throws RdfUtilsException;

}
