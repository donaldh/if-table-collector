package net.donaldh.iftable.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.net.donaldh._if.table.collector.rev150105.SnmpAttrs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyChangeListener extends OurDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyChangeListener.class);
    private final ListenerRegistration<TopologyChangeListener> listener;
    private final IfTableCollectorProvider provider;

    public TopologyChangeListener(DataBroker dataBroker, IfTableCollectorProvider provider) {
        super(dataBroker);
        this.provider = provider;

        final DataTreeIdentifier<Node> dataTreeIid =
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, getTopoIid());
        listener = dataBroker.registerDataTreeChangeListener(dataTreeIid, this);
        LOG.info("TopologyChangeListener created and registered");
    }

    private InstanceIdentifier<Node> getTopoIid() {
        final InstanceIdentifier<Node> evcPath = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")))
                .child(Node.class);
        return evcPath;
    }

    @Override
    public void close() throws Exception {
        listener.close();
    }

    @Override
    public void add(DataTreeModification<Node> newDataObject) {
        Node node = newDataObject.getRootNode().getDataAfter();
        String name = node.getKey().getNodeId().getValue();
        LOG.info("Node added to topology-netconf - " + name);

        NetconfNode netconfAugment = node.getAugmentation(NetconfNode.class);
        SnmpAttrs params = node.getAugmentation(SnmpAttrs.class);
        if (params != null
                && params.getPollInterval() != null
                && params.getSnmpCommunity() != null) {
            provider.addNode(name,
                    new String(netconfAugment.getHost().getValue()),
                    params.getSnmpCommunity(),
                    params.getPollInterval());
        }
    }

    @Override
    public void remove(DataTreeModification<Node> removedDataObject) {
        Node node = removedDataObject.getRootNode().getDataBefore();
        String name = node.getKey().getNodeId().getValue();
        provider.removeNode(name);
        LOG.info("Node removed from topology-netconf - " + name);
    }

    @Override
    public void update(DataTreeModification<Node> modifiedDataObject) {
        LOG.info("Node modified in topology-netconf - "
                + modifiedDataObject.getRootNode().getDataAfter().getKey().getNodeId().getValue());
    }


}
