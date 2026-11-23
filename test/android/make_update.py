# Fabrique test/update.txt : la liste que l'appli de test lit à chaque ouverture pour se mettre à jour « à chaud ».
# À relancer à chaque publication de la version de test : python3 test/android/make_update.py
import hashlib, os
d = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
L = ['build=' + open(os.path.join(d, 'version.txt')).read().strip()]
for f in ('index.html', 'jet_cine.glb', 'livekit-client.umd.js'):
    b = open(os.path.join(d, f), 'rb').read(); L.append(f'file={f} {len(b)} {hashlib.sha256(b).hexdigest()}')
open(os.path.join(d, 'update.txt'), 'w').write('\n'.join(L) + '\n'); print('\n'.join(L))
