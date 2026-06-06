interfaces {
    ethernet eth1 {
        address 10.0.0.0/31
        mtu 9500
    }
    ethernet eth2 {
        address 10.0.0.3/31
        mtu 9500
    }
    ethernet eth3 {
        address 10.0.0.7/31
        mtu 9500
    }
    ethernet eth4 {
        address 10.0.0.11/31
        mtu 9500
    }
    ethernet eth5 {
        address 10.0.0.15/31
        mtu 9500
    }
    ethernet eth6 {
        address 10.0.0.19/31
        mtu 9500
    }
    ethernet eth7 {
        address 10.0.0.23/31
        mtu 9500
    }
    loopback lo {
        address 192.0.2.11/32
    }
}
protocols {
    isis {
        net 49.0001.1920.0000.2011.00
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
        interface eth3 {
            network point-to-point
            fast-reroute {
                ti-lfa {
                    level-2 {
                        node-protection
                    }
                }
            }
        }
        interface eth4 {
            network point-to-point
            fast-reroute {
                ti-lfa {
                    level-2 {
                        node-protection
                    }
                }
            }
        }
        interface eth5 {
            network point-to-point
            fast-reroute {
                ti-lfa {
                    level-2 {
                        node-protection
                    }
                }
            }
        }
        interface eth6 {
            network point-to-point
            fast-reroute {
                ti-lfa {
                    level-2 {
                        node-protection
                    }
                }
            }
        }
        interface eth7 {
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
            prefix 192.0.2.11/32 {
                index {
                    value 11
                }
            }
        }
    }
    mpls {
        interface eth1 {
        }
        interface eth2 {
        }
        interface eth3 {
        }
        interface eth4 {
        }
        interface eth5 {
        }
        interface eth6 {
        }
        interface eth7 {
        }
    }
    bgp {
        system-as 65000
        parameters {
            router-id 192.0.2.11
        }
        neighbor 192.0.2.101 {
            remote-as 65000
            update-source lo
            address-family {
                ipv4-unicast {
                }
            }
        }
        neighbor 192.0.2.102 {
            remote-as 65000
            update-source lo
            address-family {
                ipv4-unicast {
                }
            }
        }
    }
}
system {
    host-name p1
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
