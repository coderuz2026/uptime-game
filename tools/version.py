#!/usr/bin/env python3
"""Генерирует web/version.json: номер версии игры = число коммитов, номер APK = android/version.txt,
и список файлов для обновления по воздуху."""
import json, os, subprocess
web = int(subprocess.check_output(['git', 'rev-list', '--count', 'HEAD']).decode().strip())
apk = int(open('android/version.txt').read().strip())
files = []
for root, _, names in os.walk('web'):
    for n in names:
        rel = os.path.relpath(os.path.join(root, n), 'web').replace(os.sep, '/')
        if rel != 'version.json':
            files.append(rel)
json.dump({'web': web, 'apk': apk, 'files': sorted(files)}, open('web/version.json', 'w'), ensure_ascii=False, indent=1)
print('web', web, 'apk', apk, 'files', len(files))
