package dappermongo

import dappermongo.MongoITSpecDefault.layer
import zio.{Chunk, ZIO, ZLayer, durationInt}
import zio.test.{TestAspect, TestAspectAtLeastR, TestEnvironment, ZIOSpec}

abstract class MongoITSpecDefault extends ZIOSpec[MongoClient] {

  val bootstrap = layer

  override val aspects: Chunk[TestAspectAtLeastR[MongoClient with TestEnvironment]] =
    super.aspects ++ List(TestAspect.timeout(60.seconds))

  def database(name: String): ZLayer[MongoClient, Throwable, Database] =
    ZLayer(ZIO.serviceWithZIO[MongoClient](_.database(name)))
}

object MongoITSpecDefault {
  val layer = Container.live >>> MongoClient.live
}
