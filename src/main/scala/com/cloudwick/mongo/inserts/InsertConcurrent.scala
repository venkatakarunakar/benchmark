package com.cloudwick.mongo.inserts

import com.cloudwick.mongo.OptionsConfig
import com.cloudwick.mongo.dao.LogDAO
import org.slf4j.LoggerFactory
import java.util.concurrent.{Executors, ExecutorService}
import java.util.concurrent.atomic.AtomicLong
import com.cloudwick.generator.utils.Utils

/**
 * Inserts data into mongo using concurrency or in regular mode
 * @param events total number of documents to insert into mongo
 * @param config scopt options
 * @param mongo mongo log data access object
 * @author ashrith
 */
class InsertConcurrent(events: Long, config: OptionsConfig, mongo: LogDAO) extends Runnable {
  lazy val logger = LoggerFactory.getLogger(getClass)
  val utils = new Utils
  val threadPool: ExecutorService = Executors.newFixedThreadPool(config.threadPoolSize)
  val finalCounter:AtomicLong = new AtomicLong(0L)
  val messagesPerThread: Int = (events / config.threadCount).toInt
  val messagesRange = Range(0, events.toInt, messagesPerThread)

  def run() = {
    utils.time(s"inserting $events") {
      try {
        (1 to config.threadCount).foreach { threadCount =>
          logger.info("Initializing thread" + threadCount)
          threadPool.execute(
            new Insert(
              messagesRange(threadCount-1), // start range for thread
              messagesRange(threadCount-1) + (messagesPerThread - 1), // end range for thread
              finalCounter,
              config,
              mongo))
        }
      } finally {
        threadPool.shutdown()
      }
      while(!threadPool.isTerminated) {}
      logger.info(s"Total documents processed by ${config.threadCount} threads: " + finalCounter)
    }
  }
}