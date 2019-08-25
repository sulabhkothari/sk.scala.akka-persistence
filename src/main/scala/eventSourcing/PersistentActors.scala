package eventSourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor
import com.typesafe.config.ConfigFactory

object PersistentActors extends App {

  // Commands
  case class Invoice(recipient: String, date: Date, amount: Int)

  case class InvoiceBulk(invoices: List[Invoice])

  // Events
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  case object Shutdown

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    /**
      * Handler that will called on recovery
      */
    override def receiveRecover: Receive = {
      case InvoiceRecorded(id, _, _, amount) =>
        log.info(s"Recovered invoice #$id for amount: $amount, total amount: $totalAmount")
        latestInvoiceId += id
        totalAmount += amount
    }

    /**
      * Normal receive method
      */
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
        When you receive a command
        1) you create an event to persist into the store
        2) you persist the event, then pass in a callback that will get triggered once the event is written
        3) Update the actor's state when the event has persisted
         */
        log.info(s"Received invoice for amount: $amount")

        // Both persist and the callback will be called asynchronously
        // Safe to access mutable state here. This does not cause race conditions, or break actor encapsulation
        // Akka persistence guarantees that no other threads are accessing the actor during a callback
        // Behind the scenes akka persistence is also message based
        // Akka persistence also guarantees that all messages received between below persist and following callback
        // (the time gap) are stashed
        //  So order of messages are maintained
        persist(InvoiceRecorded(latestInvoiceId, recipient, date, amount))
          /* time gap: all other messages sent to this actor are STASHED */ { e =>
          // updated state
          latestInvoiceId += 1
          totalAmount += amount
          // This is also safe and it can correctly identify the sender of the command
          sender ! "PersistenceACK"
          log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case InvoiceBulk(invoices) =>
        /*
        1. Create events (Plural)
        2. Persist all the events
        3. update the actor state when each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map {
          pair =>
            val id = pair._2
            val invoice = pair._1

            InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) {
          e: InvoiceRecorded =>
            latestInvoiceId += 1
            totalAmount += e.amount
            // This is also safe and it can correctly identify the sender of the command
            //sender ! "PersistenceACK"
            log.info(s"Persisted SINGLE $e as invoice #${e.id}, for total amount $totalAmount")
        }

      // act like a normal actor
      case "print" =>
        log.info(s"Latest invoice id: $latestInvoiceId, total amount: $totalAmount")
      case Shutdown =>
        log.warning("Shutting down actor")
        context.stop(self)
    }

    override def persistenceId: String = "simple-accountant"

    /*
    This method is called if persisting failed.
    Tha ACTOR is stopped regardless of the supervision strategy.
    This is because since we don't know that the event was persisted or not, actor is in an inconsistent state so it
    cannot be trusted even if it is resumed

    BEST PRACTISE: Start the actor again after a while.
    (use Backoff supervisor)
     */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
    Called if journal fails to persist the event
    The actor is RESUMED.
     */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")

      super.onPersistRejected(cause, event, seqNr)
    }

    /**
      * Persisting multiple events
      *
      * persistAll
      */
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")
    for (i <- 1 to 10) {
      accountant ! Invoice("The Sofa Company", new Date, i * 1000)
    }

  //val newInvoices = for (i <- 1 to 10) yield Invoice("The Awesome Chairs", new Date, i * 2000)
  //accountant ! InvoiceBulk(newInvoices.toList)
  accountant ! "print"

  /*
  NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES, because it breaks actor encapsulation
   */

  /**
    * SHUTDOWN of persistent actors
    *
    * BEST PRACTISE: Define your own shutdown message
   */

  // This is because Poison Pill goes to a different mailbox
  // accountant ! PoisonPill

  accountant ! Shutdown
  // Dead letters we see are because sender is not an actor, we are calling from MAIN thread.
}
