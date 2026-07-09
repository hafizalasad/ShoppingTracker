package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.ExpenseViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
    assertEquals("Shop Expense Tracker", appName)
  }

  @Test
  fun `parse gemini api error 503 response successfully`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ExpenseViewModel(application)

    val errorJson = """
      {
        "error": {
          "code": 503,
          "message": "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
          "status": "UNAVAILABLE"
        }
      }
    """.trimIndent()

    val mediaType = "application/json".toMediaType()
    val body = errorJson.toResponseBody(mediaType)
    val response = retrofit2.Response.error<Any>(503, body)
    val exception = retrofit2.HttpException(response)

    val parsedMessage = viewModel.getErrorMessage(exception)
    assertEquals(
      "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
      parsedMessage
    )
  }
}
