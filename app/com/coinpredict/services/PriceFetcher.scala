package com.coinpredict.services

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import com.coinpredict.models.{Price, RawPriceEntry}
import javax.inject.{Inject, Singleton}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsError, JsSuccess}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PriceFetcher @Inject()(ws: WSClient,
                             cache: AsyncCacheApi,
                             configs: Configuration)(implicit ec: ExecutionContext) {

  private val url = configs.underlying.getString("coinbase.url")
  private val cacheExpiration = configs.underlying.getDuration("coinbase.cache.expire.time").toMillis.milli
  private val wsRequest = ws.url(url)

  /**
    * get if there is a cache record for the given key values
    */
  private def getPricesFromCache(from: Date, entries: Int): Future[Option[List[Price]]] = {
    cache.get[List[Price]](cacheKey(from, entries))
  }

  /**
    * Get prices from the cache or by a rest api call to coinbase
    */
  def getPrices(from: Date, entries: Int): Future[Either[Exception, List[Price]]] = {
    getPricesFromCache(from, entries).flatMap {
      case Some(ps) =>
        Logger.debug(s"cache hit for key - ${cacheKey(from, entries)}")
        Future.successful(Right(ps))
      case None =>
        for {
          prices <- fetchPrices(from, entries)
        } yield {
          // cannot use scala 2.12's right biased Either since spark is only supporting up to scala 2.11
          // Hence some boilerplate
          prices match {
            case Right(ps) => cache.set(cacheKey(from, entries), ps, cacheExpiration)
            case Left(ex) => Future.failed(ex)
          }
          prices
        }
    }
  }

  /**
    * Fetch prices from coinbase api and parse it to Price objects
    */
  private def fetchPrices(from: Date, entries: Int): Future[Either[Exception, List[Price]]] = {
    val complexRequest =
      wsRequest.addHttpHeaders("Accept" -> "application/json")
        .addQueryStringParameters("period" -> "year")
        .withRequestTimeout(10000.millis)

    import Price._
    complexRequest.get().map(response => {
      (response.json \ "data" \ "prices").validate[List[RawPriceEntry]] match {
        case s: JsSuccess[List[RawPriceEntry]] =>
          val prices = s.value.map(p => {
            val doublePrice = p.price.toDouble
            val date = Date.from(Instant.parse(p.time))
            Price(doublePrice, date)
          }).filter(_.date.after(from)).take(entries).sortBy(_.date)
          Logger.debug(s"fetched ${prices.size} entires from $from")
          Right(prices)

        case JsError(e) =>
          Logger.error(s"error when fetching - $e")
          Left(new RuntimeException(e.map(_._2.map(_.message)).mkString(",")))
      }
    })
  }

  /**
    * Get rolling average for the given time frame and window.
    * Prices are from the cache or fetched from coinbase
    */
  def getRollingAverage(from: Date, to: Date, window: Int): Future[Either[Exception, List[Price]]] = {
    for {
      d <- Future.successful(ChronoUnit.DAYS.between(from.toInstant, to.toInstant))
      prices <- getPrices(from, d.toInt)
    } yield {
      prices match {
        case Right(ps) => Right(rollingPriceAverages(ps, window))
        case Left(ex) => Left(ex)
      }
    }
  }

  /**
    * rolling average calculator
    */
  @tailrec
  private def rollingPriceAverages(entries: List[Price], window: Int, rollingAverages: List[Price] = List.empty): List[Price] = {
    entries match {
      case es if es.size < window => rollingAverages
      case es =>
        val entriesWindow = es.take(window)
        val averagePrice = entriesWindow.map(_.price).sum / window
        val averageTime = entriesWindow.map(p => BigInt(p.date.getTime)).sum.bigInteger.divide(BigInt(window).bigInteger).longValue()
        val rollingAvg = Price(averagePrice, new Date(averageTime))
        rollingPriceAverages(es.drop(1), window, rollingAverages :+ rollingAvg)
    }
  }

  /**
    * Computes cache key for the given date and entries quantity
    */
  private def cacheKey(from: Date, entries: Int): String = {
    s"${from.getTime}-$entries"
  }
}
