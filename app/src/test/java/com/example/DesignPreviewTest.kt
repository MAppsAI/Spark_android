package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.NodeStatus
import com.example.data.model.TailNode
import com.example.ui.components.NodeCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local design-QA harness: renders real redesigned components with seeded data
 * and records PNGs via Roborazzi (Robolectric NATIVE). Not wired to navigation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DesignPreviewTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun node(
    id: Long, name: String, ip: String, os: String,
    fav: Boolean = false, tags: String = "Workstation",
  ) = TailNode(
    id = id, name = name, tailscaleIp = ip, osType = os,
    sshPort = 22, llmPort = 11434, fileServerPort = 8080,
    remoteDesktopPort = 6080, isFavorite = fav, tags = tags,
  )

  private val nodes = listOf(
    node(1, "Rig", "100.82.14.92", "LINUX", fav = true, tags = "AI Lab"),
    node(2, "Studio Display", "100.99.3.7", "MACOS", tags = "Creative"),
    node(3, "Home Server", "100.101.22.8", "LINUX", tags = "Media · Docker"),
    node(4, "Office PC", "100.77.5.201", "WINDOWS", tags = "Work"),
    node(5, "Old Laptop", "100.64.19.4", "LINUX", tags = "Spare"),
  )

  private val statuses = mapOf(
    1L to NodeStatus(isOnline = true, latencyMs = 12),
    2L to NodeStatus(isOnline = true, latencyMs = 214),
    3L to NodeStatus(isOnline = true, latencyMs = 45),
    4L to NodeStatus(isOnline = false, latencyMs = -1),
    5L to NodeStatus(isOnline = false, latencyMs = -1),
  )

  @Test
  fun nodes_list_preview() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(nodes.size) { i ->
              val n = nodes[i]
              NodeCard(
                node = n,
                status = statuses[n.id],
                isSelected = n.id == 1L,
                onSelect = {},
                onToggleFavorite = {},
                onOpenFeature = {},
                modifier = Modifier.testTag("preview_card_${n.id}")
              )
            }
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/design_nodes_list.png")
  }
}
