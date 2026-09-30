package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TailNode Power", appName)
  }

  @Test
  fun `test chat message input and send`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val factory = com.example.viewmodel.TailNodeViewModelFactory(app)
    val viewModel = factory.create(com.example.viewmodel.TailNodeViewModel::class.java)
    viewModel.setChatInput("Hello world")
    assertEquals("Hello world", viewModel.chatInput.value)
    viewModel.sendChatMessage()
  }
}
