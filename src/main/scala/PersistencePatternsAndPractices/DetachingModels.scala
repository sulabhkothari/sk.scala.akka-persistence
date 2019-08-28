package PersistencePatternsAndPractices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventAdapter, EventSeq}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object DomainModel {

  case class User(id: String, name: String, email: String)

  case class Coupon(code: String, promotionAmount: Int)

  // Command
  case class ApplyCoupon(coupon: Coupon, user: User)

  // Event
  case class CouponApplied(code: String, user: User)

}

object DataModel {

  case class WrittenCouponApplied(code: String, userId: String, userEmail: String)

  case class WrittenCouponAppliedV2(code: String, userId: String, userName: String, userEmail: String)

}

class ModelAdapter extends EventAdapter {

  import DomainModel._
  import DataModel._

  override def fromJournal(event: Any, manifest: String): EventSeq = event match {
    case WrittenCouponApplied(code, userId, userEmail) =>
      println(s"Converting $event to Domain Model")
      EventSeq.single(CouponApplied(code, User(userId, "", userEmail)))
    case WrittenCouponAppliedV2(code, userId, userName, userEmail) =>
      println(s"Converting $event to Domain Model")
      EventSeq.single(CouponApplied(code, User(userId, userName, userEmail)))
  }

  override def manifest(event: Any): String = "CMA"

  override def toJournal(event: Any): Any = event match {
    case CouponApplied(code, user) =>
      println(s"Converting $event to Data Model")
      WrittenCouponAppliedV2(code, user.id, user.name, user.email)
    case other => EventSeq.single(other)
  }
}

object DetachingModels extends App {

  import DomainModel._

  class CouponManager extends PersistentActor with ActorLogging {
    val coupons = new mutable.HashMap[String, User]()

    override def receiveRecover: Receive = {
      case event@CouponApplied(code, user) =>
        log.info(s"Recovered $event")
        coupons.put(code, user)
    }

    override def receiveCommand: Receive = {
      case ApplyCoupon(coupon, user) =>
        if (!coupons.contains(coupon.code))
          persist(CouponApplied(coupon.code, user)) {
            e =>
              log.info(s"Persisted event $e")
              coupons.put(coupon.code, user)
          }
    }

    override def persistenceId: String = "coupon-manager"
  }

  val system = ActorSystem("DetachingModel", ConfigFactory.load().getConfig("detachingModel"))
  val couponManager = system.actorOf(Props[CouponManager], "couponManager")

  //    for (i <- 6 to 10) {
  //      val coupon = Coupon(s"MEGA COUPON $i", 100)
  //      val user = User(s"$i", s"user $i", s"user_$i@rtjvm.com")
  //
  //      couponManager ! ApplyCoupon(coupon, user)
  //    }
}
