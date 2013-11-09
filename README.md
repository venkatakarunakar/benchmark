Benchmark NoSQL
===============

This project is intended to test write, read and query performances of several NoSQL data-stores like MongoDB, Cassandra,
HBase, Redis, Solr and various others.

**Supported**: MongoDB, Solr

**WIP**: Cassandra

Build Project
-------------
Requirement for building the project:

 * [sbt](http://www.scala-sbt.org/) (version 0.13.0)
 * [sbt-assembly](https://github.com/sbt/sbt-assembly)

Once, requirements are installed. Follow these steps to build a jar with dependencies (run the following command from
projects root)

```
sbt assembly
```

Running
-------
To run the benchmark programs, use the wrapper script

```
chmod +x bin/run
bin/run
```

with out any arguments, `run` script will show the supported sub-commands that a user can run:

```
Usage: run COMMAND
Possible COMMAND(s)
  mongo       mongo benchmark driver
  solr        solr benchmark driver
  cassandra   cassandra benchmark driver
```

###To benchmark Mongo
Using the mongo benchmark driver

```
$bin/run mongo --help

mongo 0.1
Usage: mongo_benchmark [options] [<totalEvents>...]

  -m <insert|read|agg_query> | --mode <insert|read|agg_query>
        operation mode ('insert' will insert log events, 'read' will perform random reads & 'agg_query' performs pre-defined aggregate queries)
  -u <value> | --mongoURL <value>
        mongo connection url to connect to, defaults to: 'mongodb://localhost:27017' (for more information on mongo connection url format, please refer: http://goo.gl/UglKHb)
  -e <value> | --eventsPerSec <value>
        number of log events to generate per sec
  <totalEvents>...
        total number of events to insert|read
  -s <value> | --ipSessionCount <value>
        number of times a ip can appear in a session
  -l <value> | --ipSessionLength <value>
        size of the session
  -b <value> | --batchSize <value>
        size of the batch to flush to mongo instead of single inserts, defaults to: '1000'
  -d <value> | --dbName <value>
        name of the database to create|connect in mongo, defaults to: 'logs'
  -c <value> | --collectionName <value>
        name of the collection to create|connect in mongo, defaults to: 'logEvents'
  -i | --indexData
        index data on 'response_code' and 'request_page' after inserting, defaults to: 'false'
  --help
        prints this usage text
```


Mongo Driver Example(s):

1. To benchmark the inserts of 100000, 1000000 and 100000000 documents consecutively:

    ```
    bin/run mongo --mode insert 100000 1000000 100000000
    ```

2. To benchmark the inserts of 100000, 1000000 and 100000000 documents consecutively and also index the data once inserted:

    ```
    bin/run mongo --mode insert 100000 1000000 100000000 --indexData
    ```

3. Inserting data with indexing and custom batch size:

    ```
    bin/run mongo --mode insert 100000 1000000 100000000 --indexData --batchSize 5000
    ```

4. Benchmark random reads of 10000, 100000 and 1000000 documents:

    ```
    bin/run mongo --mode read 10000 100000 1000000
    ```

5. Perform aggregation queries on the inserted data

    ```
    bin/run mongo --mode agg_query
    ```

###To benchmark Solr
Using the solr benchmark driver

```
$bin/run solr --help

solr 0.1
Usage: index_logs [options] [<totalEvents>...]

Indexes log events to solr
  -m <index|read|search> | --mode <index|read|search>
        operation mode ('index' will index logs, 'search' will perform search &'read' will perform random reads against solr core)
  -u <value> | --solrServerUrl <value>
        url for connecting to solr server instance
  <totalEvents>...
        total number of events to insert
  -s <value> | --ipSessionCount <value>
        number of times a ip can appear in a session
  -l <value> | --ipSessionLength <value>
        size of the session
  -b <value> | --batchSize <value>
        flushes events from memory to solr index, defaults to: '1000'
  -c | --cleanPreviousIndex
        deletes the existing index on solr core
  -q <value> | --query <value>
        solr query to execute, defaults to: '*:*'
  --queryCount <value>
        number of documents to return on executed query, default:10
  --help
        prints this usage text
```

Solr Driver Example(s):

1. To benchmark the inserts of 100000, 1000000 and 100000000 log events consecutively:

    ```
    bin/run solr --mode insert 100000 1000000 100000000
    ```

2. To benchmark the inserts of 100000, 1000000 and 100000000 log events consecutively while clearing existing index data:

    ```
    bin/run solr --mode insert 100000 1000000 100000000 --cleanPreviousIndex
    ```

3. To benchmark the inserts of 100000, 1000000 and 100000000 log events consecutively while clearing existing index data and
also providing batch size using which the driver will flush the data:

    ```
    bin/run solr --mode insert 100000 1000000 100000000 --cleanPreviousIndex --batchSize 10000
    ```

4. To benchmark random reads

    ```
    bin/run solr --mode read 10000 100000 1000000
    ```

5. To execute custom queries and return specified number of documents

    ```
    bin/run solr --mode search --query '*:*' --queryCount 20
    ```