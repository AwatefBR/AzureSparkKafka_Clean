package producer

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.util.Properties
import scala.collection.JavaConverters._

object Utils {
  def getLastOffset(topic: String, bootstrap: String): Long = {
    try {
      val props = new Properties()
      props.put("bootstrap.servers", bootstrap)
      props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      props.put("group.id", s"producer-checkpoint-$topic")
      
      val consumer = new KafkaConsumer[String, String](props)
      val partition = new TopicPartition(topic, 0)
      consumer.assign(java.util.Collections.singletonList(partition))
      consumer.seekToEnd(java.util.Collections.singletonList(partition))
      val lastOffset = consumer.position(partition)
      consumer.close()
      
      lastOffset
    } catch {
      case e: Exception =>
        println(s"[Checkpoint] ⚠️  Erreur lors de la lecture de l'offset Kafka: ${e.getMessage}")
        println(s"[Checkpoint] Repartant depuis 0...")
        0L
    }
  }
}