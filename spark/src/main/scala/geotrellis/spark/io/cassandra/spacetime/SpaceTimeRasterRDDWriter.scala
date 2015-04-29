package geotrellis.spark.io.cassandra.spacetime

import java.nio.ByteBuffer

import geotrellis.spark._
import geotrellis.spark.io.cassandra._
import geotrellis.spark.io.index._
import geotrellis.spark.utils._
import geotrellis.raster._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import com.datastax.spark.connector.rdd.CassandraRDD
import com.datastax.spark.connector._

import com.datastax.driver.core.DataType.{text, cint, blob}
import com.datastax.driver.core.schemabuilder.SchemaBuilder

object SpaceTimeRasterRDDWriter extends RasterRDDWriter[SpaceTimeKey] {

  def createTableIfNotExists(tileTable: String)(implicit session: CassandraSession): Unit = {
    val schema = SchemaBuilder.createTable(session.keySpace, tileTable).ifNotExists()
      .addPartitionKey("reverse_index", text)
      .addClusteringColumn("zoom", cint)
      .addClusteringColumn("indexer", text)
      .addClusteringColumn("date", text)
      .addClusteringColumn("name", text)
      .addColumn("value", blob)

    session.execute(schema)
  }

  def saveRasterRDD(
    layerId: LayerId,
    raster: RasterRDD[SpaceTimeKey], 
    kIndex: KeyIndex[SpaceTimeKey],
    tileTable: String)(implicit session: CassandraSession): Unit = {

    val closureKeyIndex = kIndex
    raster
      .map(KryoClosure { case (key, tile) =>
         val value = KryoSerializer.serialize[(SpaceTimeKey, Array[Byte])]( (key, tile.toBytes) )
 
         val indexer = closureKeyIndex.toIndex(key).toString
         (indexer.reverse, layerId.zoom, indexer, timeText(key), layerId.name, value) 
      })
    .saveToCassandra(session.keySpace, tileTable,
                     SomeColumns("reverse_index", "zoom", "indexer", "date", "name", "value")) 
  }
}
