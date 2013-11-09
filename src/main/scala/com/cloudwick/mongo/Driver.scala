package com.cloudwick.mongo

import com.cloudwick.generator.log.{IPGenerator,LogGenerator}
import com.cloudwick.mongo.dao.LogDAO
import com.mongodb.casbah.Imports._
import scala.collection.mutable.ListBuffer
import com.mongodb.casbah.commons.MongoDBObject
import scala.util.Random
import org.slf4j.LoggerFactory

/**
 * Driver for the mongo benchmark
 * @author ashrith 
 */
object Driver extends App {
  private val logger = LoggerFactory.getLogger(getClass)

  /*
   * Command line option parser
   */
  val optionsParser = new scopt.OptionParser[OptionsConfig]("mongo_benchmark") {
    head("mongo", "0.1")
    opt[String]('m', "mode") required() valueName "<insert|read|agg_query>" action { (x, c) =>
      c.copy(mode = x)
    } validate { x: String =>
      if (x == "insert" || x == "read" || x == "agg_query")
        success
      else
        failure("value of '--mode' must be either 'insert', 'read' or 'agg_query'")
    } text "operation mode ('insert' will insert log events, 'read' will perform random reads" +
           " & 'agg_query' performs pre-defined aggregate queries)"
    opt[String]('u', "mongoURL") action { (x, c) =>
      c.copy(mongoURL = x)
    } text "mongo connection url to connect to, defaults to: 'mongodb://localhost:27017' (for more information on " +
           "mongo connection url format, please refer: http://goo.gl/UglKHb)"
    opt[Int]('e', "eventsPerSec") action { (x, c) =>
      c.copy(eventsPerSec = x)
    } text "number of log events to generate per sec"
    arg[Int]("<totalEvents>...") unbounded() optional() action { (x, c) =>
      c.copy(totalEvents = c.totalEvents :+ x)
    } text "total number of events to insert|read"
    opt[Int]('s', "ipSessionCount") action { (x, c) =>
      c.copy(ipSessionCount = x)
    } text "number of times a ip can appear in a session, defaults to: '25'"
    opt[Int]('l', "ipSessionLength") action { (x, c) =>
      c.copy(ipSessionLength = x)
    } text "size of the session, defaults to: '50'"
    opt[Int]('b', "batchSize") action { (x, c) =>
      c.copy(batchSize = x)
    } text "size of the batch to flush to mongo instead of single inserts, defaults to: '1000'"
    opt[String]('d', "dbName") action { (x,c) =>
      c.copy(mongoDbName = x)
    } text "name of the database to create|connect in mongo, defaults to: 'logs'"
    opt[String]('c', "collectionName") action { (x,c) =>
      c.copy(mongoCollectionName = x)
    } text "name of the collection to create|connect in mongo, defaults to: 'logEvents'"
    opt[Unit]('i', "indexData") action { (_, c) =>
      c.copy(indexData = true)
    } text "index data on 'response_code' and 'request_page' after inserting, defaults to: 'false'"
    help("help") text "prints this usage text"
  }

