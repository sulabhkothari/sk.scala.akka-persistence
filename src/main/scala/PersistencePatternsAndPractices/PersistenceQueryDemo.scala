package PersistencePatternsAndPractices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged, WriteEventAdapter}
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.util.Random

object PersistenceQueryDemo extends App {

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case m => log.info(s"Recovered $m")
    }

    override def receiveCommand: Receive = {
      case m => persist(m) {
        _ => log.info(s"Persisted $m")
      }
    }

    override def persistenceId: String = "persistent-query-id-1"
  }

  val system = ActorSystem("PersistenceQuery", ConfigFactory.load().getConfig("persistenceQuery"))
  val readJournal = PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val persistenceIds = readJournal.persistenceIds()

  //Finite Stream
  //val persistenceIds = readJournal.currentPersistenceIds()

  // persistence IDs
  implicit val materializer = ActorMaterializer()(system)
  persistenceIds.runForeach {
    persistenceId =>
      println(s"Found PersistenceId: $persistenceId")
  }

  val simplePersistentActor = system.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  import system.dispatcher
  import scala.concurrent.duration._

  //  system.scheduler.scheduleOnce(5 seconds) {
  //    simplePersistentActor ! "Another message!"
  //  }

  // events by persistence ID
  val events = readJournal.eventsByPersistenceId("persistent-query-id-1", 0, Long.MaxValue)
  events.runForeach {
    event =>
      println(s"Read Event $event")
  }

  //events by tags
  val genres = Array("pop", "rock", "hip-hop", "jazz", "disco")

  case class Song(artist: String, title: String, genre: String)

  //Command
  case class Playlist(songs: List[Song])

  //Event
  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    override def receiveRecover: Receive = {
      case event@PlaylistPurchased(id, _) =>
        log.info(s"Recovered $event")
        latestPlaylistId = id
    }

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPlaylistId, songs)) {
          _ =>
            log.info(s"User purchased $songs")
            latestPlaylistId += 1
        }
    }

    override def persistenceId: String = "music-store-checkout"

    var latestPlaylistId = 0
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "musicStore"

    override def toJournal(event: Any): Any = event match {
      case event@PlaylistPurchased(_, songs) =>
        val genreSet = songs.map(_.genre).toSet
        Tagged(event, genreSet)
      case event => event
    }
  }

  val checkoutActor = system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")

  //  val r = Random
  //  for(_ <- 1 to 10) {
  //    val  maxSongs = r.nextInt(5)
  //    val songs = for(i <- 1 to maxSongs) yield {
  //      val randomGenre = genres(r.nextInt(5))
  //      Song(s"Artist $i", s"My song $i", randomGenre)
  //    }
  //    checkoutActor ! Playlist(songs.toList)
  //  }

  val rockPlaylists = readJournal.eventsByTag("rock", Offset.noOffset)

  rockPlaylists.runForeach {
    p =>
      println(s"Found a playlist with a rock song: $p")
  }
}
