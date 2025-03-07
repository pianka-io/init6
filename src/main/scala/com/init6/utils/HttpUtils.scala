package com.init6.utils

import sttp.client3.quick.backend
import sttp.client3.{UriContext, basicRequest}

object HttpUtils {

  def postMessage(url: String, message: String): String = {
    val request = basicRequest
      .post(uri"$url")
      .body(s"""{"message": "$message"}""")
      .header("Content-Type", "application/json")

    val response = request.send(backend)

    response.body match {
      case Right(body) => body
      case Left(error) => s"Request failed: $error"
    }
  }
}
