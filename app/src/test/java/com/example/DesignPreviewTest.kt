package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import com.example.ui.components.IconTile
import com.example.ui.components.SparkEmptyState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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

  @Test
  fun chat_bubbles_preview() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            ChatMessageBubblePreview(
              content = "Hey! I checked the GPU temps on Rig — all nominal at 54C.",
              isUser = false,
              meta = "llama3.2:latest • Rig (LINUX) • 21 tok/s",
            )
            ChatMessageBubblePreview(content = "Run nvidia-smi and paste the output.", isUser = true, meta = null)
            ChatMessageBubblePreview(
              content = "Here you go — four A100s idle, 10GB used each. Want me to start the fine-tune job?",
              isUser = false,
              meta = "hermes-agent • Rig",
              gold = true,
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/design_chat.png")
  }

  @Composable
  private fun ChatMessageBubblePreview(content: String, isUser: Boolean, meta: String?, gold: Boolean = false) {
    val align = if (isUser) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
      if (!isUser && meta != null) {
        Text(
          text = meta,
          fontSize = 10.sp,
          fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
          fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
          color = if (gold) com.example.ui.theme.HermesGold else com.example.ui.theme.SparkBlueBright,
          modifier = Modifier.padding(bottom = 3.dp, start = 4.dp),
        )
      }
      val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (isUser) 18.dp else 4.dp,
        topEnd = if (isUser) 4.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp,
      )
      Surface(
        shape = shape,
        color = when {
          isUser -> com.example.ui.theme.SparkBlue.copy(alpha = 0.14f)
          gold -> com.example.ui.theme.HermesSurfaceGold
          else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          when {
            isUser -> com.example.ui.theme.SparkBlue.copy(alpha = 0.30f)
            gold -> com.example.ui.theme.HermesBorderGold
            else -> com.example.ui.theme.DarkBorder
          },
        ),
        modifier = Modifier.widthIn(max = 330.dp),
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
          Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = com.example.ui.theme.TextPrimary,
            lineHeight = 21.sp,
          )
        }
      }
    }
  }

  @Test
  fun hero_and_empty_preview() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
              IconTile(
                icon = Icons.Outlined.Hub,
                tint = com.example.ui.theme.SparkBlue,
                size = 44.dp,
                iconSize = 24.dp,
                corner = 14,
              )
              androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 14.dp))
              Column {
                Text(
                  "Spark",
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  "Your Tailscale mesh",
                  style = MaterialTheme.typography.bodySmall,
                  color = com.example.ui.theme.TextMuted,
                )
              }
            }
            SparkEmptyState(
              title = "No computers yet",
              subtitle = "Add a machine from your Tailscale network to get terminal, files, LLM chat, Hermes and remote desktop in one place.",
              icon = Icons.Outlined.Hub,
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/design_empty_state.png")
  }
}
