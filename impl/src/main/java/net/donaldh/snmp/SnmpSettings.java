package net.donaldh.snmp;

import java.net.Inet4Address;
import java.net.UnknownHostException;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.Target;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;

public class SnmpSettings {
	
	private static final Logger LOG = LoggerFactory.getLogger(SnmpSettings.class);

    static final String DEFAULT_COMMUNITY = "public";
    static final Integer SNMP_LISTEN_PORT = 161;
    static final int RETRIES = 0;
    static final int TIMEOUT = 15000;
    static final int MAXREPETITIONS = 1000;

    static Target getTargetForIp(Ipv4Address address, String community) {
        Address addr = null;
        try {
            addr = new UdpAddress(Inet4Address.getByName(address.getValue()), SNMP_LISTEN_PORT);
        } catch (UnknownHostException e) {
            LOG.warn("Failed to create UDP Address", e);
            return null;
        }

        CommunityTarget communityTarget = new CommunityTarget();
        communityTarget.setCommunity(new OctetString(community));
        communityTarget.setAddress(addr);
        communityTarget.setRetries(RETRIES);
        communityTarget.setTimeout(TIMEOUT);
        communityTarget.setVersion(SnmpConstants.version2c);
        return communityTarget;
    }
}
