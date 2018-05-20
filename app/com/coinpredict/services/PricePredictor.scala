package com.coinpredict.services

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import java.util.{Calendar, Date}

import com.coinpredict.models.Price
import javax.inject.{Inject, Singleton}
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.sql.SparkSession
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PricePredictor @Inject()(cache: AsyncCacheApi,
                               priceFetcher: PriceFetcher,
                               config: Configuration)(implicit ec: ExecutionContext) {

  private val rollingAverageWindowForRegression = config.underlying.getInt("coinbase.predictions.regression.rolling.window")
  private val spark = SparkSession.builder()
    .appName("SomeAppName")
    .config("spark.master", "local").getOrCreate()

  import spark.implicits._

  private val lr = new LinearRegression()
    .setFeaturesCol("timestamp")
    .setLabelCol("price")
    .setMaxIter(100)

  /**
    * Prediction for coin price for the next 15 days from today
    * Uses rolling averages to capture trends more and LinearRegression
    */
  def fifteenDayPricePrediction: Future[Either[Exception, List[Price]]] = {
    // using rolling average since it is capable of capturing trends than real values
    val rollingAverages = priceFetcher.getRollingAverage(
      Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(365))),
      Date.from(Instant.now()),
      rollingAverageWindowForRegression
    )

    for {
      rs <- rollingAverages
    } yield {
      rs match {
        case Right(avgs) =>
          val pricesDF = avgs.map(p => (Vectors.dense(p.date.getTime), p.price)).toDF("timestamp", "price")
          val lrModel = lr.fit(pricesDF)
          val trainingSummary = lrModel.summary
          val fifteenDays = fifteenDaysFromNow
          val fifteenDaysDF = fifteenDays.map(t => (Vectors.dense(t), 0)).toDF("timestamp", "price")
          val predictionsDF = lrModel.transform(fifteenDaysDF)
          val pricePredictions = predictionsDF.select("prediction").collect().map(_.getDouble(0)).toList
          val predictions = pricePredictions.zip(fifteenDays).map({ case (price, timestamp) => Price(price, new Date(timestamp)) })
          Logger.debug(s"r2: ${trainingSummary.r2}")
          Logger.debug(s"RMS: ${trainingSummary.rootMeanSquaredError}")
          Right(predictions)

        case Left(ex) => Left(ex)
      }
    }
  }

  /**
    * Creates a sequence of 15 dates
    * @return a List of Dates
    */
  private def fifteenDaysFromNow: List[Long] = {
    val now = Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS))
    val cal = Calendar.getInstance
    val fifteenDays = (1 to 15).map(i => {
      cal.setTime(now)
      cal.add(Calendar.DATE, i)
      cal.getTimeInMillis
    })
    fifteenDays.toList
  }
}
