"""Download official, pinned Windows SDK packages; libxposed API 102 is compile-only."""
from pathlib import Path
import concurrent.futures
import hashlib
import urllib.request
import zipfile
import os

root=Path(__file__).resolve().parent/'tools'
root.mkdir(exist_ok=True)
jobs=[
 ('https://dl.google.com/android/repository/platform-36_r02.zip','platform.zip','2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576','platform'),
 ('https://dl.google.com/android/repository/build-tools_r36_windows.zip','build-tools.zip','f16ccffd34de8790dede813a6c7d8e2c11a27b50','build-tools'),
 ('https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar','libxposed-api-102.0.0.aar',None,None),
]
def fetch(job):
    url,name,sha,dest=job;p=root/name
    if not p.exists(): urllib.request.urlretrieve(url,p)
    if sha and hashlib.sha1(p.read_bytes()).hexdigest()!=sha: raise RuntimeError('SDK checksum mismatch: '+name)
    if name=='libxposed-api-102.0.0.aar' and hashlib.sha256(p.read_bytes()).hexdigest()!='423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0':
        raise RuntimeError('libxposed API checksum mismatch')
    if name=='libxposed-api-102.0.0.aar':
        with zipfile.ZipFile(p) as z: (root/'libxposed-api-102.jar').write_bytes(z.read('classes.jar'))
    if dest:
        target=root/dest
        with zipfile.ZipFile(p) as z:
            for entry in z.namelist():
                if not (target/entry).resolve().is_relative_to(target.resolve()): raise RuntimeError('Unsafe archive path')
            z.extractall(target)
    print(name+' ready',flush=True)
if os.name!='nt': raise SystemExit('Install the Android SDK for your OS and set ANDROID_HOME. This helper downloads Windows tools.')
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(fetch,jobs))
