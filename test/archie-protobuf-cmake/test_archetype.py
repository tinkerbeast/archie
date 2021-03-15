import os
import subprocess
import hashlib

APP_NAME = 'tinkerbeast.App'

def md5(fname):
    hash_md5 = hashlib.md5()
    with open(fname, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def test_abc():
    namespace = 'myOuter.myInner'
    project = 'myProject'
    archetype = 'archie-protobuf-cmake'
    data = {'myProject/myProject.proto': '1cd343e6add6c1286ddf814b4d223e2d',
            'myProject/CMakeLists.txt': '22a5db8038ae7827d2dd786022af5fc5'}
    # Run archie on given archetype
    cmd = 'mvn  package'
    out = subprocess.run(cmd, shell=True, check=True, stdout=subprocess.PIPE, text=True)
    print(out)
    cmd = 'mvn  exec:java  -Dexec.mainClass="{}" -Dexec.args="-n {} -p {} -a {}"'.format(
        APP_NAME, namespace, project, archetype)
    out = subprocess.run(cmd, shell=True, check=True, stdout=subprocess.PIPE, text=True)
    print(out)
    # Check generated files
    file_list = [os.path.join(dp, f) for dp, dn, fnames in os.walk(project) for f in fnames]
    for file_name in file_list:
        assert data[file_name] == md5(file_name)
    # Cleanup the archetype generation
    cmd = 'rm -rf {}'.format(project)
    out = subprocess.run(cmd, shell=True, check=True, stdout=subprocess.PIPE, text=True)
    print(out)

