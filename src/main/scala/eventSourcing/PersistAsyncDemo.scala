package eventSourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  case class Command(contents: String)

  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case message =>
        log.info(s"Recovered: $message")
    }

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"
        // persistAsync name is a misnomer in this case because both persist & persistAsync are both asynchronous
        persistAsync(Event(contents)) /* TIME GAP: We do not stash incoming messages in case of persistAsync */ {
          e =>
            eventAggregator ! e
        }

        // some actual processing
        val processedContents = contents + "_processing"
        persistAsync(Event(processedContents)) /* TIME GAP: We do not stash incoming messages in case of persistAsync */ {
          e =>
            eventAggregator ! e
        }

        // Since even persistAsync operates using message passing, the writes to journal as well as execution of the
        //  callbacks are in order.
    }

    override def persistenceId: String = "critical-stream-processor"
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(s"Aggregating: $message")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "criticalStreamProcessor")
  streamProcessor ! Command("command1")
  streamProcessor ! Command("command2")

  /**
    * Persist vs PersistAsync
    * - PersistAsync gives higher throughput
    * - Persist guarantees absolute ordering of events (mutations of actor internal state by these events are in order,
    *   so do not create inconsistent state).
    */
}
