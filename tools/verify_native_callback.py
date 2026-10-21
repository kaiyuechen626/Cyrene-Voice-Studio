"""Verify the JNI callback's concrete method definition in the minified APK."""
import json
import struct
import sys
import zipfile

TARGET = 'Lio/cyrene/voice/core/NativeSpeechCallback;'

def inspect(data):
    def u32(at): return struct.unpack_from('<I', data, at)[0]
    def uleb(at):
        result = shift = 0
        while True:
            value = data[at]
            at += 1
            result |= (value & 127) << shift
            if value < 128: return result, at
            shift += 7
    def string(at):
        _, at = uleb(at)
        return data[at:data.index(b'\0', at)].decode('utf-8', errors='replace')
    strings = [string(u32(u32(60) + i * 4)) for i in range(u32(56))]
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    for i in range(u32(96)):
        at = u32(100) + i * 32
        if types[u32(at)] != TARGET: continue
        at = u32(at + 24)
        counts = []
        for _ in range(4):
            count, at = uleb(at)
            counts.append(count)
        for _ in range(counts[0] + counts[1]):
            _, at = uleb(at)
            _, at = uleb(at)
        found = []
        for count in counts[2:]:
            index = 0
            for _ in range(count):
                diff, at = uleb(at)
                index += diff
                access, at = uleb(at)
                code, at = uleb(at)
                cls, proto, name = struct.unpack_from('<HHI', data, u32(92) + index * 8)
                proto_at = u32(76) + proto * 12
                params_at = u32(proto_at + 8)
                params = [types[struct.unpack_from('<H', data, params_at + 4 + n * 2)[0]]
                          for n in range(u32(params_at))] if params_at else []
                signature = '(' + ''.join(params) + ')' + types[u32(proto_at + 4)]
                if strings[name] == 'invoke':
                    found.append({'name': strings[name], 'signature': signature,
                                  'public': bool(access & 1), 'static': bool(access & 8),
                                  'hasCode': bool(code)})
        return found
    return []

with zipfile.ZipFile(sys.argv[1]) as apk:
    methods = [m for name in apk.namelist() if name.endswith('.dex') and '/' not in name
               for m in inspect(apk.read(name))]
expected = [m for m in methods if m['signature'] == '([F)Ljava/lang/Integer;'
            and m['public'] and not m['static'] and m['hasCode']]
print(json.dumps({'concreteClass': TARGET, 'declaredInvokeMethods': methods,
                  'jniSignaturePresent': len(expected) == 1}, indent=2))
assert len(expected) == 1, 'Minified APK does not retain the concrete JNI callback signature'
