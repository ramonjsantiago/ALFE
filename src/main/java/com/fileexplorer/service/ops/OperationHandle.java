package com.fileexplorer.service.ops;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;

/**
 * OperationHandle.
 * <p>
 * Auto-generated API documentation for this type.
 */
public interface OperationHandle {
    String id();
    OperationItem item();

    ReadOnlyObjectProperty<OperationStatus> statusProperty();
    ReadOnlyObjectProperty<OperationProgress> progressProperty();
    ReadOnlyBooleanProperty cancellableProperty();

    void cancel();
}
