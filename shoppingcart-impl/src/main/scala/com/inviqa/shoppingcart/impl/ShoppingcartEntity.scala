package com.inviqa.shoppingcart.impl

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, JsValue, Json}
import com.inviqa.shoppingcart.api.Product

import scala.collection.immutable.Seq

class ShoppingcartEntity extends PersistentEntity {

  override type Command = ShoppingcartCommand[_]
  override type Event = ShoppingcartEvent
  override type State = ShoppingcartState

  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  override def initialState: ShoppingcartState = ShoppingcartState(List.empty[Product])

  /**
    * An entity can define different behaviours for different states, so the behaviour
    * is a function of the current state to a set of actions.
    */
  override def behavior: Behavior = {
    case ShoppingcartState(_) => Actions()
    .onCommand[AddToCartCommand, Done] {
      case (AddToCartCommand(product), context, state) =>
        context.thenPersist(
          AddedToCartEvent(product)
        ) { _ =>
          context.reply(Done)
        }
    }.onCommand[RemoveFromCartCommand, Done] {
      case (RemoveFromCartCommand(product), context, state) =>
        context.thenPersist(
          RemoveFromCartEvent(product)
        ) { _ =>
          context.reply(Done)
        }
    }.onReadOnlyCommand[ShowCartCommand.type, List[Product]] {
      case (ShowCartCommand, context, state) => context.reply(state.products)
    }.onEvent {
      case (AddedToCartEvent(product), state) =>
        ShoppingcartState(product :: state.products)
      case (RemoveFromCartEvent(product), state) =>
        ShoppingcartState(state.products.filterNot(_ == product))
    }
  }
}

final case class AddToCartCommand(product: Product) extends ShoppingcartCommand[Done]

object AddToCartCommand {
  implicit val format: Format[AddToCartCommand] = Json.format

  def apply(product: JsValue): AddToCartCommand = {
    product.validate[Product].asOpt match {
      case Some(p) => new AddToCartCommand(p)
      case None => throw new Exception(s"$product is not parsable")
    }
  }
}

case object ShowCartCommand extends ShoppingcartCommand[List[Product]]

case class RemoveFromCartCommand(product: Product) extends ShoppingcartCommand[Done]

object RemoveFromCartCommand {
  implicit val format: Format[RemoveFromCartCommand] = Json.format

  def apply(product: JsValue): RemoveFromCartCommand = {
    product.validate[Product].asOpt match {
      case Some(p) => new RemoveFromCartCommand(p)
      case None => throw new Exception(s"$product is not parsable")
    }
  }
}

case class AddedToCartEvent(product: Product) extends ShoppingcartEvent

object AddedToCartEvent {
  implicit val format: Format[AddedToCartEvent] = Json.format
}

case class RemoveFromCartEvent(product: Product) extends ShoppingcartEvent

object RemoveFromCartEvent {
  implicit val format: Format[RemoveFromCartEvent] = Json.format
}

/**
  * The current state held by the persistent entity.
  */
case class ShoppingcartState(products: List[Product])

object ShoppingcartState {
  implicit val format: Format[ShoppingcartState] = Json.format
}

/**
  * This interface defines all the events that the ShoppingcartEntity supports.
  */
sealed trait ShoppingcartEvent extends AggregateEvent[ShoppingcartEvent] {
  def aggregateTag: AggregateEventTag[ShoppingcartEvent] = ShoppingcartEvent.Tag
}

object ShoppingcartEvent {
  val Tag: AggregateEventTag[ShoppingcartEvent] = AggregateEventTag[ShoppingcartEvent]
}

/**
  * This interface defines all the commands that the ShoppingcartEntity supports.
  */
sealed trait ShoppingcartCommand[R] extends ReplyType[R]

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object ShoppingcartSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[ShoppingcartState],
    JsonSerializer[RemoveFromCartEvent],
    JsonSerializer[AddedToCartEvent],
    JsonSerializer[RemoveFromCartCommand],
    JsonSerializer[AddToCartCommand],
    JsonSerializer[Product]
  )
}
