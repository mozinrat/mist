package io.hydrosphere.mist.ml.transformers

import io.hydrosphere.mist.lib.{LocalData, LocalDataColumn}
import io.hydrosphere.mist.utils.Logger
import org.apache.spark.ml.classification.{LogisticRegressionModel, MultilayerPerceptronClassificationModel}
import org.apache.spark.ml.feature.{HashingTF, Tokenizer}
import org.apache.spark.ml.linalg.{SparseVector, Vector}
import org.apache.spark.ml.{PipelineModel, Transformer}
import org.apache.spark.mllib.feature.{HashingTF => HTF}
import org.apache.spark.mllib.linalg.{SparseVector => SVector}

import scala.language.implicitConversions
import scala.collection.mutable

object LocalTransformers extends Logger {

  implicit class LocalPipeline(val pipeline: PipelineModel) {

    def transform(localData: LocalData): LocalData = {
      pipeline.stages.foldLeft(localData)((x: LocalData, y: Transformer) => y match {
        case tokenizer: Tokenizer => tokenizer.transform(x)
        case hashingTF: HashingTF => hashingTF.transform(x)
        case logisticRegression: LogisticRegressionModel => logisticRegression.transform(x)
        case perceptron: MultilayerPerceptronClassificationModel => perceptron.transform(x)
        case _ => throw new Exception(s"Unknown pipeline stage: ${y.getClass}")
      })
    }

  }
  
  implicit class LocalTokenizer(val tokenizer: Tokenizer) {
    def transform(localData: LocalData): LocalData = {
      logger.debug(s"Local Tokenizer")
      logger.debug(localData.toString)
      localData.column(tokenizer.getInputCol) match {
        case Some(column) =>
          val method = classOf[Tokenizer].getMethod("createTransformFunc")
          val newData = column.data.map(s => {
            method.invoke(tokenizer).asInstanceOf[String => Seq[String]](s.asInstanceOf[String])
          })
          localData.withColumn(LocalDataColumn(tokenizer.getOutputCol, newData))
        case None => localData
      }
    }
  }

  implicit class LocalHashingTF(val hashingTF: HashingTF) {
    def transform(localData: LocalData): LocalData = {
      logger.debug(s"Local HashingTF")
      logger.debug(localData.toString)
      localData.column(hashingTF.getInputCol) match {
        case Some(column) =>
          val htf = new HTF(hashingTF.getNumFeatures).setBinary(hashingTF.getBinary)
          val newData = column.data.map((m) => htf.transform(m.asInstanceOf[mutable.WrappedArray[String]]))
          localData.withColumn(LocalDataColumn(hashingTF.getOutputCol, newData))
        case None => localData
      }
    }
  }

  implicit class LocalMultilayerPerceptronClassificationModel(val perceptron: MultilayerPerceptronClassificationModel) {

    def transform(localData: LocalData): LocalData = {
      logger.debug("Local MultilayerPerceptronClassificationModel")
      logger.debug(localData.toString)
      localData.column(perceptron.getFeaturesCol) match {
        case Some(column) =>
          val method = classOf[MultilayerPerceptronClassificationModel].getMethod("predict", classOf[Vector])
          method.setAccessible(true)
          val newColumn = LocalDataColumn(perceptron.getPredictionCol, column.data map { feature =>
            method.invoke(perceptron, feature.asInstanceOf[Vector]).asInstanceOf[Double]
          })
          localData.withColumn(newColumn)
        case None => localData
      }
    }

  }


  implicit class LocalLogisticRegression(val logisticRegression: LogisticRegressionModel) {

    implicit def mllibVectorToMlVector(v: SVector): SparseVector = new SparseVector(v.size, v.indices, v.values)

    def transform(localData: LocalData): LocalData = {
      logger.debug("Local LogisticRegression")
      logger.debug(localData.toString)
      localData.column(logisticRegression.getFeaturesCol) match {
        case Some(column) =>
          var newData = localData
          val predict = classOf[LogisticRegressionModel].getMethod("predict", classOf[Vector])
          if (logisticRegression.getPredictionCol.nonEmpty) {
            val newColumn = LocalDataColumn(logisticRegression.getPredictionCol, column.data.map(m => {
              val vector: SparseVector = m.asInstanceOf[SVector]
              predict.invoke(logisticRegression, vector)
            }))
            newData = newData.withColumn(newColumn)
          }
          val predictRaw = classOf[LogisticRegressionModel].getMethod("predictRaw", classOf[Vector])
          if (logisticRegression.getRawPredictionCol.nonEmpty) {
            val newColumn = LocalDataColumn(logisticRegression.getRawPredictionCol, column.data.map(m => {
              val vector: SparseVector = m.asInstanceOf[SVector]
              predictRaw.invoke(logisticRegression, vector)
            }))
            newData = newData.withColumn(newColumn)
          }
          val predictProbability = classOf[LogisticRegressionModel].getMethod("predictProbability", classOf[AnyRef])
          if (logisticRegression.getProbabilityCol.nonEmpty) {
            val newColumn = LocalDataColumn(logisticRegression.getProbabilityCol, column.data.map(m => {
              val vector: SparseVector = m.asInstanceOf[SVector]
              predictProbability.invoke(logisticRegression, vector)
            }))
            newData = newData.withColumn(newColumn)
          }
          newData
        case None => localData
      }
    }
  }

}
