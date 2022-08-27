package com.kubukoz

import java.io.DataOutput
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.Arrays

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.comcast.ip4s.SocketAddress
import fs2.Chunk
import fs2.io.file.Files
import fs2.io.net.Network
import org.apache.activemq.command.ConnectionInfo
import org.apache.activemq.openwire.OpenWireFormat
import org.apache.activemq.openwire.v12.MarshallerFactory
import org.apache.activemq.openwire.v12.MessageMarshaller
import org.apache.activemq.ActiveMQConnectionFactory
import javax.jms.Session
import cats.effect.ExitCode

object Main extends IOApp.Simple {

  import com.comcast.ip4s.IpLiteralSyntax
  import scala.util.chaining._

  val contentSize = 62

  val conninfo = new ConnectionInfo()
  conninfo.setUserName("admin")
  conninfo.setPassword("admin")

  val format = new OpenWireFormat(OpenWireFormat.DEFAULT_WIRE_VERSION)
  val marsh = MarshallerFactory
    .createMarshallerMap(
      format
    )
    .head

  def addStringType: ByteBuffer => ByteBuffer = _.put(77: Byte)

  def writeString(value: String): ByteBuffer => ByteBuffer =
    _.put(
      1: Byte /* not empty */
    ).putInt(value.getBytes().size)
      .put(value.getBytes())

  def addKeyValue(key: String, value: String): ByteBuffer => ByteBuffer = bb =>
    {
      bb
        .pipe(addStringType)
        .pipe(writeString(key))
        .pipe(addStringType)
        .pipe(writeString(value))
    }

  val content = ByteBuffer
    .allocate(contentSize)
    .put(1: Byte /* map not empty */ )
    .putInt(2 /* size of map keyset */ )
    .pipe(addKeyValue("username", "admin"))
    .pipe(addKeyValue("password", "admin"))
    .array()

  val helloCommand: Array[Byte] = {
    val sizePrefix = {
      ByteBuffer.allocate(4).putInt(content.size + 1).array()
    }

    sizePrefix ++ Array[Byte](3 /* CONNECTION_INFO */ ) ++ content
  }

  println(Arrays.toString(helloCommand))
  // This is your new "main"!
  def run: IO[Unit] =
    IO {
      val cf = new ActiveMQConnectionFactory(
        "admin",
        "admin",
        "tcp://localhost:8080"
      )

      val conn = cf.createConnection()

      val ses = conn.createSession(true, Session.SESSION_TRANSACTED)

    } *> IO.never
  // fs2.Stream
  //   .emits(helloCommand)
  //   .through(Files[IO].writeAll(Paths.get("./msg.bin")))
  //   .compile
  //   .drain *>
  //   Network[IO].socketGroup().use {
  //     _.client(SocketAddress(host"localhost", port"61616")).use {
  //       clientSocket =>
  //         IO.println("started") *>
  //           clientSocket.reads
  //             .take(396)
  //             // .through(fs2.text.utf8Decode[IO])
  //             // .debug()
  //             // .through(fs2.text.utf8Encode[IO])
  //             .through(clientSocket.writes)
  //             .compile
  //             .drain *>
  //           clientSocket.reads
  //             .take(96)
  //             // .through(Files[IO].writeAll(Paths.get("out2.bin")))
  //             // .through(fs2.text.utf8Decode[IO])
  //             // .debug()
  //             .compile
  //             .drain *>
  //           clientSocket.write(Chunk.array(helloCommand)) *>
  //           clientSocket.reads
  //             .through(fs2.text.utf8Decode[IO])
  //             .debug()
  //             .compile
  //             .drain

  //     }
  //   }
}

// case class Command(size: Int, tpe: Byte, fields: Array[])

object Server extends IOApp.Simple {
  import com.comcast.ip4s._
  def run: IO[Unit] = fs2.Stream
    .resource(Network[IO].socketGroup())
    .flatMap {
      _.server(address = Some(host"localhost"), port = Some(port"8080"))
    }
    .map { sock =>
      fs2.Stream.exec(IO.println("Got connection")) ++
        sock.reads
          .through(Files[IO].writeAll(Paths.get("./out4.bin")))
          .interruptAfter(1.second)
          .drain ++
        Files[IO]
          .readAll(Paths.get("./out.bin"), 4096)
          .through(sock.writes) ++
        Files[IO]
          .readAll(Paths.get("./out2.bin"), 4096)
          .through(sock.writes) ++
        sock.reads
          .through(Files[IO].writeAll(Paths.get("./out5.bin")))
          .interruptAfter(1.second)
          .drain
    }
    .parJoinUnbounded
    .compile
    .drain
}
