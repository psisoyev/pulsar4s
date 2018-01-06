package com.sksamuel.pulsar4s

import java.util.function.BiConsumer

import org.apache.pulsar.client.api
import org.apache.pulsar.client.api.Message

import scala.concurrent.ExecutionContext

case class Topic(name: String)
case class Subscription(name: String)

trait PulsarClient {
  def close(): Unit
  def producer(topic: Topic): Producer
  def send(topic: Topic): Unit
  def subscribe(topic: Topic, name: String): Consumer
  def consumer(topic: Topic, subscription: Subscription): Consumer2
}

trait Consumer {
  def consume(listener: Listener)
}

trait Listener {
  def onError(t: Throwable) = throw t
  def onSuccess(m: Message): Unit = println("Message received: " + new String(m.getData))
}

object PulsarClient {

  def apply(url: String, namespace: String)
           (implicit ec: ExecutionContext): PulsarClient = new PulsarClient {

    val client: api.PulsarClient = org.apache.pulsar.client.api.PulsarClient.create(url)

    override def send(topic: Topic): Unit = {
      val producer = client.createProducer(topic.name)
      producer.send("hello-world".getBytes("UTF8"))
      println("Message sent")
      producer.close()
    }

    override def subscribe(topic: Topic, name: String): Consumer = new Consumer {
      override def consume(listener: Listener): Unit = {
        val consumer = client.subscribe(topic.name, name)
        consumer.receiveAsync().whenComplete(new BiConsumer[Message, Throwable] {
          override def accept(m: Message, t: Throwable): Unit = {
            if (m != null)
              listener.onSuccess(m)
            else if (t != null)
              listener.onError(t)
          }
        })
      }
    }

    override def close(): Unit = client.close()

    override def producer(topic: Topic) = new DefaultProducer(client.createProducer(topic.name), topic)
    override def consumer(topic: Topic, subscription: Subscription) = new DefaultConsumer(client.subscribe(topic.name, subscription.name), topic, subscription)
  }
}

object Test extends App {

  import ExecutionContext.Implicits.global

  val client = PulsarClient("pulsar://localhost:6650", "sample/standalone/ns1")
  val topic = Topic("persistent://sample/standalone/ns1/my-topic")
  client.send(topic)
  client.subscribe(topic, "mysub").consume(new Listener {})
}