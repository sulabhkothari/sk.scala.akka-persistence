package StoresAndSerialization

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("Recovery Done!")
      case SnapshotOffer(metadata, payload: Int) =>
        log.info(s"Recovering snapshot: $payload")
        nMessages = payload
      case message =>
        log.info(s"Recovered: $message")
        nMessages += 1
    }

    override def receiveCommand: Receive = {
      case SaveSnapshotSuccess(metadata) =>
        log.info("Snapshot was successful!")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"Save snapshot failed: $reason")
      case "print" =>
        log.info(s"I have persisted $nMessages so far")
      case "snap" =>
        saveSnapshot(nMessages)
      case message => persist(message) { _ =>
        log.info(s"I have persisted $message")
        nMessages += 1
      }
    }

    override def persistenceId: String = "simple-persistent-actor"

    // mutable state
    var nMessages = 0
  }