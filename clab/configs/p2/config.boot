interfaces {
    ethernet eth1 {
        address 10.0.0.1/31
        mtu 9500
    }
    ethernet eth2 {
        address 10.0.0.5/31
        mtu 9500
    }
    ethernet eth3 {
        address 10.0.0.9/31
        mtu 9500
    }
    ethernet eth4 {
        address 10.0.0.13/31
        mtu 9500
    }
    ethernet eth5 {
        address 10.0.0.17/31
        mtu 9500
    }
    ethernet eth6 {
        address 10.0.0.21/31
        mtu 9500
    }
    ethernet eth7 {
        address 10.0.0.25/31
        mtu 9500
    }
    loopback lo {
        address 192.0.2.12/32
    }
}
protocols {
    isis {
        net 49.0001.1920.0000.2012.00
        level level-2
        interface eth1 { network point-to-point }
        interface eth2 { network point-to-point }
        interface eth3 { network point-to-point }
        interface eth4 { network point-to-point }
        interface eth5 { network point-to-point }
        interface eth6 { network point-to-point }
        interface eth7 { network point-to-point }
        interface lo { passive }
    }
    bgp {
        system-as 65000
        parameters {
            router-id 192.0.2.12
        }
        neighbor 192.0.2.101 {
            remote-as 65000
            update-source lo
            address-family { ipv4-unicast { } }
        }
        neighbor 192.0.2.102 {
            remote-as 65000
            update-source lo
            address-family { ipv4-unicast { } }
        }
    }
}
system {
    host-name p2
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
