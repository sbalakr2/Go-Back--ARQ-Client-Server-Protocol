package gopack;

import java.io.*;
import java.net.*;
import java.util.Random;

import static java.net.InetAddress.getByName;


public class gobackClient extends Thread{
    private int transmitted  = -1;
    private volatile int acknowledged  = -1;
    private int timeout = 1000;
    private String server;
    private int serverPort;
    private String fileName;
    private int windowSize;
    private int MaxSegSize;
    private int pktCount;
    private int lastPkt;
    private DatagramSocket datagramSocket = null;
    private final goPacket buffer[];

    public gobackClient(String server, int serverPort, String fileName, int WindowSize,
    			int MSS) throws SocketException, UnknownHostException {
    	this.server = server;
    	this.serverPort = serverPort;
    	this.fileName = fileName;
    	this.windowSize = WindowSize;
    	this.MaxSegSize = MSS;
    	Random random = new Random();
    	int portNum = random.nextInt(5000) + 1000;	
    	datagramSocket = new DatagramSocket(portNum);
    	datagramSocket.connect(getByName(server), serverPort);
    	buffer = new goPacket[this.windowSize];
    	System.out.println("Client running at " + InetAddress.getLocalHost().getHostAddress()
    			+ " on port " + datagramSocket.getLocalPort());
    	System.out.println("The maximum segment size is "+ MaxSegSize);
}

    private class MorePackets extends Thread{
        private final DatagramSocket datagramSock;
        private volatile boolean transfer;
        private boolean connection;
        public MorePackets(DatagramSocket datasocket) {
            this.datagramSock = datasocket;
            transfer = true;
        }

        public void finish(){
            transfer = false;
        }

        public void run(){
            while(transfer){
            	int isSockOpen;
                byte databuffer[] = new byte[1024 * 1000 * 2];
                int length = databuffer.length;
                DatagramPacket datagrampacket = new DatagramPacket(databuffer, length);

                try {
                	connection = datagramSock.isClosed();
                    if(!connection)
                    	isSockOpen = 1;
                    else
                    	isSockOpen = 0;
                    if(isSockOpen == 1) {
                    	datagramSock.receive(datagrampacket);
                        ObjectInputStream outputStream = new ObjectInputStream(new ByteArrayInputStream(datagrampacket.getData()));
                        goPacket packet = (goPacket) outputStream.readObject();
                        if (packet.type == (short)43690)
                        	acknowledged = packet.sequencenumber;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Error");
                }
            }
        }
    }

    public void run(){
        try{
        	boolean isOpen = true;
        	if(isOpen) {
	            long startTime = System.currentTimeMillis();
	            File file = new File(fileName);
	            if(!file.exists()){
	            	System.out.println("Error: 404 File Not Found - the specified file does not exist");
	            } else{
	            	  int size = (int)file.length();
	  	            pktCount = size/MaxSegSize;
	  	            lastPkt = size % MaxSegSize;
	  	            byte data[] = new byte[MaxSegSize];
	  	            MorePackets newpkt = null;
	  	            
	  	            int lostPktCnt = 0;
	  	            int isNext = 1;
	  	            FileInputStream fis = new FileInputStream(file);
	  	            while(fis.read(data) > -1){
	  	                lostPktCnt = calLostPackets(lostPktCnt);
	  	                sendToServer(data);
	  	                if(isNext == 1){
	  	                    isNext = 0;
	  	                    newpkt = new MorePackets(datagramSocket);
	  	                    newpkt.start();
	  	                }
	  	                
	  	                int tempAcked = acknowledged;
	                  	lostPktCnt = calTimer(lostPktCnt, tempAcked);
	  	            }
	  	            revokeThread(startTime, fis, newpkt, lostPktCnt, isOpen);
	            }
        	}
        } catch (IOException e) {
           System.out.println("Error: Unable to reach the server");
        }
    }

	private void revokeThread(long startTime, FileInputStream fis,
			MorePackets newpkt, int lostPktCnt, boolean isOpen) throws IOException {
		fis.close();
		long endTime = System.currentTimeMillis();	
		sendPacket(null);
		newpkt.finish();
		isOpen = false;
		datagramSocket.close();
		System.out.println("Delay: " + (endTime - startTime));
		System.out.println("Packets Lost: " + lostPktCnt);	            
		datagramSocket.close();
	}

