# Clé de signature de l'appli de TEST, recréée à chaque construction à partir d'une phrase secrète
# (secret GitHub TEST_KEY_PASSPHRASE). La clé n'est donc écrite NULLE PART : ni dans le code public, ni dans un fichier.
# Même phrase -> exactement la même clé -> Android accepte chaque nouvelle version par-dessus l'ancienne.
# Usage : TEST_KEY_PASSPHRASE=… TEST_KS_PASS=… python3 testkey.py sortie.p12   (affiche l'empreinte publique du certificat)
import hashlib, os, sys, datetime
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12

phrase = os.environ.get('TEST_KEY_PASSPHRASE', '').strip().encode()
if len(phrase) < 16: sys.exit('phrase secrète absente ou trop courte (16 caractères minimum)')
seed = hashlib.scrypt(phrase, salt=b'egoshot-test-signing-key-v1', n=2**15, r=8, p=2, maxmem=2**27, dklen=64)

class Stream:  # suite d'octets reproductible tirée de la phrase
    def __init__(s, label): s.k = hashlib.sha512(seed + label).digest(); s.i = 0; s.buf = b''
    def take(s, n):
        while len(s.buf) < n: s.buf += hashlib.sha512(s.k + s.i.to_bytes(8, 'big')).digest(); s.i += 1
        out, s.buf = s.buf[:n], s.buf[n:]; return out
SMALL = [p for p in range(3, 2000, 2) if all(p % q for q in range(3, int(p ** 0.5) + 1, 2))]
def is_prime(n, st):
    if any(n % p == 0 for p in SMALL): return False
    d, r = n - 1, 0
    while d % 2 == 0: d //= 2; r += 1
    for _ in range(40):
        a = 2 + int.from_bytes(st.take(64), 'big') % (n - 4); x = pow(a, d, n)
        if x in (1, n - 1): continue
        for _ in range(r - 1):
            x = pow(x, 2, n)
            if x == n - 1: break
        else: return False
    return True
def prime(label):
    st = Stream(label)
    while True:
        c = int.from_bytes(st.take(128), 'big') | (3 << 1022) | 1
        if c % 65537 != 1 and is_prime(c, st): return c
e = 65537; p, q = prime(b'p'), prime(b'q')
if p < q: p, q = q, p
n = p * q; d = pow(e, -1, (p - 1) * (q - 1))
key = rsa.RSAPrivateNumbers(p, q, d, d % (p - 1), d % (q - 1), pow(q, -1, p), rsa.RSAPublicNumbers(e, n)).private_key()
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'EgoShot Test'), x509.NameAttribute(NameOID.ORGANIZATION_NAME, 'Minebeat Studios')])
cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(20261123)
        .not_valid_before(datetime.datetime(2026, 1, 1)).not_valid_after(datetime.datetime(2056, 1, 1)).sign(key, hashes.SHA256()))
pw = os.environ.get('TEST_KS_PASS', '').encode() or sys.exit('TEST_KS_PASS manquant')
open(sys.argv[1], 'wb').write(pkcs12.serialize_key_and_certificates(b'egoshottest', key, cert, None, serialization.BestAvailableEncryption(pw)))
print('empreinte du certificat (publique) :', cert.fingerprint(hashes.SHA256()).hex())
