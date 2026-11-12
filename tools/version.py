#!/usr/bin/env python3
"""Генерирует web/version.json: номер версии игры = число коммитов, номер APK = android/version.txt,
и список файлов для обновления по воздуху."""
import hashlib, json, os, subprocess
web = int(subprocess.check_output(['git', 'rev-list', '--count', 'HEAD']).decode().strip())
apk = int(open('android/version.txt').read().strip())
files = []
hashes = {}
for root, _, names in os.walk('web'):
    for n in names:
        rel = os.path.relpath(os.path.join(root, n), 'web').replace(os.sep, '/')
        if rel != 'version.json':
            files.append(rel)
            hashes[rel] = hashlib.sha1(open(os.path.join(root, n), 'rb').read()).hexdigest()[:12]
json.dump({'web': web, 'apk': apk, 'files': sorted(files), 'hash': hashes}, open('web/version.json', 'w'), ensure_ascii=False, indent=1)
print('web', web, 'apk', apk, 'files', len(files))
