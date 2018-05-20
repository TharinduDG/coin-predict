package com.coinpredict.controllers

import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit

import com.coinpredict.services.{PriceFetcher, PricePredictor}
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class MainController @Inject()(cc: ControllerComponents,
                               ws: WSClient,
                               priceFetcher: PriceFetcher,
                               pricePredictor: PricePredictor) extends AbstractController(cc) {
  private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")

  def getPrices(from: String, entries: Int) = Action.async {
    Try(dateFormatter.parse(from)).toOption.map { d =>
      priceFetcher.getPrices(d, entries).map {
        case Left(e) => BadRequest(e.getMessage)
        case Right(l) => Ok(Json.toJson(l))
      }
    }.getOrElse(Future.successful(BadRequest(s"$from dates should be in `yyyy-MM-dd` format!")))
  }

  def getRollingAvg(from: String, to: String, window: Int) = Action.async {

    Try((dateFormatter.parse(from), dateFormatter.parse(to))).toOption.map(dates => {
      val (fromDate, toDate) = dates
      if (fromDate.after(toDate)) {
        Left(new RuntimeException(s"$from should be before $to"))
      } else if (window > ChronoUnit.DAYS.between(fromDate.toInstant, toDate.toInstant))
        Left(new RuntimeException(s"window size $window is greater than the date difference"))
      else Right(dates)
    }).map({
      case Left(e) => Future.successful(BadRequest(e.getMessage))
      case Right((fromDate, toDate)) =>
        priceFetcher.getRollingAverage(fromDate, toDate, window).map({
          case Left(e) => BadRequest(e.getMessage)
          case Right(ps) => Ok(Json.toJson(ps))
        })
    }).getOrElse(Future.successful(BadRequest(s"$from, $to dates should be in `yyyy-MM-dd` format!")))
  }

  def predictPrices = Action.async {
    pricePredictor.fifteenDayPricePrediction.map {
      case Right(ps) => Ok(Json.toJson(ps))
      case Left(ex) => BadRequest(ex.getMessage)
    }
  }
}
