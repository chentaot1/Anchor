"""Prevent private integrations from slipping into the public source screen."""
import unittest
from pathlib import Path
from verify_public_source import component_failures


class PublicEditionBoundaryTest(unittest.TestCase):
    def test_rejects_force_added_vendor_binaries_and_private_paths(self):
        for name in ("app/libs/sdk.aar", "app/libs/SDK.AAR", "app/libs/sdk.jar", ".private-edition/README.md", ".private-sdk/notes.md"):
            with self.subTest(name=name):
                self.assertTrue(component_failures(Path(name), b"fixture"))

    def test_rejects_vendor_source_and_gradle_dependencies(self):
        for name, data in (
            ("app/build.gradle.kts", b'implementation("com.google.android.gms:play-services-auth:21.3.0")'),
            ("app/src/Bridge.kt", b"import com.samsung.android.sdk.health.data.HealthDataService"),
            ("app/src/Bridge.java", b"com.google.android.gms.auth.GoogleAuthUtil.getToken(context, email, scope);"),
        ):
            with self.subTest(name=name):
                self.assertTrue(component_failures(Path(name), data))

    def test_rejects_removed_plant_assets(self):
        self.assertTrue(component_failures(Path("app/src/main/assets/plant.glb"), b"glTF"))

    def test_integration_edition_permits_only_the_approved_source_paths(self):
        approved = Path("app/src/main/java/com/anchor/adhd/health/SamsungHealthBridge.kt")
        source = b"import com.samsung.android.sdk.health.data.HealthDataService"
        self.assertEqual([], component_failures(approved, source, "integrations"))
        self.assertTrue(component_failures(approved, source))
        self.assertTrue(component_failures(Path("shared/src/UnexpectedBridge.kt"), source, "integrations"))
        self.assertTrue(component_failures(Path("app/libs/sdk.aar"), b"vendor SDK", "integrations"))
        self.assertTrue(component_failures(Path(".integrations-edition/README.md"), b"nested checkout", "integrations"))

    def test_permits_public_integrations_and_gradle_wrapper(self):
        self.assertEqual([], component_failures(Path("app/build.gradle.kts"), b'implementation("androidx.health.connect:connect-client:1.1.0")'))
        self.assertEqual([], component_failures(Path("app/src/Bridge.kt"), b"import android.provider.CalendarContract"))
        self.assertEqual([], component_failures(Path("gradle/wrapper/gradle-wrapper.jar"), b"wrapper"))

    def test_documentation_may_explain_vendor_boundary(self):
        self.assertEqual([], component_failures(Path("docs/editions.md"), b"com.google.android.gms and com.samsung.android.sdk belong to the private edition."))


if __name__ == "__main__":
    unittest.main()
