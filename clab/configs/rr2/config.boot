interfaces {
    ethernet eth1 {
        address 10.0.0.22/31
        mtu 9500
    }
    ethernet eth2 {
        address 10.0.0.24/31
        mtu 9500
    }
    loopback lo {
        address 192.0.2.102/32
    }
}
protocols {
    isis {
        net 49.0001.1920.0000.2102.00
        level level-2
        interface eth1 { network point-to-point }
        interface eth2 { network point-to-point }
        interface lo { passive }
    }
    bgp {
        system-as 65000
        parameters {
            router-id 192.0.2.102
            cluster-id 2.2.2.2
        }
        peer-group RRC {
            remote-as 65000
            update-source lo
            address-family {
                ipv4-unicast {
                    route-reflector-client {
                    }
                }
            }
        }
        neighbor 192.0.2.1 { peer-group RRC }
        neighbor 192.0.2.2 { peer-group RRC }
        neighbor 192.0.2.3 { peer-group RRC }
        neighbor 192.0.2.4 { peer-group RRC }
        neighbor 192.0.2.11 { peer-group RRC }
        neighbor 192.0.2.12 { peer-group RRC }
        neighbor 192.0.2.101 {
            remote-as 65000
            update-source lo
            address-family { ipv4-unicast { } }
        }
    }
}
system {
    host-name rr2
    login {
        user vyos {
            authentication {
                plaintext-password vyos
            }
        }
    }
}
service {
    ssh {
    }
}
