package com.linkedpipes.etl.rdf.utils;

import com.linkedpipes.etl.rdf.utils.model.RdfSource;
import com.linkedpipes.etl.rdf.utils.model.TripleWriter;
import com.linkedpipes.etl.rdf.utils.vocabulary.XSD;

/**
 * Can be used to produce RDF statements.
 *
 * The builder must be closed to submit the transaction.
 */
public class RdfBuilder {

    public class EntityBuilder {

        private final EntityBuilder parent;

        private final String resource;

        protected EntityBuilder(EntityBuilder parent, String resource) {
            this.parent = parent;
            this.resource = resource;
        }

        public EntityBuilder entity(String predicate, String resource)
                throws RdfUtilsException {
            writer.iri(this.resource, predicate, resource);
            return new EntityBuilder(this, resource);
        }

        public EntityBuilder iri(String predicate, String value)
                throws RdfUtilsException {
            writer.iri(resource, predicate, value);
            return this;
        }

        public EntityBuilder string(String predicate, String value)
                throws RdfUtilsException {
            writer.string(resource, predicate, value, null);
            return this;
        }

        public EntityBuilder string(String predicate, String value,
                String language) throws RdfUtilsException {
            writer.string(resource, predicate, value, language);
            return this;
        }

        public EntityBuilder integer(String predicate, int value)
                throws RdfUtilsException {
            writer.typed(resource, predicate,
                    Integer.toString(value), XSD.INTEGER);
            return this;
        }

        public EntityBuilder bool(String predicate, boolean value)
                throws RdfUtilsException {
            writer.typed(resource, predicate,
                    Boolean.toString(value), XSD.BOOLEAN);
            return this;
        }

        public EntityBuilder typed(String predicate, String value, String type)
                throws RdfUtilsException {
            writer.typed(resource, predicate, value, type);
            return this;
        }

        public EntityBuilder close() {
            return parent;
        }

    }

    private final TripleWriter writer;

    private RdfBuilder(TripleWriter writer) {
        this.writer = writer;
    }

    public EntityBuilder entity(String resource) {
        return new EntityBuilder(null, resource);
    }

    /**
     * Add triples into the source.
     */
    public void commit() throws RdfUtilsException {
        writer.flush();
    }

    public static RdfBuilder create(RdfSource source, String graph)
            throws RdfUtilsException {
        TripleWriter writer = source.getTripleWriter(graph);
        return new RdfBuilder(writer);
    }

}
