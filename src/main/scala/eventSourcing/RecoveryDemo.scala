package eventSourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)

  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case Event(id, contents) =>
        log.info(s"Recovered: $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
      //        if (contents.contains("314"))
      //          throw new RuntimeException("I can't take this anymore!")

      context.become(online(id))
      // THIS will not change the event handler during recovery
      // After recovery the "normal" handler will be the result of ALL the stacking of context.becomes.
      case RecoveryCompleted =>
        log.info("I have finished recovery")
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error(s"I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    //override def recovery: Recovery = Recovery(toSequenceNr = 100)
    //override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
    //override def recovery: Recovery = Recovery.none

    override def persistenceId: String = "recovery-actor"
  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")
  /*
  Stashing commands
   */
  //  for (i <- 1 to 1000) {
  //    recoveryActor ! Command(s"Command: $i")
  //  }
  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
    2 - failure during recovery
    - onRecoveryFailure + the actor is STOPPED (unconditional) because actor is in inconsistent state and cannot be trusted anymore
   */

  /*
    3 - customizing recovery - You can override recovery method to debug your recovery
      - DO NOT send/persist more events after a customized recovery (especially in case of incomplete recovery)
   */

  /*
    4 - recovery status or Knowing when you are done recovering
      - get a signal when you are done recovering
   */

  /*
    5 - Stateless actors
   */
}
