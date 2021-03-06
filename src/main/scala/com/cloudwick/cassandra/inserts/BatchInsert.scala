package com.cloudwick.cassandra.inserts

import java.util.concurrent.atomic.AtomicLong
import com.cloudwick.cassandra.OptionsConfig
import org.slf4j.LoggerFactory
import com.cloudwick.generator.utils.Utils
import com.cloudwick.generator.movie.{Customers, MovieGenerator}
import com.cloudwick.cassandra.dao.MovieDAO
import scala.collection.JavaConversions._
import scala.util.Random
import java.util
import scala.collection.mutable.ArrayBuffer

/**
 * Description goes here
 * @author ashrith 
 */
class BatchInsert(eventsStartRange: Int,
                  eventsEndRange: Int,
                  counter: AtomicLong,
                  customersMap: Map[Int, String],
                  config: OptionsConfig) extends Runnable {
  lazy val logger = LoggerFactory.getLogger(getClass)
  val utils = new Utils
  val movie = new MovieGenerator

  // Batch holders & counters
  val customerWatchHistoryData = new util.HashMap[Integer, util.List[String]]()
  val customerRatingData = new util.HashMap[Integer, util.List[String]]()
  val customerQueueData = new util.HashMap[Integer, util.List[String]]()
  val movieGenreData = new util.HashMap[Integer, util.List[String]]()
  var batchMessagesCount: Int = 0

  def threadName = Thread.currentThread().getName

  def run() = {
    val totalRecords = eventsEndRange - eventsStartRange + 1
    val movieDAO = new MovieDAO(config.cassandraNode)
    val customersSize = customersMap.size

    try {
      utils.time(s"inserting $totalRecords by thread $threadName") {
        (eventsStartRange to eventsEndRange).foreach { recordID =>
          batchMessagesCount += 1

          val cID: Int = Random.nextInt(customersSize) + 1
          val cName: String = customersMap(cID)
          val movieInfo: Array[String] = movie.gen
          val customer: Customers = new Customers(cID, cName, movieInfo(3).toInt)
          val customerID: Int               = customer.custId
          val customerName: String          = customer.custName
          // val customerActive: String     = customer.userActiveOrNot
          val customerTimeWatchedInit: Long = customer.timeWatched
          val customerPausedTime: Int       = customer.pausedTime
          val customerRating: String        = customer.rating
          val movieId: String               = movieInfo(0)
          val movieName: String             = movieInfo(1).replace("'", "")
          val movieReleaseYear: String      = movieInfo(2)
          val movieRunTime: String          = movieInfo(3)
          val movieGenre: String            = movieInfo(4)

          customerWatchHistoryData.put(recordID, bufferAsJavaList(ArrayBuffer(
            customerID.toString,
            customerTimeWatchedInit.toString,
            customerPausedTime.toString,
            movieId,
            movieName)))
          customerRatingData.put(recordID, bufferAsJavaList(ArrayBuffer(
            customerID.toString,
            movieId,
            movieName,
            customerName,
            customerRating)))
          customerQueueData.put(recordID, bufferAsJavaList(ArrayBuffer(
            customerID.toString,
            customerTimeWatchedInit.toString,
            customerName,
            movieId,
            movieName)))
          movieGenreData.put(recordID, bufferAsJavaList(ArrayBuffer(
            movieGenre,
            movieReleaseYear,
            movieId,
            movieRunTime,
            movieName)))

          if (batchMessagesCount == config.batchSize || batchMessagesCount == totalRecords) {
            // logger.debug("Flushing batch size of :" + config.batchSize)
            movieDAO.batchLoadWatchHistory(config.keyspaceName, customerWatchHistoryData, config.aSync)
            movieDAO.batchLoadCustomerRatings(config.keyspaceName, customerRatingData, config.aSync)
            movieDAO.batchLoadCustomerQueue(config.keyspaceName, customerQueueData, config.aSync)
            movieDAO.batchLoadMovieGenre(config.keyspaceName, movieGenreData, config.aSync)
            // reset counters and batch holders
            batchMessagesCount = 0
            customerWatchHistoryData.clear()
            customerRatingData.clear()
            customerQueueData.clear()
            movieGenreData.clear()
          }
        }
        logger.info(s"Records inserted by $threadName is : ${totalRecords * 4} from($eventsStartRange) to($eventsEndRange)")
      }
      counter.getAndAdd(totalRecords)
    } finally {
      movieDAO.close()
    }
  }
}
