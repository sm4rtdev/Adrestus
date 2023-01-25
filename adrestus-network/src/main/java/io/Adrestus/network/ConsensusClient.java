package io.Adrestus.network;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

import static io.Adrestus.config.ConsensusConfiguration.*;

public class ConsensusClient {

    private static Logger LOG = LoggerFactory.getLogger(ConsensusClient.class);
    private int MESSAGES = 6;
    private int MAX_MESSAGES = 6;
    private static final int MAX_AVAILABLE = 6;

    private final Semaphore available;
    private final String IP;
    private ZContext ctx;
    private final ZMQ.Socket subscriber;
    private final ZMQ.Socket push;
    private final ZMQ.Socket connected;
    private final LinkedBlockingDeque<byte[]> message_deque;


    public ConsensusClient(String IP) {
        this.ctx = new ZContext();
        this.message_deque = new LinkedBlockingDeque<>();
        this.IP = IP;
        this.available = new Semaphore(MAX_AVAILABLE, true);
        this.subscriber = ctx.createSocket(SocketType.SUB);
        this.push = ctx.createSocket(SocketType.PUSH);

        this.connected = ctx.createSocket(SocketType.REQ);
        this.connected.setReceiveTimeOut(CONSENSUS_CONNECTED_TIMEOUT);
        this.connected.setSendTimeOut(CONSENSUS_CONNECTED_TIMEOUT);

        this.subscriber.setReceiveBufferSize(10000);
        this.subscriber.setLinger(-1);
        this.subscriber.setHWM(10000);
        this.subscriber.setRcvHWM(1);
        this.subscriber.setConflate(true);

        this.subscriber.connect("tcp://" + IP + ":" + SUBSCRIBER_PORT);
        this.connected.connect("tcp://" + IP + ":" + CONNECTED_PORT);
        this.subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
        this.subscriber.setReceiveTimeOut(CONSENSUS_TIMEOUT);
        this.push.connect("tcp://" + IP + ":" + COLLECTOR_PORT);

    }

    public void PollOut() {
        ZMQ.Poller poller = ctx.createPoller(1);
        poller.register(this.subscriber, ZMQ.Poller.POLLOUT);
        int rc = -1;
        while (rc == -1) {
            rc = poller.poll(15000);
        }
        poller.pollout(0);
    }

    public void PollIn() {
        ZMQ.Poller poller = ctx.createPoller(1);
        poller.register(this.subscriber, ZMQ.Poller.POLLIN);
        int rc = -1;
        while (rc == -1) {
            rc = poller.poll(1);
        }
        poller.pollin(0);
    }

    public void pushMessage(byte[] data) {
        push.send(data);
    }

    public byte[] receiveData() {
        byte[] data = subscriber.recv();
        return data;
    }


    @SneakyThrows
    public byte[] deque_message() {
        while (message_deque.isEmpty()) {
        }
        // System.out.println("take");
        return message_deque.pollFirst();
    }

    public void receive_handler() {
        (new Thread() {
            @SneakyThrows
            public void run() {
                byte[] data = {1};
                while (MESSAGES > 0 && MAX_MESSAGES > 0) {
                    //available.acquire();
                    //         System.out.println("acquire");
                    try {
                        data = subscriber.recv();
                    } catch (ZMQException e) {
                        if (e.getErrorCode() != 156384765) {
                            LOG.info("ZMQ EXCEPTION caught");
                        }
                    }
                    catch (NullPointerException exception){
                        LOG.info("NullPointerException caught "+ exception.toString());
                    }
                    if (data != null) {
                        message_deque.add(data);
                        MESSAGES--;
                    } else {
                        message_deque.add(new byte[0]);
                        break;
                    }
                    // System.out.println("receive" + MESSAGES);
                    MAX_MESSAGES--;
                    // available.release();
                }
            }
        }).start();
    }

    public String rec_heartbeat() {
        return this.connected.recvStr(0);
    }

    public void send_heartbeat(String data) {
        this.connected.send(data);
    }

    public void close() {
        this.subscriber.close();
        this.push.close();
        this.ctx.close();
    }

}
