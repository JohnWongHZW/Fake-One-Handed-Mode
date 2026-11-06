"""Package source and documentation without SDK, signing keys or third-party installers."""
from pathlib import Path
import hashlib
import zipfile
import shutil

ROOT=Path(__file__).resolve().parent
DIST=ROOT/'dist'
VERSION='1.2.0-preview'
RELEASE=VERSION.removesuffix('-preview')
apk=DIST/('DanShouMode-'+VERSION+'.apk')
assert apk.is_file(), 'Run build.py first'
inputs=[ROOT/n for n in ['build.py','setup_tools.py','package_release.py','README.md','BUILDING.md','ICON_PROMPT.md','.gitignore','icon.webp']]
for name in ['src','app','tests']:
    inputs.extend(p for p in (ROOT/name).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
assert all(p.suffix not in ('.p12','.keystore') and 'password' not in p.name for p in inputs)
for p in inputs:
    if p.suffix=='.java' or 'res' in p.parts or p.name=='AndroidManifest.xml':
        assert p.stat().st_mtime<=apk.stat().st_mtime, 'APK predates source: '+str(p)
with zipfile.ZipFile(apk) as z:
    assert z.read('META-INF/xposed/java_init.list').decode().strip()=='com.johnwong.danshoumode.Entry'
    assert z.read('META-INF/xposed/scope.list').decode().splitlines()==['system','com.android.systemui','com.miui.home']
    assert b'minApiVersion=102' in z.read('META-INF/xposed/module.prop')
    assert z.read('res/drawable-nodpi-v4/portrait.webp')==(ROOT/'icon.webp').read_bytes()
    # aapt2 re-encodes PNG losslessly; its bytes need not match the source.
    image=z.read('res/drawable-nodpi-v4/hand_icon.png')
    original=(ROOT/'app/res/drawable-nodpi/hand_icon.png').read_bytes()
    assert image[:8]==b'\x89PNG\r\n\x1a\n' and image[16:24]==original[16:24]
    assert b'Lcom/johnwong/danshoumode/HandTileService;' in z.read('classes.dex')
    assert b'Lcom/liulang/lubenwei/' not in z.read('classes.dex')
source=DIST/('DanShouMode-'+RELEASE+'-source.zip')
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('app/assets/','')
    for p in sorted(inputs): z.write(p,p.relative_to(ROOT).as_posix())
with zipfile.ZipFile(source) as z: assert z.testzip() is None
readme=DIST/('DanShouMode-'+RELEASE+'-README.md')
shutil.copyfile(ROOT/'README.md',readme)
shutil.copyfile(ROOT/'ICON_PROMPT.md',DIST/'ICON_PROMPT.md')
shutil.copyfile(ROOT/'README.md',DIST/'使用教程.md')
files=[apk,source,readme,DIST/'ICON_PROMPT.md']
checksums=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in files)
(DIST/('DanShouMode-'+RELEASE+'-SHA256SUMS.txt')).write_text(checksums,encoding='utf-8')
(DIST/'SHA256SUMS.txt').write_text(checksums,encoding='utf-8')
print('Release packaged; icons, entry, scope, package and source archive verified.')
for p in files: print(p.name, p.stat().st_size, 'bytes')
