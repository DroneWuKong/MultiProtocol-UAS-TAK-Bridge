#!/usr/bin/env python3
"""Reproduce software fixtures; requires pymavlink==2.4.49. Opens no device/socket."""
from pathlib import Path
import struct
from pymavlink.dialects.v10 import common as v1
from pymavlink.dialects.v20 import common as v2
out = Path(__file__).resolve().parents[1] / 'app/src/test/resources/telemetry'
out.mkdir(parents=True, exist_ok=True)
def save(name, data):
    (out / (name + '.hex')).write_text(bytes(data).hex() + '\n')
for version, dialect in [(1,v1),(2,v2)]:
    encoder = dialect.MAVLink(None, srcSystem=1, srcComponent=1)
    raw = encoder.gps_raw_int_encode(1000000,3,0,-875000000,123000,90,120,1250,9000,12)
    save('mav%d-gps' % version, raw.pack(encoder))
    save('mav%d-global' % version, encoder.global_position_int_encode(
        1000,0,-875000000,123000,10000,300,400,0,9000).pack(encoder))
    save('mav%d-heartbeat' % version, encoder.heartbeat_encode(2,3,0,0,3).pack(encoder))
    save('mav%d-no-fix' % version, encoder.gps_raw_int_encode(
        1000000,0,0,-875000000,123000,65535,65535,65535,65535,0).pack(encoder))
    encoder.srcSystem=2
    save('mav%d-other-source' % version, raw.pack(encoder))
    if version==2:
        encoder.srcSystem=1
        encoder.signing.secret_key=bytes(range(32))
        encoder.signing.link_id=1
        encoder.signing.timestamp=123456
        encoder.signing.sign_outgoing=True
        save('mav2-signed-gps',raw.pack(encoder))
def radio(addr,kind,payload):
    body=bytes([kind])+payload
    crc=0
    for octet in body:
        crc ^= octet
        for _ in range(8):
            crc=((crc<<1)^0xd5 if crc&128 else crc<<1)&255
    return bytes([addr,len(body)+1])+body+bytes([crc])
# Operator GHST downlink layout: signed metre altitude, speed cm/s, course deci-degrees.
save('ghst-primary',radio(0x80,0x25,struct.pack('<iih',0,-875000000,-25)))
for flag in [0,1]:
    save('ghst-secondary-%d'%flag,radio(0x80,0x26,struct.pack('<HHBHHB',1250,900,12,10,90,flag)))
# TAC capture established addr 0x81 and RC page types, not this synthetic payload.
save('tac-rc-only',b''.join(radio(0x81,t,bytes(10)) for t in [0x10,0x11,0x12]))
save('crsf-gps',radio(0xea,0x02,struct.pack('>iiHHHB',0,-875000000,450,9000,975,12)))
