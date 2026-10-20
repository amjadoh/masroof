"""Validate measured test-host evidence, never establish a performance budget or M0 result."""
import json
import math
import pathlib
import re
import sys


def check(directory):
    root = pathlib.Path(directory)
    for name in ("before.json", "after.json"):
        assert 0 < (root / name).stat().st_size < 10 * 1024 * 1024
    before = json.loads((root / "before.json").read_text(encoding="utf-8"))
    after = json.loads((root / "after.json").read_text(encoding="utf-8"))
    assert before["version"] == 1 and after["version"] == 2
    assert before["uid"] == after["uid"] and before["pid"] != after["pid"]
    assert before["snapshot"] == after["snapshot"]
    assert before["snapshot"]["schema"] == 18
    assert before["snapshot"]["financialRows"] == 40
    for key in ("encryptedRestoreAcrossUpdate", "wrongPasswordPreservedCurrent", "tamperingPreservedCurrent"):
        assert after[key] is True
    metrics = {}
    for phase, record in (("before", before), ("after", after)):
        perf = record["performance"]
        values = perf["samplesMs"]
        assert perf["warmup"] == 2 and len(values) == 5
        assert all(isinstance(v, (int, float)) and math.isfinite(v) and v > 0 for v in values)
        ordered = sorted(values)
        assert perf["p50Ms"] == ordered[2] and perf["p95Ms"] == ordered[4]
        assert perf["acceptanceBudget"] is None
        metrics[phase] = {key: perf[key] for key in ("p50Ms", "p95Ms", "api", "device")}
    certificates = []
    for version in (1, 2):
        text = (root / f"certificate-{version}.txt").read_text(encoding="utf-8")
        matches = re.findall(r"^(?:Signer #1 |V2 Signer: )certificate SHA-256 digest: ([a-fA-F0-9]{64})$", text, re.MULTILINE)
        assert len(matches) == 1, "Exactly one verified test signer certificate is required"
        certificates.append(matches[0].lower())
        package = (root / f"package-{version}.txt").read_text(encoding="utf-8")
        assert f"package: name='com.baraa.masroof.beta.upgrade.rehearsal' versionCode='{version}'" in package
        assert "uses-permission: name='android.permission.READ_SMS'" not in package
    assert certificates[0] == certificates[1]
    summary = {"result": "passed", "versions": [1, 2], "sameUid": before["uid"], "schema": before["snapshot"]["schema"],
               "financialRows": 40, "publicTestCertificateSha256": certificates[0],
               "metrics": metrics,
               "acceptanceBudget": None, "productionMigration": False}
    (root / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    check(sys.argv[1])
