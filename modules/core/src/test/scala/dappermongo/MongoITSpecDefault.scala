package dappermongo

import zio.{Chunk, durationInt}
import zio.test.{TestAspect, TestAspectAtLeastR, TestEnvironment, ZIOSpec}

abstract class MongoITSpecDefault extends ZIOSpec[MongoClient] {

  val bootstrap =
    Container.live >>> MongoClient.live

  override val aspects: Chunk[TestAspectAtLeastR[MongoClient with TestEnvironment]] =
    super.aspects ++ List(TestAspect.timeout(60.seconds))
}