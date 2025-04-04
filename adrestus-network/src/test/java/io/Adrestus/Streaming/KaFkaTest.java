package io.Adrestus.Streaming;

import io.Adrestus.config.KafkaConfiguration;
import io.Adrestus.network.*;
import lombok.SneakyThrows;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KaFkaTest {

    private static KafkaSmith kafkaSmith;
    private static ArrayList<String> ipList = new ArrayList<>();

    //to make it work import each class separately
    @BeforeAll
    public static void setUp() {
        KafkaConfiguration.KAFKA_HOST = "127.0.0.1";
        ipList.add(KafkaConfiguration.KAFKA_HOST);
        ipList.add(KafkaConfiguration.KAFKA_HOST);
        ipList.add(KafkaConfiguration.KAFKA_HOST);
        ipList.add(KafkaConfiguration.KAFKA_HOST);
        ipList.add(KafkaConfiguration.KAFKA_HOST);
        HashMap<String, String> map = new HashMap<>();
        TopicFactory.getInstance().constructTopicName(TopicType.PREPARE_PHASE, map, 1);
        TopicFactory.getInstance().constructTopicName(TopicType.COMMITTEE_PHASE, map, 1);
        TopicFactory.getInstance().constructTopicName(TopicType.DISPERSE_PHASE1, map, 5);
        TopicFactory.getInstance().constructTopicName(TopicType.DISPERSE_PHASE2, map, 1);
        TopicFactory.getInstance().constructTopicName(TopicType.ANNOUNCE_PHASE, map, 1);
        Collection<NewTopic> newTopics = Arrays.asList(TopicFactory.getInstance().getTopicName(TopicType.PREPARE_PHASE), TopicFactory.getInstance().getTopicName(TopicType.ANNOUNCE_PHASE), TopicFactory.getInstance().getTopicName(TopicType.COMMITTEE_PHASE), TopicFactory.getInstance().getTopicName(TopicType.DISPERSE_PHASE1), TopicFactory.getInstance().getTopicName(TopicType.DISPERSE_PHASE2));
        kafkaSmith = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 0);
        kafkaSmith.manufactureKafkaComponent(KafkaKingdomType.ZOOKEEPER);
        kafkaSmith.manufactureKafkaComponent(KafkaKingdomType.BROKER);
        kafkaSmith.manufactureKafkaComponent(KafkaKingdomType.TOPIC_CREATOR);
        kafkaSmith.manufactureKafkaComponent(KafkaKingdomType.PRODUCER);
        kafkaSmith.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
//        kafkaSmith.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
//        KafkaZookeeper zookeeper = kafkaSmith.getKafkaComponent(KafkaKingdomType.ZOOKEEPER);
//        KafkaBroker broker = kafkaSmith.getKafkaComponent(KafkaKingdomType.BROKER);
//        KafkaCreatorTopic topic_creator=kafkaSmith.getKafkaComponent(KafkaKingdomType.TOPIC_CREATOR);
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);
        KafkaProducer producer1 = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.COMMITTEE_PHASE).name(), "key-" + 01, new String("value-" + 0).getBytes(StandardCharsets.UTF_8));
        ProducerRecord<String, byte[]> record1 = new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.COMMITTEE_PHASE).name(), "key-" + 02, new String("value-" + 0).getBytes(StandardCharsets.UTF_8));
        producer.getProducer().send(record);
        producer.getProducer().send(record1);
        KafkaConsumerPrivateGroup consumerPrivate = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
        KafkaConsumerPrivateGroup consumerPrivate1 = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
        //KafkaConsumerSameGroup consumerSame = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        try {
            ConsumerRecords<String, byte[]> res1 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST).poll(Duration.ofMillis(15000));
            ConsumerRecords<String, byte[]> res2 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "1").poll(Duration.ofMillis(15000));
            ConsumerRecords<String, byte[]> res3 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "2").poll(Duration.ofMillis(15000));
            ConsumerRecords<String, byte[]> res4 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "3").poll(Duration.ofMillis(15000));
            ConsumerRecords<String, byte[]> res5 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "4").poll(Duration.ofMillis(15000));
            assertEquals(2, res1.count());
            assertEquals(2, res2.count());
            assertEquals(2, res3.count());
            assertEquals(2, res4.count());
            assertEquals(2, res5.count());
        } catch (Exception e) {
            e.printStackTrace();
            int f = 4;
        }
        //ConsumerRecords<String, String> res6 = consumerSame.getConsumer().poll(Duration.ofMillis(1000));
        int g = 3;
    }

    @Test
    @Order(4)
    public void testNoParallel() throws ExecutionException, InterruptedException {
        Map<String, byte[]> map = new HashMap<>();
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);
        KafkaConsumerPrivateGroup consumerPrivate = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
        Consumer<String, byte[]> consumer = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST);

        int size = 100000;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.PREPARE_PHASE).name(), "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
        }

        long start = System.currentTimeMillis();
        int count = 0;
        while (count < size) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(10000));
            count += records.count();
            for (ConsumerRecord<String, byte[]> recordf : records) {
                map.put(recordf.key(), recordf.value());
            }

        }
        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;
        System.out.println("Simple Consumers Time elapsed: " + timeElapsed);
        assertEquals(size, map.size());
    }


    // if latch wait do 180 ms then the sum of poll times must be 180x mapsize
    @Test
    @Order(2)
    public void testMultiConsumersSingeDataWithFor() throws ExecutionException, InterruptedException {
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);
        KafkaConsumerPrivateGroup consumerPrivate = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
        int size = 1;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicType.DISPERSE_PHASE2.name(), "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
            producer.getProducer().flush();
        }

        ConcurrentHashMap<String, Consumer<String, byte[]>> map = consumerPrivate.getConsumer_map();
        CountDownLatch latch = new CountDownLatch(map.size());
        for (Map.Entry<String, Consumer<String, byte[]>> entry : map.entrySet()) {
            Thread.ofVirtual().start(() -> {
                int count = 0;
                while (count < size) {
                    try {
                        ConsumerRecords<String, byte[]> records = entry.getValue().poll(Duration.ofMillis(10));
                        count += records.count();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                assertEquals(size, count);
                latch.countDown();
                System.out.println(latch.getCount());
            });
        }
        latch.await();
    }

    @Test
    @Order(3)
    public void testMultiDataConsumers() throws ExecutionException, InterruptedException {
        Map<String, byte[]> map1 = new HashMap<>();
        Map<String, byte[]> map2 = new HashMap<>();
        Map<String, byte[]> map3 = new HashMap<>();
        Map<String, byte[]> map4 = new HashMap<>();
        Map<String, byte[]> map5 = new HashMap<>();
        KafkaConsumerPrivateGroup consumerPrivate = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
        Consumer<String, byte[]> consumer1 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST);
        Consumer<String, byte[]> consumer2 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "1");
        Consumer<String, byte[]> consumer3 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "2");
        Consumer<String, byte[]> consumer4 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "3");
        Consumer<String, byte[]> consumer5 = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST + "4");
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);
        int size = 100000;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.DISPERSE_PHASE2).name(), "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
        }

        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(consumerPrivate.getConsumer_map().size());
        Thread.ofVirtual().start(() -> {
            int count = 0;
            while (count < size) {
                ConsumerRecords<String, byte[]> records = consumer1.poll(Duration.ofMillis(100));
                count += records.count();
                for (ConsumerRecord<String, byte[]> recordf : records) {
                    map1.put(recordf.key(), recordf.value());
                }
            }
            System.out.println("1");
            latch.countDown();
        });
        Thread.ofVirtual().start(() -> {
            int count = 0;
            while (count < size) {
                ConsumerRecords<String, byte[]> records = consumer2.poll(Duration.ofMillis(100));
                count += records.count();
                for (ConsumerRecord<String, byte[]> recordf : records) {
                    map2.put(recordf.key(), recordf.value());
                }

            }
            System.out.println("2");
            latch.countDown();
        });
        Thread.ofVirtual().start(() -> {
            int count = 0;
            while (count < size) {
                ConsumerRecords<String, byte[]> records = consumer3.poll(Duration.ofMillis(10));
                count += records.count();
                for (ConsumerRecord<String, byte[]> recordf : records) {
                    map3.put(recordf.key(), recordf.value());
                }

            }
            System.out.println("3");
            latch.countDown();
        });
        Thread.ofVirtual().start(() -> {
            int count = 0;
            while (count < size) {
                ConsumerRecords<String, byte[]> records = consumer4.poll(Duration.ofMillis(10));
                count += records.count();
                for (ConsumerRecord<String, byte[]> recordf : records) {
                    map4.put(recordf.key(), recordf.value());
                }

            }
            System.out.println("4");
            latch.countDown();
        });
        Thread.ofVirtual().start(() -> {
            int count = 0;
            while (count < size) {
                ConsumerRecords<String, byte[]> records = consumer5.poll(Duration.ofMillis(10));
                count += records.count();
                for (ConsumerRecord<String, byte[]> recordf : records) {
                    map5.put(recordf.key(), recordf.value());
                }

            }
            System.out.println("5");
            latch.countDown();
        });

        latch.await();
        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;
        System.out.println("Multi Consumers Time elapsed: " + timeElapsed);
        assertEquals(size, map2.size());
        assertEquals(size, map1.size());
        assertEquals(size, map3.size());
        assertEquals(size, map4.size());
        assertEquals(size, map5.size());
    }

    @SneakyThrows
    @Test
    @Order(1)
    public void testDisperseSamePartition() throws ExecutionException, InterruptedException {
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);

        CopyOnWriteArrayList<Consumer<String, byte[]>> iterate = new CopyOnWriteArrayList<>();
        KafkaManufactureSmith kafkaManufactureSmith0 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 0);
        KafkaManufactureSmith kafkaManufactureSmith1 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 1);
        KafkaManufactureSmith kafkaManufactureSmith2 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 2);
        KafkaManufactureSmith kafkaManufactureSmith3 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 3);
        KafkaManufactureSmith kafkaManufactureSmith4 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 4);
        kafkaManufactureSmith0.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith1.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith2.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith3.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith4.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup0 = kafkaManufactureSmith0.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup1 = kafkaManufactureSmith1.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup2 = kafkaManufactureSmith2.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup3 = kafkaManufactureSmith3.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup4 = kafkaManufactureSmith4.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        iterate.add(consumerSameGroup0.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST));
        iterate.add(consumerSameGroup1.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "1"));
        iterate.add(consumerSameGroup2.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "2"));
        iterate.add(consumerSameGroup3.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "3"));
        iterate.add(consumerSameGroup4.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "4"));

        int size = 5;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.DISPERSE_PHASE1).name(), i, "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
        }

        producer.getProducer().flush();

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < iterate.size(); i++) {
            int finalI = i;
            Runnable task = () -> {
                try {
                    ConsumerRecords<String, byte[]> records = iterate.get(finalI).poll(Duration.ofMillis(10000));
                    System.out.println(finalI + " " + records.count());
                    assertEquals(1, records.count());
                    for (ConsumerRecord<String, byte[]> recordf : records) {
                        list.add(recordf.key());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            executorService.submit(task);
        }


        executorService.shutdown();
        executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        executorService.shutdownNow();
        executorService.close();

        assertEquals(5, list.size());
    }

    @Test
    @Order(5)
    public void testParallel() throws ExecutionException, InterruptedException {
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);
        KafkaConsumerPrivateGroup consumerPrivate = kafkaSmith.getKafkaComponent(KafkaKingdomType.CONSUMER_PRIVATE);
        Consumer<String, byte[]> consumer = consumerPrivate.getConsumer_map().get(KafkaConfiguration.KAFKA_HOST);

        int size = 100000;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.ANNOUNCE_PHASE).name(), "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
        }


        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(size - 1);
        long start = System.currentTimeMillis();
        while (latch.getCount() > 0) {
            executorService.submit(() -> {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(10000));
                records.forEach(record -> {
                    latch.countDown();
                });
            });

        }
        latch.await();
        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;
        System.out.println("Time elapsed: " + timeElapsed);
        assertEquals(0, latch.getCount());
        executorService.shutdownNow();
    }

    @SneakyThrows
    @Test
    @Order(6)
    public void testDisperseSamePartitionOddNumber() throws ExecutionException, InterruptedException {
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);

        CopyOnWriteArrayList<Consumer<String, byte[]>> iterate = new CopyOnWriteArrayList<>();
        KafkaManufactureSmith kafkaManufactureSmith0 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 0);
        KafkaManufactureSmith kafkaManufactureSmith1 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 1);
        KafkaManufactureSmith kafkaManufactureSmith2 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 2);
        KafkaManufactureSmith kafkaManufactureSmith3 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 3);
        kafkaManufactureSmith0.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith1.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith2.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith3.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup0 = kafkaManufactureSmith0.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup1 = kafkaManufactureSmith1.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup2 = kafkaManufactureSmith2.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup3 = kafkaManufactureSmith3.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        iterate.add(consumerSameGroup0.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST));
        iterate.add(consumerSameGroup1.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "1"));
        iterate.add(consumerSameGroup2.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "2"));
        iterate.add(consumerSameGroup3.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "3"));

        int size = 5;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.DISPERSE_PHASE1).name(), i, "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
        }

        producer.getProducer().flush();

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < iterate.size(); i++) {
            int finalI = i;
            Runnable task = () -> {
                ConsumerRecords<String, byte[]> records = iterate.get(finalI).poll(Duration.ofMillis(10000));
                System.out.println(finalI + " " + records.count());
                assertEquals(2, records.count());
                for (ConsumerRecord<String, byte[]> recordf : records) {
                    list.add(recordf.key());
                }
            };
            executorService.submit(task);
        }


        executorService.shutdown();
        executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        executorService.shutdownNow();
        executorService.close();

        iterate.forEach(consumer -> {
            consumer.wakeup();
            consumer.close();
        });
        assertEquals(8, list.size());

    }

    @SneakyThrows
    @Test
    @Order(7)
    public void testDisperseSamePartitionTestPartionSame() throws ExecutionException, InterruptedException {
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        ConcurrentHashMap<Integer, Integer> equal_partitions = new ConcurrentHashMap<>();
        KafkaProducer producer = kafkaSmith.getKafkaComponent(KafkaKingdomType.PRODUCER);

        CopyOnWriteArrayList<Consumer<String, byte[]>> iterate = new CopyOnWriteArrayList<>();
        KafkaManufactureSmith kafkaManufactureSmith0 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 0);
        KafkaManufactureSmith kafkaManufactureSmith1 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 1);
        KafkaManufactureSmith kafkaManufactureSmith2 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 2);
        KafkaManufactureSmith kafkaManufactureSmith3 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 3);
        KafkaManufactureSmith kafkaManufactureSmith4 = new KafkaManufactureSmith(ipList, KafkaConfiguration.KAFKA_HOST, KafkaConfiguration.KAFKA_HOST, 3);
        kafkaManufactureSmith0.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith1.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith2.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith3.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        kafkaManufactureSmith4.manufactureKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup0 = kafkaManufactureSmith0.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup1 = kafkaManufactureSmith1.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup2 = kafkaManufactureSmith2.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup3 = kafkaManufactureSmith3.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        KafkaConsumerSameGroup consumerSameGroup4 = kafkaManufactureSmith4.getKafkaComponent(KafkaKingdomType.CONSUMER_SAME);
        iterate.add(consumerSameGroup0.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST));
        iterate.add(consumerSameGroup1.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "1"));
        iterate.add(consumerSameGroup2.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "2"));
        iterate.add(consumerSameGroup3.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "3"));
        iterate.add(consumerSameGroup4.receiveLeaderConsumer(KafkaConfiguration.KAFKA_HOST + "4"));

        int size = 5;
        for (int i = 0; i < size; i++) {
            producer.getProducer().send(new ProducerRecord<>(TopicFactory.getInstance().getTopicName(TopicType.DISPERSE_PHASE1).name(), i, "key-" + i, ("value-" + i).getBytes(StandardCharsets.UTF_8)));
        }

        producer.getProducer().flush();

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < iterate.size(); i++) {
            int finalI = i;
            Runnable task = () -> {
                ConsumerRecords<String, byte[]> records = iterate.get(finalI).poll(Duration.ofMillis(10000));
                System.out.println(finalI + " " + records.count());
                assertEquals(3, records.count());
                list.add(String.valueOf(records.count()));
                if (finalI == 3 || finalI == 4) {
                    records.partitions().forEach(partition -> {
                        equal_partitions.put(finalI, partition.partition());
                    });
                }
            };
            executorService.submit(task);
        }


        executorService.shutdown();
        executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        executorService.shutdownNow();
        executorService.close();

        iterate.forEach(consumer -> {
            consumer.wakeup();
            consumer.close();
        });
        assertEquals(5, list.size());
        assertEquals(15, list.stream().mapToInt(Integer::parseInt).sum());
        assertEquals(2, equal_partitions.size());
        assertEquals(equal_partitions.get(3), equal_partitions.get(4));
        assertEquals(Integer.valueOf(3), equal_partitions.get(3));

    }

    @SneakyThrows
    @Test
    @Order(8)
    public void tearDown() {
        kafkaSmith.shutDownGracefully();
    }
}