  optionsParser.parse(args, OptionsConfig()) map { config =>
    logger.info(s"Successfully parsed command line args : $config")

    /*
     * Initialize mongo connection and initializes db and collection
     */
    val mongo = new LogDAO(config.mongoURL)
    val mongoClient = mongo.initialize
    val collection = mongo.initCollection(mongoClient, config.mongoDbName, config.mongoCollectionName)

    /*
     * Initialize generator
     */
    val ipGenerator = new IPGenerator(config.ipSessionCount, config.ipSessionLength)
    val logEventGen = new LogGenerator(ipGenerator)
    // val sleepTime = if(config.eventsPerSec == 0) 0 else 1000/config.eventsPerSec

    /*
     * Handles shutdown gracefully - close connection to mongo when exiting
     */
    sys.addShutdownHook({
      println()
      logger.info("ShutdownHook called - Closing connection with Mongo")
      mongo.close(mongoClient)
    })

    if(config.mode == "insert") {
      /*
       * Benchmark inserts
       */
      try {
        val batch = new ListBuffer[MongoDBObject]
        var messagesCount = 0
        var totalMessagesCount = 0

        val benchmarkInserts = (events: Int) => {
          time(s"inserting $events") {
            (1 to events).foreach { _ =>
              messagesCount += 1
              totalMessagesCount += 1
              printf("\rMessages Count: " + totalMessagesCount)
              batch += mongo.makeMongoObject(logEventGen.eventGenerate, totalMessagesCount)
              if (messagesCount == config.batchSize || messagesCount == events) {
                mongo.batchAdd(collection, batch)
                logger.debug("Flushing batch size of " + config.batchSize)
                messagesCount = 0 // reset counter
                batch.clear()     // reset list
              }
            }
            println()
            // index inserted data
            if (config.indexData) {
              mongo.createIndexes(collection, List("request_page", "response_code"))
            }
          }
          totalMessagesCount = 0 // reset main counter
        }

        if (config.totalEvents.size == 0) {
          logger.info("Defaulting inserts to 1000")
          logger.info("Dropping existing data in the collection")
          mongo.dropCollection(collection)
          benchmarkInserts(1000)
        } else {
          config.totalEvents.foreach{ events =>
            logger.info("Dropping existing data in the collection")
            mongo.dropCollection(collection)
            benchmarkInserts(events)
          }
        }
      } catch {
        case e: Exception => logger.error("Oops! something went wrong " + e.printStackTrace()); System.exit(1)
      }
    } else if (config.mode == "read") {
      /*
       * Performs random reads
       */
      val benchmarkReads = (numberOfReads: Int) => {
        time(s"reading $numberOfReads") {
          // Get the count of events from mongo
          val totalDocuments = mongo.documentsCount(collection)
          logger.info("Total number of documents in the collection :" + totalDocuments)
          if (totalDocuments == 0) {
            logger.info("No documents found to read, please insert documents first using '--mode insert'")
            System.exit(0)
          }
          (1 to numberOfReads).foreach { readQueryCount =>
            mongo.findDocument(collection, MongoDBObject("record_id" -> Random.nextInt(totalDocuments)))
            printf("\rRead queries executed: " + readQueryCount)
          }
          println()
        }
      }

      if(config.totalEvents.size == 0) {
        logger.info("Defaulting reads to 100")
        benchmarkReads(100)
      } else {
        config.totalEvents.foreach { totalReads =>
          benchmarkReads(totalReads)
        }
      }
    } else {
      /*
       * Execute aggregation queries
       */
      val pipeline1 = mongo.buildQueryOne
      logger.info("Query 1 : Gets the number of times a status code has appeared")
      time("aggregate query 1") {
        mongo.aggregationResult(mongoClient, config.mongoDbName, config.mongoCollectionName, pipeline1)
      }

      val pipeline2 = mongo.buildQueryTwo
      logger.info("Query 2: Co-relates request page to response code")
      time("aggregate query 2") {
        mongo.aggregationResult(mongoClient, config.mongoDbName, config.mongoCollectionName, pipeline2)
      }

      val pipeline3 = mongo.buildQueryThree
      logger.info("Query 3: Counts total number of bytes served for each page by web server")
      time("aggregate query 3") {
        mongo.aggregationResult(mongoClient, config.mongoDbName, config.mongoCollectionName, pipeline3)
      }

      val pipeline4 = mongo.buildQueryFour
      logger.info("Query 4: Counts how many times a client visited the site")
      time("aggregate query 4") {
        mongo.aggregationResult(mongoClient, config.mongoDbName, config.mongoCollectionName, pipeline4)
      }

      val pipeline5 = mongo.buildQueryFive
      logger.info("Query 5: Top 10 site visitors")
      time("aggregate query 5") {
        mongo.aggregationResult(mongoClient, config.mongoDbName, config.mongoCollectionName, pipeline5)
      }

      val pipeline6 = mongo.buildQuerySix
      logger.info("Query 5: Top Browsers")
      time("aggregate query 6") {
        mongo.aggregationResult(mongoClient, config.mongoDbName, config.mongoCollectionName, pipeline6)
      }
    }
  } getOrElse {
    logger.error("Failed to parse command line arguments")
  }

  /**
   * Measures time took to run a block
   * @param block code block to run
   * @param message additional message to print
   * @tparam R type
   * @return returns block output
   */
  def time[R](message: String = "code block")(block: => R): R = {
    val s = System.nanoTime
    // block: => R , implies call by name i.e, the execution of block is delayed until its called by name
    val ret = block
    logger.info("Time elapsed in " + message + " : " +(System.nanoTime - s)/1e6+"ms")
    ret
  }
}