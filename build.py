"""Build the standalone libxposed API 102 APK with official Android SDK tools."""
from pathlib import Path
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
os.chdir(ROOT)
SDK = Path(os.environ.get('ANDROID_HOME', ROOT / 'tools'))
platforms = list(SDK.rglob('android.jar'))
build_tools = list(SDK.rglob('aapt2.exe')) + list(SDK.rglob('aapt2'))
if not platforms or not build_tools:
    raise SystemExit('Set ANDROID_HOME to an SDK with platform 36+ and build-tools 36.0.0, or run setup_tools.py.')
ANDROID = sorted(platforms)[-1]
BT = build_tools[0].parent
JAVA_HOME = Path(os.environ.get('JAVA_HOME', r'D:\Java\17'))
def java_tool(name):
    p = JAVA_HOME / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(p) if p.exists() else name
def run(*args):
    # aapt2 on Windows cannot reliably consume non-ASCII absolute argv paths.
    argv = []
    for x in args:
        if isinstance(x, Path) and x.is_absolute() and x.is_relative_to(ROOT):
            x = x.relative_to(ROOT)
        argv.append(str(x))
    subprocess.run(argv, check=True)
VERSION = '1.2.0-preview'
API = ROOT / 'tools/libxposed-api-102.jar'
BUILD = ROOT / 'build' / VERSION
for path in [BUILD / 'classes', BUILD / 'dex', BUILD / 'tests', ROOT / 'dist', ROOT / 'app/assets']:
    path.mkdir(parents=True, exist_ok=True)
sources = sorted((ROOT / 'src').rglob('*.java'))
classpath = os.pathsep.join([str(ANDROID), str(API)])
run(java_tool('javac'), '-encoding', 'UTF-8', '--release', '8', '-cp', classpath,
    '-d', BUILD / 'classes', *sources)
run(java_tool('javac'), '-encoding', 'UTF-8', '--release', '8', '-d', BUILD / 'tests',
    ROOT / 'src/com/johnwong/danshoumode/Geometry.java', ROOT / 'tests/GeometryTest.java',
    ROOT / 'src/com/johnwong/danshoumode/Reflect.java', ROOT / 'tests/ReflectionTest.java')
run(java_tool('java'), '-cp', BUILD / 'tests', 'GeometryTest')
run(java_tool('java'), '-cp', BUILD / 'tests', 'com.johnwong.danshoumode.ReflectionTest')
with zipfile.ZipFile(BUILD / 'classes.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    for p in (BUILD / 'classes').rglob('*.class'):
        z.write(p, p.relative_to(BUILD / 'classes').as_posix())
run(java_tool('java'), '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '33',
    '--lib', ANDROID, '--classpath', API,
    '--output', BUILD / 'dex', BUILD / 'classes.jar')
suffix = '.exe' if os.name == 'nt' else ''
run(BT / ('aapt2' + suffix), 'compile', '--dir', ROOT / 'app/res', '-o', BUILD / 'resources.zip')
run(BT / ('aapt2' + suffix), 'link', '-o', BUILD / 'unsigned.apk', '--manifest', ROOT / 'app/AndroidManifest.xml',
    '-I', ANDROID, '-A', ROOT / 'app/assets', '--auto-add-overlay', BUILD / 'resources.zip')
with zipfile.ZipFile(BUILD / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(BUILD / 'dex/classes.dex', 'classes.dex')
    for resource in (ROOT / 'app/java-resources').rglob('*'):
        if resource.is_file(): z.write(resource, resource.relative_to(ROOT / 'app/java-resources').as_posix())
run(BT / ('zipalign' + suffix), '-f', '-p', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
signing = ROOT / 'tools/local-signing'
signing.mkdir(exist_ok=True)
password = signing / 'password.txt'
key = signing / 'lubenwei.p12'
if not key.exists():
    password.write_text(secrets.token_urlsafe(30), encoding='utf-8')
    run(java_tool('keytool'), '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
        '-storepass:file', password, '-alias', 'lubenwei', '-keyalg', 'RSA', '-keysize', '3072',
        '-validity', '10000', '-dname', 'CN=LubenweiMode Preview, O=LiulangBenzishangren, C=CN')
apk = ROOT / ('dist/DanShouMode-' + VERSION + '.apk')
signer = BT / 'lib/apksigner.jar'
run(java_tool('java'), '-jar', signer, 'sign', '--ks', key, '--ks-pass', 'file:' + str(password),
    '--ks-key-alias', 'lubenwei', '--out', apk, BUILD / 'aligned.apk')
run(java_tool('java'), '-jar', signer, 'verify', '--verbose', apk)
run(BT / ('zipalign' + suffix), '-c', '-p', '4', apk)
run(BT / ('aapt2' + suffix), 'dump', 'badging', apk)
with zipfile.ZipFile(apk) as z:
    assert z.read('META-INF/xposed/java_init.list').decode().strip() == 'com.johnwong.danshoumode.Entry'
    assert z.read('META-INF/xposed/module.prop').decode().find('minApiVersion=102') >= 0
    # Modern API distinguishes system_server (system) from system UI dialogs (android).
    assert z.read('META-INF/xposed/scope.list').decode().splitlines() == ['system','com.android.systemui','com.miui.home']
    assert 'assets/xposed_init' not in z.namelist()
    assert 'classes.dex' in z.namelist()
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(ROOT / 'dist/SHA256SUMS.txt').write_text(digest + '  ' + apk.name + '\n', encoding='utf-8')
print('Built:', apk)
print('SHA256:', digest)
