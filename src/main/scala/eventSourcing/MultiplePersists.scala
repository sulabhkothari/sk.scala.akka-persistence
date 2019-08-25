package eventSourcing

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object MultiplePersists extends App {

  /*
  Diligent Accountant: with every invoice , will persist TWO events
   - a tax record for the fiscal authorities
   - an invoice record for the personal logs or some auditing authority
   */
  // Command
  case class Invoice(recipient: String, date: Date, amount: Int)

  // Event
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)

  case class InvoiceRecord(id: Int, recipient: String, date: Date, amount: Int)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {
    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0

    override def receiveRecover: Receive = {
      case event =>
        log.info(s"Recovered: $event")
    }

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        // it can be imagined something as journal ! TaxRecord
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 3)) {
          record =>
            taxAuthority ! record
            latestTaxRecordId += 1

            persist("I hereby declare this tax record to be true & complete!") {
              declaration =>
                taxAuthority ! declaration
            }
        }
        // it can be imagined something as journal ! InvoiceRecord
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) {
          invoiceRecord =>
            taxAuthority ! invoiceRecord
            latestInvoiceRecordId += 1

            persist("I hereby declare this invoice record to be true & complete!") {
              declaration =>
                taxAuthority ! declaration
            }

          // even though perist calls are asynchronous, the ordering of the events is guaranteed because it is implemented
          //  using message passing. Also, handlers for these persists are implemented using message passing so handlers
          //  for these persists events are also called in order.
        }
    }

    override def persistenceId: String = "diligent-accountant"
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received: $message")
    }
  }

  val system = ActorSystem("MultiplePersistsDemo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "HMRC")
  val accountant = system.actorOf(DiligentAccountant.props("UK52352_58325", taxAuthority))
  accountant ! Invoice("The Sofa Company", new Date, 2001)

  /*
  The message ordering (TaxRecord -> InvoiceRecord) is GUARANTEED
   */

  /**
    * PERSISTENCE is also based on message passing. Journals are actually implemented using ACTORS.
    */

  // Nested persisting

  // Group from first invoice followed by group of messages from second invoice
  // Below message will be stashed until first message related persists are complete
  accountant ! Invoice("Super Car Company", new Date, 2004)
}
