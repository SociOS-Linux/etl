package com.linkedpipes.etl.rdf.utils.rdf4j;

import com.linkedpipes.etl.rdf.utils.RdfUtilsException;
import com.linkedpipes.etl.rdf.utils.model.*;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Rdf4jSource implements RdfSource, RdfSource.SparqlQueryable {

    private final ValueFactory valueFactory = SimpleValueFactory.getInstance();

    protected final Repository repository;

    protected Rdf4jSource(Repository repository) {
        this.repository = repository;
    }

    @Override
    public TripleWriter getTripleWriter(String graph) {
        return new BufferedTripleWriter(graph, repository);
    }

    @Override
    public List<Map<String, RdfValue>> sparqlSelect(String query)
            throws RdfUtilsException {
        List<Map<String, RdfValue>> output = new LinkedList<>();
        try (RepositoryConnection connection = repository.getConnection()) {
            TupleQueryResult result =
                    connection.prepareTupleQuery(query).evaluate();
            while (result.hasNext()) {
                BindingSet bindingSet = result.next();
                output.add(convertBinding(bindingSet));
            }
        } catch (RuntimeException ex) {
            throw new RdfUtilsException("Can't execute query.", ex);
        }
        return output;
    }

    private Map<String, RdfValue> convertBinding(BindingSet bindingSet) {
        Map<String, RdfValue> output = new HashMap<>();
        for (Binding binding : bindingSet) {
            output.put(binding.getName(), new Rdf4jValue(binding.getValue()));
        }
        return output;
    }

    @Override
    public void triples(String graph, TripleHandler handler)
            throws RdfUtilsException {
        triples(null, graph, handler);
    }

    @Override
    public void triples(String resource, String graph, TripleHandler handler)
            throws RdfUtilsException {
        Resource resourceFilter = createResourceOrNull(resource);
        try (RepositoryConnection connection = repository.getConnection()) {
            RepositoryResult<Statement> result = connection.getStatements(
                    resourceFilter, null, null, valueFactory.createIRI(graph));
            while (result.hasNext()) {
                try {
                    handler.handle(new Rdf4jTriple(result.next()));
                } catch (Exception ex) {
                    throw new RdfUtilsException("Handler failed.", ex);
                }
            }
        }
    }

    private Resource createResourceOrNull(String resource) {
        if (resource == null) {
            return null;
        }
        return valueFactory.createIRI(resource);
    }

    @Override
    public SparqlQueryable asQueryable() {
        return this;
    }

    public Repository getRepository() {
        return repository;
    }

    public static ClosableRdf4jSource createInMemory() {
        final Repository repository = new SailRepository(new MemoryStore());
        repository.initialize();
        return new ClosableRdf4jSource(repository);
    }

    public static Rdf4jSource createWrap(Repository repository) {
        return new Rdf4jSource(repository);
    }

}
