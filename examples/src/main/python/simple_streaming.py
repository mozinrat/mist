from mist.mist_job import *

class SimpleStreaming(MistJob, WithStreamingContext, WithMQTTPublisher):

    def do_stuff(self, parameters):
        import time

        def takeAndPublish(time, rdd):
            taken = rdd.take(11)
            self.mqtt.publish("-------------------------------------------")
            self.mqtt.publish("Time: %s" % time)
            self.mqtt.publish("-------------------------------------------")
            self.mqtt.publish(str(taken))

        ssc = self.streaming_context
        type(ssc)
        rddQueue = []
        for i in range(500):
            rddQueue += [ssc.sparkContext.parallelize([j for j in range(1, 1001)], 10)]

        # Create the QueueInputDStream and use it do some processing
        inputStream = ssc.queueStream(rddQueue)
        mappedStream = inputStream.map(lambda x: (x % 10, 1))
        reducedStream = mappedStream.reduceByKey(lambda a, b: a + b)
        #reducedStream.pprint()

        reducedStream.foreachRDD(takeAndPublish)

        ssc.start()
        time.sleep(15)
        ssc.stop(stopSparkContext=False, stopGraceFully=False)

        result = "success"

        return result
