interfaces {
    ethernet eth1 {
        address 10.0.0.6/31
        mtu 9500
    }
    ethernet eth2 {
        address 10.0.0.8/31
        mtu 9500
    }
    ethernet eth3 {
        address 10.1.1.2/31
        mtu 9500
    }
    loopback lo {
        address 192.0.2.2/32
    }
}
protocols {
    isis {
        net 49.0001.1920.0000.2002.00
        level level-2
        interface eth1 {
            network point-to-point
        }
        interface eth2 {
            network point-to-point
        }
        interface lo {
            passive
        }
    }
    bgp {
        system-as 65000
        parameters {
            router-id 192.0.2.2
        }
        neighbor 192.0.2.101 {
            remote-as 65000
            update-source lo
            address-family {
                ipv4-unicast {
                    nexthop-self {
                    }
                }
            }
        }
        neighbor 192.0.2.102 {
            remote-as 65000
            update-source lo
            address-family {
                ipv4-unicast {
                    nexthop-self {
                    }
                }
            }
        }
        neighbor 10.1.1.3 {
            remote-as 65001
            address-family {
                ipv4-unicast {
                }
            }
        }
    }
}
system {
    host-name pe2
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
