package com.linkedpipes.etl.executor.dataunit;

import com.linkedpipes.etl.executor.ExecutorException;
import com.linkedpipes.etl.executor.api.v1.dataunit.DataUnit;
import com.linkedpipes.etl.executor.api.v1.dataunit.ManageableDataUnit;
import com.linkedpipes.etl.executor.execution.model.ExecutionModel;
import com.linkedpipes.etl.executor.pipeline.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * This class is responsible for handling dataunit management.
 */
public class DataUnitManager {

    public interface DataUnitInstanceSource {

        /**
         * @param iri
         * @return Instance for data unit of given IRI.
         */
        ManageableDataUnit getDataUnit(String iri) throws ExecutorException;

    }

    private static final Logger LOG =
            LoggerFactory.getLogger(DataUnitManager.class);

    private final Map<ExecutionModel.DataUnit, DataUnitContainer> dataUnits
            = new HashMap<>();

    /**
     * Map of instances. Given to data units for initialization.
     */
    private final Map<String, ManageableDataUnit> instances = new HashMap<>();

    private final PipelineModel pipeline;

    public DataUnitManager(
            PipelineModel pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * @param dataUnitInstanceSource Source of data unit instances.
     * @param dataUnits Data units to bindToPipeline.
     */
    public void initialize(DataUnitInstanceSource dataUnitInstanceSource,
            Collection<ExecutionModel.DataUnit> dataUnits)
            throws ExecutorException {
        // Create instances of data units.
        for (ExecutionModel.DataUnit dataUnit : dataUnits) {
            createDataUnitContainer(dataUnitInstanceSource, dataUnit);
        }
    }

    /**
     * Close all opened data units.
     */
    public void close() {
        for (DataUnitContainer container : dataUnits.values()) {
            try {
                container.close();
            } catch (ExecutorException ex) {
                LOG.info("Can't closeRepository data unit: {}", ex);
            }
        }
    }

    /**
     * Prepare data unit used by given component.
     *
     * @param component
     * @return Data units referred by given component.
     */
    public Map<String, DataUnit> onComponentWillExecute(
            ExecutionModel.Component component) throws ExecutorException {
        final Map<String, DataUnit> usedDataUnits = new HashMap<>();
        for (ExecutionModel.DataUnit dataUnit : component.getDataUnits()) {
            final DataUnitContainer container = dataUnits.get(dataUnit);
            if (container == null) {
                throw new ExecutorException("Missing data unit: {} for {}",
                        dataUnit.getDataUnitIri(), component.getComponentIri());
            }
            //
            final File dataDirectory = dataUnit.getLoadDirectory();
            if (dataDirectory == null) {
                container.initialize(instances);
            } else {
                container.initialize(dataDirectory);
            }
            usedDataUnits.put(dataUnit.getDataUnitIri(),
                    container.getInstance());
        }
        return usedDataUnits;
    }

    /**
     * Called when component has been executed.
     *
     * @param component
     */
    public void onComponentDidExecute(ExecutionModel.Component component)
            throws ExecutorException {
        for (ExecutionModel.DataUnit dataUnit : component.getDataUnits()) {
            final DataUnitContainer container = dataUnits.get(dataUnit);
            if (container == null) {
                throw new ExecutorException("Missing data unit: {} for {}",
                        dataUnit.getDataUnitIri(), component.getComponentIri());
            }
            if (dataUnit.getPort().isSaveDebugData()) {
                container.save();
            }
        }
    }

    public void onComponentMapByReference(ExecutionModel.Component component)
            throws ExecutorException {
        for (ExecutionModel.DataUnit dataUnit : component.getDataUnits()) {
            final DataUnitContainer container = dataUnits.get(dataUnit);
            if (container == null) {
                throw new ExecutorException("Missing data unit: {} for {}",
                        dataUnit.getDataUnitIri(), component.getComponentIri());
            }
            final File sourceFile = dataUnit.getLoadDirectory();
            if (isDataUnitUsed(component, dataUnit)) {
                container.initialize(sourceFile);
                container.save();
            } else {
                container.mapByReference(sourceFile);
            }
        }
    }

    /**
     * Create {@link DataUnitContainer} and add it to {@link #dataUnits}.
     *
     * If the data unit should not be used during the execution, then
     * nothing happen.
     *
     * @param dataUnitInstanceSource
     * @param dataUnit
     */
    private void createDataUnitContainer(
            DataUnitInstanceSource dataUnitInstanceSource,
            ExecutionModel.DataUnit dataUnit) throws ExecutorException {
        final String iri = dataUnit.getDataUnitIri();
        final ManageableDataUnit instance;
        try {
            instance = dataUnitInstanceSource.getDataUnit(iri);
        } catch (ExecutorException ex) {
            throw new ExecutorException("Can't instantiate data unit: {}",
                    iri, ex);
        }
        dataUnits.put(dataUnit, new DataUnitContainer(instance, dataUnit));
        instances.put(iri, instance);
    }

    private boolean isDataUnitUsed(ExecutionModel.Component component,
            ExecutionModel.DataUnit dataUnit) throws ExecutorException {
        final Component pplComponent =
                pipeline.getComponent(component.getComponentIri());
        if (pplComponent == null) {
            throw new ExecutorException(
                    "Missing component definition: {} for {}",
                    component.getComponentIri());
        }
        final Port pplPort =
                pplComponent.getPort(dataUnit.getDataUnitIri());
        if (pplPort == null) {
            throw new ExecutorException("Missing definition: {} for {}",
                    dataUnit.getDataUnitIri(), component.getComponentIri());
        }
        return isPortUsed(pplComponent, pplPort);
    }

    public boolean isPortUsed(Component component, Port port)
            throws ExecutorException {
        switch (component.getExecutionType()) {
            case EXECUTE:
                return true;
            case SKIP:
                return false;
            case MAP:
                break;
            default:
                throw new ExecutorException("Invalid execution type: {} ",
                        component.getExecutionType());
        }
        if (port.isInput()) {
            return false;
        }
        for (Connection connection : findConnections(component, port)) {
            final Component source =
                    pipeline.getComponent(connection.getSourceComponent());
            if (source.getExecutionType() == ExecutionType.EXECUTE) {
                return true;
            }
            final Component target =
                    pipeline.getComponent(connection.getTargetComponent());
            if (target.getExecutionType() == ExecutionType.EXECUTE) {
                return true;
            }
        }
        return false;
    }

    private Collection<Connection> findConnections(
            Component component, Port port) {
        final Collection<Connection> output = new LinkedList<>();
        final String componentIri = component.getIri();
        final String binding = port.getBinding();
        for (Connection connection : pipeline.getConnections()) {
            if (!connection.isDataConnection()) {
                continue;
            }
            if (connection.getSourceComponent().equals(componentIri) &&
                    connection.getSourceBinding().equals(binding)) {
                output.add(connection);
                continue;
            }
            if (connection.getTargetComponent().equals(componentIri) &&
                    connection.getTargetBinding().equals(binding)) {
                output.add(connection);
                continue;
            }
        }
        return output;
    }


}
