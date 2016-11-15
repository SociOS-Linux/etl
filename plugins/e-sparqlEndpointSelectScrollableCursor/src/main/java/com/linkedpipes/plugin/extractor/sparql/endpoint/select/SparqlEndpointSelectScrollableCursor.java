package com.linkedpipes.plugin.extractor.sparql.endpoint.select;

import com.linkedpipes.etl.component.api.Component;
import com.linkedpipes.etl.component.api.service.ExceptionFactory;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.SingleGraphDataUnit;
import com.linkedpipes.etl.dataunit.system.api.files.WritableFilesDataUnit;
import com.linkedpipes.etl.executor.api.v1.exception.LpException;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.query.*;
import org.openrdf.query.impl.SimpleDataset;
import org.openrdf.query.resultio.text.csv.SPARQLResultsCSVWriterFactory;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Use scrollable cursors to execute SPARQL select.
 */
public final class SparqlEndpointSelectScrollableCursor
        implements Component.Sequential {

    /**
     * Wrap that enable us to check if there were any results.
     */
    private static class ResultHandlerWrap implements TupleQueryResultHandler {

        private final TupleQueryResultHandler wrap;

        public boolean solutionHandled = false;

        public ResultHandlerWrap(TupleQueryResultHandler wrap) {
            this.wrap = wrap;
        }

        @Override
        public void handleBoolean(boolean value)
                throws QueryResultHandlerException {
            wrap.handleBoolean(value);
        }

        @Override
        public void handleLinks(List<String> linkUrls)
                throws QueryResultHandlerException {
            wrap.handleLinks(linkUrls);
        }

        @Override
        public void startQueryResult(List<String> bindingNames)
                throws TupleQueryResultHandlerException {
            wrap.startQueryResult(bindingNames);
        }

        @Override
        public void endQueryResult() throws TupleQueryResultHandlerException {
            wrap.endQueryResult();
        }

        @Override
        public void handleSolution(BindingSet bindingSet)
                throws TupleQueryResultHandlerException {
            wrap.handleSolution(bindingSet);
            solutionHandled = true;
        }
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(SparqlEndpointSelectScrollableCursor.class);

    @Component.InputPort(id = "OutputFiles")
    public WritableFilesDataUnit outputFiles;

    @Component.ContainsConfiguration
    @Component.InputPort(id = "Configuration")
    public SingleGraphDataUnit configurationRdf;

    @Component.Inject
    public ExceptionFactory exceptionFactory;

    @Component.Configuration
    public SparqlEndpointSelectScrollableCursorConfiguration configuration;

    @Override
    public void execute() throws LpException {
        final Repository repository = new SPARQLRepository(
                configuration.getEndpoint());
        repository.initialize();
        //
        LOG.info("Used query: {}", prepareQuery(0));
        final File outputFile = outputFiles.createFile(
                configuration.getFileName()).toFile();
        try (final OutputStream stream = new FileOutputStream(outputFile)) {
            final ResultHandlerWrap writer = createWriter(stream);
            //
            int offset = 0;
            while (true) {
                writer.solutionHandled = false;
                LOG.info("offset: {}", offset);
                executeQuery(repository, writer, offset);
                if (!writer.solutionHandled) {
                    break;
                }
                offset += configuration.getSelectSize();
            }
        } catch (IOException ex) {
            throw exceptionFactory.failure("Can't save data.", ex);
        } catch (Throwable t) {
            throw exceptionFactory.failure("Can't query remote SPARQL.", t);
        } finally {
            try {
                repository.shutDown();
            } catch (RepositoryException ex) {
                LOG.error("Can't close repository.", ex);
            }
        }
    }

    protected static ResultHandlerWrap createWriter(OutputStream stream) {
        final SPARQLResultsCSVWriterFactory writerFactory =
                new SPARQLResultsCSVWriterFactory();
        return new ResultHandlerWrap(writerFactory.getWriter(stream));
    }

    /**
     * @param repository
     * @param handler
     * @param offset
     */
    protected void executeQuery(Repository repository,
            TupleQueryResultHandler handler, int offset) throws LpException {
        try (final RepositoryConnection connection =
                     repository.getConnection()) {
            //
            final TupleQuery query = connection.prepareTupleQuery(
                    QueryLanguage.SPARQL, prepareQuery(offset));
            //
            final SimpleDataset dataset = new SimpleDataset();
            for (String iri : configuration.getDefaultGraphs()) {
                if (!iri.isEmpty()) {
                    dataset.addDefaultGraph(
                            SimpleValueFactory.getInstance().createIRI(iri));
                }
            }
            query.setDataset(dataset);
            //
            query.evaluate(handler);
        }
    }

    protected String prepareQuery(int offset) {
        return configuration.getPrefixes() + "\n SELECT " +
                configuration.getOuterSelect() + "\n WHERE { {" +
                configuration.getInnerSelect() +
                "\n} }" +
                "\nLIMIT " + Integer.toString(configuration.getSelectSize()) +
                "\nOFFSET " + Integer.toString(offset);
    }

}
