#!/usr/bin/env python3
"""Stores screenshots as git blobs (no branch, no commit) and lists their SHAs in a
job annotation, so they can be fetched through the GitHub API, e.g.
  gh api repos/OWNER/REPO/git/blobs/SHA --jq .content | base64 -d > shot.png

Usage: publish_shots.py <dir> <label>   (needs GITHUB_TOKEN with contents: write)
"""
import base64
import glob
import json
import os
import sys
import urllib.request

directory, label = sys.argv[1], sys.argv[2]
api = f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}/git/blobs"
lines = []
for path in sorted(glob.glob(os.path.join(directory, "*.png"))):
    body = json.dumps({"content": base64.b64encode(open(path, "rb").read()).decode(), "encoding": "base64"})
    req = urllib.request.Request(api, data=body.encode(), method="POST", headers={
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json",
    })
    with urllib.request.urlopen(req) as resp:
        lines.append(f"{os.path.basename(path)} {json.load(resp)['sha']}")
print(f"::notice title=screenshots {label}::" + "%0A".join(lines))
