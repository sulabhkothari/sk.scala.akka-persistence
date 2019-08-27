package PersistencePatternsAndPractices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapters extends App {
  val ACOUSTIC = "acoustic"
  val ELECTRIC = "electric"

  case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)

  case class AddGuitar(guitar: Guitar, quantity: Int)

  case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)

  case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)

  class InventoryManager extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case event@GuitarAddedV2(id, model, make, quantity, guitarType) =>
        val guitar = Guitar(id, model, make, guitarType)
        addGuitar(guitar, quantity)
        log.info(s"Recovered $event")
    }

    def addGuitar(guitar: Guitar, quantity: Int) = {
      val existingQuantity = inventory.getOrElse(guitar, 0)
      inventory.put(guitar, existingQuantity + quantity)
    }

    override def receiveCommand: Receive = {
      case AddGuitar(guitar@Guitar(id, model, make, guitarType), quantity) =>
        persist(GuitarAddedV2(id, model, make, quantity, guitarType)) { e =>
          addGuitar(guitar, e.quantity)
          log.info(s"Added $quantity x $guitar to inventory")
        }
      case "print" =>
        log.info(s"Current Inventory is $inventory")
    }

    override def persistenceId: String = "guitar-inventory-manager"

    val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()
  }

  class GuitarReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case GuitarAdded(id, model, make, quantity) =>
        EventSeq.single(GuitarAddedV2(id, model, make, quantity, ACOUSTIC))
      case other => EventSeq.single(other)
    }
  }

  // WriteEventAdapter - used for backwards compatibility
  // actor -> write event adapter -> serializer -> journal
  // EventAdapter extends both if you want to implement both, then extend just one adapter using EventAdapter trait

  val system = ActorSystem("eventAdapters", ConfigFactory.load().getConfig("eventAdapters "))
  val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")
  //  val guitars = for(i <- 100 to 102) yield Guitar(i.toString, s"Hakker2 $i", "RockTheJVM", ELECTRIC)
  //  guitars.foreach(inventoryManager ! AddGuitar(_, 5))
}