	private int calTimer(int lostPktCnt, int tempAcked) throws IOException {
		boolean success = false;
		while(tempAcked != transmitted) {
		    if(System.currentTimeMillis() - buffer[(tempAcked+1) % windowSize].sentTime <= timeout){
		    	success = true;
		    }
		    else {
		    	success = false;
		    }
			if(!success) {
		        int j = 0;
		        while(j < (transmitted - tempAcked)) {
		            System.out.println("Timeout, sequence number = " + buffer[(tempAcked + 1 + j) % windowSize].sequencenumber);
		            lostPktCnt++;
		            sendPacket(buffer[(tempAcked + 1 + j) % windowSize]);
		            j++;
		        }
		    }
		    tempAcked = acknowledged;
		}
		return lostPktCnt;
	}

	private int calLostPackets(int lostPktCnt) throws IOException {
		boolean success;
		while(transmitted - acknowledged == windowSize) {
		    if(System.currentTimeMillis() - buffer[(acknowledged+1) % windowSize].sentTime <= timeout){
		    	success = true;
		    }
		    else {
		    	success = false;
		    }
			if(!success) {
		        int tempAck = acknowledged;
		        int i = 0;
		        while(i < (transmitted - acknowledged)) {
		            System.out.println("Timeout, sequence number = " + buffer[(tempAck + 1 + i) % windowSize].sequencenumber);
		            lostPktCnt++;
		            sendPacket(buffer[(tempAck+1+i) % windowSize]);
		            i++;
		        }
		    }
		}
		return lostPktCnt;
	}

    private void sendToServer(byte data[]) throws IOException {
    	byte firstData[] = new byte[MaxSegSize];
        short dataType = (short)21845;
    	byte lastData[] = new byte[lastPkt];
        firstData = data;
        transmitted++;
        if(transmitted == pktCount) {
        	for(int k = 0 ; k < lastPkt ; k++){
        		lastData[k] = firstData[k];
        	}
        	firstData = null;
        	firstData = lastData;
        }
        int checksum = checksum(firstData);
        goPacket packet = new goPacket(transmitted, checksum, dataType, firstData);
        int index = (transmitted % windowSize);
        buffer[index] = packet;
        sendPacket(packet) ;
    }
    

    private void sendPacket(goPacket packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream outStream = new ObjectOutputStream(baos);
        outStream.writeObject(packet);
        byte[] sendData = baos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
        		getByName(server), serverPort);
        if(packet != null){
        	packet.sentTime = System.currentTimeMillis();
        	}else
        	{
        		//System.out.println("No data to send");
        	}
        	
        datagramSocket.send(sendPacket);
    }

    private static int checksum(byte data[]) {
        int checkSumValue = 0;
        if (data == null) return 0;
        try {
        	for(int i = 0; i < data.length; i++) {
            	checkSumValue = ((i % 2) == 0) ? (checkSumValue + ((data[i] << 8) & 0xFF00)) 
            			: (checkSumValue + ((data[i]) & 0xFF));

	        	if((data.length % 2) != 0){
	        		checkSumValue = checkSumValue + 0xFF;
	        	}
	
	            while ((checkSumValue >> 16) == 1){
	            	 checkSumValue =  ((checkSumValue & 0xFFFF) + (checkSumValue >> 16));
	                 checkSumValue =  ~checkSumValue;
	            }
        	} 
        }
        catch (Exception e) {}
        return checkSumValue;
    }

    public static void main(String[] args) throws InterruptedException {
    	if(args.length != 5 ) return;
    	try{
    		String server = args[0];
    		int serverPort = Integer.parseInt(args[1]);
    		String filename = args[2];
    		int windowsize = Integer.parseInt(args[3]);
    		int MSS = Integer.parseInt(args[4]);
    		new gobackClient(server, serverPort, filename, windowsize, MSS).start();
    	} catch (UnknownHostException | SocketException e) {
    		System.out.println("The file transfer is completed");
    	}
    }
    }
