import akka.actor._

import scala.concurrent.Await
import scala.util.control


object akka_study {
  def main(args: Array[String]): Unit = {
    P177.start
    P179.start
    P180.start
    P182.start
    P183.start
  }

  //このアクター終了をモニタリング
  object P177 {
    def start{
      val system = ActorSystem()
      val parentRef = system.actorOf(Props[ParentActor], name = "ParentActor")
      val childRef = system.actorOf(Props[ChildActor], name = "ChildActor")
      parentRef ! "test"
      parentRef ! "bye"
      Thread.sleep(1000)
      system.terminate()
    }

    class ParentActor extends Actor{
      import context._
      val subref = actorOf(Props[ChildActor], name = "childactor")
      context.watch(subref)
      def receive = {
        case "bye" => subref ! "bye"
        case Terminated(subref) => {
          println("Terminated")
          context.stop(self)
        }
        case _ => println("I'm parent actor.")
      }
    }

    class ChildActor extends Actor{
      def receive = {
        case "bye" => {
          println("bye!")
          context.stop(self)
        }
        case _ => println("I'm child actor.")
      }
    }
  }

  object P178{
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._

    class HelloActor extends Actor {
      import context._

      //OneForOneStrategyは失敗した子のアクターに対してのみ適用
      override val supervisorStrategy = OneForOneStrategy(
        //再起動の最大リトライ回数
        maxNrOfRetries = 10,
        //制限時間
        withinTimeRange = 1 minute
      ){
        //ArithmeticException         ・・算術計算で例外的条件が発生した場合にスロー
        //Resume　　　　　　          ・・メッセージの処理を再開
        case _ : ArithmeticException => Resume

        //ActorInitializationException・・アクターの初期化が失敗した場合にスロー？
        //Restart　　　　　　         ・・再起動
        case _ : ActorInitializationException => Restart
        //Escalate                    ・・親のアクターへエラーをエスカレートする
        case _ : Exception => Escalate
      }

      val subref = actorOf(Props[SubActor], name = "subActor")

      def receive = {
        case s :String => subref ! s
      }
    }
    class SubActor extends Actor{
      def receive = {
        case "bye" => {
          println("bye!")
          context.stop(self)
        }
        case _ => println("I'm sub actor.")
      }
    }
  }

  //actorの処理結果をFutureで管理
  object P179{
    import akka.pattern.ask
    import scala.concurrent.duration._
    import akka.util.Timeout

    implicit val timeout = Timeout(5 seconds)
    val system = ActorSystem()
    def start = {
      val ref = system.actorOf(Props[SampleActor], name = "SampleActor")
      //5秒(上記で設定した5秒)
      val future1 = ref ? "World"
      println("future1 is " + Await.result(future1, timeout.duration))
      //10秒
      val future2 = ref.ask("World")(10 seconds)
      println("future2 is " + Await.result(future2, timeout.duration))
      system.terminate()
    }

    class SampleActor extends Actor{
      override def receive: Receive = {
        case s:String => sender ! s"Hello $s"
        case _ => println("I'm SampleActor.")
      }
    }
  }

  object P180 {
    import akka.pattern.ask
    import scala.concurrent.duration._
    import akka.util.Timeout

    // onComplete分岐をする際に必要
    import scala.util._

    //書かないとandThenでエラーが発生する
    import scala.concurrent.ExecutionContext.Implicits.global


    implicit val timeout = Timeout(5 seconds)
    val system = ActorSystem()
    def start = {
      val ref = system.actorOf(Props[SampleActor], name = "SampleActor")
      val future = ref ? "World"
//    onCompleteがおすすめらしい
//      future onSuccess {
//        case _ => println("success")
//      }
//
//      future onFailure{
//        case e:Exception => e.printStackTrace
//      }

      //成功時、失敗時の分岐例は下記
//      future onComplete {
//        case Success(result) => println(result)
//        case Failure(failure) => failure.printStackTrace
//      }
      //成功時、失敗時の処理が完了後、最後に共通で処理を行う場合に下記のように行う
      future andThen {
        case Success(result) => println(result)
        case Failure(failure) => failure.printStackTrace
      } andThen {
        case _  => println("complete andThen")
      }

      println("future is " + future)
      system.terminate()
    }

    class SampleActor extends Actor{
      override def receive: Receive = {
        case s:String => sender ! s"Hello $s"
        case _ => println("I'm SampleActor.")
      }
    }
  }

  //Akkaのアクターからメッセージを転送
  object P182{
    val system = ActorSystem()
    def start: Unit ={
      val forwardRef = system.actorOf(Props[ForwardActor], name = "ForwardActor")
//      val subactorRef = system.actorOf(Props[ForwardActor], name = "SubActor")
      forwardRef ! "p182"
      Thread.sleep(1000)
      system.terminate()
    }

    class ForwardActor extends Actor {
      import context._

      //転送先のアクター
      val subref = actorOf(Props[SubActor], name = "SubActor")
      def receive = {
        case s:String => subref.forward(s)
      }
    }

    class SubActor extends Actor {
      override def receive = {
        case s:String => println("I'm SubActor. Message is " + s)
        case _ => println("I'm SubActor.")
      }
    }
  }

  //アクターの開始、終了、再起動のコールバックを受け取る
  object P183 {
    def start = {
      val system = ActorSystem()
      val helloRef = system.actorOf(Props[HelloActor], name = "HelloActor")
      helloRef ! 192
      Thread.sleep(2000)
      helloRef ! "gab"
    }

    class HelloActor extends Actor{
      override def receive: Receive = {
        case s: String => {
          println("Hello " + s)
          context.system.terminate()
        }
          //例外が発生しても、自動で再起動をする
        case _ => throw new IllegalArgumentException
      }

      override def preStart: Unit = {
        println("preStart")
      }

      override def postStop: Unit = {
        println("postStop")
      }

      //再起動前
      override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
        println("preRestart: " + message.get)
      }

      //再起動後
      override def postRestart(reason: Throwable): Unit = {
        println("postRestart: " + reason)
      }
    }
  }
}
