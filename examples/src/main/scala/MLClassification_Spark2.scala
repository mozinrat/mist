import io.hydrosphere.mist.lib._
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.feature.{HashingTF, Tokenizer}


object MLClassification extends MLMistJob with SQLSupport {
  override def train(params: Map[String, Any]): Map[String, Any] = {
    val training = session.createDataFrame(Seq(
      (0L, "a b c d e spark", 1.0),
      (1L, "b d", 0.0),
      (2L, "spark f g h", 1.0),
      (3L, "hadoop mapreduce", 0.0)
    )).toDF("id", "text", "label")

    val tokenizer = new Tokenizer()
      .setInputCol("text")
      .setOutputCol("words")
    val hashingTF = new HashingTF()
      .setNumFeatures(1000)
      .setInputCol(tokenizer.getOutputCol)
      .setOutputCol("features")
    val lr = new LogisticRegression()
      .setMaxIter(10)
      .setRegParam(0.01)
    val pipeline = new Pipeline()
      .setStages(Array(tokenizer, hashingTF, lr))

    val model = pipeline.fit(training)

    model.write.overwrite().save("regression")

    Map.empty[String, Any]
  }

  override def serve(params: Map[String, Any]): Map[String, Any] = {

    import io.hydrosphere.mist.ml.transformers.LocalTransformers._
    
    val text = params("text").asInstanceOf[List[String]]
    
    val pipeline = PipelineLoader.load(s"regression")
    val data = LocalData(
      LocalDataColumn("text", text)
    )
    val result: LocalData = pipeline.transform(data)
    Map("result" -> result.select("text", "prediction").toMapList)
  }
}
