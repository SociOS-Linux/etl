package com.linkedpipes.etl.storage.mapping;

import com.linkedpipes.etl.storage.Configuration;
import com.linkedpipes.etl.storage.rdf.RdfUtils;
import com.linkedpipes.etl.storage.template.Template;
import com.linkedpipes.etl.storage.template.TemplateFacade;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;

/**
 * A single component can have multiple IRIs, those
 * IRIs may appear as the template is imported from one instance to
 * another.
 *
 * This facade should provide functionality for supporting tracking and
 * resolving of these IRIs. Each template can be either original - created
 * from JarTemplate of Template on some instance or it could be imported.
 *
 * For imported templates we keep track of the original template using
 * owl:sameAs predicate.
 *
 * The mapping facade use fixed graph to store RDF data. The triples are of
 * shape ORIGINAL_IRI predicate LOCAL_IRI.
 */
@Service
public class MappingFacade {

    private static final Logger LOG
            = LoggerFactory.getLogger(MappingFacade.class);

    private static final IRI GRAPH;

    private static final String MAPPING_FILE = "mapping.trig";

    static {
        final ValueFactory vf = SimpleValueFactory.getInstance();
        GRAPH = vf.createIRI(
                "http://etl.linkedpipes.com/resources/plugins/mapping");
    }

    @Autowired
    private Configuration configuration;

    @Autowired
    private TemplateFacade templateFacade;

    /**
     * For original template IRI store mappings on local components.
     */
    private Map<String, String> mappings = new HashMap<>();

    private File getMappingFile() {
        return new File(configuration.getKnowledgeDirectory(), MAPPING_FILE);
    }

    @PostConstruct
    public void initialize() throws RdfUtils.RdfException {
        final File mappingFile = getMappingFile();
        // Make sure knowledge directory exists.
        configuration.getKnowledgeDirectory().mkdirs();
        if (!mappingFile.exists()) {
            return;
        }
        // Load data.
        final Collection<Statement> statements = RdfUtils.read(mappingFile);
        for (Statement statement : statements) {
            if (!statement.getContext().equals(GRAPH)) {
                continue;
            }
            if (!statement.getPredicate().equals(OWL.SAMEAS)) {
                continue;
            }
            // Store mapping.
            mappings.put(statement.getSubject().stringValue(),
                    statement.getObject().stringValue());
        }
    }

    /**
     * Export mappings for given templates.
     *
     * @param templates
     * @return
     */
    public Collection<Statement> write(Collection<Template> templates) {
        final List<Statement> result = new LinkedList<>();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        for (Template template : templates) {
            String originalIri = null;
            // We need search the values, as keys are the original IRI.
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (entry.getValue().equals(template.getIri())) {
                    originalIri = entry.getKey();
                    break;
                }
            }
            ;
            if (originalIri == null) {
                // There is no mapping.
                continue;
            }
            result.add(vf.createStatement(vf.createIRI(originalIri),
                    OWL.SAMEAS, vf.createIRI(template.getIri()), GRAPH));
        }
        return result;
    }

    /**
     * Read mapping from given RDF data and return it in form of a mapping
     * object. Does not import nor store any new mappings.
     *
     * @param statements
     * @return Object representing the given mapping.
     */
    public Mapping read(Collection<Statement> statements) {
        // Mapping from remote to localhost based on the "original" iri.
        final Map<String, String> remoteToLocal = new HashMap<>();
        // For remote component store it's original.
        final Map<String, String> remoteToOriginal = new HashMap<>();
        for (Statement statement : statements) {
            if (!statement.getContext().equals(GRAPH)) {
                continue;
            }
            if (!statement.getPredicate().equals(OWL.SAMEAS)) {
                continue;
            }
            // Prepare and store mapping from the template to original.
            final String original = statement.getSubject().stringValue();
            final String remote = statement.getObject().stringValue();
            remoteToOriginal.put(remote, original);
            // Now we use our mapping to get mapping to local components.
            final String local = mappings.get(original);
            if (local != null) {
                remoteToLocal.put(remote, local);
            }
        }
        // Create and return Mapping object. It use the
        // read mapping and as a fallback instance mapping.
        return new Mapping() {

            @Override
            public String remoteToLocal(String iri) {
                // Check known mappings.
                String localMapping = mappings.get(iri);
                if (localMapping != null) {
                    return localMapping;
                }
                // Check mappings from the pipeline.
                localMapping = remoteToLocal.get(iri);
                if (localMapping != null) {
                    return localMapping;
                }
                // The mapping could be newly added during pipeline import
                // we need to check for original.
                String original = remoteToOriginal.getOrDefault(iri, iri);
                return mappings.getOrDefault(original, original);
            }

            public String toOriginal(String iri) {
                return remoteToOriginal.getOrDefault(iri, iri);
            }
        };
    }

    /**
     * Add source IRI (mapping) for given template. If any other mapping
     * exists it is overwritten.
     *
     * @param template
     * @param originalIri IRI of the original template.
     */
    public void add(Template template, String originalIri) {
        if (mappings.containsKey(originalIri)) {
            LOG.error("There is already a local template form given IRI.");
        }
        mappings.put(originalIri, template.getIri());
    }

    /**
     * Remove mappings for the template.
     *
     * @param template
     */
    public void remove(Template template) {
        mappings.values().remove(template);
    }

    /**
     * Should be called after any call (or sequence of calls) of
     * {@link #add(Template, String)}.
     */
    public void save() {
        // Create RDF representation.
        final List<Statement> result = new LinkedList<>();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            result.add(vf.createStatement(
                    vf.createIRI(entry.getKey()),
                    OWL.SAMEAS,
                    vf.createIRI(entry.getValue()),
                    GRAPH
            ));
        }
        // Save to file.
        final File mappingFile = new File(configuration.getKnowledgeDirectory(),
                MAPPING_FILE);
        try {
            RdfUtils.write(mappingFile, RDFFormat.TRIG, result);
        } catch (RdfUtils.RdfException ex) {
            LOG.error("Can't save new mappings.", ex);
        }
    }

}
