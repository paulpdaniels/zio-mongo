package zio.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.RawBsonDocument
import reactivemongo.api.bson.BSONDocumentWriter
import zio.ZIO
import zio.bson._
import zio.stream.ZStream
import zio.interop.reactivestreams.publisherToStream
import zio.mongo.internal.PublisherOps

trait FindOps {

  def find[Q: BsonEncoder](q: Q): FindBuilder[Collection]

  def find[Q: BsonEncoder, P: BsonEncoder](q: Q, p: P): FindBuilder[Collection]

  def findAll: FindBuilder[Collection]

}

trait FindBuilder[-R] {
  def one[A](implicit ev: BsonDecoder[A]): ZIO[R, Throwable, Option[A]]

  def stream[A](limit: Option[Int] = None, chunkSize: Int = 101)(implicit ev: BsonDecoder[A]): ZStream[R, Throwable, A]

  def explain[A](implicit ev: BsonDecoder[A]): ZIO[R, Throwable, Option[A]]

  def allowDiskUse(allowDiskUse: Boolean): FindBuilder[R]

  def collation(collation: Collation): FindBuilder[R]

  def comment(comment: String): FindBuilder[R]

  def hint(hint: String): FindBuilder[R]

  def noCursorTimeout(noCursorTimeout: Boolean): FindBuilder[R]

  def projection[P: BsonEncoder](projection: P): FindBuilder[R]

  def skip(skip: Int): FindBuilder[R]

  def sort[S: BsonEncoder](sort: S): FindBuilder[R]
}

object FindBuilder {

  private[mongo] case class Impl(database: MongoDatabase, options: QueryBuilderOptions)
      extends FindBuilder[Collection] {
    override def one[A](implicit ev: BsonDecoder[A]): ZIO[Collection, Throwable, Option[A]] =
      ZIO.serviceWithZIO { collection =>
        makePublisher(collection, options, Some(1), 1)
          .first()
          .single
          .map {
            case Some(doc) => BsonDecoder[A].fromBsonValue(doc).map(Some(_))
            case None      => Right(None)
          }
          .absolve
      }

    override def stream[A](limit: Option[Int], chunkSize: Int)(implicit
      ev: BsonDecoder[A]
    ): ZStream[Collection, Throwable, A] =
      (for {
        collection <- ZStream.service[Collection]
        doc        <- makePublisher(collection, options, limit, chunkSize).toZIOStream(chunkSize)
      } yield BsonDecoder[A].fromBsonValue(doc)).absolve

    override def explain[A](implicit ev: BsonDecoder[A]): ZIO[Collection, Throwable, Option[A]] =
      copy(options = options.copy(explain = Some(true))).one[A]

    override def allowDiskUse(allowDiskUse: Boolean): FindBuilder[Collection] =
      copy(options = options.copy(allowDiskUse = Some(allowDiskUse)))

    override def collation(collation: Collation): FindBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def comment(comment: String): FindBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def hint(hint: String): FindBuilder[Collection] =
      copy(options = options.copy(hint = Some(hint)))

    override def noCursorTimeout(noCursorTimeout: Boolean): FindBuilder[Collection] =
      copy(options = options.copy(noCursorTimeout = Some(noCursorTimeout)))

    override def projection[P: BsonEncoder](projection: P): FindBuilder[Collection] =
      copy(options =
        options.copy(projection = Some(BsonEncoder[P].toBsonValue(projection).asDocument()))
      ) // TODO This conversion is unsafe

    override def skip(skip: Int): FindBuilder[Collection] =
      copy(options = options.copy(skip = Some(skip)))

    override def sort[S: BsonEncoder](sort: S): FindBuilder[Collection] =
      copy(options =
        options.copy(sort = Some(BsonEncoder[S].toBsonValue(sort).asDocument()))
      ) // TODO This conversion is unsafe

    private def makePublisher(
      collection: Collection,
      options: QueryBuilderOptions,
      limit: Option[Int],
      batchSize: Int
    ) = {
      val underlying = database.getCollection(collection.name, classOf[RawBsonDocument])
      val builder    = options.filter.fold(underlying.find())(filter => underlying.find(filter))

      builder
        .sort(options.sort.orNull)
        .projection(options.projection.orNull)
        .filter(options.filter.orNull)
        .collation(options.collation.map(_.asJava).orNull)
        .comment(options.comment.orNull)
        .hintString(options.hint.orNull)
        .batchSize(batchSize)
        .allowDiskUse(options.allowDiskUse.map(java.lang.Boolean.valueOf).orNull)

      options.skip.foreach(builder.skip)
      limit.foreach(builder.limit)
      options.explain.foreach(if (_) builder.explain())
      options.noCursorTimeout.foreach(builder.noCursorTimeout)

      builder
    }
  }

}
