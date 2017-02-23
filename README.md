if-table-collector
----

This is a rudimentary application that uses SNMP to collect IF-MIB.ifTable from devices.

You can enable SNMP collection for a device by adding if-table-collector specific fields to a
node in **topology-netconf** like this:

```
POST http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf
Content-Type: application/xml
<node xmlns="urn:TBD:params:xml:ns:yang:network-topology">
 <node-id>{node-name}</node-id>
 <host xmlns="urn:opendaylight:netconf-node-topology">{ip-address}</host>
 <port xmlns="urn:opendaylight:netconf-node-topology">{netconf-port}</port>
 <username xmlns="urn:opendaylight:netconf-node-topology">{username}</username>
 <password xmlns="urn:opendaylight:netconf-node-topology">{password}</password>
 <tcp-only xmlns="urn:opendaylight:netconf-node-topology">false</tcp-only>
 <keepalive-delay xmlns="urn:opendaylight:netconf-node-topology">0</keepalive-delay>
 <snmp-community xmlns="urn:net:donaldh:if-table-collector">{snmp-community}</snmp-community>
 <poll-interval xmlns="urn:net:donaldh:if-table-collector">60</poll-interval>
</node>
```

When running, you should see log messages for each device as the SNMP polling happens:

```
2017-02-22 16:48:24,079 | INFO | ... | Executing poll cycle for 10.1.1.105 ...
2017-02-22 16:48:24,460 | INFO | ... | Polled 6 rows.
```
