package com.anchor.adhd.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Health Connect's permission-rationale and permission-usage entry point. */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                        Text("Health data in Anchor", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Anchor reads today's step count and exercise duration from Health Connect " +
                                "to complete habits whose thresholds you set. It requests read access only " +
                                "and does not write health records.\n\n" +
                                "Health totals are read when you sync or return to Anchor. Anchor does not " +
                                "upload the health records or request background health access. Habit " +
                                "completion is stored on this device and can be included in backups you export.\n\n" +
                                "You can revoke access at any time in Health Connect settings. To use " +
                                "Samsung Health data, enable its sharing with Health Connect first.",
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                        Button(onClick = { finish() }) { Text("Done") }
                    }
                }
            }
        }
    }
}
