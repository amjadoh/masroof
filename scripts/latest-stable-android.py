#!/usr/bin/env python3
"""Select the newest stable, base Google APIs x86_64 emulator image."""

import os
import re
import urllib.request
import xml.etree.ElementTree as ET

REPOSITORY = "https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-3.xml"
IMAGE_PATH = re.compile(r"system-images;android-(\d+);google_apis;x86_64")


def latest_stable_api(xml):
    root = ET.fromstring(xml)
    for element in root.iter():
        element.tag = element.tag.rsplit("}", 1)[-1]
    candidates = []
    for package in root.findall("remotePackage"):
        match = IMAGE_PATH.fullmatch(package.get("path", ""))
        channel = package.find("channelRef")
        if not match or package.get("obsolete") == "true":
            continue
        if channel is None or channel.get("ref") != "channel-0":
            continue
        if package.find("revision/preview") is not None:
            continue
        details = package.find("type-details")
        if details is None or details.findtext("codename", "REL") != "REL":
            continue
        if details.findtext("base-extension", "true") != "true":
            continue
        api = int(match.group(1))
        if details.findtext("api-level") != str(api):
            continue
        candidates.append(api)
    if not candidates:
        raise ValueError("No stable base Google APIs x86_64 system image published")
    return max(candidates)


def main():
    for attempt in range(3):
        try:
            with urllib.request.urlopen(REPOSITORY, timeout=60) as response:
                api = latest_stable_api(response.read())
            break
        except OSError:
            if attempt == 2:
                raise
    print(f"Selected stable emulator: system-images;android-{api};google_apis;x86_64")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"api-level={api}\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"Emulator tests use **API {api}**, the latest stable base Google APIs x86_64 image.\n")


if __name__ == "__main__":
    main()
