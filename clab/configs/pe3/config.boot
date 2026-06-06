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
        vrf CUST
    }
    loopback lo {
        address 192.0.2.3/32
    }
}
protocols {
    isis {
        net 49.0001.1920.0000.2003.00
        level level-2
        metric-style wide
        interface eth1 {
            network point-to-point
            fast-reroute {
                ti-lfa {
                    level-2 {
                        node-protection
                    }
                }
            }
        }
        interface eth2 {
            network point-to-point
            fast-reroute {
                ti-lfa {
                    level-2 {
                        node-protection
                    }
                }
            }
        }
        interface lo {
            passive
        }
        segment-routing {
            global-block {
                low-label-value 16000
                high-label-value 23999
            }
            prefix 192.0.2.3/32 {
                index {
                    value 3
                }
            }
        }
    }
    mpls {
        interface eth1 {
        }
        interface eth2 {
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
                ipv4-vpn {
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
                ipv4-vpn {
                    nexthop-self {
                    }
                }
            }
        }
    }
}
vrf {
    name CUST {
        table 100
        protocols {
            bgp {
                system-as 65000
                address-family {
                    ipv4-unicast {
                        export vpn
                        import vpn
                        label {
                            vpn {
                                export auto
                            }
                        }
                        rd {
                            vpn {
                                export 192.0.2.3:100
                            }
                        }
                        route-target {
                            vpn {
                                both 65000:100
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
