import java.io.File

import akka.actor._
import com.typesafe.config.ConfigFactory

object akka_study_P185_remote {
  def main(args: Array[String]): Unit = {
    P185.start
  }

  object P185{
    def start: Unit = {
      val configFile = getClass.getClassLoader.getResource("remote-application.conf").getFile
      val config = ConfigFactory.parseFile(new File(configFile))
      val system = ActorSystem("remote-system",config)
      val serverActorRef = system.actorOf(Props[ServerActor], "sample185Actor")
      system.log.info("Remote is ready")
      serverActorRef ! "test"

    }

    class ServerActor extends Actor{
      override def receive: Receive = {
        case x:String => println("execute remote actor: " + x)
      }
    }
  }
}
