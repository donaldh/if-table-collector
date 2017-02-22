package net.donaldh.iftable.impl;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;

import org.opendaylight.controller.md.sal.dom.api.DOMNotificationPublishService;
import org.opendaylight.controller.messagebus.app.util.TopicDOMNotification;
import org.opendaylight.controller.messagebus.spi.EventSource;
import org.opendaylight.snmp.OID;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicNotification;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.DisJoinTopicInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.JoinTopicStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.smiv2._if.mib.rev000614.interfaces.group.IfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Optional;

import net.donaldh.snmp.MibTable;

public class IfTableEventSource implements EventSource {

	private static final Logger LOG = LoggerFactory.getLogger(IfTableEventSource.class);
    private final java.lang.String name = "if-table-collector";
    private final java.lang.String namespace = "urn:net:donaldh:if-table-collector";
    private final java.lang.String revision = "2017-02-22";

    public static final String XMLNS_ATTRIBUTE_KEY = "xmlns";
    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";

    private static final NodeIdentifier TOPIC_NOTIFICATION_ARG = new NodeIdentifier(TopicNotification.QNAME);
    private static final NodeIdentifier EVENT_SOURCE_ARG = new NodeIdentifier(QName.create(TopicNotification.QNAME, "node-id").intern());
    private static final NodeIdentifier TOPIC_ID_ARG = new NodeIdentifier(QName.create(TopicNotification.QNAME, "topic-id").intern());
    private static final NodeIdentifier PAYLOAD_ARG = new NodeIdentifier(QName.create(TopicNotification.QNAME, "payload").intern());

	private final DOMNotificationPublishService publishService;
	private final Snmp snmp;

	private final String address;
	private final String community;

	private final NodeKey nodeKey;
	private final List<SchemaPath> schemaPaths = new ArrayList<>();
	private Set<TopicId> acceptedTopics = new HashSet<>();

	public IfTableEventSource(DOMNotificationPublishService publishService, Snmp snmp, String address, String community) {
		this.publishService = publishService;
		this.snmp = snmp;
		this.address = address;
		this.community = community;

        nodeKey = new NodeKey(new NodeId(address));
		schemaPaths.add(SchemaPath.create(true, QName.create(namespace, revision, name)));
	}

	@Override
	public Future<RpcResult<Void>> disJoinTopic(DisJoinTopicInput input) {
		LOG.info("Node {} DisJoin topic {}", address,
				input.getTopicId().getValue());
		boolean removed = acceptedTopics.remove(input.getTopicId());
		if (removed == false) {
			LOG.warn("Node {} DisJoin topic {} - nothing to remove", address,
					input.getTopicId().getValue());
		}
		return immediateFuture(RpcResultBuilder.success((Void) null).build());
	}

	@Override
	public Future<RpcResult<JoinTopicOutput>> joinTopic(JoinTopicInput input) {
		LOG.info("Node {} Join topic {}", address, input.getTopicId().getValue());
		boolean added = acceptedTopics.add(input.getTopicId());
		final JoinTopicOutput output = new JoinTopicOutputBuilder().setStatus(
				added ? JoinTopicStatus.Up : JoinTopicStatus.Down).build();
        return immediateFuture(RpcResultBuilder.success(output).build());
	}

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public NodeKey getSourceNodeKey() {
	    return nodeKey;
	}

	@Override
	public List<SchemaPath> getAvailableNotifications() {
		return Collections.unmodifiableList(this.schemaPaths);
	}

	public void execute() {
		LOG.info("Executing poll cycle for " + address + " ...");
		MibTable<IfEntryBuilder> historyTable =
		        new MibTable<>(snmp, new Ipv4Address(address), community,
		                IfEntryBuilder.class);

		Map<Integer, IfEntryBuilder> historyStatsBuilders = historyTable.populate();
		LOG.info("Polled " + historyStatsBuilders.size() + " rows.");

		for (Integer index : historyStatsBuilders.keySet()) {
			IfEntryBuilder entryBuilder = historyStatsBuilders.get(index);
			AnyXmlNode any = encapsulate(entryBuilder);

            final ContainerNode topicNotification = Builders.containerBuilder()
                    .withNodeIdentifier(TOPIC_NOTIFICATION_ARG)
                    .withChild(ImmutableNodes.leafNode(TOPIC_ID_ARG, new TopicId(address)))
                    .withChild(ImmutableNodes.leafNode(EVENT_SOURCE_ARG, "soamcollector"))
                    .withChild(any)
                    .build();

			try {
				publishService.putNotification(new TopicDOMNotification(topicNotification));
			} catch (InterruptedException e) {
				LOG.warn("Failed to put notification: " + e.getMessage());
			}
		}
	}

    private AnyXmlNode encapsulate(IfEntryBuilder statsEntry) {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;

        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Can not create XML DocumentBuilder");
        }

        Document doc = docBuilder.newDocument();

        final Optional<String> namespace = Optional.of(PAYLOAD_ARG.getNodeType().getNamespace().toString());
        final Element rootElement = createElement(doc , "payload", namespace);

        final Element sourceElement = doc.createElement("source");
        sourceElement.appendChild(doc.createTextNode(address));
        rootElement.appendChild(sourceElement);

        final Element messageElement = doc.createElement("message");
        messageElement.appendChild(toXml(doc, statsEntry));
        rootElement.appendChild(messageElement);

        return Builders.anyXmlBuilder().withNodeIdentifier(PAYLOAD_ARG)
                     .withValue(new DOMSource(rootElement))
                     .build();
    }

    private Element toXml(Document doc, IfEntryBuilder entry) {
    	final Element container = doc.createElement(entry.getClass().getSimpleName());

    	for (Method method : entry.getClass().getMethods()) {
    	    OID oid = method.getAnnotation(OID.class);
    	    String name = method.getName();
    		if (oid != null && name.startsWith("get")) {
    			try {
        			name = name.substring(3);
					final Element item = doc.createElement(name);
					final Object data = method.invoke(entry);
					if (data != null) {
					    item.appendChild(doc.createTextNode(getValue(data)));
					    container.appendChild(item);
					}
				} catch (Throwable e) {
					LOG.warn("Failed to add message data", e);
				}
    		}
    	}

    	return container;
    }

    /*
     * Many of the SNMP4J value types have a getValue() method and an
     * overridden toString() that includes the type name.
     */
    private String getValue(Object data) {
        try {
            Method method = data.getClass().getMethod("getValue");
            data = method.invoke(data);
        } catch (Throwable t) {
        }
        return data.toString();
    }

    // Helper to create root XML element with correct namespace and attribute
    private Element createElement(final Document document, final String qName, final Optional<String> namespaceURI) {
        if(namespaceURI.isPresent()) {
            final Element element = document.createElementNS(namespaceURI.get(), qName);
            String name = XMLNS_ATTRIBUTE_KEY;
            if(element.getPrefix() != null) {
                name += ":" + element.getPrefix();
            }
            element.setAttributeNS(XMLNS_URI, name, namespaceURI.get());
            return element;
        }
        return document.createElement(qName);
    }
}
