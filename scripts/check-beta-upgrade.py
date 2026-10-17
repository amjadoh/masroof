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
    assert before["fixture"]["financialCount"] == 600
    assert before["fixture"]["activeCount"] == 585
    assert after["encryptedRestoreAcrossUpdate"] is True
    assert after["wrongPasswordPreservedCurrent"] is True
    assert after["tamperingPreservedCurrent"] is True
    metrics = {}
    for phase, record in (("before", before), ("after", after)):
        perf = record["performance"]
        values = perf["samplesMs"]
        assert perf["warmup"] == 5 and len(values) == 25
        assert all(isinstance(v, (int, float)) and math.isfinite(v) and v > 0 for v in values)
        ordered = sorted(values)
        assert perf["p50Ms"] == ordered[12] and perf["p95Ms"] == ordered[23]
        assert perf["peakObservedJvmUsedBytes"] > 0 and perf["peakObservedProcessPssKiB"] > 0
        assert perf["acceptanceBudget"] is None
        assert len(perf["queryPlans"]) == 3 and all(x["plan"] for x in perf["queryPlans"].values())
        metrics[phase] = {key: perf[key] for key in ("p50Ms", "p95Ms", "peakObservedJvmUsedBytes", "peakObservedProcessPssKiB", "api", "device")}
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
               "financialRows": 600, "activeRows": 585, "publicTestCertificateSha256": certificates[0],
               "metrics": metrics, "m0Comparison": "unavailable; different workload/platform and no native baseline",
               "acceptanceBudget": None, "productionMigration": False}
    (root / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    check(sys.argv[1])
