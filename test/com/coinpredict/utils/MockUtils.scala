package com.coinpredict.utils

import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.Future

object MockUtils extends Mockito {

  def wsClientMockFor(jsonResponse: String): WSClient = {
    val wsClientMock = mock[WSClient]
    val wsRequestMock = mock[WSRequest]
    val wsResponse = mock[WSResponse]

    wsClientMock.url(anyString) returns wsRequestMock
    wsRequestMock.addHttpHeaders(any()) returns wsRequestMock
    wsRequestMock.addQueryStringParameters(any()) returns wsRequestMock
    wsRequestMock.withRequestTimeout(any()) returns wsRequestMock
    wsRequestMock.withMethod(any()) returns wsRequestMock
    //  wsRequestMock.withBody(any()) returns wsRequestMock
    wsRequestMock.execute() returns Future.successful(wsResponse)
    wsRequestMock.get() returns Future.successful(wsResponse)
    wsRequestMock.post(anyString)(any()) returns Future.successful(wsResponse)
    wsResponse.json returns Json.parse(jsonResponse.getBytes)

    wsClientMock
  }
}
