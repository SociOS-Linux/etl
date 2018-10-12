package com.linkedpipes.etl.executor.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkedpipes.etl.executor.api.v1.vocabulary.LP_OVERVIEW;
import com.linkedpipes.etl.executor.pipeline.model.PipelineComponent;
import com.linkedpipes.etl.executor.pipeline.model.PipelineModel;
import com.linkedpipes.etl.rdf.utils.RdfFormatter;
import com.linkedpipes.etl.rdf.utils.vocabulary.XSD;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Date;

public class ExecutionOverview {

    private final String executionIri;

    private String pipelineIri;

    private final File directory;

    private int numberOfComponentsToExecute;

    private int numberOfFinishedComponents;

    private String pipelineStarted;

    private String pipelineFinished;

    private Long directorySize = null;

    /**
     * Used only to read the status.
     */
    private final ExecutionStatusMonitor statusMonitor;

    private String lastChange;

    public ExecutionOverview(
            File directory,
            String executionIri,
            ExecutionStatusMonitor statusMonitor) {
        onBeforeUpdate();
        this.directory = directory;
        this.executionIri = executionIri;
        this.statusMonitor = statusMonitor;
    }

    /**
     * Upon component begin the information about data units is written into
     * execution, so we need to change tha last change status.
     */
    public void onExecutionBegin(Date date) {
        onBeforeUpdate();
        pipelineStarted = RdfFormatter.toXsdDate(date);
    }

    public void onPipelineLoaded(PipelineModel pipeline) {
        onBeforeUpdate();
        pipelineIri = pipeline.getIri();
        numberOfComponentsToExecute = 0;
        for (PipelineComponent component : pipeline.getComponents()) {
            if (component.shouldExecute()) {
                ++numberOfComponentsToExecute;
            }
        }
    }

    public void onComponentBegin() {
        onBeforeUpdate();
    }

    private void onBeforeUpdate() {
        this.lastChange = RdfFormatter.toXsdDate(new Date());
    }

    public void onComponentExecutionEnd() {
        onBeforeUpdate();
        ++numberOfFinishedComponents;
    }

    public void onExecutionCancelling() {
        onBeforeUpdate();
    }

    public void onExecutionEnd(Date date) {
        onBeforeUpdate();
        pipelineFinished = RdfFormatter.toXsdDate(date);
        computeDirectorySize();
    }

    private void computeDirectorySize() {
        this.directorySize = FileUtils.sizeOfDirectory(directory);
    }

    public ObjectNode toJsonLd(ObjectMapper mapper) {
        final ObjectNode contextNode = mapper.createObjectNode();
        contextNode.put("pipeline", LP_OVERVIEW.HAS_PIPELINE);
        contextNode.put("execution", LP_OVERVIEW.HAS_EXECUTION);

        final ObjectNode startNode = mapper.createObjectNode();
        startNode.put("@id", LP_OVERVIEW.HAS_START);
        startNode.put("@type", XSD.DATETIME);
        contextNode.set("executionStarted", startNode);

        final ObjectNode finishedNode = mapper.createObjectNode();
        finishedNode.put("@id", LP_OVERVIEW.HAS_END);
        finishedNode.put("@type", XSD.DATETIME);
        contextNode.set("executionFinished", finishedNode);

        contextNode.put("status", LP_OVERVIEW.HAS_STATUS);

        final ObjectNode lastChangNode = mapper.createObjectNode();
        lastChangNode.put("@id", LP_OVERVIEW.HAS_LAST_CHANGE);
        lastChangNode.put("@type", XSD.DATETIME);
        contextNode.set("lastChange", lastChangNode);

        contextNode.put("pipelineProgress", LP_OVERVIEW.HAS_PIPELINE_PROGRESS);
        contextNode.put("total", LP_OVERVIEW.HAS_PROGRESS_TOTAL);
        contextNode.put("current", LP_OVERVIEW.HAS_PROGRESS_CURRENT);

        final ObjectNode responseNode = mapper.createObjectNode();
        responseNode.set("@context", contextNode);
        responseNode.put("@id", executionIri + "/overview");

        final ObjectNode pipelineNode = mapper.createObjectNode();
        pipelineNode.put("@id", pipelineIri);
        responseNode.set("pipeline", pipelineNode);

        final ObjectNode executionNode = mapper.createObjectNode();
        executionNode.put("@id", executionIri);
        responseNode.set("execution", executionNode);

        if (pipelineStarted != null) {
            responseNode.put("executionStarted", pipelineStarted);
        }
        if (pipelineFinished != null) {
            responseNode.put("executionFinished", pipelineFinished);
        }

        final ObjectNode statusNode = mapper.createObjectNode();
        statusNode.put("@id", statusMonitor.getStatus().getIri());
        responseNode.set("status", statusNode);

        responseNode.put("lastChange", lastChange);

        final ObjectNode executionProgressNode = mapper.createObjectNode();
        executionProgressNode.put("@id",
                executionIri + "/overview/executionProgress");
        executionProgressNode.put("total", numberOfComponentsToExecute);
        executionProgressNode.put("current", numberOfFinishedComponents);

        responseNode.set("pipelineProgress", executionProgressNode);

        if (this.directorySize != null) {
            responseNode.put("directorySize", directorySize);
            contextNode.put("directorySize", LP_OVERVIEW.HAS_DIRECTORY_SIZE);
        }

        return responseNode;
    }

}
