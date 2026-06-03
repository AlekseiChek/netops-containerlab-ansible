interfaces {
    ethernet eth1 {
        address 10.0.0.10/31
        mtu 9500
    }
    ethernet eth2 {
        address 10.0.0.12/31
        mtu 9500
    }
    ethernet eth3 {
        address 10.1.2.0/31
        mtu 9500
    }
    loopback lo {
        address 192.0.2.3/32
    }
}
protocols {
    isis {
        net 49.0001.1920.0000.2003.00
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
            router-id 192.0.2.3
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
        neighbor 10.1.2.1 {
            remote-as 65002
            address-family {
                ipv4-unicast {
                }
            }
        }
    }
}
system {
    host-name pe3
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
