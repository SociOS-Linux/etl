package com.linkedpipes.plugin.transformer.filesToRdfGraph;

/**
 * Reuse of files to RDF.
 */
public final class FilesToRdfGraphVocabulary {

    private static final String PREFIX
            = "http://plugins.linkedpipes.com/ontology/t-filesToRdfGraph#";

    public static final String CONFIG = PREFIX + "Configuration";

    public static final String HAS_COMMIT_SIZE = PREFIX + "commitSize";

    public static final String HAS_MIME_TYPE = PREFIX + "mimeType";

    public static final String HAS_SKIP_ON_FAILURE = PREFIX + "softFail";

    private FilesToRdfGraphVocabulary() {
    }

}
