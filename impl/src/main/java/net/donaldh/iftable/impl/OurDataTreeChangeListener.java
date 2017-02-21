package net.donaldh.iftable.impl;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yangtools.yang.binding.DataObject;

public abstract class OurDataTreeChangeListener<D extends DataObject> implements DataTreeChangeListener<D>, AutoCloseable {

    protected DataBroker dataBroker;

    public OurDataTreeChangeListener(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    /**
     * Basic Implementation of DataTreeChange Listener to execute add, update or remove command
     * based on the data object modification type.
     */
    @Override
    public void onDataTreeChanged(Collection<DataTreeModification<D>> collection) {
        for (final DataTreeModification<D> change : collection) {
            final DataObjectModification<D> root = change.getRootNode();
            switch (root.getModificationType()) {
                case SUBTREE_MODIFIED:
                    update(change);
                    break;
                case WRITE:
                    // Treat an overwrite as an update
                    boolean update = change.getRootNode().getDataBefore() != null;
                    if (update) {
                        update(change);
                    } else {
                        add(change);
                    }
                    break;
                case DELETE:
                    remove(change);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Method should implements the added data object command.
     * @param newDataObject newly added object
     */
    public abstract void add(DataTreeModification<D> newDataObject);

    /**
     * Method should implements the removed data object command.
     * @param removedDataObject existing object being removed
     */
    public abstract void remove(DataTreeModification<D> removedDataObject);

    /**
     * Method should implements the updated data object command.
     * @param modifiedDataObject existing object being modified
     */
    public abstract void update(DataTreeModification<D> modifiedDataObject);
}
