import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "latest_stable_android", Path(__file__).with_name("latest-stable-android.py")
)
resolver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolver)


def image(api, *, channel="channel-0", suffix="", extra="", revision="", obsolete="false"):
    return f'''<remotePackage path="system-images;android-{api}{suffix};google_apis;x86_64" obsolete="{obsolete}">
      <type-details><api-level>{api}</api-level>{extra}</type-details>
      <revision><major>1</major>{revision}</revision>
      <channelRef ref="{channel}"/>
    </remotePackage>'''


class LatestStableAndroidTest(unittest.TestCase):
    def test_selects_highest_stable_not_repository_order(self):
        xml = "<repository>" + image(36) + image(34) + image(35) + "</repository>"
        self.assertEqual(36, resolver.latest_stable_api(xml))

    def test_rejects_preview_extension_obsolete_and_wrong_arch_images(self):
        xml = "<repository>" + "".join([
            image(36),
            image(37, channel="channel-1"),
            image(38, revision="<preview>1</preview>"),
            image(39, extra="<codename>Preview</codename>"),
            image(40, suffix="-ext1"),
            image(41, extra="<base-extension>false</base-extension>"),
            image(42, obsolete="true"),
            image(43).replace("x86_64", "arm64-v8a"),
            image(44).replace("google_apis", "google_apis_playstore"),
        ]) + "</repository>"
        self.assertEqual(36, resolver.latest_stable_api(xml))

    def test_fails_instead_of_falling_back_when_no_stable_image_exists(self):
        with self.assertRaises(ValueError):
            resolver.latest_stable_api("<repository>" + image(37, channel="channel-1") + "</repository>")

    def test_supports_namespaced_metadata(self):
        xml = '<repository xmlns="urn:sdk">' + image(37) + '</repository>'
        self.assertEqual(37, resolver.latest_stable_api(xml))


if __name__ == "__main__":
    unittest.main()
