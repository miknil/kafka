/*
 * Copyright 2010 LinkedIn
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.consumer

import scala.collection.mutable._
import scala.collection.JavaConversions._
import joptsimple._
import java.util.Arrays.asList
import java.util.Properties
import java.util.Random
import java.io.PrintStream
import kafka.message._
import kafka.utils.Utils

object ConsoleConsumer {

  def main(args: Array[String]) {
    val parser = new OptionParser
    val topicIdOpt = parser.acceptsAll(asList("t", "topic"), "REQUIRED: The topic id to consume on.")
                           .withRequiredArg
                           .describedAs("topic")
                           .ofType(classOf[String])
    val zkConnectOpt = parser.acceptsAll(asList("z", "zk-urls"), "REQUIRED: The connection string for the zookeeper connection.")
                           .withRequiredArg
                           .describedAs("urls")
                           .ofType(classOf[String])
    val groupIdOpt = parser.acceptsAll(asList("g", "group"), "The group id to consume on.")
                           .withRequiredArg
                           .describedAs("gid")
                           .defaultsTo("console-consumer-" + new Random().nextInt(100000))   
                           .ofType(classOf[String])
    val fetchSizeOpt = parser.acceptsAll(asList("s", "fetch-size"), "The amount of data to fetch in a single request.")
                           .withRequiredArg
                           .describedAs("size")
                           .ofType(classOf[Integer])
                           .defaultsTo(1024 * 1024)   
    val socketBufferSizeOpt = parser.acceptsAll(asList("b", "socket-buffer-size"), "The size of the tcp socket size.")
                           .withRequiredArg
                           .describedAs("size")
                           .ofType(classOf[Integer])
                           .defaultsTo(2 * 1024 * 1024)
    val messageFormatterOpt = parser.acceptsAll(asList("f", "formatter"))
                           .withRequiredArg
                           .describedAs("class")
                           .ofType(classOf[String])
                           .defaultsTo(classOf[NewlineMessageFormatter].getName)
    val messageFormatterArgOpt = parser.acceptsAll(asList("p", "property"))
                           .withRequiredArg
                           .describedAs("prop")
                           .ofType(classOf[String])
    
    val options = parser.parse(args : _*)
    
    checkRequiredArgs(parser, options, topicIdOpt, zkConnectOpt)
    
    val props = new Properties()
    props.put("groupid", options.valueOf(groupIdOpt))
    props.put("socket.buffer.size", options.valueOf(socketBufferSizeOpt).toString)
    props.put("fetch.size", options.valueOf(fetchSizeOpt).toString)
    props.put("auto.commit", "true")
    props.put("autooffset.reset", "largest")
    props.put("zk.connect", options.valueOf(zkConnectOpt))
    val config = new ConsumerConfig(props)
    
    val topic = options.valueOf(topicIdOpt)
    val messageFormatterClass = Class.forName(options.valueOf(messageFormatterOpt))
    val formatterArgs = parseFormatterArgsOrDie(options.valuesOf(messageFormatterArgOpt))
    
    val connector = Consumer.create(config)
    val stream: KafkaMessageStream = connector.createMessageStreams(Map(topic -> 1)).get(topic).get.get(0)
    
    val formatter: MessageFormatter = messageFormatterClass.newInstance().asInstanceOf[MessageFormatter]
    formatter.init(formatterArgs)
    
    for(message <- stream)
      formatter.writeTo(message, System.out)
      
    System.out.flush()
    formatter.close()
  }
  
  def checkRequiredArgs(parser: OptionParser, options: OptionSet, required: OptionSpec[_]*) {
    for(arg <- required) {
    	if(!options.has(arg)) {
        System.err.println("Missing required argument \"" + arg + "\"") 
    		parser.printHelpOn(System.err)
        System.exit(1)
      }
    }
  }
  
  def parseFormatterArgsOrDie(args: Buffer[String]): Properties = {
    val splits = args.map(_ split "=").filterNot(_ == null).filterNot(_.length == 0)
    if(!splits.forall(_.length == 2)) {
      System.err.println("Invalid parser arguments: " + args.mkString(" "))
      System.exit(1)
    }
    val props = new Properties
    for(a <- splits)
      props.put(a(0), a(1))
    props
  }
  
  trait MessageFormatter {
    def writeTo(message: Message, output: PrintStream)
    def init(props: Properties) {}
    def close() {}
  }
  
  class NewlineMessageFormatter extends MessageFormatter {
    def writeTo(message: Message, output: PrintStream) {
      val payload = message.payload
      output.write(payload.array, payload.arrayOffset, payload.limit)
      output.write('\n')
    }
  }
   
}
