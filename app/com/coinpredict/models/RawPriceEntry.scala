package com.coinpredict.models

import play.api.libs.json.Json

case class RawPriceEntry(price: String, time: String)

object RawPriceEntry {
  implicit val rawPriceFormat = Json.format[RawPriceEntry]
}