package com.linkedpipes.etl.storage.jar;

import com.linkedpipes.etl.storage.rdf.PojoLoader;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Load the JarComponent from the JAR file. The file must contains
 * LP-ETL/jar/info.* file, where the extension must be a valid RDF
 * file extension.
 */
final class JarLoader {

    private static final Logger LOG = LoggerFactory.getLogger(JarLoader.class);

    JarLoader() {
    }

    public JarComponent load(File jarFile) {
        final JarFile jar;
        try {
            jar = new JarFile(jarFile);
        } catch (IOException ex) {
            LOG.warn("Can not createMappingFromStatements JAR file: {}", jarFile, ex);
            return null;
        }
        // We need to load jar/info.* file.
        for (Enumeration<JarEntry> enums = jar.entries();
                enums.hasMoreElements(); ) {
            final JarEntry entry = enums.nextElement();
            final String name = entry.getName();
            if (name.startsWith("LP-ETL/jar/definition")) {
                return loadFromDefinition(jarFile, jar, entry);
            }
        }
        LOG.warn("No jar-definition found in file: {}", jarFile);
        return null;
    }

    private JarComponent loadFromDefinition(File jarFile, JarFile jar,
            JarEntry entry) {
        // Determine the used RDF format.
        final Optional<RDFFormat> format
                = Rio.getWriterFormatForFileName(entry.getName());
        if (!format.isPresent()) {
            LOG.info("Can't determine format of info file in JAR file: {}",
                    jarFile);
            return null;
        }
        // Read the definition file.
        final RDFParser reader = Rio.createParser(format.get(),
                SimpleValueFactory.getInstance());
        final List<Statement> statements = new ArrayList<>(16);
        final StatementCollector collector = new StatementCollector(statements);
        reader.setRDFHandler(collector);
        try (InputStream stream = jar.getInputStream(entry)) {
            reader.parse(stream, "http://localhost/base");
        } catch (IOException ex) {
            LOG.info("Can't createMappingFromStatements info file in JAR file: {}", jarFile, ex);
            return null;
        }
        // Load definition into the JarComponent.
        final JarComponent component = new JarComponent(jarFile);
        try {
            PojoLoader.loadOfType(statements, JarComponent.TYPE, component);
        } catch (PojoLoader.CantLoadException ex) {
            LOG.info("Can't load metadata from JAR file: {}", jarFile, ex);
            return null;
        }
        //
        LOG.info("path: {}", component.getFile());
        return component;
    }

}
