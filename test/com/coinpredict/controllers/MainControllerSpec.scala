package com.coinpredict.controllers

import java.time.Instant
import java.util.Date

import com.coinpredict.models.Price
import com.coinpredict.services.{PriceFetcher, PricePredictor}
import com.coinpredict.utils.MockUtils._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect._

class MainControllerSpec extends PlaySpec with Mockito with BeforeAndAfterEach {
  private val cacheMock = mock[AsyncCacheApi]
  private val wsClientEmptyMock = wsClientMockFor("{}")
  private val configs = Configuration.apply(
    "coinbase.url" -> "http://fake.com",
    "coinbase.cache.expire.time" -> "1 hour",
    "coinbase.predictions.regression.rolling.window" -> 2
  )
  private val priceFetcherMock = mock[PriceFetcher]
  private val pricePredictorMock = mock[PricePredictor]


  "getPrices in MainController" should {
    "return BadRequest when the date is in a bad format" in {
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcherMock, pricePredictorMock)
      val response = mainController.getPrices("2018.01.01", 10).apply(FakeRequest())
      status(response) must be(BAD_REQUEST)
    }

    "return cached result when there is a cached entry" in {
      val fromDate = "2018-01-01"
      val time = Date.from(Instant.parse(s"${fromDate}T00:00:00.00Z")).getTime
      val entries = 10
      val expected = List(Price(1.4, new Date(time - 5000)), Price(1.6, new Date(time - 4000)), Price(1.5, new Date(time - 3000)))
      cacheMock.get[List[Price]](anyObject)(anyObject).returns(Future.successful(Option(expected)))

      val priceFetcher = new PriceFetcher(wsClientEmptyMock, cacheMock, configs)
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcher, pricePredictorMock)
      val response = mainController.getPrices(fromDate, entries).apply(FakeRequest())
      status(response) must be(OK)
      Json.fromJson[List[Price]](contentAsJson(response)).get must be(expected)
      verify(cacheMock).get("1514745000000-10")(classTag[List[Price]])
    }

    "return prices by calling coinbase service when the cache is empty" in {
      val fromDate = "2018-01-01"
      val entries = 10
      val expectedPrices = List(
        Price(8577.00, Date.from(Instant.parse(s"2018-05-14T00:00:00.00Z"))),
        Price(8642.02, Date.from(Instant.parse(s"2018-05-15T00:00:00.00Z"))),
        Price(8478.22, Date.from(Instant.parse(s"2018-05-16T00:00:00.00Z")))
      )
      cacheMock.get[List[Price]](anyObject)(anyObject).returns(Future.successful(None))
      val json =
        """
          |{
          |   "data":{
          |      "base":"BTC",
          |      "currency":"USD",
          |      "prices":[
          |         {
          |            "price":"8478.22",
          |            "time":"2018-05-16T00:00:00Z"
          |         },
          |         {
          |            "price":"8642.02",
          |            "time":"2018-05-15T00:00:00Z"
          |         },
          |         {
          |            "price":"8577.00",
          |            "time":"2018-05-14T00:00:00Z"
          |         }
          |      ]
          |   }
          |}
        """.stripMargin
      val wsClientMockWithPrices = wsClientMockFor(json)
      val priceFetcher = new PriceFetcher(wsClientMockWithPrices, cacheMock, configs)
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcher, pricePredictorMock)
      val response = mainController.getPrices(fromDate, entries).apply(FakeRequest())
      status(response) must be(OK)
      verify(cacheMock).get("1514745000000-10")(classTag[List[Price]])
      Json.fromJson[List[Price]](contentAsJson(response)).get must be(expectedPrices)
    }
  }

  "getRollingAvg in MainController" should {
    "return BadRequest when the dates are in a bad format" in {
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcherMock, pricePredictorMock)
      val response = mainController.getRollingAvg("2018.01.01", "2018.01.02", 10).apply(FakeRequest())
      status(response) must be(BAD_REQUEST)
    }

    "return BadRequest when rolling window is larger than the date difference" in {
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcherMock, pricePredictorMock)
      val response = mainController.getRollingAvg("2018-01-01", "2018-01-10", 20).apply(FakeRequest())
      status(response) must be(BAD_REQUEST)
    }

    "return BadRequest when from date is after to date" in {
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcherMock, pricePredictorMock)
      val response = mainController.getRollingAvg("2018-02-01", "2018-01-10", 5).apply(FakeRequest())
      status(response) must be(BAD_REQUEST)
    }

    "return cached result when there is a cached entry" in {
      val fromDate = "2018-01-01"
      val toDate = "2018-02-01"
      val time = Date.from(Instant.parse(s"${fromDate}T00:00:00.00Z")).getTime
      val window = 2
      val prices = List(Price(1.4, new Date(time - 5000)), Price(1.6, new Date(time - 4000)), Price(1.5, new Date(time - 3000)))
      cacheMock.get[List[Price]](anyObject)(anyObject).returns(Future.successful(Option(prices)))

      val priceFetcher = new PriceFetcher(wsClientEmptyMock, cacheMock, configs)
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcher, pricePredictorMock)
      val response = mainController.getRollingAvg(fromDate, toDate, window).apply(FakeRequest())
      status(response) must be(OK)
      val expected = List(Price(1.5, new Date(1514764795500L)), Price(1.55, new Date(1514764796500L)))
      val actual = Json.fromJson[List[Price]](contentAsJson(response)).get
      actual must be(expected)
      verify(cacheMock).get("1514745000000-31")(classTag[List[Price]])
    }

    "return rolling averages by calling coinbase service when the cache is empty" in {
      val fromDate = "2018-01-01"
      val toDate = "2018-02-01"
      val window = 2
      val expectedPrices = List(
        Price(8609.51, new Date(1526299200000L)),
        Price(8560.11, new Date(1526385600000L))
      )
      cacheMock.get[List[Price]](anyObject)(anyObject).returns(Future.successful(None))
      val json =
        """
          |{
          |   "data":{
          |      "base":"BTC",
          |      "currency":"USD",
          |      "prices":[
          |         {
          |            "price":"8478.20",
          |            "time":"2018-05-16T00:00:00Z"
          |         },
          |         {
          |            "price":"8642.02",
          |            "time":"2018-05-15T00:00:00Z"
          |         },
          |         {
          |            "price":"8577.00",
          |            "time":"2018-05-14T00:00:00Z"
          |         }
          |      ]
          |   }
          |}
        """.stripMargin
      val wsClientMockWithPrices = wsClientMockFor(json)
      val priceFetcher = new PriceFetcher(wsClientMockWithPrices, cacheMock, configs)
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcher, pricePredictorMock)
      val response = mainController.getRollingAvg(fromDate, toDate, window).apply(FakeRequest())
      status(response) must be(OK)
      verify(cacheMock).get("1514745000000-31")(classTag[List[Price]])
      val actual = Json.fromJson[List[Price]](contentAsJson(response)).get
      actual must be(expectedPrices)
    }
  }

  "predictPrices in MainController" should {
    "return 15 predictions" in {
      val fromDate = "2018-01-01"
      val time = Date.from(Instant.parse(s"${fromDate}T00:00:00.00Z")).getTime
      val expected = List(Price(1.4, new Date(time - 5000)), Price(1.6, new Date(time - 4000)), Price(1.5, new Date(time - 3000)))
      cacheMock.get[List[Price]](anyObject)(anyObject).returns(Future.successful(Option(expected)))

      val priceFetcher = new PriceFetcher(wsClientEmptyMock, cacheMock, configs)
      val pricePredictor = new PricePredictor(cacheMock, priceFetcher, configs)
      val mainController = new MainController(stubControllerComponents(), wsClientEmptyMock, priceFetcher, pricePredictor)
      val response = mainController.predictPrices().apply(FakeRequest())
      status(response)(1.minute) must be(OK)
      Json.fromJson[List[Price]](contentAsJson(response)).get.size must be(15)
      verify(cacheMock).get("1495238400000-365")(classTag[List[Price]])
    }
  }

  override protected def beforeEach(): Unit = {
    reset(cacheMock)
    reset(priceFetcherMock)
    reset(pricePredictorMock)
    reset(wsClientEmptyMock)
  }
}
