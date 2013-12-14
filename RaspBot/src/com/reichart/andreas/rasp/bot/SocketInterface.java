package com.reichart.andreas.rasp.bot;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SocketInterface {

    private final static int SERVER_PORT = 5500;
    private boolean inputRunning = true;
    private Lock lock = new ReentrantLock(true);
    private Condition availableCondition = lock.newCondition();
    private SocketChannel socketChannel;
    private Selector selector;
    private final Logger log;
    private final BotDispatcher dispatcher;
    
    
    private ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
    

    public SocketInterface(BotDispatcher dispatcher) throws SocketException, IOException {

	log = LogManager.getLogger(this.getClass());
	this.dispatcher = dispatcher;
	ServerSocketChannel channel = ServerSocketChannel.open();
	channel.socket().bind(new InetSocketAddress(SERVER_PORT));
	channel.configureBlocking(true);

	socketChannel = channel.accept();

	selector = Selector.open();
	socketChannel.register(selector, socketChannel.validOps());

	InThread inputThread = new InThread();
	inputThread.run();
	ProcessingThread procThread = new ProcessingThread();
	procThread.run();

    }
    
    private Inet4Address getAddress() throws SocketException {

	Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
	while (e.hasMoreElements()) {
	    NetworkInterface nInterface = e.nextElement();
	    List<InterfaceAddress> addresses = nInterface.getInterfaceAddresses();
	    for (InterfaceAddress interfaceAddress : addresses) {
		Inet4Address address = (Inet4Address) interfaceAddress.getAddress();
		if (!address.isLoopbackAddress() && !address.isMulticastAddress() && !address.isLinkLocalAddress()) {
		    return address;
		}
	    }
	}
	return null;
    }
   
    
    class InThread extends Thread {
	public InThread() {
	    super("InThread");
	}
	
	@Override
	public void run() {
	    if (socketChannel!=null && inputRunning) {
		try {
		    selector.select();
		} catch (IOException e) {
		    log.error("Error while waiting selector was blocked", e);
		}
		
		Iterator iterator = selector.selectedKeys().iterator();
		
		while (iterator.hasNext()) {
		    
		    SelectionKey key = (SelectionKey) iterator.next();
		    iterator.remove();
		    
		    if (key.isValid() && key.isConnectable()) {
			
			SocketChannel sChannel = (SocketChannel) key.channel();
			boolean established = false;
			try {
			    established = sChannel.finishConnect();
			} catch (IOException e) {
			    // TODO Auto-generated catch block
			    e.printStackTrace();
			}

			if (!established) {
			    log.error("sChannel not established");
			    key.cancel();
			}
		    }
		    if (key.isValid() && key.isReadable()) {
			SocketChannel sChannel = (SocketChannel) key.channel();
			try {
			    lock.lock();
			    buffer.clear();
			    int readLength = 0;

			    readLength = sChannel.read(buffer);

			    if (readLength == -1) {
				sChannel.close();
			    } else {
				buffer.flip();
			    }
			    availableCondition.signal();
			    lock.unlock();
			} catch (IOException e) {
			    log.error("Error while reading from sChannel", e);
			}
		    }
		}
	    }
	}
    }

    class ProcessingThread extends Thread {
	public ProcessingThread() {
	    super("ProcessingThread");
	}

	@Override
	public void run() {
	    while (inputRunning) {
		lock.lock();
		try {
		    availableCondition.await();
		} catch (InterruptedException e) {
		    log.error("Interrupted while waiting for availableCondition", e);
		}
		while (buffer.hasRemaining()){
		    char nextChar = buffer.getChar();
		    switch (nextChar) {
		    case 'l':
			/* Left motor parameter coming in */
			int leftMotorValue = buffer.getInt();
			dispatcher.setLeftMotorValue(leftMotorValue);
			log.trace("Left motor value <"+String.valueOf(leftMotorValue)+">");
			break;
		    case 'r':
			/* Right motor parameter coming in */
			int rightMotorValue = buffer.getInt();
			dispatcher.setRightMotorValue(rightMotorValue);
			log.trace("Right motor value <"+String.valueOf(rightMotorValue)+">");
			break;
		    case 's':
			dispatcher.setRightMotorValue(0);
			dispatcher.setLeftMotorValue(0);
			break;
		    default:
			// Do nothing
		    }
		}
	    }
	}
    }
}
