package com.inviqa.shoppingcart.api

import play.api.libs.json._

object Product {
  implicit val format: Format[Product] = Json.format
}

case class Product(name: String, price: Long)

