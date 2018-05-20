package com.coinpredict.models

import java.util.Date

import play.api.libs.json._

case class Price(price: Double, date: Date)

object Price {
  implicit val priceFormat = Json.format[Price]
}