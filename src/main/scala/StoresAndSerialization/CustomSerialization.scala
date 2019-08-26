package StoresAndSerialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory

// Command
case class RegisterUser(email: String, name: String)

// Event
case class UserRegistered(id: Int, email: String, name: String)

//serializer
class UserRegistrationSerializer extends Serializer {
  val separator = "//"

  override def identifier: Int = 76234

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event@UserRegistered(id, email, name) =>
      println(s"Serializing $event")
      s"[$id$separator$email$separator$name]".getBytes
    case _ => throw new IllegalArgumentException("only user registration events supported in this serializer")
  }

  override def includeManifest: Boolean = false

  // manifest: Option[Class[_]]: this is used to instantiate the proper class you want via reflection
  //  manifest is passed with some class if includeManifest above returns true, so that you can use constructor of this
  //  class to instantiate actual object from bytes that you parse. If it returns false manifest: Option[Class[_]] will have None.
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split(separator)
    val id = values(0).toInt
    val email = values(1)
    val name = values(2)

    val result = UserRegistered(id, email, name)
    println(s"Deserialized $string to $result")
    result
  }
}


class UserRegistrationActor extends PersistentActor with ActorLogging {
  override def receiveRecover: Receive = {
    case event@UserRegistered(id, _, _) =>
      log.info(s"Recovered: $event")
      currentId = id
  }

  var currentId = 0

  override def receiveCommand: Receive = {
    case RegisterUser(email, name) =>
      persist(UserRegistered(currentId, email, name)) {
        e =>
          currentId += 1
          log.info(s"Persisted: $e")
      }
  }

  override def persistenceId: String = "user-registration"
}

object CustomSerialization extends App {
  val cassandraActorSystem = ActorSystem("customSerialization", ConfigFactory.load().getConfig("customSerializerDemo"))
  val userRegistration = cassandraActorSystem.actorOf(Props[UserRegistrationActor], "userRegistration")

  //for(i <- 11 to 20){
  //  userRegistration ! RegisterUser(s"a@b_$i.com", s"User:$i")
  //}

}
