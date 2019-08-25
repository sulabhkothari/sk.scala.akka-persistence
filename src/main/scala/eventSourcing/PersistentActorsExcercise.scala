package eventSourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActorsExcercise extends App {

  case class Vote(citizenPID: String, candidate: String)

  class VotingStation extends PersistentActor with ActorLogging {
    private var citizensVoted: Set[String] = Set()
    private var votes: Map[String, Int] = Map()

    override def receiveRecover: Receive = {
      case vote@Vote(citizenPID, candidate) =>
        log.info(s"$citizenPID voted for $candidate recovered")
        handleInternalStateChange(vote)
    }

    override def receiveCommand: Receive = {
      case vote@Vote(citizenPID, candidate) =>
        if (!citizensVoted.contains(vote.citizenPID)) {
          log.info(s"$citizenPID voted for $candidate")

          persist(vote) { _ => // Command sourcing because we are not sourcing a different event, we are persisting the command directly
            log.info(s"$citizenPID voted for $candidate persisted")
            handleInternalStateChange(vote)
          }
        }
        else
          log.warning(s"Citizen:$citizenPID trying to vote multiple times!")
      case "countVotes" =>
        log.info(s"Voting Map: $votes")
    }

    def handleInternalStateChange(vote: Vote) = {
      votes += (vote.candidate -> (votes.getOrElse(vote.candidate, 0) + 1))
      citizensVoted += vote.citizenPID
    }

    override def persistenceId: String = "voting-station"
  }

  val system = ActorSystem("Elections")
  val voteStation = system.actorOf(Props[VotingStation], "votingStation")
  //  voteStation ! Vote("Jerry", "Trump")
  //  voteStation ! Vote("Jack", "Clinton")
  //  voteStation ! Vote("Jerry", "Sanders")
  //  voteStation ! Vote("Jerry", "Sanders")
  //  voteStation ! Vote("James", "Trump")
  //  voteStation ! Vote("Jimmy", "Sanders")
  //  voteStation ! Vote("Jenkins", "Trump")
  voteStation ! Vote("Jenkins", "Sanders")
  voteStation ! "countVotes"

}
