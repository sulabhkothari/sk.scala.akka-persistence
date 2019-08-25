package eventSourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object Snapshots extends App {

  // Commands
  case class ReceivedMessage(contents: String) //message FROM your contact

  case class SentMessage(contents: String) //message TO your contact

  // Events
  case class ReceivedMessageRecord(id: Int, contents: String)

  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }

  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    val MAX_MESSAGES = 10
    var currentMessageId = 0
    var commandsWithoutCheckpoint = 0
    val lastMessages = new mutable.Queue[(String, String)]

    override def receiveRecover: Receive = {
      case rec@ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered received message: $rec")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case sent@SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent message: $sent")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) =>
        log.info(s"Recovered snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving checkpoint...")
        saveSnapshot(lastMessages)
        commandsWithoutCheckpoint = 0
      }
    }

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) {
          e =>
            log.info(s"Received Message: $contents")

            maybeReplaceMessage(contact, contents)
            currentMessageId += 1
            maybeCheckpoint()
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) {
          e =>
            log.info(s"Sent Message: $contents")

            maybeReplaceMessage(owner, contents)
            currentMessageId += 1
            maybeCheckpoint()
        }
      case "print" =>
        log.info(s"Most recent messages: $lastMessages")
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving snapshot succeeded: $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.info(s"Saving snapshot $metadata failed because of $reason")
    }

    def maybeReplaceMessage(sender: String, contents: String) = {
      if (lastMessages.size >= MAX_MESSAGES) lastMessages.dequeue()
      lastMessages.enqueue((sender, contents))
    }

    override def persistenceId: String = s"$owner-$contact-chat"
  }

  val system = ActorSystem("ChatDemo")
  val chat = system.actorOf(Chat.props("daniel123", "martin345"))
  //  for (i <- 1 to 100004) {
  //    chat ! ReceivedMessage(s"Akka Rocks: $i")
  //    chat ! SentMessage(s"Akka Rules: $i")
  //  }
  chat ! "print"

  /*
  Pattern:
    - after each persist, maybe save a snapshot (logic is up to you)
    - if you save a snapshot, handle SnapshotOffer message in receiveRecover
    - (optional, but best practise): handle SaveSnapshotSuccess & SaveSnapshotFailure in receiveCommand
    and profit from the extra speed!!
   */
}
