# DynamicQoS
Dynamic Qos provisioning for multimedia traffic in an SDN network

In the proposed design, the edge router will classify the incoming packets and label them before forwarding them towards the core. The controller is responsible for installing entries for these labeled flows on the core switches.
The controller computes the route for the multimedia traffic using a different algorithm and computes the shortest path for regular data traffic.
This design is borrowed from work done in [1]
