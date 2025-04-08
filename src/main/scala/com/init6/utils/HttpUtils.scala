package com.init6.utils

import okhttp3._

object HttpUtils {
  private val client = new OkHttpClient()

  def postMessage(url: String, message: String) = {
//    val requestBody = RequestBody.create(MediaType.get("application/json"), s"""{"message": "$message"}""")
//
//    val request = new Request.Builder()
//      .url(url)
//      .post(requestBody)
//      .build()
//
//    val response = client.newCall(request).execute()
//
//    response.body().string()
  }
}
